package com.stellarcielo.loginetwork.address;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

// IPv4-style host address, stored as an unsigned 32-bit value.
public record Ipv4Address(int bits) implements Comparable<Ipv4Address> {
    public static final Codec<Ipv4Address> CODEC = Codec.STRING.comapFlatMap(
            text -> parse(text).map(DataResult::success).orElseGet(() -> DataResult.error(() -> "Invalid address: " + text)),
            Ipv4Address::toString);

    public static Optional<Ipv4Address> parse(String text) {
        String[] parts = text.trim().split("\\.", -1);
        if (parts.length != 4) {
            return Optional.empty();
        }
        int bits = 0;
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3 || !part.chars().allMatch(Character::isDigit)) {
                return Optional.empty();
            }
            int octet = Integer.parseInt(part);
            if (octet > 255) {
                return Optional.empty();
            }
            bits = (bits << 8) | octet;
        }
        return Optional.of(new Ipv4Address(bits));
    }

    public Ipv4Address next() {
        return new Ipv4Address(bits + 1);
    }

    public boolean inSubnet(Ipv4Address network, int prefixLength) {
        int mask = prefixLength == 0 ? 0 : -1 << (32 - prefixLength);
        return (bits & mask) == (network.bits & mask);
    }

    @Override
    public int compareTo(Ipv4Address other) {
        return Integer.compareUnsigned(bits, other.bits);
    }

    @Override
    public String toString() {
        return (bits >>> 24) + "." + ((bits >>> 16) & 0xFF) + "." + ((bits >>> 8) & 0xFF) + "." + (bits & 0xFF);
    }
}
