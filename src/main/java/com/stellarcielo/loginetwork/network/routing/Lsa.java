package com.stellarcielo.loginetwork.network.routing;

import java.util.List;

import com.stellarcielo.loginetwork.address.Ipv4Address;
import com.stellarcielo.loginetwork.address.Ipv4Interface;
import com.stellarcielo.loginetwork.address.MacAddress;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;

// Link-state advertisement: what one router knows first-hand. Flooded to every router, never saved.
// seq increases with every new version from the same router; newer versions replace older ones.
public record Lsa(MacAddress routerId, long seq, BlockPos routerPos, List<Link> links, List<HostRoute> hosts,
        List<PortAddress> portAddresses, List<HostItems> items) {
    // A neighbouring router reachable through one of our ports, and the cable distance to it.
    public record Link(MacAddress neighbourId, Direction port, int cost) {}

    // A host in a segment directly attached to this router.
    public record HostRoute(Ipv4Address address, Direction port) {}

    public record PortAddress(Direction port, Ipv4Interface address) {}

    // Item types a directly attached host advertises as available.
    public record HostItems(Ipv4Address host, List<ItemStack> items) {}

    // Same content apart from the sequence number.
    public boolean sameContent(Lsa other) {
        return routerId.equals(other.routerId) && routerPos.equals(other.routerPos) && links.equals(other.links) && hosts.equals(other.hosts)
                && portAddresses.equals(other.portAddresses) && sameItems(items, other.items);
    }

    private static boolean sameItems(List<HostItems> a, List<HostItems> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            HostItems x = a.get(i);
            HostItems y = b.get(i);
            if (!x.host().equals(y.host()) || x.items().size() != y.items().size()) {
                return false;
            }
            for (int j = 0; j < x.items().size(); j++) {
                if (!ItemStack.isSameItemSameComponents(x.items().get(j), y.items().get(j))) {
                    return false;
                }
            }
        }
        return true;
    }

    public Lsa withSeq(long newSeq) {
        return new Lsa(routerId, newSeq, routerPos, links, hosts, portAddresses, items);
    }
}
