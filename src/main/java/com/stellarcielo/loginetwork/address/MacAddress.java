package com.stellarcielo.loginetwork.address;

import com.mojang.serialization.Codec;

import net.minecraft.util.RandomSource;

// 48-bit hardware identifier for a network interface, fixed when the interface is created.
public record MacAddress(long bits) {
    public static final Codec<MacAddress> CODEC = Codec.LONG.xmap(MacAddress::new, MacAddress::bits);

    private static final long MASK = 0xFFFF_FFFF_FFFFL;
    // Locally administered, unicast: set bit 1 and clear bit 0 of the first octet.
    private static final long LOCAL_BIT = 0x02L << 40;
    private static final long MULTICAST_BIT = 0x01L << 40;

    public static MacAddress random(RandomSource random) {
        return new MacAddress((random.nextLong() & MASK | LOCAL_BIT) & ~MULTICAST_BIT);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder(17);
        for (int shift = 40; shift >= 0; shift -= 8) {
            if (builder.length() > 0) {
                builder.append(':');
            }
            builder.append(String.format("%02x", (bits >>> shift) & 0xFF));
        }
        return builder.toString();
    }
}
