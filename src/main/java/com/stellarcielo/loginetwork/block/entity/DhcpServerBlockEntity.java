package com.stellarcielo.loginetwork.block.entity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.stellarcielo.loginetwork.address.Ipv4Address;
import com.stellarcielo.loginetwork.address.Ipv4Interface;
import com.stellarcielo.loginetwork.address.MacAddress;
import com.stellarcielo.loginetwork.network.CableNetwork;
import com.stellarcielo.loginetwork.network.NetworkManager;
import com.stellarcielo.loginetwork.network.payload.OpenDhcpScreenPayload;
import com.stellarcielo.loginetwork.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.network.PacketDistributor;

// Address pool plus the lease table. Leases last until the interface is removed or moves away.
public class DhcpServerBlockEntity extends BlockEntity {
    public static final Ipv4Address DEFAULT_RANGE_START = new Ipv4Address(0x0A000002); // 10.0.0.2
    public static final Ipv4Address DEFAULT_RANGE_END = new Ipv4Address(0x0A0000FE); // 10.0.0.254
    public static final int DEFAULT_PREFIX_LENGTH = 24;
    private static final int RECLAIM_INTERVAL = 200;

    public record Lease(MacAddress mac, Ipv4Address address, BlockPos interfacePos) {
        static final Codec<Lease> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                MacAddress.CODEC.fieldOf("mac").forGetter(Lease::mac),
                Ipv4Address.CODEC.fieldOf("address").forGetter(Lease::address),
                BlockPos.CODEC.fieldOf("interface").forGetter(Lease::interfacePos)
        ).apply(instance, Lease::new));
    }

    private Ipv4Address rangeStart = DEFAULT_RANGE_START;
    private Ipv4Address rangeEnd = DEFAULT_RANGE_END;
    private int prefixLength = DEFAULT_PREFIX_LENGTH;
    private final Map<MacAddress, Lease> leases = new LinkedHashMap<>();
    private int reclaimCooldown = RECLAIM_INTERVAL;
    // False only for a server that was just placed; it then takes its own subnet so pools never overlap by default.
    private boolean poolAssigned;

    public DhcpServerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DHCP_SERVER.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (!poolAssigned && level instanceof ServerLevel serverLevel) {
            int subnet = NetworkManager.get(serverLevel).allocateDhcpSubnet();
            // Subnets 10.1.0.0/24, 10.2.0.0/24, ... 10.255.0.0/24, then 10.1.1.0/24 and so on.
            int network = 0x0A000000 | ((1 + (subnet - 1) % 255) << 16) | (((subnet - 1) / 255 & 0xFF) << 8);
            rangeStart = new Ipv4Address(network | 2);
            rangeEnd = new Ipv4Address(network | 254);
            prefixLength = DEFAULT_PREFIX_LENGTH;
            poolAssigned = true;
            setChanged();
        }
    }

    public Ipv4Address rangeStart() {
        return rangeStart;
    }

    public List<Lease> leases() {
        return List.copyOf(leases.values());
    }

    public void configure(Ipv4Address rangeStart, Ipv4Address rangeEnd, int prefixLength) {
        this.rangeStart = rangeStart;
        this.rangeEnd = rangeEnd;
        this.prefixLength = prefixLength;
        // Leases outside the new pool are dropped; their interfaces ask again on their next check.
        leases.values().removeIf(lease -> !inPool(lease.address()));
        setChanged();
    }

    // OFFER: the client's current lease if it has one, otherwise the lowest free address in the pool.
    public @Nullable Ipv4Interface offer(MacAddress mac, CableNetwork segment) {
        Lease existing = leases.get(mac);
        if (existing != null) {
            return new Ipv4Interface(existing.address(), prefixLength);
        }
        Set<Ipv4Address> inUse = new HashSet<>();
        leases.values().forEach(lease -> inUse.add(lease.address()));
        if (level != null) {
            for (BlockPos pos : segment.interfaces()) {
                if (level.getBlockEntity(pos) instanceof NetworkInterfaceBlockEntity other && other.assigned() != null) {
                    inUse.add(other.assigned().address());
                }
            }
        }
        for (Ipv4Address candidate = rangeStart; candidate.compareTo(rangeEnd) <= 0; candidate = candidate.next()) {
            if (!inUse.contains(candidate)) {
                return new Ipv4Interface(candidate, prefixLength);
            }
            if (candidate.bits() == -1) {
                break;
            }
        }
        return null;
    }

    // REQUEST -> ACK (true) or NAK (false).
    public boolean request(MacAddress mac, Ipv4Interface requested, BlockPos interfacePos) {
        if (!inPool(requested.address()) || requested.prefixLength() != prefixLength) {
            return false;
        }
        for (Lease lease : leases.values()) {
            if (lease.address().equals(requested.address()) && !lease.mac().equals(mac)) {
                return false;
            }
        }
        leases.put(mac, new Lease(mac, requested.address(), interfacePos.immutable()));
        setChanged();
        return true;
    }

    public boolean holdsLease(MacAddress mac, Ipv4Interface assigned) {
        Lease lease = leases.get(mac);
        return lease != null && lease.address().equals(assigned.address()) && assigned.prefixLength() == prefixLength;
    }

    public void release(MacAddress mac) {
        if (leases.remove(mac) != null) {
            setChanged();
        }
    }

    private boolean inPool(Ipv4Address address) {
        return address.compareTo(rangeStart) >= 0 && address.compareTo(rangeEnd) <= 0;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, DhcpServerBlockEntity blockEntity) {
        if (blockEntity.reclaimCooldown-- > 0) {
            return;
        }
        blockEntity.reclaimCooldown = RECLAIM_INTERVAL;
        blockEntity.reclaim((ServerLevel) level);
    }

    // Drop leases whose interface is gone. Leases in unloaded chunks are kept until they can be checked.
    private void reclaim(ServerLevel level) {
        boolean removed = leases.values().removeIf(lease -> level.isLoaded(lease.interfacePos())
                && !(level.getBlockEntity(lease.interfacePos()) instanceof NetworkInterfaceBlockEntity ni && ni.mac().equals(lease.mac())));
        if (removed) {
            setChanged();
        }
    }

    public void openScreen(ServerPlayer player) {
        List<OpenDhcpScreenPayload.LeaseView> views = new ArrayList<>();
        for (Lease lease : leases.values()) {
            views.add(new OpenDhcpScreenPayload.LeaseView(lease.mac().toString(), lease.address().toString()));
        }
        PacketDistributor.sendToPlayer(player, new OpenDhcpScreenPayload(worldPosition, rangeStart.toString(), rangeEnd.toString(), prefixLength, views));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store("range_start", Ipv4Address.CODEC, rangeStart);
        output.store("range_end", Ipv4Address.CODEC, rangeEnd);
        output.putInt("prefix_length", prefixLength);
        output.store("leases", Lease.CODEC.listOf(), List.copyOf(leases.values()));
        output.putBoolean("pool_assigned", poolAssigned);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        // Servers saved before pools were assigned per server keep the pool they already had.
        poolAssigned = input.getBooleanOr("pool_assigned", input.read("range_start", Ipv4Address.CODEC).isPresent());
        rangeStart = input.read("range_start", Ipv4Address.CODEC).orElse(DEFAULT_RANGE_START);
        rangeEnd = input.read("range_end", Ipv4Address.CODEC).orElse(DEFAULT_RANGE_END);
        prefixLength = input.getIntOr("prefix_length", DEFAULT_PREFIX_LENGTH);
        leases.clear();
        input.read("leases", Lease.CODEC.listOf()).ifPresent(list -> list.forEach(lease -> leases.put(lease.mac(), lease)));
    }
}
