package com.stellarcielo.loginetwork.network;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.stellarcielo.loginetwork.LogiNetwork;
import com.stellarcielo.loginetwork.network.payload.TransitHopPayload;
import com.stellarcielo.loginetwork.network.payload.TransitRemovePayload;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

// Per-dimension owner of everything moving through cables: extraction, routing, movement and delivery.
// Phase 3 rules: push from extract-mode sides to the nearest accepting inventory; never delete items.
public final class NetworkManager extends SavedData {
    public static final int TICKS_PER_BLOCK = 4;
    public static final int EXTRACT_INTERVAL = 20;
    public static final int EXTRACT_AMOUNT = 8;
    private static final int MAX_REROUTES = 8;

    private static final Codec<NetworkManager> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("next_id").forGetter(m -> m.nextId),
            TransitPacket.CODEC.listOf().fieldOf("packets").forGetter(m -> m.packets),
            // Saved under its phase 3 name so existing worlds keep their extract settings.
            BlockPos.CODEC.listOf().fieldOf("extract_cables").forGetter(m -> List.copyOf(m.extractNodes))
    ).apply(instance, NetworkManager::new));

    public static final SavedDataType<NetworkManager> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(LogiNetwork.MOD_ID, "network"), NetworkManager::new, CODEC);

    private long nextId;
    private final List<TransitPacket> packets;
    // Nodes with at least one extracting side.
    private final Set<BlockPos> extractNodes;
    // Route cache keyed by origin cable; cleared whenever the topology changes.
    private final Map<BlockPos, CableNetwork> networkCache = new HashMap<>();

    public NetworkManager() {
        this(0L, List.of(), List.of());
    }

    private NetworkManager(long nextId, List<TransitPacket> packets, List<BlockPos> extractNodes) {
        this.nextId = nextId;
        this.packets = new ArrayList<>(packets);
        this.extractNodes = new HashSet<>(extractNodes);
    }

    public static NetworkManager get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public List<TransitPacket> packets() {
        return packets;
    }

    // ---- topology -------------------------------------------------------------------------------

    public void onNodeChanged(BlockPos pos, BlockState state) {
        networkCache.clear();
        boolean extracts = !NetworkNodes.extractSides(state).isEmpty();
        boolean changed = extracts ? extractNodes.add(pos.immutable()) : extractNodes.remove(pos);
        if (changed) {
            setDirty();
        }
    }

    // Chunk loads and unloads change which cables a search can see.
    public void invalidateRoutes() {
        networkCache.clear();
    }

    public void onNodeRemoved(BlockPos pos) {
        networkCache.clear();
        if (extractNodes.remove(pos)) {
            setDirty();
        }
    }

    public CableNetwork network(ServerLevel level, BlockPos origin) {
        return networkCache.computeIfAbsent(origin.immutable(), o -> CableNetwork.explore(level, o));
    }

    // ---- tick -----------------------------------------------------------------------------------

    public void tick(ServerLevel level) {
        long gameTime = level.getGameTime();
        if (gameTime % EXTRACT_INTERVAL == 0) {
            extractAll(level, gameTime);
        }
        if (packets.removeIf(packet -> advance(level, packet, gameTime))) {
            setDirty();
        }
    }

    private void extractAll(ServerLevel level, long gameTime) {
        Iterator<BlockPos> it = extractNodes.iterator();
        while (it.hasNext()) {
            BlockPos nodePos = it.next();
            if (!level.isLoaded(nodePos)) {
                continue;
            }
            List<Direction> sides = NetworkNodes.extractSides(level.getBlockState(nodePos));
            if (sides.isEmpty()) {
                it.remove();
                setDirty();
                continue;
            }
            for (Direction side : sides) {
                extractFrom(level, nodePos, side, gameTime);
            }
        }
    }

    private void extractFrom(ServerLevel level, BlockPos nodePos, Direction side, long gameTime) {
        BlockPos inventoryPos = nodePos.relative(side);
        ResourceHandler<ItemResource> source = itemHandler(level, inventoryPos, side.getOpposite());
        if (source == null) {
            return;
        }
        CableNetwork network = network(level, nodePos);
        for (int slot = 0; slot < source.size(); slot++) {
            ItemResource resource = source.getResource(slot);
            if (resource.isEmpty()) {
                continue;
            }
            int wanted = Math.min(EXTRACT_AMOUNT, source.getAmountAsInt(slot));
            Destination destination = findDestination(level, network, resource, wanted, inventoryPos);
            if (destination == null) {
                continue;
            }
            int extracted;
            try (Transaction tx = Transaction.openRoot()) {
                extracted = source.extract(slot, resource, Math.min(wanted, destination.accepts), tx);
                tx.commit();
            }
            if (extracted <= 0) {
                continue;
            }
            List<BlockPos> nodes = new ArrayList<>();
            nodes.add(inventoryPos);
            nodes.addAll(network.pathTo(destination.endpoint.nodePos()));
            nodes.add(destination.endpoint.inventoryPos());
            TransitPacket packet = new TransitPacket(nextId++, resource.toStack(extracted), nodes, true, 0, gameTime, 0, inventoryPos, false, 0);
            startHop(level, packet, gameTime);
            packets.add(packet);
            setDirty();
            return;
        }
    }

    // Returns true when the packet has left the cables.
    private boolean advance(ServerLevel level, TransitPacket packet, long gameTime) {
        int next = packet.hop + 1;
        if (!level.isLoaded(packet.nodes.get(packet.hop)) || !level.isLoaded(packet.nodes.get(next))) {
            // Pause while any part of the current segment is unloaded.
            packet.hopStart++;
            return false;
        }
        if (isBroken(level, packet, packet.hop) || isBroken(level, packet, next)) {
            drop(level, packet, packet.currentPosition(gameTime));
            return true;
        }
        if (gameTime < packet.hopStart + packet.hopDuration) {
            return false;
        }
        if (next == packet.lastIndex()) {
            return deliver(level, packet, gameTime);
        }
        BlockPos following = packet.nodes.get(next + 1);
        if (!level.isLoaded(following)) {
            return false;
        }
        if (isBroken(level, packet, next + 1)) {
            // The route ahead is gone; find another way from the node we are standing on.
            return reroute(level, packet, packet.nodes.get(next), null, gameTime);
        }
        packet.hop = next;
        startHop(level, packet, gameTime);
        setDirty();
        return false;
    }

    private static boolean isBroken(ServerLevel level, TransitPacket packet, int index) {
        return !packet.isInventoryNode(index) && !NetworkNodes.isNode(level.getBlockState(packet.nodes.get(index)));
    }

    private boolean deliver(ServerLevel level, TransitPacket packet, long gameTime) {
        BlockPos inventoryPos = packet.destination();
        BlockPos lastNode = packet.nodes.get(packet.lastIndex() - 1);
        Direction side = directionBetween(inventoryPos, lastNode);
        ResourceHandler<ItemResource> target = itemHandler(level, inventoryPos, side);
        if (target != null) {
            ItemResource resource = ItemResource.of(packet.stack);
            int inserted;
            try (Transaction tx = Transaction.openRoot()) {
                inserted = target.insert(resource, packet.stack.getCount(), tx);
                tx.commit();
            }
            packet.stack.shrink(inserted);
        }
        if (packet.stack.isEmpty()) {
            sendRemove(level, packet);
            return true;
        }
        // Leftovers come back out of the inventory face and look for somewhere else to go.
        return reroute(level, packet, lastNode, inventoryPos, gameTime);
    }

    // Order: another destination, then back to the source, then drop on the spot.
    private boolean reroute(ServerLevel level, TransitPacket packet, BlockPos fromNode, @Nullable BlockPos comingOutOf, long gameTime) {
        Vec3 here = packet.currentPosition(gameTime);
        if (packet.reroutes >= MAX_REROUTES || !NetworkNodes.isNode(level.getBlockState(fromNode))) {
            drop(level, packet, here);
            return true;
        }
        packet.reroutes++;
        CableNetwork network = network(level, fromNode);
        ItemResource resource = ItemResource.of(packet.stack);
        Destination destination = null;
        if (!packet.returning) {
            destination = findDestination(level, network, resource, packet.stack.getCount(), packet.source, comingOutOf);
        }
        if (destination == null) {
            destination = findReturn(level, network, resource, packet.stack.getCount(), packet.source);
            packet.returning = true;
        }
        if (destination == null) {
            drop(level, packet, here);
            return true;
        }
        List<BlockPos> nodes = new ArrayList<>();
        if (comingOutOf != null) {
            nodes.add(comingOutOf);
        }
        nodes.addAll(network.pathTo(destination.endpoint.nodePos()));
        nodes.add(destination.endpoint.inventoryPos());
        packet.setRoute(nodes, comingOutOf != null);
        startHop(level, packet, gameTime);
        setDirty();
        return false;
    }

    private void drop(ServerLevel level, TransitPacket packet, Vec3 at) {
        ItemEntity entity = new ItemEntity(level, at.x, at.y, at.z, packet.stack.copy());
        entity.setDefaultPickUpDelay();
        level.addFreshEntity(entity);
        sendRemove(level, packet);
    }

    // ---- routing --------------------------------------------------------------------------------

    private record Destination(CableNetwork.Endpoint endpoint, int accepts) {}

    private @Nullable Destination findDestination(ServerLevel level, CableNetwork network, ItemResource resource, int amount, BlockPos... excluded) {
        for (CableNetwork.Endpoint endpoint : network.endpoints()) {
            if (endpoint.extract() || isExcluded(endpoint.inventoryPos(), excluded)) {
                continue;
            }
            int accepts = simulateInsert(level, endpoint, resource, amount);
            if (accepts > 0) {
                return new Destination(endpoint, accepts);
            }
        }
        return null;
    }

    private @Nullable Destination findReturn(ServerLevel level, CableNetwork network, ItemResource resource, int amount, BlockPos source) {
        for (CableNetwork.Endpoint endpoint : network.endpoints()) {
            if (endpoint.inventoryPos().equals(source) && simulateInsert(level, endpoint, resource, amount) > 0) {
                return new Destination(endpoint, amount);
            }
        }
        return null;
    }

    private static boolean isExcluded(BlockPos pos, BlockPos[] excluded) {
        for (BlockPos other : excluded) {
            if (other != null && other.equals(pos)) {
                return true;
            }
        }
        return false;
    }

    private static int simulateInsert(ServerLevel level, CableNetwork.Endpoint endpoint, ItemResource resource, int amount) {
        ResourceHandler<ItemResource> handler = itemHandler(level, endpoint.inventoryPos(), endpoint.side().getOpposite());
        if (handler == null) {
            return 0;
        }
        try (Transaction tx = Transaction.openRoot()) {
            return handler.insert(resource, amount, tx);
        }
    }

    private static @Nullable ResourceHandler<ItemResource> itemHandler(ServerLevel level, BlockPos pos, Direction side) {
        if (!level.isLoaded(pos)) {
            return null;
        }
        return level.getCapability(Capabilities.Item.BLOCK, pos, side);
    }

    private static Direction directionBetween(BlockPos from, BlockPos to) {
        BlockPos delta = to.subtract(from);
        Direction direction = Direction.getNearest(delta, null);
        if (direction == null) {
            throw new IllegalStateException("Route nodes are not adjacent: " + from + " -> " + to);
        }
        return direction;
    }

    // ---- client sync ----------------------------------------------------------------------------

    private void startHop(ServerLevel level, TransitPacket packet, long gameTime) {
        Vec3 from = packet.pointOf(packet.hop);
        Vec3 to = packet.pointOf(packet.hop + 1);
        packet.hopStart = gameTime;
        packet.hopDuration = Math.max(1, Mth.ceil(from.distanceTo(to) * TICKS_PER_BLOCK));
        TransitHopPayload payload = hopPayload(packet);
        ChunkPos fromChunk = ChunkPos.containing(BlockPos.containing(from));
        ChunkPos toChunk = ChunkPos.containing(BlockPos.containing(to));
        PacketDistributor.sendToPlayersTrackingChunk(level, fromChunk, payload);
        if (!toChunk.equals(fromChunk)) {
            PacketDistributor.sendToPlayersTrackingChunk(level, toChunk, payload);
        }
    }

    private static TransitHopPayload hopPayload(TransitPacket packet) {
        return new TransitHopPayload(packet.id, packet.stack.copy(), packet.pointOf(packet.hop), packet.pointOf(packet.hop + 1),
                packet.hopStart, packet.hopDuration);
    }

    private static void sendRemove(ServerLevel level, TransitPacket packet) {
        Vec3 at = packet.pointOf(Math.min(packet.hop + 1, packet.lastIndex()));
        PacketDistributor.sendToPlayersTrackingChunk(level, ChunkPos.containing(BlockPos.containing(at)), new TransitRemovePayload(packet.id));
        Vec3 from = packet.pointOf(packet.hop);
        PacketDistributor.sendToPlayersTrackingChunk(level, ChunkPos.containing(BlockPos.containing(from)), new TransitRemovePayload(packet.id));
    }

    // Brings a player who just started watching a chunk up to date with packets passing through it.
    public void sendChunkState(ServerPlayer player, ChunkPos chunk) {
        for (TransitPacket packet : packets) {
            if (ChunkPos.containing(BlockPos.containing(packet.pointOf(packet.hop))).equals(chunk)
                    || ChunkPos.containing(BlockPos.containing(packet.pointOf(packet.hop + 1))).equals(chunk)) {
                PacketDistributor.sendToPlayer(player, hopPayload(packet));
            }
        }
    }
}
