package com.stellarcielo.loginetwork.network.payload;

import com.stellarcielo.loginetwork.LogiNetwork;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

// Client -> server: apply the address pool from the DHCP server screen.
public record ConfigureDhcpPayload(BlockPos pos, String rangeStart, String rangeEnd, int prefixLength, String dnsServer) implements CustomPacketPayload {
    public static final Type<ConfigureDhcpPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(LogiNetwork.MOD_ID, "configure_dhcp"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ConfigureDhcpPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ConfigureDhcpPayload::pos,
            ByteBufCodecs.stringUtf8(32), ConfigureDhcpPayload::rangeStart,
            ByteBufCodecs.stringUtf8(32), ConfigureDhcpPayload::rangeEnd,
            ByteBufCodecs.VAR_INT, ConfigureDhcpPayload::prefixLength,
            ByteBufCodecs.stringUtf8(32), ConfigureDhcpPayload::dnsServer,
            ConfigureDhcpPayload::new);

    @Override
    public Type<ConfigureDhcpPayload> type() {
        return TYPE;
    }
}
