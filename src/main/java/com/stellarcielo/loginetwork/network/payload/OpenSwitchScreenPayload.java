package com.stellarcielo.loginetwork.network.payload;

import java.util.List;

import com.stellarcielo.loginetwork.LogiNetwork;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

// Server -> client: open the switch screen with its MAC address table.
public record OpenSwitchScreenPayload(BlockPos pos, List<MacEntry> entries, List<PortView> ports) implements CustomPacketPayload {
    public static final Type<OpenSwitchScreenPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(LogiNetwork.MOD_ID, "open_switch_screen"));

    public record MacEntry(String mac, String port, int ageSeconds) {
        static final StreamCodec<RegistryFriendlyByteBuf, MacEntry> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, MacEntry::mac,
                ByteBufCodecs.STRING_UTF8, MacEntry::port,
                ByteBufCodecs.VAR_INT, MacEntry::ageSeconds,
                MacEntry::new);
    }

    // port is a Direction serialized name; allowed is the trunk's VLAN list text ("all" or "10,20-25").
    public record PortView(String port, boolean trunk, int accessVlan, String allowed) {
        static final StreamCodec<RegistryFriendlyByteBuf, PortView> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, PortView::port,
                ByteBufCodecs.BOOL, PortView::trunk,
                ByteBufCodecs.VAR_INT, PortView::accessVlan,
                ByteBufCodecs.STRING_UTF8, PortView::allowed,
                PortView::new);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenSwitchScreenPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, OpenSwitchScreenPayload::pos,
            MacEntry.STREAM_CODEC.apply(ByteBufCodecs.list()), OpenSwitchScreenPayload::entries,
            PortView.STREAM_CODEC.apply(ByteBufCodecs.list()), OpenSwitchScreenPayload::ports,
            OpenSwitchScreenPayload::new);

    @Override
    public Type<OpenSwitchScreenPayload> type() {
        return TYPE;
    }
}
