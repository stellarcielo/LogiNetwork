package com.stellarcielo.loginetwork.network;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.stellarcielo.loginetwork.block.AccessSwitchBlock;
import com.stellarcielo.loginetwork.block.CableBlock;
import com.stellarcielo.loginetwork.block.CableType;
import com.stellarcielo.loginetwork.block.DiscardChestBlock;
import com.stellarcielo.loginetwork.block.DhcpServerBlock;
import com.stellarcielo.loginetwork.block.NetworkInterfaceBlock;
import com.stellarcielo.loginetwork.block.RouterBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import org.jspecify.annotations.Nullable;

// Breadth-first view of a cable network as seen from one node. Built on topology change, then reused.
// Cables and switches relay between all their connections. Interfaces and DHCP servers are leaves: they
// are reached but never relay, except when the search starts from them. Routers bound the segment: they
// are reached but never relay, and a search from a router covers only the segment on one of its ports.
// A CableNetwork is therefore exactly one segment (one broadcast domain).
public final class CableNetwork {
    // A node side that faces an inventory: a cable's inventory connection or an interface's front.
    // discard: the inventory is a discard chest, used only as an overflow sink.
    public record Endpoint(BlockPos nodePos, Direction side, boolean extract, boolean discard) {
        public BlockPos inventoryPos() {
            return nodePos.relative(side);
        }
    }

    private final BlockPos origin;
    private final Map<BlockPos, BlockPos> parents;
    // All lists are ordered by hop count from the origin, nearest first.
    private final List<Endpoint> endpoints;
    private final List<BlockPos> interfaces;
    private final List<BlockPos> dhcpServers;
    private final List<RouterAttachment> routers;
    // Link cost of entering each node (cable cost per block; zero for devices).
    private final Map<BlockPos, Integer> weights;

    // A router reached by the search, and the router port this segment is attached to.
    public record RouterAttachment(BlockPos routerPos, Direction port) {}

    private CableNetwork(BlockPos origin, Map<BlockPos, BlockPos> parents, List<Endpoint> endpoints, List<BlockPos> interfaces,
            List<BlockPos> dhcpServers, List<RouterAttachment> routers, Map<BlockPos, Integer> weights) {
        this.origin = origin;
        this.parents = parents;
        this.endpoints = endpoints;
        this.interfaces = interfaces;
        this.dhcpServers = dhcpServers;
        this.routers = routers;
        this.weights = weights;
    }

    public static CableNetwork explore(ServerLevel level, BlockPos origin) {
        return explore(level, origin, null);
    }

    // The segment on one port of a router; the router itself is the origin of every path.
    public static CableNetwork explorePort(ServerLevel level, BlockPos routerPos, Direction port) {
        return explore(level, routerPos, port);
    }

    private static CableNetwork explore(ServerLevel level, BlockPos origin, @Nullable Direction originPort) {
        Map<BlockPos, BlockPos> parents = new HashMap<>();
        List<Endpoint> endpoints = new ArrayList<>();
        List<BlockPos> interfaces = new ArrayList<>();
        List<BlockPos> dhcpServers = new ArrayList<>();
        List<RouterAttachment> routers = new ArrayList<>();
        Map<BlockPos, Integer> weights = new HashMap<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        parents.put(origin, origin);
        queue.add(origin);
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            boolean isOrigin = pos.equals(origin);
            CableType cableType = CableBlock.typeOf(state);
            weights.put(pos.immutable(), cableType == null ? 0 : cableType.costPerBlock());
            if (state.getBlock() instanceof CableBlock) {
                for (Direction direction : Direction.values()) {
                    if (!state.getValue(CableBlock.PROPERTY_BY_DIRECTION.get(direction))) {
                        continue;
                    }
                    BlockPos neighbour = pos.relative(direction);
                    if (level.isLoaded(neighbour) && NetworkNodes.isNode(level.getBlockState(neighbour))) {
                        visit(parents, queue, neighbour, pos);
                    } else {
                        endpoints.add(new Endpoint(pos.immutable(), direction, state.getValue(CableBlock.EXTRACT_BY_DIRECTION.get(direction)),
                                isDiscardChest(level, neighbour)));
                    }
                }
            } else if (state.getBlock() instanceof NetworkInterfaceBlock) {
                interfaces.add(pos.immutable());
                Direction front = state.getValue(NetworkInterfaceBlock.FACING);
                BlockPos inventoryPos = pos.relative(front);
                if (level.isLoaded(inventoryPos) && level.getCapability(Capabilities.Item.BLOCK, inventoryPos, front.getOpposite()) != null) {
                    endpoints.add(new Endpoint(pos.immutable(), front, state.getValue(NetworkInterfaceBlock.EXTRACT), isDiscardChest(level, inventoryPos)));
                }
                if (isOrigin) {
                    visitAttachedCables(level, parents, queue, pos);
                }
            } else if (state.getBlock() instanceof AccessSwitchBlock) {
                visitAttachedCables(level, parents, queue, pos);
            } else if (state.getBlock() instanceof DhcpServerBlock) {
                dhcpServers.add(pos.immutable());
                if (isOrigin) {
                    visitAttachedCables(level, parents, queue, pos);
                }
            } else if (state.getBlock() instanceof RouterBlock) {
                if (isOrigin) {
                    if (originPort != null) {
                        visitAttachedCable(level, parents, queue, pos, originPort);
                    }
                } else {
                    BlockPos parent = parents.get(pos);
                    routers.add(new RouterAttachment(pos.immutable(), Direction.getNearest(parent.subtract(pos), Direction.NORTH)));
                }
            }
        }
        return new CableNetwork(origin, parents, endpoints, interfaces, dhcpServers, routers, weights);
    }

    private static boolean isDiscardChest(ServerLevel level, BlockPos pos) {
        return level.isLoaded(pos) && level.getBlockState(pos).getBlock() instanceof DiscardChestBlock;
    }

    private static void visit(Map<BlockPos, BlockPos> parents, ArrayDeque<BlockPos> queue, BlockPos pos, BlockPos parent) {
        if (parents.putIfAbsent(pos.immutable(), parent) == null) {
            queue.add(pos.immutable());
        }
    }

    // Non-cable nodes have no connection state of their own; a cable counts as attached when it connects towards them.
    private static void visitAttachedCables(ServerLevel level, Map<BlockPos, BlockPos> parents, ArrayDeque<BlockPos> queue, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            visitAttachedCable(level, parents, queue, pos, direction);
        }
    }

    private static void visitAttachedCable(ServerLevel level, Map<BlockPos, BlockPos> parents, ArrayDeque<BlockPos> queue, BlockPos pos, Direction direction) {
        BlockPos neighbour = pos.relative(direction);
        if (!level.isLoaded(neighbour)) {
            return;
        }
        BlockState neighbourState = level.getBlockState(neighbour);
        if (neighbourState.getBlock() instanceof CableBlock && neighbourState.getValue(CableBlock.PROPERTY_BY_DIRECTION.get(direction.getOpposite()))) {
            visit(parents, queue, neighbour, pos);
        }
    }

    public boolean contains(BlockPos pos) {
        return parents.containsKey(pos);
    }

    public List<Endpoint> endpoints() {
        return endpoints;
    }

    public List<BlockPos> interfaces() {
        return interfaces;
    }

    public List<BlockPos> dhcpServers() {
        return dhcpServers;
    }

    public List<RouterAttachment> routers() {
        return routers;
    }

    // Number of links between the origin and a node in this segment.
    public int distanceTo(BlockPos nodePos) {
        return pathTo(nodePos).size() - 1;
    }

    // Link cost from the origin to a node: the sum of the cable costs along the way.
    public int costTo(BlockPos nodePos) {
        int cost = 0;
        for (BlockPos pos : pathTo(nodePos)) {
            if (!pos.equals(origin)) {
                cost += weights.getOrDefault(pos, 0);
            }
        }
        return cost;
    }

    // Node positions from the origin to the given node, both inclusive.
    public List<BlockPos> pathTo(BlockPos nodePos) {
        List<BlockPos> path = new ArrayList<>();
        BlockPos current = nodePos;
        while (!current.equals(origin)) {
            path.add(current);
            current = parents.get(current);
        }
        path.add(origin);
        Collections.reverse(path);
        return path;
    }
}
