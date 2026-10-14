package com.stellarcielo.loginetwork.registry;

import com.stellarcielo.loginetwork.LogiNetwork;

import com.stellarcielo.loginetwork.item.ObservationToolItem;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(LogiNetwork.MOD_ID);

    public static final DeferredItem<BlockItem> COPPER_CABLE = ITEMS.registerSimpleBlockItem(ModBlocks.COPPER_CABLE);
    public static final DeferredItem<BlockItem> FIBER_CABLE = ITEMS.registerSimpleBlockItem(ModBlocks.FIBER_CABLE);
    public static final DeferredItem<BlockItem> DISCARD_CHEST = ITEMS.registerSimpleBlockItem(ModBlocks.DISCARD_CHEST);
    public static final DeferredItem<BlockItem> NETWORK_INTERFACE = ITEMS.registerSimpleBlockItem(ModBlocks.NETWORK_INTERFACE);
    public static final DeferredItem<BlockItem> DHCP_SERVER = ITEMS.registerSimpleBlockItem(ModBlocks.DHCP_SERVER);
    public static final DeferredItem<BlockItem> ACCESS_SWITCH = ITEMS.registerSimpleBlockItem(ModBlocks.ACCESS_SWITCH);
    public static final DeferredItem<BlockItem> ROUTER = ITEMS.registerSimpleBlockItem(ModBlocks.ROUTER);
    public static final DeferredItem<BlockItem> DNS_SERVER = ITEMS.registerSimpleBlockItem(ModBlocks.DNS_SERVER);

    public static final DeferredItem<ObservationToolItem> OBSERVATION_TOOL = ITEMS.registerItem("observation_tool", ObservationToolItem::new,
            p -> p.stacksTo(1));

    // Render-only stand-in for control messages; never placed in the world or in any creative tab.
    public static final DeferredItem<Item> CONTROL_PACKET = ITEMS.registerSimpleItem("control_packet");

    private ModItems() {}

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
