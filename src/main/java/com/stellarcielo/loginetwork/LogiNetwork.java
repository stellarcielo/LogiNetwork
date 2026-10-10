package com.stellarcielo.loginetwork;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.stellarcielo.loginetwork.gametest.ModGameTests;
import com.stellarcielo.loginetwork.network.payload.ModPayloads;
import com.stellarcielo.loginetwork.registry.ModBlockEntities;
import com.stellarcielo.loginetwork.registry.ModBlocks;
import com.stellarcielo.loginetwork.registry.ModCreativeTabs;
import com.stellarcielo.loginetwork.registry.ModItems;
import com.stellarcielo.loginetwork.registry.ModMenus;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;

@Mod(LogiNetwork.MOD_ID)
public class LogiNetwork {
    public static final String MOD_ID = "loginetwork";
    public static final Logger LOGGER = LogUtils.getLogger();

    public LogiNetwork(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModMenus.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
        modEventBus.addListener(ModPayloads::register);
        ModGameTests.register(modEventBus);
        modEventBus.addListener(LogiNetwork::registerCapabilities);

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.COMMON_SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, Config.CLIENT_SPEC);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.DISCARD_CHEST.get(), (chest, side) -> VanillaContainerWrapper.of(chest));
    }
}
