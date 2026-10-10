package com.stellarcielo.loginetwork.network.control;

// Control-plane messages from SPEC.md 7.1. ADVERTISE/WITHDRAW arrive with routers in phase 6.
public enum ControlMessageType {
    REQUEST(0xFFD24D),
    OFFER(0x55E05A),
    RESERVE(0x4DA6FF),
    ACK(0x4DE3E3),
    NAK(0xFF4D4D),
    RECEIVED(0xF2F2F2);

    // Colour of the light particle drawn on the client.
    private final int color;

    ControlMessageType(int color) {
        this.color = color;
    }

    public int color() {
        return color;
    }

    public static ControlMessageType byOrdinal(int ordinal) {
        ControlMessageType[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : REQUEST;
    }
}
