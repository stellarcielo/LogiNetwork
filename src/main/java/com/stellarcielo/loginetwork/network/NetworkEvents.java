package com.stellarcielo.loginetwork.network;

import com.stellarcielo.loginetwork.LogiNetwork;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

@EventBusSubscriber(modid = LogiNetwork.MOD_ID)
public final class NetworkEvents {
    private NetworkEvents() {}

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            NetworkManager.get(level).tick(level);
        }
    }

    @SubscribeEvent
    static void onChunkSent(ChunkWatchEvent.Sent event) {
        NetworkManager.get(event.getLevel()).sendChunkState(event.getPlayer(), event.getPos());
    }

    @SubscribeEvent
    static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            NetworkManager.get(level).invalidateRoutes();
        }
    }

    @SubscribeEvent
    static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            NetworkManager.get(level).invalidateRoutes();
        }
    }
}
