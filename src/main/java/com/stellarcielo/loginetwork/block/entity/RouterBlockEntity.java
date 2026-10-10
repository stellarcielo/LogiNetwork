package com.stellarcielo.loginetwork.block.entity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.stellarcielo.loginetwork.address.Ipv4Address;
import com.stellarcielo.loginetwork.address.Ipv4Interface;
import com.stellarcielo.loginetwork.address.MacAddress;
import com.stellarcielo.loginetwork.network.CableNetwork;
import com.stellarcielo.loginetwork.network.NetworkManager;
import com.stellarcielo.loginetwork.network.control.ControlEndpoint;
import com.stellarcielo.loginetwork.network.control.ControlMessage;
import com.stellarcielo.loginetwork.network.control.ControlMessageType;
import com.stellarcielo.loginetwork.network.payload.OpenRouterScreenPayload;
import com.stellarcielo.loginetwork.network.routing.Lsa;
import com.stellarcielo.loginetwork.network.routing.RouteDecision;
import com.stellarcielo.loginetwork.network.routing.ShortestPaths;
import com.stellarcielo.loginetwork.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.network.PacketDistributor;

// Link-state router. Each of its six faces is a port into a separate segment.
// Every REFRESH_INTERVAL ticks it rebuilds its own LSA from what it sees on its ports; when that changes it
// floods the new version to its neighbours, who flood it on. Dijkstra runs only when the database changes.
public class RouterBlockEntity extends BlockEntity implements ControlEndpoint {
    private static final int REFRESH_INTERVAL = 20;
    // Re-flood our LSA this often even without changes, and forget others not refreshed for three times as long.
    private static final long LSA_REFRESH_TICKS = 600;
    private static final long LSA_MAX_AGE_TICKS = 3 * LSA_REFRESH_TICKS;
    // Hosts re-advertise their full list every 600 ticks; items not heard of for three times that are forgotten.
    private static final long ADVERTISEMENT_MAX_AGE_TICKS = 1800;
    // A REQUEST is forwarded to at most this many remote suppliers, cheapest first.
    private static final int MAX_FORWARDED_REQUESTS = 3;

    private static final class AdvertisedItem {
        final ItemStack item;
        long lastHeard;

        AdvertisedItem(ItemStack item, long lastHeard) {
            this.item = item;
            this.lastHeard = lastHeard;
        }
    }

    // One supplier in the inventory table.
    public record Supplier(Ipv4Address host, int cost) {}

    // Traffic for network/prefixLength goes to the router port that owns nextHop. Wins over learned routes.
    public record StaticRoute(Ipv4Address network, int prefixLength, Ipv4Address nextHop) {
        static final Codec<StaticRoute> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Ipv4Address.CODEC.fieldOf("network").forGetter(StaticRoute::network),
                Codec.INT.fieldOf("prefix_length").forGetter(StaticRoute::prefixLength),
                Ipv4Address.CODEC.fieldOf("next_hop").forGetter(StaticRoute::nextHop)
        ).apply(instance, StaticRoute::new));

        // "a.b.c.d/len via e.f.g.h"
        public static Optional<StaticRoute> parse(String text) {
            String[] parts = text.trim().split("\\s+");
            if (parts.length != 3 || !parts[1].equalsIgnoreCase("via")) {
                return Optional.empty();
            }
            Optional<Ipv4Interface> destination = Ipv4Interface.parse(parts[0]);
            Optional<Ipv4Address> nextHop = Ipv4Address.parse(parts[2]);
            if (destination.isEmpty() || nextHop.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new StaticRoute(destination.get().address(), destination.get().prefixLength(), nextHop.get()));
        }

        @Override
        public String toString() {
            return network + "/" + prefixLength + " via " + nextHop;
        }
    }

    public static final int MAX_STATIC_ROUTES = 8;

    private record NeighbourInfo(BlockPos pos, Direction port) {}

    private MacAddress routerId;
    private final Map<Direction, Ipv4Interface> portAddresses = new EnumMap<>(Direction.class);
    private final List<StaticRoute> staticRoutes = new ArrayList<>();

    // Runtime state, rebuilt after loading.
    private final Map<MacAddress, Lsa> database = new HashMap<>();
    private final Map<MacAddress, Long> lastHeard = new HashMap<>();
    private final Map<MacAddress, NeighbourInfo> neighbours = new HashMap<>();
    private @Nullable Lsa self;
    private long lastFlood;
    private Map<MacAddress, ShortestPaths.Path> paths = Map.of();
    private boolean routesDirty = true;
    private int refreshCooldown;
    // Items advertised by hosts in our own segments, keyed by host address.
    private final Map<Ipv4Address, List<AdvertisedItem>> hostItems = new HashMap<>();

    public RouterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ROUTER.get(), pos, state);
        // Replaced by the saved value when loading an existing router.
        this.routerId = MacAddress.random(RandomSource.create());
    }

    public MacAddress routerId() {
        return routerId;
    }

    public Map<Direction, Ipv4Interface> portAddresses() {
        return portAddresses;
    }

    public void setPortAddress(Direction port, @Nullable Ipv4Interface address) {
        if (address == null) {
            portAddresses.remove(port);
        } else {
            portAddresses.put(port, address);
        }
        refreshCooldown = 0;
        setChanged();
    }

    public List<StaticRoute> staticRoutes() {
        return staticRoutes;
    }

    public boolean addStaticRoute(StaticRoute route) {
        if (staticRoutes.size() >= MAX_STATIC_ROUTES) {
            return false;
        }
        staticRoutes.add(route);
        setChanged();
        return true;
    }

    public void removeStaticRoute(int index) {
        if (index >= 0 && index < staticRoutes.size()) {
            staticRoutes.remove(index);
            setChanged();
        }
    }

    public Map<MacAddress, Lsa> database() {
        return database;
    }

    public Map<MacAddress, ShortestPaths.Path> paths() {
        ensureRoutes();
        return paths;
    }

    // ---- tick -----------------------------------------------------------------------------------

    public static void serverTick(Level level, BlockPos pos, BlockState state, RouterBlockEntity router) {
        if (router.refreshCooldown-- > 0) {
            return;
        }
        router.refreshCooldown = REFRESH_INTERVAL;
        router.refresh((ServerLevel) level);
    }

    private void refresh(ServerLevel level) {
        long now = level.getGameTime();
        Set<MacAddress> previousNeighbours = new HashSet<>(neighbours.keySet());
        Lsa local = buildLocalLsa(level);
        if (self == null || !local.sameContent(self) || now - lastFlood >= LSA_REFRESH_TICKS) {
            self = local.withSeq(self == null ? now : Math.max(self.seq() + 1, now));
            database.put(routerId, self);
            lastHeard.put(routerId, now);
            routesDirty = true;
            lastFlood = now;
            flood(level, self, null);
        }
        // Database exchange with new neighbours, so a router that just appeared learns everything at once.
        for (Map.Entry<MacAddress, NeighbourInfo> entry : neighbours.entrySet()) {
            if (!previousNeighbours.contains(entry.getKey())) {
                for (Lsa lsa : database.values()) {
                    sendLsa(level, lsa, entry.getValue());
                }
            }
        }
        if (database.entrySet().removeIf(entry -> !entry.getKey().equals(routerId)
                && now - lastHeard.getOrDefault(entry.getKey(), now) > LSA_MAX_AGE_TICKS)) {
            routesDirty = true;
        }
    }

    private Lsa buildLocalLsa(ServerLevel level) {
        NetworkManager manager = NetworkManager.get(level);
        neighbours.clear();
        List<Lsa.Link> links = new ArrayList<>();
        List<Lsa.HostRoute> hosts = new ArrayList<>();
        for (Direction port : Direction.values()) {
            CableNetwork segment = manager.portNetwork(level, worldPosition, port);
            for (CableNetwork.RouterAttachment attachment : segment.routers()) {
                if (!attachment.routerPos().equals(worldPosition) && level.getBlockEntity(attachment.routerPos()) instanceof RouterBlockEntity neighbour) {
                    links.add(new Lsa.Link(neighbour.routerId, port, Math.max(1, segment.costTo(attachment.routerPos()))));
                    neighbours.putIfAbsent(neighbour.routerId, new NeighbourInfo(attachment.routerPos(), port));
                }
            }
            for (BlockPos hostPos : segment.interfaces()) {
                if (level.getBlockEntity(hostPos) instanceof NetworkInterfaceBlockEntity host && host.hasUsableAddress() && host.assigned() != null) {
                    hosts.add(new Lsa.HostRoute(host.assigned().address(), port));
                }
            }
        }
        List<Lsa.PortAddress> addresses = new ArrayList<>();
        portAddresses.forEach((port, address) -> addresses.add(new Lsa.PortAddress(port, address)));
        return new Lsa(routerId, 0L, worldPosition, links, hosts, addresses, attachedHostItems(hosts, level.getGameTime()));
    }

    // Advertised items of hosts that are still attached, dropping hosts that left and items that went stale.
    private List<Lsa.HostItems> attachedHostItems(List<Lsa.HostRoute> hosts, long now) {
        hostItems.keySet().removeIf(address -> hosts.stream().noneMatch(host -> host.address().equals(address)));
        List<Lsa.HostItems> result = new ArrayList<>();
        for (Lsa.HostRoute host : hosts) {
            List<AdvertisedItem> items = hostItems.get(host.address());
            if (items == null) {
                continue;
            }
            items.removeIf(item -> now - item.lastHeard > ADVERTISEMENT_MAX_AGE_TICKS);
            if (!items.isEmpty()) {
                result.add(new Lsa.HostItems(host.address(), items.stream().map(item -> item.item).toList()));
            }
        }
        return result;
    }

    private void flood(ServerLevel level, Lsa lsa, @Nullable BlockPos except) {
        for (NeighbourInfo neighbour : neighbours.values()) {
            if (!neighbour.pos().equals(except)) {
                sendLsa(level, lsa, neighbour);
            }
        }
    }

    private void sendLsa(ServerLevel level, Lsa lsa, NeighbourInfo neighbour) {
        CableNetwork segment = NetworkManager.get(level).portNetwork(level, worldPosition, neighbour.port());
        if (!segment.contains(neighbour.pos())) {
            return;
        }
        ControlMessage.Content content = ControlMessage.Content.of(ControlMessageType.LSA, routerId, null, worldPosition).withBody(lsa);
        NetworkManager.get(level).controlPlane().sendAlongPath(level, content, segment.pathTo(neighbour.pos()));
    }

    @Override
    public void onControlMessage(ServerLevel level, ControlMessage message) {
        switch (message.type) {
            case LSA -> {
                if (message.body() instanceof Lsa lsa) {
                    receiveLsa(level, lsa, message.sourcePos);
                }
            }
            case ADVERTISE -> updateHostItems(message, level.getGameTime(), true);
            case WITHDRAW -> updateHostItems(message, level.getGameTime(), false);
            case REQUEST -> forwardRequest(level, message);
            default -> {
            }
        }
    }

    private void updateHostItems(ControlMessage message, long now, boolean available) {
        if (message.sourceAddress == null || !(message.body() instanceof List<?> items)) {
            return;
        }
        List<AdvertisedItem> known = hostItems.computeIfAbsent(message.sourceAddress, address -> new ArrayList<>());
        for (Object object : items) {
            if (!(object instanceof ItemStack item)) {
                continue;
            }
            AdvertisedItem existing = known.stream().filter(entry -> ItemStack.isSameItemSameComponents(entry.item, item)).findFirst().orElse(null);
            if (!available) {
                known.remove(existing);
            } else if (existing != null) {
                existing.lastHeard = now;
            } else {
                known.add(new AdvertisedItem(item.copyWithCount(1), now));
            }
        }
        // The next refresh puts the change into our LSA.
        refreshCooldown = 0;
    }

    // A REQUEST broadcast in one of our segments: pass it on to suppliers elsewhere that advertise the item.
    // The original requester stays the source, so offers go straight back to it.
    private void forwardRequest(ServerLevel level, ControlMessage message) {
        if (message.sourceAddress == null) {
            return;
        }
        Direction incoming = Direction.getNearest(message.previousHop().subtract(worldPosition), Direction.NORTH);
        int sent = 0;
        for (Supplier supplier : suppliersOf(message.item)) {
            if (sent >= MAX_FORWARDED_REQUESTS) {
                break;
            }
            if (supplier.host().equals(message.sourceAddress) || isAttachedVia(supplier.host(), incoming)) {
                // Hosts in the requester's own segment already heard the broadcast.
                continue;
            }
            if (NetworkManager.get(level).controlPlane().sendToAddress(level, message.content, worldPosition, supplier.host())) {
                sent++;
            }
        }
    }

    private boolean isAttachedVia(Ipv4Address host, Direction port) {
        return self != null && self.hosts().stream().anyMatch(route -> route.address().equals(host) && route.port() == port);
    }

    // Inventory table: hosts advertising an item anywhere we can reach, cheapest first.
    public List<Supplier> suppliersOf(ItemStack item) {
        ensureRoutes();
        List<Supplier> suppliers = new ArrayList<>();
        for (Lsa lsa : database.values()) {
            int cost;
            if (lsa.routerId().equals(routerId)) {
                cost = 0;
            } else if (paths.containsKey(lsa.routerId())) {
                cost = paths.get(lsa.routerId()).cost();
            } else {
                continue;
            }
            for (Lsa.HostItems hostItems : lsa.items()) {
                if (hostItems.items().stream().anyMatch(candidate -> ItemStack.isSameItemSameComponents(candidate, item))) {
                    suppliers.add(new Supplier(hostItems.host(), cost));
                }
            }
        }
        suppliers.sort(Comparator.comparingInt(Supplier::cost));
        return suppliers;
    }

    private void receiveLsa(ServerLevel level, Lsa lsa, BlockPos from) {
        if (lsa.routerId().equals(routerId)) {
            return;
        }
        Lsa known = database.get(lsa.routerId());
        long now = level.getGameTime();
        if (known != null && lsa.seq() <= known.seq()) {
            if (lsa.seq() == known.seq()) {
                lastHeard.put(lsa.routerId(), now);
            }
            return;
        }
        database.put(lsa.routerId(), lsa);
        lastHeard.put(lsa.routerId(), now);
        routesDirty = true;
        flood(level, lsa, from);
    }

    // ---- forwarding -----------------------------------------------------------------------------

    private void ensureRoutes() {
        if (routesDirty) {
            paths = ShortestPaths.compute(routerId, database);
            routesDirty = false;
        }
    }

    // Directly attached hosts first, then static routes (longest prefix), then the longest matching prefix among
    // other routers' hosts (/32) and port subnets, cheapest on ties. Returns null when there is no route.
    public @Nullable RouteDecision lookup(Ipv4Address destination) {
        if (self == null) {
            return null;
        }
        for (Lsa.HostRoute host : self.hosts()) {
            if (host.address().equals(destination)) {
                return new RouteDecision.Deliver(host.port(), 0);
            }
        }
        RouteDecision viaStatic = lookupStatic(destination);
        if (viaStatic != null) {
            return viaStatic;
        }
        ensureRoutes();
        RouteDecision best = null;
        int bestPrefix = -1;
        for (Lsa.PortAddress portAddress : self.portAddresses()) {
            Ipv4Interface subnet = portAddress.address();
            if (subnet.prefixLength() > bestPrefix && destination.inSubnet(subnet.address(), subnet.prefixLength())) {
                best = new RouteDecision.Deliver(portAddress.port(), 0);
                bestPrefix = subnet.prefixLength();
            }
        }
        for (Map.Entry<MacAddress, ShortestPaths.Path> entry : paths.entrySet()) {
            Lsa lsa = database.get(entry.getKey());
            NeighbourInfo firstHop = neighbours.get(entry.getValue().firstHop());
            if (lsa == null || firstHop == null) {
                continue;
            }
            int prefix = matchLength(lsa, destination);
            int cost = entry.getValue().cost();
            if (prefix > bestPrefix || (prefix == bestPrefix && prefix >= 0 && best != null && cost < best.cost())) {
                best = new RouteDecision.Forward(entry.getValue().firstPort(), firstHop.pos(), cost);
                bestPrefix = prefix;
            }
        }
        return best;
    }

    private @Nullable RouteDecision lookupStatic(Ipv4Address destination) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        StaticRoute best = null;
        for (StaticRoute route : staticRoutes) {
            if (destination.inSubnet(route.network(), route.prefixLength()) && (best == null || route.prefixLength() > best.prefixLength())) {
                best = route;
            }
        }
        return best == null ? null : nextHopDecision(serverLevel, best.nextHop());
    }

    // The port whose segment holds a neighbouring router port addressed nextHop; null if no such neighbour.
    private @Nullable RouteDecision nextHopDecision(ServerLevel level, Ipv4Address nextHop) {
        NetworkManager manager = NetworkManager.get(level);
        for (Direction port : Direction.values()) {
            CableNetwork segment = manager.portNetwork(level, worldPosition, port);
            for (CableNetwork.RouterAttachment attachment : segment.routers()) {
                if (level.getBlockEntity(attachment.routerPos()) instanceof RouterBlockEntity neighbour) {
                    Ipv4Interface address = neighbour.portAddresses.get(attachment.port());
                    if (address != null && address.address().equals(nextHop)) {
                        int linkCost = segment.costTo(attachment.routerPos());
                        return new RouteDecision.Forward(port, attachment.routerPos(), linkCost);
                    }
                }
            }
        }
        return null;
    }

    private static int matchLength(Lsa lsa, Ipv4Address destination) {
        for (Lsa.HostRoute host : lsa.hosts()) {
            if (host.address().equals(destination)) {
                return 32;
            }
        }
        int best = -1;
        for (Lsa.PortAddress portAddress : lsa.portAddresses()) {
            Ipv4Interface subnet = portAddress.address();
            if (subnet.prefixLength() > best && destination.inSubnet(subnet.address(), subnet.prefixLength())) {
                best = subnet.prefixLength();
            }
        }
        return best;
    }

    // Addresses that appear on more than one attachment anywhere in the database (same address, different segments).
    public List<Ipv4Address> duplicateAddresses() {
        Map<Ipv4Address, Set<String>> seen = new HashMap<>();
        for (Lsa lsa : database.values()) {
            for (Lsa.HostRoute host : lsa.hosts()) {
                seen.computeIfAbsent(host.address(), address -> new HashSet<>()).add(lsa.routerId() + "/" + host.port());
            }
        }
        List<Ipv4Address> duplicates = new ArrayList<>();
        seen.forEach((address, places) -> {
            if (places.size() > 1) {
                duplicates.add(address);
            }
        });
        duplicates.sort(Comparator.naturalOrder());
        return duplicates;
    }

    public void openScreen(ServerPlayer player) {
        ServerLevel level = player.level();
        NetworkManager manager = NetworkManager.get(level);
        ensureRoutes();

        List<OpenRouterScreenPayload.PortView> ports = new ArrayList<>();
        for (Direction port : Direction.values()) {
            CableNetwork segment = manager.portNetwork(level, worldPosition, port);
            Ipv4Interface address = portAddresses.get(port);
            ports.add(new OpenRouterScreenPayload.PortView(port.getSerializedName(), address == null ? "" : address.toString(),
                    (int) segment.routers().stream().filter(attachment -> !attachment.routerPos().equals(worldPosition)).count(),
                    segment.interfaces().size()));
        }

        List<OpenRouterScreenPayload.RouteView> routes = new ArrayList<>();
        if (self != null) {
            for (Lsa.HostRoute host : self.hosts()) {
                routes.add(new OpenRouterScreenPayload.RouteView(host.address() + "/32", OpenRouterScreenPayload.KIND_CONNECTED, host.port().getSerializedName(), 0));
            }
            for (Lsa.PortAddress portAddress : self.portAddresses()) {
                routes.add(new OpenRouterScreenPayload.RouteView(networkOf(portAddress.address()), OpenRouterScreenPayload.KIND_CONNECTED,
                        portAddress.port().getSerializedName(), 0));
            }
        }
        for (StaticRoute route : staticRoutes) {
            routes.add(new OpenRouterScreenPayload.RouteView(route.network() + "/" + route.prefixLength(), OpenRouterScreenPayload.KIND_STATIC,
                    route.nextHop().toString(), 0));
        }
        for (Map.Entry<MacAddress, ShortestPaths.Path> entry : paths.entrySet()) {
            Lsa lsa = database.get(entry.getKey());
            if (lsa == null) {
                continue;
            }
            String via = entry.getValue().firstHop().toString();
            int cost = entry.getValue().cost();
            for (Lsa.HostRoute host : lsa.hosts()) {
                routes.add(new OpenRouterScreenPayload.RouteView(host.address() + "/32", OpenRouterScreenPayload.KIND_LEARNED, via, cost));
            }
            for (Lsa.PortAddress portAddress : lsa.portAddresses()) {
                routes.add(new OpenRouterScreenPayload.RouteView(networkOf(portAddress.address()), OpenRouterScreenPayload.KIND_LEARNED, via, cost));
            }
        }

        List<OpenRouterScreenPayload.InventoryView> inventory = new ArrayList<>();
        for (Lsa lsa : database.values()) {
            int cost = lsa.routerId().equals(routerId) ? 0 : paths.containsKey(lsa.routerId()) ? paths.get(lsa.routerId()).cost() : -1;
            if (cost < 0) {
                continue;
            }
            for (Lsa.HostItems hostItems : lsa.items()) {
                for (ItemStack item : hostItems.items()) {
                    inventory.add(new OpenRouterScreenPayload.InventoryView(item, hostItems.host().toString(), cost));
                }
            }
        }
        inventory.sort(Comparator.comparingInt(OpenRouterScreenPayload.InventoryView::cost));

        PacketDistributor.sendToPlayer(player, new OpenRouterScreenPayload(worldPosition, routerId.toString(), ports, routes, inventory,
                staticRoutes.stream().map(StaticRoute::toString).toList(), duplicateAddresses().stream().map(Ipv4Address::toString).toList()));
    }

    private static String networkOf(Ipv4Interface address) {
        int prefix = address.prefixLength();
        int mask = prefix == 0 ? 0 : -1 << (32 - prefix);
        return new Ipv4Address(address.address().bits() & mask) + "/" + prefix;
    }

    // ---- persistence ----------------------------------------------------------------------------

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store("router_id", MacAddress.CODEC, routerId);
        output.store("static_routes", StaticRoute.CODEC.listOf(), List.copyOf(staticRoutes));
        for (Direction port : Direction.values()) {
            output.storeNullable("port_" + port.getSerializedName(), Ipv4Interface.CODEC, portAddresses.get(port));
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.read("router_id", MacAddress.CODEC).ifPresent(value -> routerId = value);
        staticRoutes.clear();
        input.read("static_routes", StaticRoute.CODEC.listOf()).ifPresent(staticRoutes::addAll);
        portAddresses.clear();
        for (Direction port : Direction.values()) {
            input.read("port_" + port.getSerializedName(), Ipv4Interface.CODEC).ifPresent(address -> portAddresses.put(port, address));
        }
    }
}
