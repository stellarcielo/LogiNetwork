package com.stellarcielo.loginetwork.block.entity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.stellarcielo.loginetwork.address.Ipv4Address;
import com.stellarcielo.loginetwork.network.payload.OpenDnsScreenPayload;
import com.stellarcielo.loginetwork.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.network.PacketDistributor;

// Name -> address records. Manual records stay until removed; dynamic ones are registered by hosts and expire
// when not refreshed. Names compare case-insensitively.
public class DnsServerBlockEntity extends BlockEntity {
    public static final int MAX_NAME_LENGTH = 32;
    public static final int MAX_RECORDS = 64;
    // Hosts refresh their registration every 600 ticks; three missed refreshes remove it.
    private static final long DYNAMIC_TTL_TICKS = 1800;

    public record DnsRecord(String name, Ipv4Address address, boolean dynamic, long refreshed) {
        static final Codec<DnsRecord> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("name").forGetter(DnsRecord::name),
                Ipv4Address.CODEC.fieldOf("address").forGetter(DnsRecord::address),
                Codec.BOOL.fieldOf("dynamic").forGetter(DnsRecord::dynamic),
                Codec.LONG.fieldOf("refreshed").forGetter(DnsRecord::refreshed)
        ).apply(instance, DnsRecord::new));
    }

    private final Map<String, DnsRecord> records = new LinkedHashMap<>();

    public DnsServerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DNS_SERVER.get(), pos, state);
    }

    // Host names: 1-32 characters, no whitespace.
    public static Optional<String> normalize(String name) {
        String trimmed = name.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_NAME_LENGTH || trimmed.chars().anyMatch(Character::isWhitespace)) {
            return Optional.empty();
        }
        return Optional.of(trimmed.toLowerCase(Locale.ROOT));
    }

    public @Nullable Ipv4Address lookup(String name, long now) {
        expire(now);
        DnsRecord record = normalize(name).map(records::get).orElse(null);
        return record == null ? null : record.address();
    }

    // Dynamic update from a host. A manual record with the same name wins and is left alone.
    public void register(String name, Ipv4Address address, long now) {
        normalize(name).ifPresent(key -> {
            DnsRecord existing = records.get(key);
            if (existing != null && !existing.dynamic()) {
                return;
            }
            if (existing == null && records.size() >= MAX_RECORDS) {
                return;
            }
            records.put(key, new DnsRecord(name.trim(), address, true, now));
            setChanged();
        });
    }

    public void unregister(String name, Ipv4Address address) {
        normalize(name).ifPresent(key -> {
            DnsRecord existing = records.get(key);
            if (existing != null && existing.dynamic() && existing.address().equals(address)) {
                records.remove(key);
                setChanged();
            }
        });
    }

    public boolean addManual(String name, Ipv4Address address) {
        Optional<String> key = normalize(name);
        if (key.isEmpty() || (!records.containsKey(key.get()) && records.size() >= MAX_RECORDS)) {
            return false;
        }
        records.put(key.get(), new DnsRecord(name.trim(), address, false, 0L));
        setChanged();
        return true;
    }

    public void remove(int index) {
        if (index >= 0 && index < records.size()) {
            records.remove(new ArrayList<>(records.keySet()).get(index));
            setChanged();
        }
    }

    public List<DnsRecord> records(long now) {
        expire(now);
        return List.copyOf(records.values());
    }

    private void expire(long now) {
        if (records.values().removeIf(record -> record.dynamic() && now - record.refreshed() > DYNAMIC_TTL_TICKS)) {
            setChanged();
        }
    }

    public void openScreen(ServerPlayer player) {
        List<OpenDnsScreenPayload.RecordView> views = new ArrayList<>();
        for (DnsRecord record : records(player.level().getGameTime())) {
            views.add(new OpenDnsScreenPayload.RecordView(record.name(), record.address().toString(), record.dynamic()));
        }
        PacketDistributor.sendToPlayer(player, new OpenDnsScreenPayload(worldPosition, views));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store("records", DnsRecord.CODEC.listOf(), List.copyOf(records.values()));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        records.clear();
        input.read("records", DnsRecord.CODEC.listOf()).ifPresent(list -> list.forEach(record ->
                normalize(record.name()).ifPresent(key -> records.put(key, record))));
    }
}
