package com.stellarcielo.loginetwork.network.payload;

import com.stellarcielo.loginetwork.LogiNetwork;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

// Client -> server: set (or clear, with an empty name) the supplier host name of one stock keeping slot.
public record SetFilterSourcePayload(BlockPos pos, int slot, String source) implements CustomPacketPayload {
    public static final Type<SetFilterSourcePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(LogiNetwork.MOD_ID, "set_filter_source"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetFilterSourcePayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SetFilterSourcePayload::pos,
            ByteBufCodecs.VAR_INT, SetFilterSourcePayload::slot,
            ByteBufCodecs.stringUtf8(64), SetFilterSourcePayload::source,
            SetFilterSourcePayload::new);

    @Override
    public Type<SetFilterSourcePayload> type() {
        return TYPE;
    }
}
