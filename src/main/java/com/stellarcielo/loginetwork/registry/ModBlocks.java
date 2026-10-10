package com.stellarcielo.loginetwork.registry;

import com.stellarcielo.loginetwork.LogiNetwork;
import com.stellarcielo.loginetwork.block.CableBlock;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(LogiNetwork.MOD_ID);

    public static final DeferredBlock<CableBlock> COPPER_CABLE = BLOCKS.registerBlock("copper_cable", CableBlock::new,
            p -> p.mapColor(MapColor.COLOR_ORANGE).strength(0.5f).sound(SoundType.COPPER).noOcclusion());

    private ModBlocks() {}

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
