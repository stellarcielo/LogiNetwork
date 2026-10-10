package com.stellarcielo.loginetwork.network.payload;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

// Client handlers for play-to-client payloads are attached in LogiNetworkClient.
public final class ModPayloads {
    private static final String PROTOCOL_VERSION = "6";

    private ModPayloads() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToClient(TransitHopPayload.TYPE, TransitHopPayload.STREAM_CODEC);
        registrar.playToClient(TransitRemovePayload.TYPE, TransitRemovePayload.STREAM_CODEC);
        registrar.playToClient(OpenInterfaceScreenPayload.TYPE, OpenInterfaceScreenPayload.STREAM_CODEC);
        registrar.playToClient(OpenDhcpScreenPayload.TYPE, OpenDhcpScreenPayload.STREAM_CODEC);
        registrar.playToServer(ConfigureInterfacePayload.TYPE, ConfigureInterfacePayload.STREAM_CODEC, ServerPayloadHandlers::configureInterface);
        registrar.playToServer(ConfigureDhcpPayload.TYPE, ConfigureDhcpPayload.STREAM_CODEC, ServerPayloadHandlers::configureDhcp);
        registrar.playToServer(OpenStockKeeperPayload.TYPE, OpenStockKeeperPayload.STREAM_CODEC, ServerPayloadHandlers::openStockKeeper);
        registrar.playToClient(ControlPathPayload.TYPE, ControlPathPayload.STREAM_CODEC);
        registrar.playToClient(OpenSwitchScreenPayload.TYPE, OpenSwitchScreenPayload.STREAM_CODEC);
        registrar.playToClient(OpenRouterScreenPayload.TYPE, OpenRouterScreenPayload.STREAM_CODEC);
        registrar.playToClient(OpenTraceroutePayload.TYPE, OpenTraceroutePayload.STREAM_CODEC);
        registrar.playToClient(HighlightPathPayload.TYPE, HighlightPathPayload.STREAM_CODEC);
        registrar.playToServer(TraceroutePayload.TYPE, TraceroutePayload.STREAM_CODEC, ServerPayloadHandlers::traceroute);
        registrar.playToServer(ConfigureRouterPayload.TYPE, ConfigureRouterPayload.STREAM_CODEC, ServerPayloadHandlers::configureRouter);
    }
}
