package com.stellarcielo.loginetwork.network;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.stellarcielo.loginetwork.block.CableBlock;
import com.stellarcielo.loginetwork.block.DhcpServerBlock;
import com.stellarcielo.loginetwork.block.NetworkInterfaceBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;

// Breadth-first view of a cable network as seen from one node. Built on topology change, then reused.
// Cables relay between all their connections. Interfaces and DHCP servers are leaves: they are reached
// but never relay, except when the search starts from them.
public final class CableNetwork {
    // A node side that faces an inventory: a cable's inventory connection or an interface's front.
    public record Endpoint(BlockPos nodePos, Direction side, boolean extract) {
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

    private CableNetwork(BlockPos origin, Map<BlockPos, BlockPos> parents, List<Endpoint> endpoints, List<BlockPos> interfaces, List<BlockPos> dhcpServers) {
        this.origin = origin;
        this.parents = parents;
        this.endpoints = endpoints;
        this.interfaces = interfaces;
        this.dhcpServers = dhcpServers;
    }

    public static CableNetwork explore(ServerLevel level, BlockPos origin) {
        Map<BlockPos, BlockPos> parents = new HashMap<>();
        List<Endpoint> endpoints = new ArrayList<>();
        List<BlockPos> interfaces = new ArrayList<>();
        List<BlockPos> dhcpServers = new ArrayList<>();
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
            if (state.getBlock() instanceof CableBlock) {
                for (Direction direction : Direction.values()) {
                    if (!state.getValue(CableBlock.PROPERTY_BY_DIRECTION.get(direction))) {
                        continue;
                    }
                    BlockPos neighbour = pos.relative(direction);
                    if (level.isLoaded(neighbour) && NetworkNodes.isNode(level.getBlockState(neighbour))) {
                        visit(parents, queue, neighbour, pos);
                    } else {
                        endpoints.add(new Endpoint(pos.immutable(), direction, state.getValue(CableBlock.EXTRACT_BY_DIRECTION.get(direction))));
                    }
                }
            } else if (state.getBlock() instanceof NetworkInterfaceBlock) {
                interfaces.add(pos.immutable());
                Direction front = state.getValue(NetworkInterfaceBlock.FACING);
                BlockPos inventoryPos = pos.relative(front);
                if (level.isLoaded(inventoryPos) && level.getCapability(Capabilities.Item.BLOCK, inventoryPos, front.getOpposite()) != null) {
                    endpoints.add(new Endpoint(pos.immutable(), front, state.getValue(NetworkInterfaceBlock.EXTRACT)));
                }
                if (isOrigin) {
                    visitAttachedCables(level, parents, queue, pos);
                }
            } else if (state.getBlock() instanceof DhcpServerBlock) {
                dhcpServers.add(pos.immutable());
                if (isOrigin) {
                    visitAttachedCables(level, parents, queue, pos);
                }
            }
        }
        return new CableNetwork(origin, parents, endpoints, interfaces, dhcpServers);
    }

    private static void visit(Map<BlockPos, BlockPos> parents, ArrayDeque<BlockPos> queue, BlockPos pos, BlockPos parent) {
        if (parents.putIfAbsent(pos.immutable(), parent) == null) {
            queue.add(pos.immutable());
        }
    }

    // Leaf nodes have no connection state of their own; a cable counts as attached when it connects towards them.
    private static void visitAttachedCables(ServerLevel level, Map<BlockPos, BlockPos> parents, ArrayDeque<BlockPos> queue, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockPos neighbour = pos.relative(direction);
            if (!level.isLoaded(neighbour)) {
                continue;
            }
            BlockState neighbourState = level.getBlockState(neighbour);
            if (neighbourState.getBlock() instanceof CableBlock && neighbourState.getValue(CableBlock.PROPERTY_BY_DIRECTION.get(direction.getOpposite()))) {
                visit(parents, queue, neighbour, pos);
            }
        }
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
