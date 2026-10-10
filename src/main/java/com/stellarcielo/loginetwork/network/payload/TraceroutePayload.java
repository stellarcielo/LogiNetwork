package com.stellarcielo.loginetwork.network.payload;

import com.stellarcielo.loginetwork.LogiNetwork;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

// Client -> server: trace from the interface at pos to the given address.
public record TraceroutePayload(BlockPos pos, String destination) implements CustomPacketPayload {
    public static final Type<TraceroutePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(LogiNetwork.MOD_ID, "traceroute"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TraceroutePayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, TraceroutePayload::pos,
            ByteBufCodecs.stringUtf8(32), TraceroutePayload::destination,
            TraceroutePayload::new);

    @Override
    public Type<TraceroutePayload> type() {
        return TYPE;
    }
}
