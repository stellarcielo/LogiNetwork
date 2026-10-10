package com.stellarcielo.loginetwork.network.payload;

import com.stellarcielo.loginetwork.LogiNetwork;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

// Server -> client: ask for a traceroute destination for the interface at pos.
public record OpenTraceroutePayload(BlockPos pos) implements CustomPacketPayload {
    public static final Type<OpenTraceroutePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(LogiNetwork.MOD_ID, "open_traceroute"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenTraceroutePayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, OpenTraceroutePayload::pos,
            OpenTraceroutePayload::new);

    @Override
    public Type<OpenTraceroutePayload> type() {
        return TYPE;
    }
}
