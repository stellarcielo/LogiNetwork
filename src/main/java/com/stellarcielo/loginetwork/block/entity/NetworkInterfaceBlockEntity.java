package com.stellarcielo.loginetwork.block.entity;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

import com.stellarcielo.loginetwork.address.Ipv4Interface;
import com.stellarcielo.loginetwork.address.MacAddress;
import com.stellarcielo.loginetwork.block.NetworkInterfaceBlock;
import com.stellarcielo.loginetwork.network.CableNetwork;
import com.stellarcielo.loginetwork.network.NetworkManager;
import com.stellarcielo.loginetwork.network.payload.OpenInterfaceScreenPayload;
import com.stellarcielo.loginetwork.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.network.PacketDistributor;

// Host identity: MAC, address configuration and the address currently in use.
// Every CHECK_INTERVAL ticks it renews its DHCP lease (or runs DISCOVER/OFFER/REQUEST/ACK) and checks for conflicts.
public class NetworkInterfaceBlockEntity extends BlockEntity {
    private static final int CHECK_INTERVAL = 40;

    private MacAddress mac;
    private boolean dhcp = true;
    private @Nullable Ipv4Interface staticConfig;
    private @Nullable Ipv4Interface assigned;
    private @Nullable BlockPos leaseServer;
    private InterfaceStatus status = InterfaceStatus.UNASSIGNED;
    private int checkCooldown;

    public NetworkInterfaceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.NETWORK_INTERFACE.get(), pos, state);
        // Replaced by the saved value when loading an existing interface.
        this.mac = MacAddress.random(RandomSource.create());
    }

    public MacAddress mac() {
        return mac;
    }

    public boolean isDhcp() {
        return dhcp;
    }

    public @Nullable Ipv4Interface staticConfig() {
        return staticConfig;
    }

    public @Nullable Ipv4Interface assigned() {
        return assigned;
    }

    public InterfaceStatus status() {
        return status;
    }

    public void configure(boolean dhcp, @Nullable Ipv4Interface staticConfig) {
        this.dhcp = dhcp;
        this.staticConfig = staticConfig;
        this.checkCooldown = 0;
        setChanged();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, NetworkInterfaceBlockEntity blockEntity) {
        if (blockEntity.checkCooldown-- > 0) {
            return;
        }
        blockEntity.checkCooldown = CHECK_INTERVAL;
        blockEntity.refresh((ServerLevel) level);
    }

    private void refresh(ServerLevel level) {
        CableNetwork segment = NetworkManager.get(level).network(level, worldPosition);
        Ipv4Interface previousAssigned = assigned;
        InterfaceStatus previousStatus = status;

        if (dhcp) {
            refreshDhcp(level, segment);
        } else {
            releaseLease(level);
            assigned = staticConfig;
        }
        if (assigned == null) {
            status = InterfaceStatus.UNASSIGNED;
        } else if (conflicts(level, segment)) {
            status = InterfaceStatus.CONFLICT;
        } else {
            status = dhcp ? InterfaceStatus.DHCP_BOUND : InterfaceStatus.STATIC;
        }

        if (!Objects.equals(previousAssigned, assigned) || previousStatus != status) {
            setChanged();
        }
    }

    private void refreshDhcp(ServerLevel level, CableNetwork segment) {
        // Renew: keep the lease while its server is still reachable and still holds it.
        if (assigned != null && leaseServer != null && segment.dhcpServers().contains(leaseServer)
                && dhcpServerAt(level, leaseServer) instanceof DhcpServerBlockEntity server && server.holdsLease(mac, assigned)) {
            return;
        }
        releaseLease(level);
        assigned = null;
        // DISCOVER is broadcast to the segment; take the first OFFER, then REQUEST it and wait for the ACK.
        for (BlockPos serverPos : segment.dhcpServers()) {
            if (!(dhcpServerAt(level, serverPos) instanceof DhcpServerBlockEntity server)) {
                continue;
            }
            Ipv4Interface offer = server.offer(mac, segment);
            if (offer != null && server.request(mac, offer, worldPosition)) {
                assigned = offer;
                leaseServer = serverPos;
                setChanged();
                return;
            }
        }
    }

    private void releaseLease(ServerLevel level) {
        if (leaseServer != null && dhcpServerAt(level, leaseServer) instanceof DhcpServerBlockEntity server) {
            server.release(mac);
        }
        if (leaseServer != null) {
            leaseServer = null;
            setChanged();
        }
    }

    private boolean conflicts(ServerLevel level, CableNetwork segment) {
        for (BlockPos other : segment.interfaces()) {
            if (!other.equals(worldPosition) && level.getBlockEntity(other) instanceof NetworkInterfaceBlockEntity otherInterface
                    && otherInterface.assigned != null && otherInterface.assigned.address().equals(assigned.address())) {
                return true;
            }
        }
        return false;
    }

    private static @Nullable BlockEntity dhcpServerAt(ServerLevel level, BlockPos pos) {
        return level.isLoaded(pos) ? level.getBlockEntity(pos) : null;
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level instanceof ServerLevel serverLevel) {
            releaseLease(serverLevel);
        }
    }

    public void openScreen(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new OpenInterfaceScreenPayload(worldPosition, mac.toString(), dhcp,
                staticConfig == null ? "" : staticConfig.toString(), assigned == null ? "" : assigned.toString(),
                status.ordinal(), getBlockState().getValue(NetworkInterfaceBlock.EXTRACT)));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store("mac", MacAddress.CODEC, mac);
        output.putBoolean("dhcp", dhcp);
        output.storeNullable("static", Ipv4Interface.CODEC, staticConfig);
        output.storeNullable("assigned", Ipv4Interface.CODEC, assigned);
        output.storeNullable("lease_server", BlockPos.CODEC, leaseServer);
        output.putInt("status", status.ordinal());
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.read("mac", MacAddress.CODEC).ifPresent(value -> mac = value);
        dhcp = input.getBooleanOr("dhcp", true);
        staticConfig = input.read("static", Ipv4Interface.CODEC).orElse(null);
        assigned = input.read("assigned", Ipv4Interface.CODEC).orElse(null);
        leaseServer = input.read("lease_server", BlockPos.CODEC).orElse(null);
        status = InterfaceStatus.byOrdinal(input.getIntOr("status", 0));
    }
}
