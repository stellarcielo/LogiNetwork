package com.stellarcielo.loginetwork.network.payload;

import java.util.List;

import com.stellarcielo.loginetwork.LogiNetwork;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

// Server -> client: open the DHCP server screen with its pool and lease table.
public record OpenDhcpScreenPayload(BlockPos pos, String rangeStart, String rangeEnd, int prefixLength, List<LeaseView> leases)
        implements CustomPacketPayload {
    public static final Type<OpenDhcpScreenPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(LogiNetwork.MOD_ID, "open_dhcp_screen"));

    public record LeaseView(String mac, String address) {
        static final StreamCodec<RegistryFriendlyByteBuf, LeaseView> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, LeaseView::mac,
                ByteBufCodecs.STRING_UTF8, LeaseView::address,
                LeaseView::new);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenDhcpScreenPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, OpenDhcpScreenPayload::pos,
            ByteBufCodecs.STRING_UTF8, OpenDhcpScreenPayload::rangeStart,
            ByteBufCodecs.STRING_UTF8, OpenDhcpScreenPayload::rangeEnd,
            ByteBufCodecs.VAR_INT, OpenDhcpScreenPayload::prefixLength,
            LeaseView.STREAM_CODEC.apply(ByteBufCodecs.list()), OpenDhcpScreenPayload::leases,
            OpenDhcpScreenPayload::new);

    @Override
    public Type<OpenDhcpScreenPayload> type() {
        return TYPE;
    }
}
