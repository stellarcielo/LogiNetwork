package com.stellarcielo.loginetwork.network.payload;

import com.stellarcielo.loginetwork.LogiNetwork;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

// Server -> client: packet `id` carrying `stack` entered the segment from -> to at `startTick`, taking `duration` ticks.
public record TransitHopPayload(long id, ItemStack stack, Vec3 from, Vec3 to, long startTick, int duration) implements CustomPacketPayload {
    public static final Type<TransitHopPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(LogiNetwork.MOD_ID, "transit_hop"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TransitHopPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, TransitHopPayload::id,
            ItemStack.STREAM_CODEC, TransitHopPayload::stack,
            Vec3.STREAM_CODEC, TransitHopPayload::from,
            Vec3.STREAM_CODEC, TransitHopPayload::to,
            ByteBufCodecs.VAR_LONG, TransitHopPayload::startTick,
            ByteBufCodecs.VAR_INT, TransitHopPayload::duration,
            TransitHopPayload::new);

    @Override
    public Type<TransitHopPayload> type() {
        return TYPE;
    }
}
