package com.stellarcielo.loginetwork.network.payload;

import com.stellarcielo.loginetwork.LogiNetwork;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

// Client -> server: add a manual record ("name a.b.c.d") or remove the record at index.
public record ConfigureDnsPayload(BlockPos pos, boolean add, String text, int index) implements CustomPacketPayload {
    public static final Type<ConfigureDnsPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(LogiNetwork.MOD_ID, "configure_dns"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ConfigureDnsPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ConfigureDnsPayload::pos,
            ByteBufCodecs.BOOL, ConfigureDnsPayload::add,
            ByteBufCodecs.stringUtf8(64), ConfigureDnsPayload::text,
            ByteBufCodecs.VAR_INT, ConfigureDnsPayload::index,
            ConfigureDnsPayload::new);

    @Override
    public Type<ConfigureDnsPayload> type() {
        return TYPE;
    }
}
