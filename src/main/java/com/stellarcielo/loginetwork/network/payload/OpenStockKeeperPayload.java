package com.stellarcielo.loginetwork.network.payload;

import com.stellarcielo.loginetwork.LogiNetwork;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

// Client -> server: open the stock keeper menu of the interface at pos.
public record OpenStockKeeperPayload(BlockPos pos) implements CustomPacketPayload {
    public static final Type<OpenStockKeeperPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(LogiNetwork.MOD_ID, "open_stock_keeper"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenStockKeeperPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, OpenStockKeeperPayload::pos,
            OpenStockKeeperPayload::new);

    @Override
    public Type<OpenStockKeeperPayload> type() {
        return TYPE;
    }
}
