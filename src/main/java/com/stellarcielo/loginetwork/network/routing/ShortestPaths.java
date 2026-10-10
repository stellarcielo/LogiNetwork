package com.stellarcielo.loginetwork.network.routing;

import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;

import com.stellarcielo.loginetwork.address.MacAddress;

import net.minecraft.core.Direction;

// Dijkstra over the link-state database. A link is used only when both ends advertise it.
public final class ShortestPaths {
    // How to reach one router from the computing router.
    public record Path(int cost, Direction firstPort, MacAddress firstHop) {}

    private record Candidate(MacAddress router, int cost, Direction firstPort, MacAddress firstHop) {}

    private ShortestPaths() {}

    public static Map<MacAddress, Path> compute(MacAddress self, Map<MacAddress, Lsa> database) {
        Map<MacAddress, Path> best = new HashMap<>();
        Lsa own = database.get(self);
        if (own == null) {
            return best;
        }
        PriorityQueue<Candidate> queue = new PriorityQueue<>((a, b) -> Integer.compare(a.cost(), b.cost()));
        for (Lsa.Link link : own.links()) {
            if (isBidirectional(database, self, link.neighbourId())) {
                queue.add(new Candidate(link.neighbourId(), link.cost(), link.port(), link.neighbourId()));
            }
        }
        while (!queue.isEmpty()) {
            Candidate candidate = queue.poll();
            if (candidate.router().equals(self) || best.containsKey(candidate.router())) {
                continue;
            }
            best.put(candidate.router(), new Path(candidate.cost(), candidate.firstPort(), candidate.firstHop()));
            Lsa lsa = database.get(candidate.router());
            if (lsa == null) {
                continue;
            }
            for (Lsa.Link link : lsa.links()) {
                if (!best.containsKey(link.neighbourId()) && isBidirectional(database, candidate.router(), link.neighbourId())) {
                    queue.add(new Candidate(link.neighbourId(), candidate.cost() + link.cost(), candidate.firstPort(), candidate.firstHop()));
                }
            }
        }
        return best;
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
