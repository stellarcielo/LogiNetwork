package com.stellarcielo.loginetwork.registry;

import com.stellarcielo.loginetwork.LogiNetwork;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(LogiNetwork.MOD_ID);

    public static final DeferredItem<BlockItem> COPPER_CABLE = ITEMS.registerSimpleBlockItem(ModBlocks.COPPER_CABLE);
    public static final DeferredItem<BlockItem> NETWORK_INTERFACE = ITEMS.registerSimpleBlockItem(ModBlocks.NETWORK_INTERFACE);
    public static final DeferredItem<BlockItem> DHCP_SERVER = ITEMS.registerSimpleBlockItem(ModBlocks.DHCP_SERVER);
    public static final DeferredItem<BlockItem> ACCESS_SWITCH = ITEMS.registerSimpleBlockItem(ModBlocks.ACCESS_SWITCH);
    public static final DeferredItem<BlockItem> ROUTER = ITEMS.registerSimpleBlockItem(ModBlocks.ROUTER);

    // Render-only stand-in for control messages; never placed in the world or in any creative tab.
    public static final DeferredItem<Item> CONTROL_PACKET = ITEMS.registerSimpleItem("control_packet");

    private ModItems() {}

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
