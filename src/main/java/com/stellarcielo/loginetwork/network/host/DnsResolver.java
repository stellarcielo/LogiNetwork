package com.stellarcielo.loginetwork.network.host;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import com.stellarcielo.loginetwork.address.Ipv4Address;
import com.stellarcielo.loginetwork.block.entity.DnsServerBlockEntity;
import com.stellarcielo.loginetwork.block.entity.NetworkInterfaceBlockEntity;
import com.stellarcielo.loginetwork.network.NetworkManager;
import com.stellarcielo.loginetwork.network.control.ControlMessage;
import com.stellarcielo.loginetwork.network.control.ControlMessageType;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

// Name resolution for one interface: asks its DNS server with DNS_QUERY, caches answers, and keeps the
// interface's own host name registered (DNS_REGISTER). When the interface itself fronts the DNS server,
// queries and registrations are answered locally. Nothing here is saved.
public final class DnsResolver {
    private static final long CACHE_TICKS = 6000;
    private static final long NEGATIVE_CACHE_TICKS = 200;
    private static final long QUERY_TIMEOUT_TICKS = 100;
    private static final long REGISTER_INTERVAL_TICKS = 600;

    // Body of DNS_QUERY (name only), DNS_RESPONSE (address may be null) and DNS_REGISTER.
    public record Answer(String name, @Nullable Ipv4Address address) {}

    public record Registration(String name, boolean remove) {}

    private record CacheEntry(@Nullable Ipv4Address address, long expires) {}

    private static final class Pending {
        final String key;
        final long deadline;
        final List<Consumer<@Nullable Ipv4Address>> callbacks = new ArrayList<>();

        Pending(String key, long deadline) {
            this.key = key;
            this.deadline = deadline;
        }
    }

    private final NetworkInterfaceBlockEntity owner;
    private final Map<String, CacheEntry> cache = new HashMap<>();
    private final Map<Long, Pending> pending = new HashMap<>();
    private @Nullable String registeredName;
    private @Nullable Ipv4Address registeredAddress;
    private long lastRegister;

    public DnsResolver(NetworkInterfaceBlockEntity owner) {
        this.owner = owner;
    }

    // The cached address for a name; when unknown a query is started and empty is returned (callers retry later).
    public Optional<Ipv4Address> lookupNow(ServerLevel level, String name) {
        Optional<String> key = DnsServerBlockEntity.normalize(name);
        if (key.isEmpty()) {
            return Optional.empty();
        }
        CacheEntry entry = cache.get(key.get());
        if (entry != null && entry.expires() > level.getGameTime()) {
            return Optional.ofNullable(entry.address());
        }
        resolve(level, name, address -> {});
        return Optional.empty();
    }

    // Resolve a name and call back with the address, or null when it is unknown or the server does not answer.
    public void resolve(ServerLevel level, String name, Consumer<@Nullable Ipv4Address> callback) {
        long now = level.getGameTime();
        Optional<String> key = DnsServerBlockEntity.normalize(name);
        if (key.isEmpty()) {
            callback.accept(null);
            return;
        }
        CacheEntry entry = cache.get(key.get());
        if (entry != null && entry.expires() > now) {
            callback.accept(entry.address());
            return;
        }
        for (Pending query : pending.values()) {
            if (query.key.equals(key.get())) {
                query.callbacks.add(callback);
                return;
            }
        }
        DnsServerBlockEntity local = owner.frontDnsServer(level);
        if (local != null) {
            Ipv4Address address = local.lookup(key.get(), now);
            remember(key.get(), address, now);
            callback.accept(address);
            return;
        }
        Ipv4Address server = owner.dnsServer(level);
        if (server == null) {
            callback.accept(null);
            return;
        }
        long id = NetworkManager.get(level).nextRequestId();
        Pending query = new Pending(key.get(), now + QUERY_TIMEOUT_TICKS);
        query.callbacks.add(callback);
        pending.put(id, query);
        if (!send(level, ControlMessage.Content.of(ControlMessageType.DNS_QUERY, owner.mac(), ownAddress(), owner.getBlockPos())
                .request(id, ItemStack.EMPTY, 0, 0).withBody(new Answer(key.get(), null)), server)) {
            pending.remove(id);
            remember(key.get(), null, now);
            callback.accept(null);
        }
    }

    public void tick(ServerLevel level) {
        long now = level.getGameTime();
        Iterator<Pending> it = pending.values().iterator();
        while (it.hasNext()) {
            Pending query = it.next();
            if (now >= query.deadline) {
                it.remove();
                remember(query.key, null, now);
                query.callbacks.forEach(callback -> callback.accept(null));
            }
        }
        keepRegistered(level, now);
    }

    // Dynamic DNS: register our host name when it or our address changes, and refresh it regularly.
    private void keepRegistered(ServerLevel level, long now) {
        String name = owner.hostname();
        Ipv4Address address = owner.hasUsableAddress() ? ownAddress() : null;
        boolean changed = !Objects.equals(name, registeredName) || !Objects.equals(address, registeredAddress);
        if (!changed && now - lastRegister < REGISTER_INTERVAL_TICKS) {
            return;
        }
        if (changed && registeredName != null && registeredAddress != null) {
            register(level, new Registration(registeredName, true), registeredAddress, now);
        }
        registeredName = name;
        registeredAddress = address;
        lastRegister = now;
        if (!name.isEmpty() && address != null) {
            register(level, new Registration(name, false), address, now);
        }
    }

    // Best effort when the interface goes away.
    public void unregister(ServerLevel level) {
        if (registeredName != null && registeredAddress != null) {
            register(level, new Registration(registeredName, true), registeredAddress, level.getGameTime());
        }
    }

    private void register(ServerLevel level, Registration registration, Ipv4Address address, long now) {
        DnsServerBlockEntity local = owner.frontDnsServer(level);
        if (local != null) {
            apply(local, registration, address, now);
            return;
        }
        Ipv4Address server = owner.dnsServer(level);
        if (server != null) {
            send(level, ControlMessage.Content.of(ControlMessageType.DNS_REGISTER, owner.mac(), address, owner.getBlockPos()).withBody(registration), server);
        }
    }

    private static void apply(DnsServerBlockEntity server, Registration registration, Ipv4Address address, long now) {
        if (registration.remove()) {
            server.unregister(registration.name(), address);
        } else {
            server.register(registration.name(), address, now);
        }
    }

    // Messages addressed to this interface: answers for us, or requests for the DNS server we front.
    public boolean onControlMessage(ServerLevel level, ControlMessage message) {
        long now = level.getGameTime();
        switch (message.type) {
            case DNS_RESPONSE -> {
                Pending query = pending.remove(message.requestId);
                if (query != null && message.body() instanceof Answer answer) {
                    remember(query.key, answer.address(), now);
                    query.callbacks.forEach(callback -> callback.accept(answer.address()));
                }
                return true;
            }
            case DNS_QUERY -> {
                DnsServerBlockEntity server = owner.frontDnsServer(level);
                if (server != null && message.body() instanceof Answer question) {
                    Answer answer = new Answer(question.name(), server.lookup(question.name(), now));
                    send(level, ControlMessage.Content.of(ControlMessageType.DNS_RESPONSE, owner.mac(), ownAddress(), owner.getBlockPos())
                            .request(message.requestId, ItemStack.EMPTY, 0, 0).withBody(answer), message.sourceAddress);
                }
                return true;
            }
            case DNS_REGISTER -> {
                DnsServerBlockEntity server = owner.frontDnsServer(level);
                if (server != null && message.body() instanceof Registration registration && message.sourceAddress != null) {
                    apply(server, registration, message.sourceAddress, now);
                }
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private void remember(String key, @Nullable Ipv4Address address, long now) {
        cache.put(key, new CacheEntry(address, now + (address == null ? NEGATIVE_CACHE_TICKS : CACHE_TICKS)));
    }

    private @Nullable Ipv4Address ownAddress() {
        return owner.assigned() == null ? null : owner.assigned().address();
    }

    private boolean send(ServerLevel level, ControlMessage.Content content, @Nullable Ipv4Address destination) {
        return NetworkManager.get(level).controlPlane().sendToAddress(level, content, owner.getBlockPos(), destination);
    }
}
