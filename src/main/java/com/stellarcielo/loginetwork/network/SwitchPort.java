package com.stellarcielo.loginetwork.network;

import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

// VLAN setting of one switch port. Frames carry a VLAN tag, or UNTAGGED.
// Access port: untagged in, untagged out, all in accessVlan; tagged frames are dropped.
// Trunk port: carries the allowed VLANs tagged, except the native VLAN which travels untagged.
public record SwitchPort(boolean trunk, int accessVlan, @Nullable Set<Integer> allowed) {
    public static final int UNTAGGED = 0;
    public static final int NATIVE_VLAN = 1;
    public static final int MIN_VLAN = 1;
    public static final int MAX_VLAN = 4094;
    // Returned by ingress/egress when the frame does not pass.
    public static final int DROP = -1;

    public static final SwitchPort DEFAULT = new SwitchPort(false, NATIVE_VLAN, null);

    public static final Codec<SwitchPort> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.fieldOf("trunk").forGetter(SwitchPort::trunk),
            Codec.INT.fieldOf("access_vlan").forGetter(SwitchPort::accessVlan),
            Codec.STRING.fieldOf("allowed").forGetter(SwitchPort::allowedText)
    ).apply(instance, (trunk, accessVlan, allowed) -> {
        Optional<Set<Integer>> parsed = parseAllowed(allowed);
        return new SwitchPort(trunk, accessVlan, parsed == null ? null : parsed.orElse(null));
    }));

    // VLAN inside the switch for a frame arriving on this port.
    public int ingress(int frameVlan) {
        if (!trunk) {
            return frameVlan == UNTAGGED ? accessVlan : DROP;
        }
        int vlan = frameVlan == UNTAGGED ? NATIVE_VLAN : frameVlan;
        return carries(vlan) ? vlan : DROP;
    }

    // Tag of a frame of the given VLAN leaving through this port.
    public int egress(int vlan) {
        if (!trunk) {
            return vlan == accessVlan ? UNTAGGED : DROP;
        }
        if (!carries(vlan)) {
            return DROP;
        }
        return vlan == NATIVE_VLAN ? UNTAGGED : vlan;
    }

    private boolean carries(int vlan) {
        return allowed == null || allowed.contains(vlan) || vlan == NATIVE_VLAN;
    }

    public String allowedText() {
        if (allowed == null) {
            return "all";
        }
        return String.join(",", allowed.stream().map(String::valueOf).toList());
    }

    // "all" (empty result), or a comma-separated list of VLAN IDs and ranges such as "10,20-25".
    // Returns null when the text is not valid.
    public static @Nullable Optional<Set<Integer>> parseAllowed(String text) {
        String trimmed = text.trim();
        if (trimmed.isEmpty() || trimmed.equalsIgnoreCase("all")) {
            return Optional.empty();
        }
        Set<Integer> vlans = new TreeSet<>();
        for (String part : trimmed.split(",")) {
            String[] range = part.trim().split("-", -1);
            try {
                int from = Integer.parseInt(range[0].trim());
                int to = range.length == 2 ? Integer.parseInt(range[1].trim()) : from;
                if (range.length > 2 || from < MIN_VLAN || to > MAX_VLAN || from > to) {
                    return null;
                }
                for (int vlan = from; vlan <= to; vlan++) {
                    vlans.add(vlan);
                }
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return Optional.of(vlans);
    }

    public static boolean isValidVlan(int vlan) {
        return vlan >= MIN_VLAN && vlan <= MAX_VLAN;
    }
}
