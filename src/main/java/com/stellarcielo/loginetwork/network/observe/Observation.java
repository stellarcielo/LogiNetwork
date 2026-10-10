package com.stellarcielo.loginetwork.network.observe;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.stellarcielo.loginetwork.address.Ipv4Address;
import com.stellarcielo.loginetwork.block.AccessSwitchBlock;
import com.stellarcielo.loginetwork.block.CableBlock;
import com.stellarcielo.loginetwork.block.CableType;
import com.stellarcielo.loginetwork.block.RouterBlock;
import com.stellarcielo.loginetwork.block.entity.InterfaceStatus;
import com.stellarcielo.loginetwork.block.entity.NetworkInterfaceBlockEntity;
import com.stellarcielo.loginetwork.block.entity.RouterBlockEntity;
import com.stellarcielo.loginetwork.network.NetworkManager;
import com.stellarcielo.loginetwork.network.NetworkNodes;
import com.stellarcielo.loginetwork.network.TransitPacket;
import com.stellarcielo.loginetwork.network.routing.RoutingService;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

// What the observation tool shows: node state, packet details and traceroute. Server side only.
public final class Observation {
    // How close a packet must be to the line of sight to be picked, in blocks.
    private static final double PICK_RADIUS = 0.4;

    private Observation() {}

    // ---- packets --------------------------------------------------------------------------------

    // The packet nearest to the eye along the look direction, within reach.
    public static @Nullable TransitPacket packetInSight(ServerLevel level, Vec3 eye, Vec3 look, double reach) {
        long now = level.getGameTime();
        TransitPacket best = null;
        double bestAlong = reach;
        for (TransitPacket packet : NetworkManager.get(level).packets()) {
            Vec3 offset = packet.currentPosition(now).subtract(eye);
            double along = offset.dot(look);
            if (along < 0 || along > bestAlong) {
                continue;
            }
            if (offset.subtract(look.scale(along)).length() <= PICK_RADIUS) {
                best = packet;
                bestAlong = along;
            }
        }
        return best;
    }

    public static List<Component> describePacket(TransitPacket packet) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("observe.loginetwork.packet.header", packet.id()).withStyle(ChatFormatting.AQUA));
        lines.add(Component.translatable("observe.loginetwork.packet.item", PacketCapture.describeItem(packet.stack(), packet.stack().getCount())));
        lines.add(Component.translatable("observe.loginetwork.packet.source", PacketCapture.address(packet.supplierAddress())));
        lines.add(Component.translatable("observe.loginetwork.packet.destination", PacketCapture.address(packet.destinationAddress())));
        lines.add(Component.translatable("observe.loginetwork.packet.request",
                packet.requestId() == TransitPacket.NO_REQUEST ? Component.translatable("gui.loginetwork.none") : Component.literal("#" + packet.requestId())));
        lines.add(Component.translatable("observe.loginetwork.packet.qos", Component.translatable("gui.loginetwork.qos." + packet.priority())));
        lines.add(Component.translatable(packet.isWaiting() ? "observe.loginetwork.packet.waiting" : "observe.loginetwork.packet.moving"));
        return lines;
    }

    // ---- nodes ----------------------------------------------------------------------------------

    public static List<Component> describeNode(ServerLevel level, BlockPos pos) {
        List<Component> lines = new ArrayList<>();
        BlockState state = level.getBlockState(pos);
        if (!NetworkNodes.isNode(state)) {
            lines.add(Component.translatable("observe.loginetwork.node.not_network"));
            return lines;
        }
        NetworkManager manager = NetworkManager.get(level);
        long now = level.getGameTime();
        lines.add(state.getBlock().getName().copy().withStyle(ChatFormatting.AQUA));

        CableType cable = CableBlock.typeOf(state);
        if (cable != null) {
            lines.add(Component.translatable("observe.loginetwork.node.cable", cable.ticksPerBlock(), cable.itemsPerSecond(), cable.costPerBlock()));
            long passing = manager.packets().stream().filter(packet -> packet.currentNode().equals(pos)).count();
            lines.add(Component.translatable("observe.loginetwork.node.passing", passing));
            return lines;
        }
        if (level.getBlockEntity(pos) instanceof NetworkInterfaceBlockEntity host) {
            lines.add(Component.translatable("observe.loginetwork.node.interface", PacketCapture.address(host.assigned()),
                    Component.translatable(host.status().translationKey())));
        }
        if (state.getBlock() instanceof AccessSwitchBlock || state.getBlock() instanceof RouterBlock) {
            lines.add(Component.translatable("observe.loginetwork.node.queue", manager.queueLength(pos), NetworkManager.QUEUE_CAPACITY));
        }
        if (level.getBlockEntity(pos) instanceof RouterBlockEntity router) {
            lines.add(Component.translatable("observe.loginetwork.node.router", router.routerId().toString(), router.paths().size()));
        }
        for (Direction port : Direction.values()) {
            int utilization = manager.portUtilization(pos, port, now);
            if (utilization > 0) {
                lines.add(Component.translatable("observe.loginetwork.node.port_usage",
                        Component.translatable("gui.loginetwork.switch.port." + port.getSerializedName()), utilization));
            }
        }
        if (manager.capture().isCapturing(pos)) {
            lines.add(Component.translatable("observe.loginetwork.node.capturing").withStyle(ChatFormatting.YELLOW));
        }
        return lines;
    }

    // ---- traceroute -----------------------------------------------------------------------------

    // One hop of a trace: a router on the way, or the destination itself. cost is the cable cost so far.
    public record Hop(BlockPos pos, Component name, int cost) {}

    public record Trace(@Nullable List<BlockPos> path, List<Hop> hops) {
        public boolean reached() {
            return path != null;
        }
    }

    public static Trace trace(ServerLevel level, BlockPos from, Ipv4Address destination) {
        List<BlockPos> path = RoutingService.resolve(level, from, destination);
        List<Hop> hops = new ArrayList<>();
        if (path == null) {
            return new Trace(null, hops);
        }
        int cost = 0;
        for (int i = 1; i < path.size(); i++) {
            BlockPos pos = path.get(i);
            CableType cable = CableBlock.typeOf(level.getBlockState(pos));
            if (cable != null) {
                cost += cable.costPerBlock();
            }
            if (level.getBlockEntity(pos) instanceof RouterBlockEntity router) {
                hops.add(new Hop(pos, Component.translatable("observe.loginetwork.trace.router", router.routerId().toString()), cost));
            }
        }
        hops.add(new Hop(path.get(path.size() - 1), Component.literal(destination.toString()), cost));
        return new Trace(path, hops);
    }

    public static List<Component> describeTrace(Ipv4Address destination, Trace trace) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("observe.loginetwork.trace.header", destination.toString()).withStyle(ChatFormatting.AQUA));
        if (!trace.reached()) {
            lines.add(Component.translatable("observe.loginetwork.trace.unreachable").withStyle(ChatFormatting.RED));
            return lines;
        }
        for (int i = 0; i < trace.hops().size(); i++) {
            Hop hop = trace.hops().get(i);
            lines.add(Component.translatable("observe.loginetwork.trace.hop", i + 1, hop.name(), hop.cost()));
        }
        return lines;
    }

    public static boolean canTraceFrom(ServerLevel level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof NetworkInterfaceBlockEntity host && host.status() != InterfaceStatus.UNASSIGNED;
    }
}
