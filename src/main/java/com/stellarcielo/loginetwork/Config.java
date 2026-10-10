package com.stellarcielo.loginetwork;

import net.neoforged.neoforge.common.ModConfigSpec;

// Common config. Entries from SPEC.md chapter 13 are added in the phase that uses them.
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static final ModConfigSpec SPEC = BUILDER.build();
}
