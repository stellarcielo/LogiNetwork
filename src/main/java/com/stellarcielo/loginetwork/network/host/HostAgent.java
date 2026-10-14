package com.stellarcielo.loginetwork.network.host;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.stellarcielo.loginetwork.Config;
import com.stellarcielo.loginetwork.address.Ipv4Address;
import com.stellarcielo.loginetwork.block.entity.NetworkInterfaceBlockEntity;
import com.stellarcielo.loginetwork.network.CableNetwork;
import com.stellarcielo.loginetwork.network.NetworkManager;
import com.stellarcielo.loginetwork.network.TransitPacket;
import com.stellarcielo.loginetwork.network.control.ControlMessage;
import com.stellarcielo.loginetwork.network.control.ControlMessageType;
import com.stellarcielo.loginetwork.network.control.ControlPlane;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

// The request/supply protocol (SPEC.md 7.2-7.3) for one network interface.
// Requester side: keeps filtered items stocked by sending REQUEST, collecting OFFERs, sending RESERVE, and
// counting deliveries. Supplier side: answers REQUEST with its surplus and ships on RESERVE.
// Only the configuration is saved; in-progress requests time out and are recreated by the next stock check.
public final class HostAgent {
    public static final int FILTER_SLOTS = 9;
    public static final int MAX_TARGET = 9999;
    private static final int STOCK_CHECK_INTERVAL = 40;
    private static final int OFFER_COLLECT_TICKS = 10;
    private static final long ADVERTISE_REFRESH_TICKS = 600;

    private record FilterEntry(int slot, ItemStack item, int target, String source) {
        static final Codec<FilterEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("slot").forGetter(FilterEntry::slot),
                ItemStack.CODEC.fieldOf("item").forGetter(FilterEntry::item),
                Codec.INT.fieldOf("target").forGetter(FilterEntry::target),
                Codec.STRING.optionalFieldOf("source", "").forGetter(FilterEntry::source)
        ).apply(instance, FilterEntry::new));
    }

    private record Offer(Ipv4Address supplier, int available, int distance) {}

    // One outstanding request: remaining = requested - received - inTransit - reserving.
    public static final class Request {
        final long id;
        final ItemStack item;
        final int requested;
        // Set when the filter names a supplier: REQUESTs go to it directly instead of being broadcast.
        @Nullable Ipv4Address target;
        int received;
        int inTransit;
        final Map<Ipv4Address, Integer> reserving = new HashMap<>();
        final List<Offer> offers = new ArrayList<>();
        long collectDeadline = -1;
        long lastProgress;

        Request(long id, ItemStack item, int requested, long now) {
            this.id = id;
            this.item = item;
            this.requested = requested;
            this.lastProgress = now;
        }

        int reservingTotal() {
            return reserving.values().stream().mapToInt(Integer::intValue).sum();
        }

        int remaining() {
            return requested - received - inTransit - reservingTotal();
        }

        public long id() {
            return id;
        }

        public ItemStack item() {
            return item;
        }

        public int requested() {
            return requested;
        }

        public int received() {
            return received;
        }

        public int inTransit() {
            return inTransit;
        }

        public boolean isReservingFrom(Ipv4Address supplier) {
            return reserving.containsKey(supplier);
        }
    }

    // Supplier-side record of what was shipped for a request, cleared by RECEIVED or timeout.
    private static final class Shipment {
        final int shipped;
        int received;
        long lastUpdate;

        Shipment(int shipped, long now) {
            this.shipped = shipped;
            this.lastUpdate = now;
        }
    }

    private final NetworkInterfaceBlockEntity owner;
    private final SimpleContainer filters;
    private final int[] targets = new int[FILTER_SLOTS];
    // Optional supplier host name per filter slot ("" for any supplier).
    private final String[] sources = new String[FILTER_SLOTS];
    private boolean supply = true;
    private final Map<Long, Request> requests = new LinkedHashMap<>();
    private final Map<Long, Shipment> shipments = new HashMap<>();
    private int stockCooldown;
    // Item types last advertised to the routers of our segment, and which routers have heard the full list.
    private List<ItemStack> advertised = List.of();
    private Set<BlockPos> advertisedTo = Set.of();
    private long lastFullAdvertise;

    public HostAgent(NetworkInterfaceBlockEntity owner) {
        this.owner = owner;
        this.filters = new SimpleContainer(FILTER_SLOTS) {
            @Override
            public void setChanged() {
                super.setChanged();
                owner.setChanged();
            }
        };
    }

    // ---- configuration --------------------------------------------------------------------------

    public SimpleContainer filters() {
        return filters;
    }

    public int target(int slot) {
        return targets[slot];
    }

    public void setTarget(int slot, int target) {
        targets[slot] = Math.max(0, Math.min(MAX_TARGET, target));
        owner.setChanged();
    }

    public String source(int slot) {
        return sources[slot] == null ? "" : sources[slot];
    }

    public void setSource(int slot, String source) {
        sources[slot] = source.trim();
        owner.setChanged();
    }

    public void setFilter(int slot, ItemStack item, int target) {
        filters.setItem(slot, item.isEmpty() ? ItemStack.EMPTY : item.copyWithCount(1));
        setTarget(slot, item.isEmpty() ? 0 : target);
        if (item.isEmpty()) {
            setSource(slot, "");
        }
    }

    public boolean supplies() {
        return supply;
    }

    public void setSupply(boolean supply) {
        this.supply = supply;
        owner.setChanged();
    }

    public List<Request> requests() {
        return List.copyOf(requests.values());
    }

    public int shipmentCount() {
        return shipments.size();
    }

    // ---- tick -----------------------------------------------------------------------------------

    public void tick(ServerLevel level) {
        long now = level.getGameTime();
        int timeout = Config.RESERVE_TIMEOUT_TICKS.getAsInt();

        Iterator<Request> it = requests.values().iterator();
        while (it.hasNext()) {
            Request request = it.next();
            if (request.received >= request.requested || now - request.lastProgress > timeout) {
                // Done, or stalled: forget it. The next stock check asks again for whatever is still missing.
                it.remove();
            } else if (request.collectDeadline >= 0 && now >= request.collectDeadline) {
                if (!reserveFromOffers(level, request)) {
                    it.remove();
                }
            }
        }
        shipments.values().removeIf(shipment -> now - shipment.lastUpdate > timeout);

        if (stockCooldown-- <= 0) {
            stockCooldown = STOCK_CHECK_INTERVAL;
            advertise(level, now);
            checkStock(level, now);
        }
    }

    private void checkStock(ServerLevel level, long now) {
        if (!owner.hasUsableAddress()) {
            return;
        }
        ResourceHandler<ItemResource> inventory = owner.frontInventory(level);
        if (inventory == null) {
            return;
        }
        for (int slot = 0; slot < FILTER_SLOTS; slot++) {
            ItemStack filter = filters.getItem(slot);
            if (filter.isEmpty() || targets[slot] <= 0 || hasRequestFor(filter)) {
                continue;
            }
            int deficit = targets[slot] - count(inventory, filter);
            if (deficit <= 0) {
                continue;
            }
            Ipv4Address target = null;
            if (!source(slot).isEmpty()) {
                // A named supplier: wait until its address is known (the lookup runs in the background).
                target = owner.resolver().lookupNow(level, source(slot)).orElse(null);
                if (target == null) {
                    continue;
                }
            }
            Request request = new Request(NetworkManager.get(level).nextRequestId(), filter.copyWithCount(1), deficit, now);
            request.target = target;
            requests.put(request.id, request);
            sendRequest(level, request, deficit);
        }
    }

    // Broadcast to the segment, or straight to the named supplier.
    private void sendRequest(ServerLevel level, Request request, int count) {
        ControlMessage.Content content = content(ControlMessageType.REQUEST).request(request.id, request.item, count, 0);
        if (request.target != null) {
            sendTo(level, content, request.target);
        } else {
            controlPlane(level).broadcast(level, content);
        }
    }

    // ADVERTISE newly available item types and WITHDRAW sold-out ones to every router in our segment.
    // Routers that are new to us, and all routers every ADVERTISE_REFRESH_TICKS, get the full list.
    private void advertise(ServerLevel level, long now) {
        List<ItemStack> current = supply && owner.hasUsableAddress() ? surplusTypes(level) : List.of();
        List<ItemStack> added = minus(current, advertised);
        List<ItemStack> removed = minus(advertised, current);
        boolean refresh = now - lastFullAdvertise >= ADVERTISE_REFRESH_TICKS;
        CableNetwork segment = NetworkManager.get(level).network(level, owner.getBlockPos());
        Set<BlockPos> routers = new HashSet<>();
        for (CableNetwork.RouterAttachment router : segment.routers()) {
            BlockPos routerPos = router.routerPos();
            routers.add(routerPos);
            List<ItemStack> toAdvertise = refresh || !advertisedTo.contains(routerPos) ? current : added;
            if (!toAdvertise.isEmpty()) {
                controlPlane(level).sendAlongPath(level, content(ControlMessageType.ADVERTISE).withBody(List.copyOf(toAdvertise)), segment.pathTo(routerPos));
            }
            if (!removed.isEmpty() && advertisedTo.contains(routerPos)) {
                controlPlane(level).sendAlongPath(level, content(ControlMessageType.WITHDRAW).withBody(List.copyOf(removed)), segment.pathTo(routerPos));
            }
        }
        if (refresh) {
            lastFullAdvertise = now;
        }
        advertised = current;
        advertisedTo = routers;
    }

    private List<ItemStack> surplusTypes(ServerLevel level) {
        ResourceHandler<ItemResource> inventory = owner.frontInventory(level);
        List<ItemStack> types = new ArrayList<>();
        if (inventory == null) {
            return types;
        }
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemResource resource = inventory.getResource(slot);
            if (resource.isEmpty()) {
                continue;
            }
            ItemStack type = resource.toStack(1);
            if (types.stream().noneMatch(known -> ItemStack.isSameItemSameComponents(known, type)) && surplus(level, type) > 0) {
                types.add(type);
            }
        }
        return types;
    }

    private static List<ItemStack> minus(List<ItemStack> from, List<ItemStack> remove) {
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack item : from) {
            if (remove.stream().noneMatch(other -> ItemStack.isSameItemSameComponents(other, item))) {
                result.add(item);
            }
        }
        return result;
    }

    private boolean hasRequestFor(ItemStack item) {
        for (Request request : requests.values()) {
            if (ItemStack.isSameItemSameComponents(request.item, item)) {
                return true;
            }
        }
        return false;
    }

    // Largest offers first (fewest splits), nearer first on ties. Returns false if nothing could be reserved.
    private boolean reserveFromOffers(ServerLevel level, Request request) {
        request.collectDeadline = -1;
        ResourceHandler<ItemResource> inventory = owner.frontInventory(level);
        int window = inventory == null ? 0 : simulateInsert(inventory, request.item, request.remaining());
        int wanted = Math.min(request.remaining(), window);
        request.offers.sort(Comparator.comparingInt(Offer::available).reversed().thenComparingInt(Offer::distance));
        for (Offer offer : request.offers) {
            if (wanted <= 0) {
                break;
            }
            int ask = Math.min(offer.available(), wanted);
            // The RESERVE carries our QoS class so the supplier ships at that priority.
            ControlMessage.Content reserve = content(ControlMessageType.RESERVE).request(request.id, request.item, ask, window).withBody(owner.priority());
            if (sendTo(level, reserve, offer.supplier())) {
                request.reserving.merge(offer.supplier(), ask, Integer::sum);
                wanted -= ask;
            }
        }
        request.offers.clear();
        return request.reservingTotal() > 0 || request.inTransit > 0;
    }

    // ---- control messages -----------------------------------------------------------------------

    public void onControlMessage(ServerLevel level, ControlMessage message) {
        long now = level.getGameTime();
        switch (message.type) {
            case REQUEST -> answerRequest(level, message);
            case RESERVE -> answerReserve(level, message, now);
            case OFFER -> {
                Request request = requests.get(message.requestId);
                if (request != null && message.count > 0 && message.sourceAddress != null) {
                    // The same supplier may answer twice when several routers forward our REQUEST; keep one offer.
                    request.offers.removeIf(offer -> offer.supplier().equals(message.sourceAddress) && offer.available() <= message.count);
                    if (request.offers.stream().noneMatch(offer -> offer.supplier().equals(message.sourceAddress))) {
                        request.offers.add(new Offer(message.sourceAddress, message.count, message.hopCount()));
                    }
                    if (request.collectDeadline < 0) {
                        request.collectDeadline = now + OFFER_COLLECT_TICKS;
                    }
                    request.lastProgress = now;
                }
            }
            case ACK -> {
                Request request = requests.get(message.requestId);
                if (request != null) {
                    request.reserving.remove(message.sourceAddress);
                    request.inTransit += message.count;
                    request.lastProgress = now;
                }
            }
            case NAK -> {
                Request request = requests.get(message.requestId);
                if (request != null) {
                    request.reserving.remove(message.sourceAddress);
                    request.lastProgress = now;
                    if (request.reserving.isEmpty() && request.inTransit == 0 && request.remaining() > 0) {
                        // Every reservation failed: ask the segment again for what is still missing.
                        sendRequest(level, request, request.remaining());
                    }
                }
            }
            case RECEIVED -> {
                Shipment shipment = shipments.get(message.requestId);
                if (shipment != null) {
                    shipment.received += message.count;
                    shipment.lastUpdate = now;
                    if (shipment.received >= shipment.shipped) {
                        shipments.remove(message.requestId);
                    }
                }
            }
        }
    }

    private void answerRequest(ServerLevel level, ControlMessage message) {
        if (!supply || !owner.hasUsableAddress()) {
            return;
        }
        int available = surplus(level, message.item);
        if (available > 0) {
            sendTo(level, content(ControlMessageType.OFFER).request(message.requestId, message.item, available, 0), message.sourceAddress);
        }
    }

    // ACK is the moment stock is committed (7.3): the items leave the inventory right away as packets.
    private void answerReserve(ServerLevel level, ControlMessage message, long now) {
        Ipv4Address requester = message.sourceAddress;
        int amount = Math.min(Math.min(message.count, message.window), supply ? surplus(level, message.item) : 0);
        ResourceHandler<ItemResource> inventory = owner.frontInventory(level);
        NetworkManager manager = NetworkManager.get(level);
        int extracted = 0;
        if (amount > 0 && inventory != null && requester != null && manager.canShip(level, owner.getBlockPos(), requester)) {
            try (Transaction tx = Transaction.openRoot()) {
                extracted = inventory.extract(ItemResource.of(message.item), amount, tx);
                tx.commit();
            }
        }
        if (extracted <= 0) {
            sendTo(level, content(ControlMessageType.NAK).request(message.requestId, message.item, 0, 0), requester);
            return;
        }
        sendTo(level, content(ControlMessageType.ACK).request(message.requestId, message.item, extracted, 0), requester);
        int priority = message.body() instanceof Integer requested ? requested : TransitPacket.PRIORITY_NORMAL;
        manager.ship(level, owner.getBlockPos(), requester, message.item.copyWithCount(extracted), message.requestId, ownAddress(), priority);
        shipments.merge(message.requestId, new Shipment(extracted, now), (old, added) -> {
            Shipment merged = new Shipment(old.shipped + added.shipped, now);
            merged.received = old.received;
            return merged;
        });
    }

    // Called by the data plane when a packet for one of our requests has been put into our inventory.
    public void onDelivered(ServerLevel level, long requestId, ItemStack item, int count, @Nullable Ipv4Address supplier) {
        Request request = requests.get(requestId);
        if (request == null) {
            // Not ours (a rerouted packet) or already timed out; the supplier's record expires on its own.
            return;
        }
        request.received += count;
        request.inTransit = Math.max(0, request.inTransit - count);
        request.lastProgress = level.getGameTime();
        if (supplier != null) {
            sendTo(level, content(ControlMessageType.RECEIVED).request(requestId, item, count, 0), supplier);
        }
    }

    // What this host can give away: stock above its own keep target for that item.
    private int surplus(ServerLevel level, ItemStack item) {
        ResourceHandler<ItemResource> inventory = owner.frontInventory(level);
        if (inventory == null) {
            return 0;
        }
        int keep = 0;
        for (int slot = 0; slot < FILTER_SLOTS; slot++) {
            if (ItemStack.isSameItemSameComponents(filters.getItem(slot), item)) {
                keep += targets[slot];
            }
        }
        return Math.max(0, count(inventory, item) - keep);
    }

    private static int count(ResourceHandler<ItemResource> inventory, ItemStack item) {
        ItemResource resource = ItemResource.of(item);
        long total = 0;
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (inventory.getResource(slot).equals(resource)) {
                total += inventory.getAmountAsLong(slot);
            }
        }
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    private static int simulateInsert(ResourceHandler<ItemResource> inventory, ItemStack item, int amount) {
        if (amount <= 0) {
            return 0;
        }
        try (Transaction tx = Transaction.openRoot()) {
            return inventory.insert(ItemResource.of(item), amount, tx);
        }
    }

    private static ControlPlane controlPlane(ServerLevel level) {
        return NetworkManager.get(level).controlPlane();
    }

    private @Nullable Ipv4Address ownAddress() {
        return owner.assigned() == null ? null : owner.assigned().address();
    }

    private ControlMessage.Content content(ControlMessageType type) {
        return ControlMessage.Content.of(type, owner.mac(), ownAddress(), owner.getBlockPos());
    }

    private boolean sendTo(ServerLevel level, ControlMessage.Content content, @Nullable Ipv4Address destination) {
        return controlPlane(level).sendToAddress(level, content, owner.getBlockPos(), destination);
    }

    // ---- persistence ----------------------------------------------------------------------------

    public void save(ValueOutput output) {
        List<FilterEntry> entries = new ArrayList<>();
        for (int slot = 0; slot < FILTER_SLOTS; slot++) {
            if (!filters.getItem(slot).isEmpty()) {
                entries.add(new FilterEntry(slot, filters.getItem(slot), targets[slot], source(slot)));
            }
        }
        output.store("filters", FilterEntry.CODEC.listOf(), entries);
        output.putBoolean("supply", supply);
    }

    public void load(ValueInput input) {
        for (int slot = 0; slot < FILTER_SLOTS; slot++) {
            filters.setItem(slot, ItemStack.EMPTY);
            targets[slot] = 0;
            sources[slot] = "";
        }
        input.read("filters", FilterEntry.CODEC.listOf()).ifPresent(entries -> entries.forEach(entry -> {
            if (entry.slot() >= 0 && entry.slot() < FILTER_SLOTS) {
                filters.setItem(entry.slot(), entry.item().copyWithCount(1));
                targets[entry.slot()] = entry.target();
                sources[entry.slot()] = entry.source();
            }
        }));
        supply = input.getBooleanOr("supply", true);
    }
}
