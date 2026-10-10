package com.stellarcielo.loginetwork.network.payload;

import java.util.List;

import com.stellarcielo.loginetwork.LogiNetwork;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

// Server -> client: briefly light up these points (a traceroute path).
public record HighlightPathPayload(List<Vec3> points) implements CustomPacketPayload {
    public static final Type<HighlightPathPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(LogiNetwork.MOD_ID, "highlight_path"));

    public static final StreamCodec<RegistryFriendlyByteBuf, HighlightPathPayload> STREAM_CODEC = StreamCodec.composite(
            Vec3.STREAM_CODEC.apply(ByteBufCodecs.list()), HighlightPathPayload::points,
            HighlightPathPayload::new);

    @Override
    public Type<HighlightPathPayload> type() {
        return TYPE;
    }
}
