package com.stellarcielo.loginetwork.address;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

// An address together with its prefix length, written as "10.0.0.5/24".
public record Ipv4Interface(Ipv4Address address, int prefixLength) {
    public static final Codec<Ipv4Interface> CODEC = Codec.STRING.comapFlatMap(
            text -> parse(text).map(DataResult::success).orElseGet(() -> DataResult.error(() -> "Invalid interface address: " + text)),
            Ipv4Interface::toString);

    public static Optional<Ipv4Interface> parse(String text) {
        String[] parts = text.trim().split("/", -1);
        if (parts.length != 2 || parts[1].isEmpty() || parts[1].length() > 2 || !parts[1].chars().allMatch(Character::isDigit)) {
            return Optional.empty();
        }
        int prefixLength = Integer.parseInt(parts[1]);
        if (!isValidPrefixLength(prefixLength)) {
            return Optional.empty();
        }
        return Ipv4Address.parse(parts[0]).map(address -> new Ipv4Interface(address, prefixLength));
    }

    public static boolean isValidPrefixLength(int prefixLength) {
        return prefixLength >= 0 && prefixLength <= 32;
    }

    @Override
    public String toString() {
        return address + "/" + prefixLength;
    }
}
