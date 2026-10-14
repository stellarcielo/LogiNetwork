package com.stellarcielo.loginetwork.network.payload;

import com.stellarcielo.loginetwork.LogiNetwork;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

// Client -> server: set one switch port (port = Direction 3D data value) to access or trunk mode.
public record ConfigureSwitchPortPayload(BlockPos pos, int port, boolean trunk, int accessVlan, String allowed) implements CustomPacketPayload {
    public static final Type<ConfigureSwitchPortPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(LogiNetwork.MOD_ID, "configure_switch_port"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ConfigureSwitchPortPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ConfigureSwitchPortPayload::pos,
            ByteBufCodecs.VAR_INT, ConfigureSwitchPortPayload::port,
            ByteBufCodecs.BOOL, ConfigureSwitchPortPayload::trunk,
            ByteBufCodecs.VAR_INT, ConfigureSwitchPortPayload::accessVlan,
            ByteBufCodecs.stringUtf8(64), ConfigureSwitchPortPayload::allowed,
            ConfigureSwitchPortPayload::new);

    @Override
    public Type<ConfigureSwitchPortPayload> type() {
        return TYPE;
    }
}
