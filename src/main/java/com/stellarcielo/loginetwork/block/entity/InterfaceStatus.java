package com.stellarcielo.loginetwork.block.entity;

public enum InterfaceStatus {
    UNASSIGNED,
    DHCP_BOUND,
    STATIC,
    // Another interface in the same segment uses the same address.
    CONFLICT;

    public String translationKey() {
        return "gui.loginetwork.interface.status." + name().toLowerCase(java.util.Locale.ROOT);
    }

    public static InterfaceStatus byOrdinal(int ordinal) {
        InterfaceStatus[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : UNASSIGNED;
    }
}
