package com.stellarcielo.loginetwork.network.control;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.stellarcielo.loginetwork.address.Ipv4Address;
import com.stellarcielo.loginetwork.block.entity.AccessSwitchBlockEntity;
import com.stellarcielo.loginetwork.network.CableNetwork;
import com.stellarcielo.loginetwork.network.NetworkManager;
import com.stellarcielo.loginetwork.network.NetworkNodes;
import com.stellarcielo.loginetwork.network.payload.ControlPathPayload;
import com.stellarcielo.loginetwork.network.routing.RoutingService;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

// Carries control messages along node paths with a per-hop delay. Messages are not saved:
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

    // Segment-wide broadcast from content.sourcePos: one copy per interface and router in the segment.
    // Routers receive it but never pass it on to other segments.
    public void broadcast(ServerLevel level, ControlMessage.Content content) {
        BlockPos source = content.sourcePos();
        CableNetwork segment = manager.network(level, source);
        for (BlockPos target : segment.interfaces()) {
            if (!target.equals(source)) {
                launch(level, content, segment.pathTo(target));
            }
        }
        for (CableNetwork.RouterAttachment router : segment.routers()) {
            launch(level, content, segment.pathTo(router.routerPos()));
        }
    }

    // Unicast to the interface holding the given address, routed through routers when needed.
    // Returns false when there is no route.
    public boolean sendToAddress(ServerLevel level, ControlMessage.Content content, BlockPos from, @Nullable Ipv4Address destination) {
        if (destination == null) {
            return false;
        }
        List<BlockPos> path = RoutingService.resolve(level, from, destination);
        if (path == null || path.size() < 2) {
            return false;
        }
        launch(level, content, path);
        return true;
    }

    // Send along a known path (used between neighbouring routers).
    public void sendAlongPath(ServerLevel level, ControlMessage.Content content, List<BlockPos> path) {
        if (path.size() >= 2) {
            launch(level, content, path);
        }
    }

    private void launch(ServerLevel level, ControlMessage.Content content, List<BlockPos> path) {
        ControlMessage message = new ControlMessage(content, path, level.getGameTime());
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
                if (level.getBlockEntity(next) instanceof ControlEndpoint endpoint) {
                    endpoint.onControlMessage(level, message);
                }
                return true;
            }
        }
        return false;
    }

    private static Direction directionBetween(BlockPos from, BlockPos to) {
        return Direction.getNearest(to.subtract(from), Direction.NORTH);
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
