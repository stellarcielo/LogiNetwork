package com.stellarcielo.loginetwork.network.routing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

import com.stellarcielo.loginetwork.address.MacAddress;

import net.minecraft.core.Direction;

// Dijkstra over the link-state database. A link is used only when both ends advertise it.
// Equal-cost paths are all kept (ECMP): a router's path lists every distinct first hop that reaches it at the best cost.
public final class ShortestPaths {
    public record FirstHop(Direction port, MacAddress neighbour) {}

    // How to reach one router from the computing router. firstHops is never empty.
    public record Path(int cost, List<FirstHop> firstHops) {
        public FirstHop firstHop() {
            return firstHops.get(0);
        }
    }

    private record Candidate(MacAddress router, int cost) {}

    private ShortestPaths() {}

    public static Map<MacAddress, Path> compute(MacAddress self, Map<MacAddress, Lsa> database) {
        Map<MacAddress, Integer> distance = new HashMap<>();
        Map<MacAddress, Set<FirstHop>> firstHops = new HashMap<>();
        Set<MacAddress> settled = new HashSet<>();
        Lsa own = database.get(self);
        if (own == null) {
            return Map.of();
        }
        PriorityQueue<Candidate> queue = new PriorityQueue<>((a, b) -> Integer.compare(a.cost(), b.cost()));
        for (Lsa.Link link : own.links()) {
            if (isBidirectional(database, self, link.neighbourId())) {
                relax(distance, firstHops, queue, link.neighbourId(), link.cost(), Set.of(new FirstHop(link.port(), link.neighbourId())));
            }
        }
        while (!queue.isEmpty()) {
            Candidate candidate = queue.poll();
            MacAddress router = candidate.router();
            if (router.equals(self) || candidate.cost() > distance.get(router) || !settled.add(router)) {
                continue;
            }
            Lsa lsa = database.get(router);
            if (lsa == null) {
                continue;
            }
            for (Lsa.Link link : lsa.links()) {
                if (!link.neighbourId().equals(self) && isBidirectional(database, router, link.neighbourId())) {
                    relax(distance, firstHops, queue, link.neighbourId(), candidate.cost() + link.cost(), firstHops.get(router));
                }
            }
        }
        Map<MacAddress, Path> paths = new HashMap<>();
        distance.forEach((router, cost) -> {
            if (!router.equals(self)) {
                paths.put(router, new Path(cost, new ArrayList<>(firstHops.get(router))));
            }
        });
        return paths;
    }

    private static void relax(Map<MacAddress, Integer> distance, Map<MacAddress, Set<FirstHop>> firstHops, PriorityQueue<Candidate> queue,
            MacAddress router, int cost, Set<FirstHop> via) {
        Integer known = distance.get(router);
        if (known == null || cost < known) {
            distance.put(router, cost);
            firstHops.put(router, new LinkedHashSet<>(via));
            queue.add(new Candidate(router, cost));
        } else if (cost == known) {
            firstHops.get(router).addAll(via);
        }
    }

    private static boolean isBidirectional(Map<MacAddress, Lsa> database, MacAddress from, MacAddress to) {
        Lsa other = database.get(to);
        if (other == null) {
            return false;
        }
        for (Lsa.Link link : other.links()) {
            if (link.neighbourId().equals(from)) {
                return true;
            }
        }
        return false;
    }
}
