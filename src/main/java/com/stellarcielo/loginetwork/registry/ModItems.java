package com.stellarcielo.loginetwork.registry;

import com.stellarcielo.loginetwork.LogiNetwork;

import net.minecraft.world.item.BlockItem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(LogiNetwork.MOD_ID);

    public static final DeferredItem<BlockItem> COPPER_CABLE = ITEMS.registerSimpleBlockItem(ModBlocks.COPPER_CABLE);

    private ModItems() {}

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
