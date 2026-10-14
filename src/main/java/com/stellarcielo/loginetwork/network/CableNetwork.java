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
import com.stellarcielo.loginetwork.block.entity.AccessSwitchBlockEntity;

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
// Switch ports split the search by VLAN (see SwitchPort). A CableNetwork is therefore exactly one segment
// (one broadcast domain).
public final class CableNetwork {
    // A node side that faces an inventory: a cable's inventory connection or an interface's front.
    // discard: the inventory is a discard chest, used only as an overflow sink.
    public record Endpoint(BlockPos nodePos, Direction side, boolean extract, boolean discard) {
        public BlockPos inventoryPos() {
            return nodePos.relative(side);
        }
    }

    // A node as reached by the search, with the VLAN tag of the frame there (SwitchPort.UNTAGGED outside trunks).
    // For a switch the tag is the VLAN inside the switch.
    private record State(BlockPos pos, int tag) {}

    private final BlockPos origin;
    private final Map<State, State> parents;
    // The state through which each node was first reached; paths follow it.
    private final Map<BlockPos, State> first;
    // All lists are ordered by hop count from the origin, nearest first.
    private final List<Endpoint> endpoints;
    private final List<BlockPos> interfaces;
    private final List<BlockPos> dhcpServers;
    private final List<RouterAttachment> routers;
    // Link cost of entering each node (cable cost per block; zero for devices).
    private final Map<BlockPos, Integer> weights;

    // A router reached by the search, and the router port this segment is attached to.
    public record RouterAttachment(BlockPos routerPos, Direction port) {}

    private CableNetwork(BlockPos origin, Map<State, State> parents, Map<BlockPos, State> first, List<Endpoint> endpoints,
            List<BlockPos> interfaces, List<BlockPos> dhcpServers, List<RouterAttachment> routers, Map<BlockPos, Integer> weights) {
        this.origin = origin;
        this.parents = parents;
        this.first = first;
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

    // Breadth-first over (node, VLAN tag). Cables keep the tag; switches apply their port VLAN rules;
    // interfaces, DHCP servers and routers only accept untagged frames.
    private static CableNetwork explore(ServerLevel level, BlockPos origin, @Nullable Direction originPort) {
        Map<State, State> parents = new HashMap<>();
        Map<BlockPos, State> first = new HashMap<>();
        List<Endpoint> endpoints = new ArrayList<>();
        List<BlockPos> interfaces = new ArrayList<>();
        List<BlockPos> dhcpServers = new ArrayList<>();
        List<RouterAttachment> routers = new ArrayList<>();
        Map<BlockPos, Integer> weights = new HashMap<>();
        ArrayDeque<State> queue = new ArrayDeque<>();
        BlockState originBlock = level.getBlockState(origin);
        State start = new State(origin.immutable(), originBlock.getBlock() instanceof AccessSwitchBlock ? SwitchPort.NATIVE_VLAN : SwitchPort.UNTAGGED);
        parents.put(start, start);
        first.put(start.pos(), start);
        queue.add(start);
        while (!queue.isEmpty()) {
            State current = queue.poll();
            BlockPos pos = current.pos();
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            boolean isOrigin = current == start;
            boolean firstVisit = first.get(pos) == current;
            if (firstVisit) {
                CableType cableType = CableBlock.typeOf(state);
                weights.put(pos, cableType == null ? 0 : cableType.costPerBlock());
            }
            if (state.getBlock() instanceof CableBlock) {
                for (Direction direction : Direction.values()) {
                    if (!state.getValue(CableBlock.PROPERTY_BY_DIRECTION.get(direction))) {
                        continue;
                    }
                    BlockPos neighbour = pos.relative(direction);
                    BlockState neighbourState = level.isLoaded(neighbour) ? level.getBlockState(neighbour) : null;
                    if (neighbourState != null && NetworkNodes.isNode(neighbourState)) {
                        State next = enter(level, neighbour, neighbourState, direction.getOpposite(), current.tag());
                        if (next != null) {
                            visit(parents, first, queue, next, current);
                        }
                    } else if (firstVisit) {
                        endpoints.add(new Endpoint(pos, direction, state.getValue(CableBlock.EXTRACT_BY_DIRECTION.get(direction)),
                                isDiscardChest(level, neighbour)));
                    }
                }
            } else if (state.getBlock() instanceof AccessSwitchBlock) {
                Direction ingress = isOrigin ? null : Direction.getNearest(parents.get(current).pos().subtract(pos), Direction.NORTH);
                AccessSwitchBlockEntity accessSwitch = level.getBlockEntity(pos) instanceof AccessSwitchBlockEntity found ? found : null;
                for (Direction direction : Direction.values()) {
                    if (direction == ingress) {
                        continue;
                    }
                    SwitchPort port = accessSwitch == null ? SwitchPort.DEFAULT : accessSwitch.port(direction);
                    int tag = port.egress(current.tag());
                    if (tag != SwitchPort.DROP) {
                        visitAttachedCable(level, parents, first, queue, current, direction, tag);
                    }
                }
            } else if (firstVisit && state.getBlock() instanceof NetworkInterfaceBlock) {
                interfaces.add(pos);
                Direction front = state.getValue(NetworkInterfaceBlock.FACING);
                BlockPos inventoryPos = pos.relative(front);
                if (level.isLoaded(inventoryPos) && level.getCapability(Capabilities.Item.BLOCK, inventoryPos, front.getOpposite()) != null) {
                    endpoints.add(new Endpoint(pos, front, state.getValue(NetworkInterfaceBlock.EXTRACT), isDiscardChest(level, inventoryPos)));
                }
                if (isOrigin) {
                    visitAttachedCables(level, parents, first, queue, current);
                }
            } else if (firstVisit && state.getBlock() instanceof DhcpServerBlock) {
                dhcpServers.add(pos);
                if (isOrigin) {
                    visitAttachedCables(level, parents, first, queue, current);
                }
            } else if (firstVisit && state.getBlock() instanceof RouterBlock) {
                if (isOrigin) {
                    if (originPort != null) {
                        visitAttachedCable(level, parents, first, queue, current, originPort, SwitchPort.UNTAGGED);
                    }
                } else {
                    routers.add(new RouterAttachment(pos, Direction.getNearest(parents.get(current).pos().subtract(pos), Direction.NORTH)));
                }
            }
        }
        return new CableNetwork(origin.immutable(), parents, first, endpoints, interfaces, dhcpServers, routers, weights);
    }

    // The state a frame with the given tag reaches when it enters a node from the given side, or null if it is not accepted.
    private static @Nullable State enter(ServerLevel level, BlockPos pos, BlockState state, Direction fromSide, int tag) {
        if (state.getBlock() instanceof CableBlock) {
            return new State(pos.immutable(), tag);
        }
        if (state.getBlock() instanceof AccessSwitchBlock) {
            SwitchPort port = level.getBlockEntity(pos) instanceof AccessSwitchBlockEntity accessSwitch ? accessSwitch.port(fromSide) : SwitchPort.DEFAULT;
            int vlan = port.ingress(tag);
            return vlan == SwitchPort.DROP ? null : new State(pos.immutable(), vlan);
        }
        // Hosts, DHCP servers and routers take untagged frames only.
        return tag == SwitchPort.UNTAGGED ? new State(pos.immutable(), SwitchPort.UNTAGGED) : null;
    }

    private static boolean isDiscardChest(ServerLevel level, BlockPos pos) {
        return level.isLoaded(pos) && level.getBlockState(pos).getBlock() instanceof DiscardChestBlock;
    }

    private static void visit(Map<State, State> parents, Map<BlockPos, State> first, ArrayDeque<State> queue, State next, State parent) {
        if (parents.putIfAbsent(next, parent) == null) {
            first.putIfAbsent(next.pos(), next);
            queue.add(next);
        }
    }

    // Non-cable nodes have no connection state of their own; a cable counts as attached when it connects towards them.
    private static void visitAttachedCables(ServerLevel level, Map<State, State> parents, Map<BlockPos, State> first, ArrayDeque<State> queue,
            State from) {
        for (Direction direction : Direction.values()) {
            visitAttachedCable(level, parents, first, queue, from, direction, SwitchPort.UNTAGGED);
        }
    }

    private static void visitAttachedCable(ServerLevel level, Map<State, State> parents, Map<BlockPos, State> first, ArrayDeque<State> queue,
            State from, Direction direction, int tag) {
        BlockPos neighbour = from.pos().relative(direction);
        if (!level.isLoaded(neighbour)) {
            return;
        }
        BlockState neighbourState = level.getBlockState(neighbour);
        if (neighbourState.getBlock() instanceof CableBlock && neighbourState.getValue(CableBlock.PROPERTY_BY_DIRECTION.get(direction.getOpposite()))) {
            visit(parents, first, queue, new State(neighbour.immutable(), tag), from);
        }
    }

    public boolean contains(BlockPos pos) {
        return first.containsKey(pos);
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
        return statesTo(nodePos).stream().map(State::pos).toList();
    }

    // VLAN tag carried on each cable along pathTo (UNTAGGED for every other node).
    public List<Integer> tagsTo(BlockPos nodePos, ServerLevel level) {
        List<Integer> tags = new ArrayList<>();
        for (State state : statesTo(nodePos)) {
            tags.add(CableBlock.typeOf(level.getBlockState(state.pos())) != null ? state.tag() : SwitchPort.UNTAGGED);
        }
        return tags;
    }

    private List<State> statesTo(BlockPos nodePos) {
        List<State> path = new ArrayList<>();
        State current = first.get(nodePos);
        if (current == null) {
            throw new IllegalArgumentException("Not in this segment: " + nodePos);
        }
        while (true) {
            path.add(current);
            State parent = parents.get(current);
            if (parent == current) {
                break;
            }
            current = parent;
        }
        Collections.reverse(path);
        return path;
    }
}
