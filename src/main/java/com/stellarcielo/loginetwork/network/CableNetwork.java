package com.stellarcielo.loginetwork.network;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.stellarcielo.loginetwork.block.CableBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

// Breadth-first view of a cable network as seen from one cable. Built on topology change, then reused.
public final class CableNetwork {
    // A cable side that faces an inventory.
    public record Endpoint(BlockPos cablePos, Direction side, boolean extract) {
        public BlockPos inventoryPos() {
            return cablePos.relative(side);
        }
    }

    private final BlockPos origin;
    private final Map<BlockPos, BlockPos> parents;
    // Ordered by hop count from the origin, nearest first.
    private final List<Endpoint> endpoints;

    private CableNetwork(BlockPos origin, Map<BlockPos, BlockPos> parents, List<Endpoint> endpoints) {
        this.origin = origin;
        this.parents = parents;
        this.endpoints = endpoints;
    }

    public static CableNetwork explore(ServerLevel level, BlockPos origin) {
        Map<BlockPos, BlockPos> parents = new HashMap<>();
        List<Endpoint> endpoints = new ArrayList<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        parents.put(origin, origin);
        queue.add(origin);
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (!CableBlock.isCable(state)) {
                continue;
            }
            for (Direction direction : Direction.values()) {
                if (!state.getValue(CableBlock.PROPERTY_BY_DIRECTION.get(direction))) {
                    continue;
                }
                BlockPos neighbour = pos.relative(direction);
                if (level.isLoaded(neighbour) && CableBlock.isCable(level.getBlockState(neighbour))) {
                    if (parents.putIfAbsent(neighbour, pos) == null) {
                        queue.add(neighbour);
                    }
                } else {
                    endpoints.add(new Endpoint(pos.immutable(), direction, state.getValue(CableBlock.EXTRACT_BY_DIRECTION.get(direction))));
                }
            }
        }
        return new CableNetwork(origin, parents, endpoints);
    }

    public List<Endpoint> endpoints() {
        return endpoints;
    }

    // Cable positions from the origin to the given cable, both inclusive.
    public List<BlockPos> pathTo(BlockPos cablePos) {
        List<BlockPos> path = new ArrayList<>();
        BlockPos current = cablePos;
        while (!current.equals(origin)) {
            path.add(current);
            current = parents.get(current);
        }
        path.add(origin);
        Collections.reverse(path);
        return path;
    }
}
