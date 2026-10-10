package com.stellarcielo.loginetwork.network.payload;

import com.stellarcielo.loginetwork.LogiNetwork;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

// Server -> client: packet `id` left the cables (delivered or dropped).
public record TransitRemovePayload(long id) implements CustomPacketPayload {
    public static final Type<TransitRemovePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(LogiNetwork.MOD_ID, "transit_remove"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TransitRemovePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, TransitRemovePayload::id,
            TransitRemovePayload::new);

    @Override
    public Type<TransitRemovePayload> type() {
        return TYPE;
    }
}
