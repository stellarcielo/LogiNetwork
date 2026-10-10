package com.stellarcielo.loginetwork.network.payload;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

// Client handlers for play-to-client payloads are attached in LogiNetworkClient.
public final class ModPayloads {
    private static final String PROTOCOL_VERSION = "2";

    private ModPayloads() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToClient(TransitHopPayload.TYPE, TransitHopPayload.STREAM_CODEC);
        registrar.playToClient(TransitRemovePayload.TYPE, TransitRemovePayload.STREAM_CODEC);
        registrar.playToClient(OpenInterfaceScreenPayload.TYPE, OpenInterfaceScreenPayload.STREAM_CODEC);
        registrar.playToClient(OpenDhcpScreenPayload.TYPE, OpenDhcpScreenPayload.STREAM_CODEC);
        registrar.playToServer(ConfigureInterfacePayload.TYPE, ConfigureInterfacePayload.STREAM_CODEC, ServerPayloadHandlers::configureInterface);
        registrar.playToServer(ConfigureDhcpPayload.TYPE, ConfigureDhcpPayload.STREAM_CODEC, ServerPayloadHandlers::configureDhcp);
    }
}
