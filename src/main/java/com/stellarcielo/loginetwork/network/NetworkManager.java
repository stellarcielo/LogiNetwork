package com.stellarcielo.loginetwork.network;

import java.util.ArrayList;
import java.util.Comparator;
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
import com.stellarcielo.loginetwork.address.Ipv4Address;
import com.stellarcielo.loginetwork.block.AccessSwitchBlock;
import com.stellarcielo.loginetwork.block.CableBlock;
import com.stellarcielo.loginetwork.block.CableType;
import com.stellarcielo.loginetwork.block.NetworkInterfaceBlock;
import com.stellarcielo.loginetwork.block.RouterBlock;
import com.stellarcielo.loginetwork.block.entity.NetworkInterfaceBlockEntity;
import com.stellarcielo.loginetwork.network.control.ControlPlane;
import com.stellarcielo.loginetwork.network.payload.TransitHopPayload;
import com.stellarcielo.loginetwork.network.routing.RoutingService;
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
import net.minecraft.world.level.block.Block;
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
    public static final int EXTRACT_INTERVAL = 20;
    // Packets a switch or router holds (queued or arriving) before senders have to wait or divert to a discard chest.
    public static final int QUEUE_CAPACITY = 8;
    public static final int EXTRACT_AMOUNT = 8;
    private static final int MAX_REROUTES = 8;

    private static final Codec<NetworkManager> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("next_id").forGetter(m -> m.nextId),
            TransitPacket.CODEC.listOf().fieldOf("packets").forGetter(m -> m.packets),
            // Saved under its phase 3 name so existing worlds keep their extract settings.
            BlockPos.CODEC.listOf().fieldOf("extract_cables").forGetter(m -> List.copyOf(m.extractNodes)),
            Codec.LONG.optionalFieldOf("next_request_id", 1L).forGetter(m -> m.nextRequestId),
            Codec.INT.optionalFieldOf("next_dhcp_subnet", 1).forGetter(m -> m.nextDhcpSubnet)
    ).apply(instance, NetworkManager::new));

    public static final SavedDataType<NetworkManager> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(LogiNetwork.MOD_ID, "network"), NetworkManager::new, CODEC);

    private long nextId;
    private final List<TransitPacket> packets;
    // Nodes with at least one extracting side.
    private final Set<BlockPos> extractNodes;
    // Segment cache keyed by origin node (or router port); cleared whenever the topology changes.
    private final Map<Object, CableNetwork> networkCache = new HashMap<>();
    private long nextRequestId;
    private int nextDhcpSubnet;
    private final ControlPlane controlPlane = new ControlPlane(this);
    // Egress ports (node + direction) busy sending until the given tick.
    private final Map<PortKey, Long> portBusyUntil = new HashMap<>();
    // Packets queued at or travelling into each switch/router, rebuilt every tick.
    private final Map<BlockPos, Integer> queueLoads = new HashMap<>();

    public NetworkManager() {
        this(0L, List.of(), List.of(), 1L, 1);
    }

    private NetworkManager(long nextId, List<TransitPacket> packets, List<BlockPos> extractNodes, long nextRequestId, int nextDhcpSubnet) {
        this.nextId = nextId;
        this.nextDhcpSubnet = nextDhcpSubnet;
        this.nextRequestId = nextRequestId;
        this.packets = new ArrayList<>(packets);
        this.extractNodes = new HashSet<>(extractNodes);
    }

    public static NetworkManager get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public List<TransitPacket> packets() {
        return packets;
    }

    public ControlPlane controlPlane() {
        return controlPlane;
    }

    // Index of the next default DHCP pool, so newly placed servers do not hand out overlapping addresses.
    public int allocateDhcpSubnet() {
        setDirty();
        return nextDhcpSubnet++;
    }

    public long nextRequestId() {
        setDirty();
        return nextRequestId++;
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
        return networkCache.computeIfAbsent(origin.immutable(), o -> CableNetwork.explore(level, (BlockPos) o));
    }

    // A router port (for segment caching) or any node's egress port (for bandwidth).
    private record PortKey(BlockPos nodePos, Direction port) {}

    // The segment on one port of a router.
    public CableNetwork portNetwork(ServerLevel level, BlockPos routerPos, Direction port) {
        return networkCache.computeIfAbsent(new PortKey(routerPos.immutable(), port), k -> CableNetwork.explorePort(level, routerPos, port));
    }

    // ---- tick -----------------------------------------------------------------------------------

    public void tick(ServerLevel level) {
        long gameTime = level.getGameTime();
        if (gameTime % EXTRACT_INTERVAL == 0) {
            extractAll(level, gameTime);
        }
        // Queued packets leave in QoS order: higher class first, then first come, first served.
        packets.sort(Comparator.comparingInt((TransitPacket packet) -> -packet.priority).thenComparingLong(packet -> packet.waitSince));
        rebuildQueueLoads(level);
        if (packets.removeIf(packet -> advance(level, packet, gameTime))) {
            setDirty();
        }
        portBusyUntil.values().removeIf(until -> until < gameTime);
        controlPlane.tick(level);
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
            launch(level, new TransitPacket(nextId++, resource.toStack(extracted), nodes, true, 0, gameTime, 0, inventoryPos, false, 0,
                    TransitPacket.NO_REQUEST, null, null), gameTime);
            return;
        }
    }

    private void launch(ServerLevel level, TransitPacket packet, long gameTime) {
        startHop(level, packet, gameTime);
        packets.add(packet);
        setDirty();
    }

    // ---- request deliveries ---------------------------------------------------------------------

    // Whether the interface holding an address can be reached from an interface right now.
    public boolean canShip(ServerLevel level, BlockPos fromInterface, Ipv4Address destination) {
        return routeToInventory(level, fromInterface, destination) != null;
    }

    // Path from a node to the inventory in front of the interface holding an address (inventory included).
    private @Nullable List<BlockPos> routeToInventory(ServerLevel level, BlockPos from, Ipv4Address destination) {
        List<BlockPos> path = RoutingService.resolve(level, from, destination);
        if (path == null || path.isEmpty()) {
            return null;
        }
        BlockState target = level.getBlockState(path.get(path.size() - 1));
        if (!(target.getBlock() instanceof NetworkInterfaceBlock)) {
            return null;
        }
        List<BlockPos> withInventory = new ArrayList<>(path);
        withInventory.add(path.get(path.size() - 1).relative(target.getValue(NetworkInterfaceBlock.FACING)));
        return withInventory;
    }

    // Sends items already taken out of the supplier's inventory, in packets of at most one stack.
    public void ship(ServerLevel level, BlockPos fromInterface, Ipv4Address destination, ItemStack items, long requestId, @Nullable Ipv4Address supplier) {
        BlockPos supplierInventory = fromInterface.relative(level.getBlockState(fromInterface).getValue(NetworkInterfaceBlock.FACING));
        List<BlockPos> route = routeToInventory(level, fromInterface, destination);
        long gameTime = level.getGameTime();
        int packetSize = Math.max(1, items.getMaxStackSize());
        while (!items.isEmpty()) {
            ItemStack part = items.split(packetSize);
            if (route == null) {
                // The route vanished between RESERVE and shipping: put the items back, or drop them if they no longer fit.
                Block.popResource(level, supplierInventory, returnToInventory(level, fromInterface, supplierInventory, part));
                continue;
            }
            List<BlockPos> nodes = new ArrayList<>();
            nodes.add(supplierInventory);
            nodes.addAll(route);
            launch(level, new TransitPacket(nextId++, part, nodes, true, 0, gameTime, 0, supplierInventory, false, 0, requestId, supplier, destination), gameTime);
        }
    }

    private static ItemStack returnToInventory(ServerLevel level, BlockPos interfacePos, BlockPos inventoryPos, ItemStack stack) {
        ResourceHandler<ItemResource> handler = itemHandler(level, inventoryPos, directionBetween(inventoryPos, interfacePos));
        if (handler == null || stack.isEmpty()) {
            return stack;
        }
        try (Transaction tx = Transaction.openRoot()) {
            int inserted = handler.insert(ItemResource.of(stack), stack.getCount(), tx);
            tx.commit();
            return stack.copyWithCount(stack.getCount() - inserted);
        }
    }

    // Returns true when the packet has left the cables.
    private boolean advance(ServerLevel level, TransitPacket packet, long gameTime) {
        if (!packet.waiting) {
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
            // Arrived at a node; from here on it waits until it may be sent on.
            packet.hop = next;
            packet.waiting = true;
            packet.waitSince = gameTime;
            setDirty();
            if (isDevice(level.getBlockState(packet.currentNode()))) {
                showInQueue(level, packet);
            }
        }
        return depart(level, packet, gameTime);
    }

    // Sends a waiting packet on when the next device has room and our outgoing port is free.
    private boolean depart(ServerLevel level, TransitPacket packet, long gameTime) {
        BlockPos here = packet.currentNode();
        BlockPos following = packet.nodes.get(packet.hop + 1);
        if (!level.isLoaded(here) || !level.isLoaded(following)) {
            return false;
        }
        if (isBroken(level, packet, packet.hop)) {
            drop(level, packet, packet.pointOf(packet.hop));
            return true;
        }
        if (isBroken(level, packet, packet.hop + 1)) {
            // The route ahead is gone; find another way from the node we are standing on.
            return reroute(level, packet, here, null, gameTime);
        }
        boolean intoNode = packet.hop + 1 < packet.lastIndex();
        BlockState followingState = level.getBlockState(following);
        if (intoNode && isDevice(followingState) && queueLoads.getOrDefault(following, 0) >= QUEUE_CAPACITY) {
            // The next device is full: spill into a discard chest if this segment has one, otherwise wait here.
            divertToDiscard(level, packet, here, gameTime);
            return false;
        }
        BlockState hereState = level.getBlockState(here);
        if (intoNode && isTransmitter(hereState)) {
            PortKey port = new PortKey(here, directionBetween(here, following));
            if (portBusyUntil.getOrDefault(port, 0L) > gameTime) {
                return false;
            }
            portBusyUntil.put(port, gameTime + mediaBetween(hereState, followingState).serializationTicks(packet.stack.getCount()));
        }
        if (isDevice(hereState)) {
            queueLoads.merge(here, -1, Integer::sum);
        }
        if (intoNode && isDevice(followingState)) {
            queueLoads.merge(following, 1, Integer::sum);
        }
        packet.waiting = false;
        startHop(level, packet, gameTime);
        setDirty();
        return false;
    }

    private void divertToDiscard(ServerLevel level, TransitPacket packet, BlockPos here, long gameTime) {
        if (!isNodeWithSegment(level.getBlockState(here))) {
            return;
        }
        CableNetwork network = network(level, here);
        for (CableNetwork.Endpoint endpoint : network.endpoints()) {
            if (!endpoint.discard() || simulateInsert(level, endpoint, ItemResource.of(packet.stack), packet.stack.getCount()) <= 0) {
                continue;
            }
            List<BlockPos> path = network.pathTo(endpoint.nodePos());
            if (path.stream().anyMatch(pos -> queueLoads.getOrDefault(pos, 0) >= QUEUE_CAPACITY && isDevice(level.getBlockState(pos)))) {
                continue;
            }
            List<BlockPos> nodes = new ArrayList<>(path);
            nodes.add(endpoint.inventoryPos());
            packet.setRoute(nodes, false);
            startHop(level, packet, gameTime);
            setDirty();
            return;
        }
    }

    private void rebuildQueueLoads(ServerLevel level) {
        queueLoads.clear();
        for (TransitPacket packet : packets) {
            BlockPos node = packet.waiting ? packet.currentNode() : packet.hop + 1 < packet.lastIndex() ? packet.nodes.get(packet.hop + 1) : null;
            if (node != null && level.isLoaded(node) && isDevice(level.getBlockState(node))) {
                queueLoads.merge(node, 1, Integer::sum);
            }
        }
    }

    // Whether a switch or router has a full input queue (used for control packet loss).
    public boolean isCongested(BlockPos devicePos) {
        return queueLoads.getOrDefault(devicePos, 0) >= QUEUE_CAPACITY;
    }

    public int queueLength(BlockPos devicePos) {
        return queueLoads.getOrDefault(devicePos, 0);
    }

    private static boolean isDevice(BlockState state) {
        return state.getBlock() instanceof AccessSwitchBlock || state.getBlock() instanceof RouterBlock;
    }

    private static boolean isTransmitter(BlockState state) {
        return isDevice(state) || state.getBlock() instanceof NetworkInterfaceBlock;
    }

    // Routers only see the segment on one port, so searching "from" a router is meaningless.
    private static boolean isNodeWithSegment(BlockState state) {
        return NetworkNodes.isNode(state) && !(state.getBlock() instanceof RouterBlock);
    }

    // The cable a hop runs over decides its speed and bandwidth; the slower end wins.
    private static CableType mediaBetween(BlockState a, BlockState b) {
        CableType first = CableBlock.typeOf(a);
        CableType second = CableBlock.typeOf(b);
        if (first == null) {
            return second == null ? CableType.COPPER : second;
        }
        if (second == null) {
            return first;
        }
        return first.ticksPerBlock() >= second.ticksPerBlock() ? first : second;
    }

    // Draws a queued packet in a line in front of the device instead of inside it.
    private void showInQueue(ServerLevel level, TransitPacket packet) {
        Vec3 device = packet.pointOf(packet.hop);
        Vec3 back = packet.pointOf(packet.hop - 1).subtract(device).normalize();
        int place = Math.max(0, queueLoads.getOrDefault(packet.currentNode(), 1) - 1);
        Vec3 spot = device.add(back.scale(0.55 + 0.3 * place));
        TransitHopPayload payload = new TransitHopPayload(packet.id, packet.stack.copy(), spot, spot, level.getGameTime(), 1);
        PacketDistributor.sendToPlayersTrackingChunk(level, ChunkPos.containing(BlockPos.containing(spot)), payload);
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
            if (inserted > 0 && packet.requestId != TransitPacket.NO_REQUEST
                    && level.getBlockEntity(lastNode) instanceof NetworkInterfaceBlockEntity requester) {
                requester.host().onDelivered(level, packet.requestId, packet.stack, inserted, packet.replyAddress);
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

    // Push packets: another destination in the segment, then back to the source, then drop on the spot.
    // Request deliveries: a new route to the requester (link broke), else back to the supplier, then as above.
    private boolean reroute(ServerLevel level, TransitPacket packet, BlockPos fromNode, @Nullable BlockPos comingOutOf, long gameTime) {
        Vec3 here = packet.currentPosition(gameTime);
        if (packet.reroutes >= MAX_REROUTES || !NetworkNodes.isNode(level.getBlockState(fromNode))) {
            drop(level, packet, here);
            return true;
        }
        packet.reroutes++;
        if (packet.requestId != TransitPacket.NO_REQUEST) {
            List<BlockPos> route = null;
            if (!packet.returning && comingOutOf == null && packet.destinationAddress != null) {
                route = routeToInventory(level, fromNode, packet.destinationAddress);
            }
            if (route == null && packet.replyAddress != null) {
                route = routeToInventory(level, fromNode, packet.replyAddress);
                packet.returning = route != null || packet.returning;
            }
            if (route != null) {
                List<BlockPos> nodes = new ArrayList<>();
                if (comingOutOf != null) {
                    nodes.add(comingOutOf);
                }
                nodes.addAll(route);
                packet.setRoute(nodes, comingOutOf != null);
                startHop(level, packet, gameTime);
                setDirty();
                return false;
            }
        }
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
            if (endpoint.extract() || endpoint.discard() || isExcluded(endpoint.inventoryPos(), excluded)) {
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
        CableType media = mediaBetween(level.getBlockState(packet.nodes.get(packet.hop)), level.getBlockState(packet.nodes.get(packet.hop + 1)));
        packet.hopStart = gameTime;
        packet.hopDuration = Math.max(1, Mth.ceil(from.distanceTo(to) * media.ticksPerBlock()));
        TransitHopPayload payload = hopPayload(packet);
        ChunkPos fromChunk = ChunkPos.containing(BlockPos.containing(from));
        ChunkPos toChunk = ChunkPos.containing(BlockPos.containing(to));
        PacketDistributor.sendToPlayersTrackingChunk(level, fromChunk, payload);
        if (!toChunk.equals(fromChunk)) {
            PacketDistributor.sendToPlayersTrackingChunk(level, toChunk, payload);
        }
    }

    private static TransitHopPayload hopPayload(TransitPacket packet) {
        if (packet.waiting) {
            Vec3 here = packet.pointOf(packet.hop);
            return new TransitHopPayload(packet.id, packet.stack.copy(), here, here, packet.waitSince, 1);
        }
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
