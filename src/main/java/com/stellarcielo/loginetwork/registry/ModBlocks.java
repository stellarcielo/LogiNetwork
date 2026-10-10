package com.stellarcielo.loginetwork.registry;

import com.stellarcielo.loginetwork.LogiNetwork;
import com.stellarcielo.loginetwork.block.AccessSwitchBlock;
import com.stellarcielo.loginetwork.block.CableBlock;
import com.stellarcielo.loginetwork.block.DhcpServerBlock;
import com.stellarcielo.loginetwork.block.NetworkInterfaceBlock;
import com.stellarcielo.loginetwork.block.RouterBlock;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(LogiNetwork.MOD_ID);

    public static final DeferredBlock<CableBlock> COPPER_CABLE = BLOCKS.registerBlock("copper_cable", CableBlock::new,
            p -> p.mapColor(MapColor.COLOR_ORANGE).strength(0.5f).sound(SoundType.COPPER).noOcclusion());

    public static final DeferredBlock<NetworkInterfaceBlock> NETWORK_INTERFACE = BLOCKS.registerBlock("network_interface", NetworkInterfaceBlock::new,
            p -> p.mapColor(MapColor.COLOR_ORANGE).strength(1.5f).sound(SoundType.COPPER));

    public static final DeferredBlock<DhcpServerBlock> DHCP_SERVER = BLOCKS.registerBlock("dhcp_server", DhcpServerBlock::new,
            p -> p.mapColor(MapColor.COLOR_ORANGE).strength(1.5f).sound(SoundType.COPPER));

    public static final DeferredBlock<AccessSwitchBlock> ACCESS_SWITCH = BLOCKS.registerBlock("access_switch", AccessSwitchBlock::new,
            p -> p.mapColor(MapColor.COLOR_ORANGE).strength(1.5f).sound(SoundType.COPPER));

    public static final DeferredBlock<RouterBlock> ROUTER = BLOCKS.registerBlock("router", RouterBlock::new,
            p -> p.mapColor(MapColor.COLOR_ORANGE).strength(1.5f).sound(SoundType.COPPER));

    private ModBlocks() {}

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
