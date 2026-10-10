package com.stellarcielo.loginetwork.network.payload;

import java.util.List;

import com.stellarcielo.loginetwork.LogiNetwork;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

// Server -> client: open the network interface screen with a snapshot of its state. Empty strings mean "none".
public record OpenInterfaceScreenPayload(BlockPos pos, String mac, boolean dhcp, String staticConfig, String assigned, int status, boolean extract,
        boolean supply, int priority, List<RequestView> requests) implements CustomPacketPayload {
    public static final Type<OpenInterfaceScreenPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(LogiNetwork.MOD_ID, "open_interface_screen"));

    // One outstanding request on the requester side.
    public record RequestView(long id, ItemStack item, int requested, int received, int inTransit) {
        static final StreamCodec<RegistryFriendlyByteBuf, RequestView> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_LONG, RequestView::id,
                ItemStack.STREAM_CODEC, RequestView::item,
                ByteBufCodecs.VAR_INT, RequestView::requested,
                ByteBufCodecs.VAR_INT, RequestView::received,
                ByteBufCodecs.VAR_INT, RequestView::inTransit,
                RequestView::new);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenInterfaceScreenPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, OpenInterfaceScreenPayload::pos,
            ByteBufCodecs.STRING_UTF8, OpenInterfaceScreenPayload::mac,
            ByteBufCodecs.BOOL, OpenInterfaceScreenPayload::dhcp,
            ByteBufCodecs.STRING_UTF8, OpenInterfaceScreenPayload::staticConfig,
            ByteBufCodecs.STRING_UTF8, OpenInterfaceScreenPayload::assigned,
            ByteBufCodecs.VAR_INT, OpenInterfaceScreenPayload::status,
            ByteBufCodecs.BOOL, OpenInterfaceScreenPayload::extract,
            ByteBufCodecs.BOOL, OpenInterfaceScreenPayload::supply,
            ByteBufCodecs.VAR_INT, OpenInterfaceScreenPayload::priority,
            RequestView.STREAM_CODEC.apply(ByteBufCodecs.list()), OpenInterfaceScreenPayload::requests,
            OpenInterfaceScreenPayload::new);

    @Override
    public Type<OpenInterfaceScreenPayload> type() {
        return TYPE;
    }
}
