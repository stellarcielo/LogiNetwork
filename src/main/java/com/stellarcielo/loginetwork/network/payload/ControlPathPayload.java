package com.stellarcielo.loginetwork.network.payload;

import java.util.List;

import com.stellarcielo.loginetwork.LogiNetwork;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

// Server -> client: a control message leaves points[0] at startTick and advances one point every ticksPerHop.
public record ControlPathPayload(int messageType, List<Vec3> points, long startTick, int ticksPerHop) implements CustomPacketPayload {
    public static final Type<ControlPathPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(LogiNetwork.MOD_ID, "control_path"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ControlPathPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ControlPathPayload::messageType,
            Vec3.STREAM_CODEC.apply(ByteBufCodecs.list()), ControlPathPayload::points,
            ByteBufCodecs.VAR_LONG, ControlPathPayload::startTick,
            ByteBufCodecs.VAR_INT, ControlPathPayload::ticksPerHop,
            ControlPathPayload::new);

    @Override
    public Type<ControlPathPayload> type() {
        return TYPE;
    }
}
