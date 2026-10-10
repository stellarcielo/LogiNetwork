package com.stellarcielo.loginetwork.network.payload;

import com.stellarcielo.loginetwork.LogiNetwork;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

// Client -> server: apply settings from the network interface screen. staticConfig is "a.b.c.d/len" or empty.
public record ConfigureInterfacePayload(BlockPos pos, boolean dhcp, String staticConfig, boolean extract, boolean supply) implements CustomPacketPayload {
    public static final Type<ConfigureInterfacePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(LogiNetwork.MOD_ID, "configure_interface"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ConfigureInterfacePayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ConfigureInterfacePayload::pos,
            ByteBufCodecs.BOOL, ConfigureInterfacePayload::dhcp,
            ByteBufCodecs.stringUtf8(32), ConfigureInterfacePayload::staticConfig,
            ByteBufCodecs.BOOL, ConfigureInterfacePayload::extract,
            ByteBufCodecs.BOOL, ConfigureInterfacePayload::supply,
            ConfigureInterfacePayload::new);

    @Override
    public Type<ConfigureInterfacePayload> type() {
        return TYPE;
    }
}
