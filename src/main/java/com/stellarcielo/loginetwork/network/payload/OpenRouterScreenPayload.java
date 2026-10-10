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

// Server -> client: open (or refresh) the router screen with a snapshot of its ports, tables and settings.
public record OpenRouterScreenPayload(BlockPos pos, String routerId, List<PortView> ports, List<RouteView> routes,
        List<InventoryView> inventory, List<String> staticRoutes, List<String> duplicates) implements CustomPacketPayload {
    public static final Type<OpenRouterScreenPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(LogiNetwork.MOD_ID, "open_router_screen"));

    // port is a Direction serialized name; address is empty when unset.
    public record PortView(String port, String address, int routers, int hosts) {
        static final StreamCodec<RegistryFriendlyByteBuf, PortView> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, PortView::port,
                ByteBufCodecs.STRING_UTF8, PortView::address,
                ByteBufCodecs.VAR_INT, PortView::routers,
                ByteBufCodecs.VAR_INT, PortView::hosts,
                PortView::new);
    }

    public static final int KIND_CONNECTED = 0;
    public static final int KIND_STATIC = 1;
    public static final int KIND_LEARNED = 2;

    // via is a port name for connected routes, a next-hop address for static ones, a router id for learned ones.
    public record RouteView(String destination, int kind, String via, int cost) {
        static final StreamCodec<RegistryFriendlyByteBuf, RouteView> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, RouteView::destination,
                ByteBufCodecs.VAR_INT, RouteView::kind,
                ByteBufCodecs.STRING_UTF8, RouteView::via,
                ByteBufCodecs.VAR_INT, RouteView::cost,
                RouteView::new);
    }

    public record InventoryView(ItemStack item, String host, int cost) {
        static final StreamCodec<RegistryFriendlyByteBuf, InventoryView> STREAM_CODEC = StreamCodec.composite(
                ItemStack.STREAM_CODEC, InventoryView::item,
                ByteBufCodecs.STRING_UTF8, InventoryView::host,
                ByteBufCodecs.VAR_INT, InventoryView::cost,
                InventoryView::new);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenRouterScreenPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, OpenRouterScreenPayload::pos,
            ByteBufCodecs.STRING_UTF8, OpenRouterScreenPayload::routerId,
            PortView.STREAM_CODEC.apply(ByteBufCodecs.list()), OpenRouterScreenPayload::ports,
            RouteView.STREAM_CODEC.apply(ByteBufCodecs.list()), OpenRouterScreenPayload::routes,
            InventoryView.STREAM_CODEC.apply(ByteBufCodecs.list()), OpenRouterScreenPayload::inventory,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), OpenRouterScreenPayload::staticRoutes,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), OpenRouterScreenPayload::duplicates,
            OpenRouterScreenPayload::new);

    @Override
    public Type<OpenRouterScreenPayload> type() {
        return TYPE;
    }
}
