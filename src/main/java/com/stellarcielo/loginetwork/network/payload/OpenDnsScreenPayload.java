package com.stellarcielo.loginetwork.network.payload;

import java.util.List;

import com.stellarcielo.loginetwork.LogiNetwork;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

// Server -> client: open (or refresh) the DNS server screen with its records.
public record OpenDnsScreenPayload(BlockPos pos, List<RecordView> records) implements CustomPacketPayload {
    public static final Type<OpenDnsScreenPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(LogiNetwork.MOD_ID, "open_dns_screen"));

    public record RecordView(String name, String address, boolean dynamic) {
        static final StreamCodec<RegistryFriendlyByteBuf, RecordView> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, RecordView::name,
                ByteBufCodecs.STRING_UTF8, RecordView::address,
                ByteBufCodecs.BOOL, RecordView::dynamic,
                RecordView::new);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenDnsScreenPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, OpenDnsScreenPayload::pos,
            RecordView.STREAM_CODEC.apply(ByteBufCodecs.list()), OpenDnsScreenPayload::records,
            OpenDnsScreenPayload::new);

    @Override
    public Type<OpenDnsScreenPayload> type() {
        return TYPE;
    }
}
