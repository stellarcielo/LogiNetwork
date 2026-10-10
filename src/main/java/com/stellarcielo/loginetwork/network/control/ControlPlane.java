package com.stellarcielo.loginetwork.network.control;

import java.util.ArrayList;
import java.util.List;

import com.stellarcielo.loginetwork.address.MacAddress;
import com.stellarcielo.loginetwork.block.entity.AccessSwitchBlockEntity;
import com.stellarcielo.loginetwork.block.entity.NetworkInterfaceBlockEntity;
import com.stellarcielo.loginetwork.network.CableNetwork;
import com.stellarcielo.loginetwork.network.NetworkManager;
import com.stellarcielo.loginetwork.network.NetworkNodes;
import com.stellarcielo.loginetwork.network.payload.ControlPathPayload;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

// Carries control messages between interfaces with a per-hop delay. Messages are not saved:
// anything in flight when the world unloads is simply lost, and the protocol's timeouts recover.
public final class ControlPlane {
    public static final int TICKS_PER_HOP = 1;
    // Players this far from a message's path are told about it so they can draw it.
    private static final double SYNC_DISTANCE = 96.0;

    private final NetworkManager manager;
    private final List<ControlMessage> inFlight = new ArrayList<>();

    public ControlPlane(NetworkManager manager) {
        this.manager = manager;
    }

    // Segment-wide broadcast: one copy per other interface, each following the shortest path.
    public void broadcast(ServerLevel level, ControlMessageType type, long requestId, ItemStack item, int count, int window,
            MacAddress sourceMac, BlockPos sourcePos) {
        CableNetwork segment = manager.network(level, sourcePos);
        for (BlockPos target : segment.interfaces()) {
            if (!target.equals(sourcePos)) {
                launch(level, new ControlMessage(type, requestId, item, count, window, sourceMac, sourcePos, segment.pathTo(target), level.getGameTime()));
            }
        }
    }

    // Returns false when the destination is not reachable from the source.
    public boolean unicast(ServerLevel level, ControlMessageType type, long requestId, ItemStack item, int count, int window,
            MacAddress sourceMac, BlockPos sourcePos, BlockPos destination) {
        CableNetwork segment = manager.network(level, sourcePos);
        if (!segment.contains(destination) || destination.equals(sourcePos)) {
            return false;
        }
        launch(level, new ControlMessage(type, requestId, item, count, window, sourceMac, sourcePos, segment.pathTo(destination), level.getGameTime()));
        return true;
    }

    private void launch(ServerLevel level, ControlMessage message) {
        inFlight.add(message);
        syncToClients(level, message);
    }

    public void tick(ServerLevel level) {
        long now = level.getGameTime();
        // Delivery can send replies, so iterate over a snapshot and drop finished messages afterwards.
        List<ControlMessage> finished = new ArrayList<>();
        for (ControlMessage message : List.copyOf(inFlight)) {
            if (step(level, message, now)) {
                finished.add(message);
            }
        }
        inFlight.removeAll(finished);
    }

    // Returns true when the message has been delivered or lost.
    private boolean step(ServerLevel level, ControlMessage message, long now) {
        while (now >= message.hopStart + TICKS_PER_HOP) {
            BlockPos next = message.path.get(message.hop + 1);
            if (!level.isLoaded(next) || !NetworkNodes.isNode(level.getBlockState(next))) {
                // The link is gone: the message is lost.
                return true;
            }
            message.hop++;
            message.hopStart += TICKS_PER_HOP;
            if (level.getBlockEntity(next) instanceof AccessSwitchBlockEntity accessSwitch) {
                BlockPos previous = message.path.get(message.hop - 1);
                accessSwitch.learn(message.sourceMac, directionBetween(next, previous), now);
            }
            if (message.hop == message.hopCount()) {
                if (level.getBlockEntity(next) instanceof NetworkInterfaceBlockEntity target) {
                    target.host().onControlMessage(level, message);
                }
                return true;
            }
        }
        return false;
    }

    private static Direction directionBetween(BlockPos from, BlockPos to) {
        BlockPos delta = to.subtract(from);
        return Direction.getNearest(delta, Direction.NORTH);
    }

    private static void syncToClients(ServerLevel level, ControlMessage message) {
        List<Vec3> points = new ArrayList<>(message.path.size());
        AABB bounds = null;
        for (BlockPos pos : message.path) {
            points.add(Vec3.atCenterOf(pos));
            AABB box = new AABB(pos);
            bounds = bounds == null ? box : bounds.minmax(box);
        }
        ControlPathPayload payload = new ControlPathPayload(message.type.ordinal(), points, message.hopStart, TICKS_PER_HOP);
        AABB area = bounds.inflate(SYNC_DISTANCE);
        for (ServerPlayer player : level.players()) {
            if (area.contains(player.position())) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    public List<ControlMessage> inFlight() {
        return inFlight;
    }
}
