package com.stellarcielo.loginetwork;

import net.neoforged.neoforge.common.ModConfigSpec;

// Entries from SPEC.md chapter 13 are added in the phase that uses them.
public class Config {
    private static final ModConfigSpec.Builder COMMON_BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue RESERVE_TIMEOUT_TICKS = COMMON_BUILDER
            .comment("Ticks without progress (offer, ack or delivery) before a request or reservation is dropped and asked again")
            .defineInRange("reserveTimeoutTicks", 200, 20, 72000);

    public static final ModConfigSpec.BooleanValue CONTROL_PACKET_LOSS = COMMON_BUILDER
            .comment("Lose control messages that reach a switch or router whose input queue is full")
            .define("controlPacketLoss", false);

    static final ModConfigSpec COMMON_SPEC = COMMON_BUILDER.build();

    private static final ModConfigSpec.Builder CLIENT_BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue ITEM_RENDER_DISTANCE = CLIENT_BUILDER
            .comment("Maximum distance in blocks at which items travelling through cables are drawn")
            .defineInRange("itemRenderDistance", 32, 0, 256);

    public static final ModConfigSpec.BooleanValue RENDER_CONTROL_PACKETS = CLIENT_BUILDER
            .comment("Draw control messages (REQUEST, OFFER, ...) as particles travelling along cables")
            .define("renderControlPackets", false);

    static final ModConfigSpec CLIENT_SPEC = CLIENT_BUILDER.build();
}
