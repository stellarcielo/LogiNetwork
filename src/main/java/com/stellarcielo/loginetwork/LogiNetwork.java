package com.stellarcielo.loginetwork;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.stellarcielo.loginetwork.registry.ModBlocks;
import com.stellarcielo.loginetwork.registry.ModCreativeTabs;
import com.stellarcielo.loginetwork.registry.ModItems;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

@Mod(LogiNetwork.MOD_ID)
public class LogiNetwork {
    public static final String MOD_ID = "loginetwork";
    public static final Logger LOGGER = LogUtils.getLogger();

    public LogiNetwork(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        ModCreativeTabs.register(modEventBus);

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }
}
