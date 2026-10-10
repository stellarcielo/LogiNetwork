package com.stellarcielo.loginetwork.network.routing;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.stellarcielo.loginetwork.address.Ipv4Address;
import com.stellarcielo.loginetwork.block.entity.NetworkInterfaceBlockEntity;
import com.stellarcielo.loginetwork.block.entity.RouterBlockEntity;
import com.stellarcielo.loginetwork.network.CableNetwork;
import com.stellarcielo.loginetwork.network.NetworkManager;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

// Turns "from this node to that address" into a full node path, asking each router on the way for its
// own decision (hop-by-hop forwarding, resolved up front). Tables are only read here, never recomputed.
public final class RoutingService {
    private static final int MAX_ROUTERS = 32;

    private RoutingService() {}

    public static @Nullable List<BlockPos> resolve(ServerLevel level, BlockPos from, Ipv4Address destination) {
        NetworkManager manager = NetworkManager.get(level);
        List<BlockPos> path = new ArrayList<>();
        BlockPos router;
        if (level.getBlockEntity(from) instanceof RouterBlockEntity) {
            router = from;
            path.add(from);
        } else {
            CableNetwork segment = manager.network(level, from);
            BlockPos host = findHost(level, segment, destination);
            if (host != null) {
                return segment.pathTo(host);
            }
            // Pick the attached router that gets there cheapest (our stand-in for a default gateway).
            router = null;
            int bestCost = Integer.MAX_VALUE;
            for (CableNetwork.RouterAttachment attachment : segment.routers()) {
                if (level.getBlockEntity(attachment.routerPos()) instanceof RouterBlockEntity candidate) {
                    RouteDecision decision = candidate.lookup(destination);
                    if (decision != null) {
                        int cost = segment.distanceTo(attachment.routerPos()) + decision.cost();
                        if (cost < bestCost) {
                            bestCost = cost;
                            router = attachment.routerPos();
                        }
                    }
                }
            }
            if (router == null) {
                return null;
            }
            path.addAll(segment.pathTo(router));
        }

        for (int i = 0; i < MAX_ROUTERS; i++) {
            if (!(level.getBlockEntity(router) instanceof RouterBlockEntity current)) {
                return null;
            }
            RouteDecision decision = current.lookup(destination);
            if (decision == null) {
                return null;
            }
            CableNetwork segment = manager.portNetwork(level, router, switch (decision) {
                case RouteDecision.Deliver deliver -> deliver.port();
                case RouteDecision.Forward forward -> forward.port();
            });
            BlockPos target = switch (decision) {
                case RouteDecision.Deliver deliver -> findHost(level, segment, destination);
                case RouteDecision.Forward forward -> forward.nextRouter();
            };
            if (target == null || !segment.contains(target)) {
                return null;
            }
            List<BlockPos> leg = segment.pathTo(target);
            path.addAll(leg.subList(1, leg.size()));
            if (decision instanceof RouteDecision.Deliver) {
                return path;
            }
            router = target;
        }
        return null;
    }

    public static @Nullable BlockPos findHost(ServerLevel level, CableNetwork segment, Ipv4Address address) {
        for (BlockPos pos : segment.interfaces()) {
            if (level.getBlockEntity(pos) instanceof NetworkInterfaceBlockEntity host && host.hasUsableAddress()
                    && host.assigned() != null && host.assigned().address().equals(address)) {
                return pos;
            }
        }
        return null;
    }
}
