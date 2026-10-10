package com.stellarcielo.loginetwork.network.payload;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

// Client handlers are attached in LogiNetworkClient.
public final class ModPayloads {
    private static final String PROTOCOL_VERSION = "1";

    private ModPayloads() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToClient(TransitHopPayload.TYPE, TransitHopPayload.STREAM_CODEC);
        registrar.playToClient(TransitRemovePayload.TYPE, TransitRemovePayload.STREAM_CODEC);
    }
}
