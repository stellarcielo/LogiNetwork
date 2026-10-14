package com.stellarcielo.loginetwork.block.entity;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.stellarcielo.loginetwork.address.MacAddress;
import com.stellarcielo.loginetwork.network.NetworkManager;
import com.stellarcielo.loginetwork.network.SwitchPort;
import com.stellarcielo.loginetwork.network.payload.OpenSwitchScreenPayload;
import com.stellarcielo.loginetwork.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.network.PacketDistributor;

// Per-port VLAN settings (saved) and the MAC address table learned from control messages passing through
// (not saved: it is relearned after a reload).
public class AccessSwitchBlockEntity extends BlockEntity {
    // Entries not refreshed for this long are aged out (5 minutes, like a typical switch default).
    private static final long AGING_TICKS = 6000;

    private record Entry(Direction port, long lastSeen) {}

    private final Map<MacAddress, Entry> macTable = new LinkedHashMap<>();
    private final Map<Direction, SwitchPort> ports = new EnumMap<>(Direction.class);

    public AccessSwitchBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ACCESS_SWITCH.get(), pos, state);
    }

    public SwitchPort port(Direction port) {
        return ports.getOrDefault(port, SwitchPort.DEFAULT);
    }

    public void setPort(Direction port, SwitchPort config) {
        if (config.equals(SwitchPort.DEFAULT)) {
            ports.remove(port);
        } else {
            ports.put(port, config);
        }
        setChanged();
        // VLANs decide which segment everything belongs to.
        if (level instanceof ServerLevel serverLevel) {
            NetworkManager.get(serverLevel).invalidateRoutes();
        }
    }

    public void learn(MacAddress mac, Direction port, long now) {
        macTable.put(mac, new Entry(port, now));
    }

    public int macTableSize() {
        return macTable.size();
    }

    public void openScreen(ServerPlayer player) {
        long now = player.level().getGameTime();
        macTable.values().removeIf(entry -> now - entry.lastSeen() > AGING_TICKS);
        List<OpenSwitchScreenPayload.MacEntry> entries = new ArrayList<>();
        macTable.forEach((mac, entry) -> entries.add(new OpenSwitchScreenPayload.MacEntry(mac.toString(), entry.port().getSerializedName(),
                (int) Math.min(Integer.MAX_VALUE, (now - entry.lastSeen()) / 20))));
        List<OpenSwitchScreenPayload.PortView> portViews = new ArrayList<>();
        for (Direction port : Direction.values()) {
            SwitchPort config = port(port);
            portViews.add(new OpenSwitchScreenPayload.PortView(port.getSerializedName(), config.trunk(), config.accessVlan(), config.allowedText()));
        }
        PacketDistributor.sendToPlayer(player, new OpenSwitchScreenPayload(worldPosition, entries, portViews));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        for (Direction port : Direction.values()) {
            output.storeNullable("port_" + port.getSerializedName(), SwitchPort.CODEC, ports.get(port));
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        ports.clear();
        for (Direction port : Direction.values()) {
            input.read("port_" + port.getSerializedName(), SwitchPort.CODEC).ifPresent(config -> ports.put(port, config));
        }
    }
}
