package com.stellarcielo.loginetwork;

import net.neoforged.neoforge.common.ModConfigSpec;

// Entries from SPEC.md chapter 13 are added in the phase that uses them.
public class Config {
    private static final ModConfigSpec.Builder COMMON_BUILDER = new ModConfigSpec.Builder();

    static final ModConfigSpec COMMON_SPEC = COMMON_BUILDER.build();

    private static final ModConfigSpec.Builder CLIENT_BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue ITEM_RENDER_DISTANCE = CLIENT_BUILDER
            .comment("Maximum distance in blocks at which items travelling through cables are drawn")
            .defineInRange("itemRenderDistance", 32, 0, 256);

    static final ModConfigSpec CLIENT_SPEC = CLIENT_BUILDER.build();
}
