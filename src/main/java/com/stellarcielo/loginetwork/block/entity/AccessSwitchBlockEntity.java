package com.stellarcielo.loginetwork.block.entity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.stellarcielo.loginetwork.address.MacAddress;
import com.stellarcielo.loginetwork.network.payload.OpenSwitchScreenPayload;
import com.stellarcielo.loginetwork.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

// MAC address table learned from control messages passing through. Not saved: it is relearned after a reload.
public class AccessSwitchBlockEntity extends BlockEntity {
    // Entries not refreshed for this long are aged out (5 minutes, like a typical switch default).
    private static final long AGING_TICKS = 6000;

    private record Entry(Direction port, long lastSeen) {}

    private final Map<MacAddress, Entry> macTable = new LinkedHashMap<>();

    public AccessSwitchBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ACCESS_SWITCH.get(), pos, state);
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
        PacketDistributor.sendToPlayer(player, new OpenSwitchScreenPayload(worldPosition, entries));
    }
}
