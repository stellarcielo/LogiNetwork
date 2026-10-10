package com.stellarcielo.loginetwork.network.payload;

import com.stellarcielo.loginetwork.LogiNetwork;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

// Client -> server: one edit from the router screen.
// SET_PORT: index = port (Direction 3D data value), text = "a.b.c.d/len" or empty to clear.
// ADD_STATIC: text = "a.b.c.d/len via e.f.g.h". REMOVE_STATIC: index = position in the list.
public record ConfigureRouterPayload(BlockPos pos, int action, int index, String text) implements CustomPacketPayload {
    public static final Type<ConfigureRouterPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(LogiNetwork.MOD_ID, "configure_router"));

    public static final int SET_PORT = 0;
    public static final int ADD_STATIC = 1;
    public static final int REMOVE_STATIC = 2;

    public static final StreamCodec<RegistryFriendlyByteBuf, ConfigureRouterPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ConfigureRouterPayload::pos,
            ByteBufCodecs.VAR_INT, ConfigureRouterPayload::action,
            ByteBufCodecs.VAR_INT, ConfigureRouterPayload::index,
            ByteBufCodecs.stringUtf8(48), ConfigureRouterPayload::text,
            ConfigureRouterPayload::new);

    @Override
    public Type<ConfigureRouterPayload> type() {
        return TYPE;
    }
}
