package com.stellarcielo.loginetwork.client;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import com.stellarcielo.loginetwork.network.payload.TransitHopPayload;
import com.stellarcielo.loginetwork.network.payload.TransitRemovePayload;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

// Client copy of the packets currently visible in cables, fed by server hop events.
public final class ClientTransitTracker {
    // Forget hops that ended this long ago without a follow-up (e.g. the player walked away).
    private static final long STALE_AFTER_TICKS = 200;

    private static final Map<Long, TransitHopPayload> HOPS = new HashMap<>();

    private ClientTransitTracker() {}

    public static void onHop(TransitHopPayload payload) {
        HOPS.put(payload.id(), payload);
    }

    public static void onRemove(TransitRemovePayload payload) {
        HOPS.remove(payload.id());
    }

    public static void clear() {
        HOPS.clear();
    }

    public static void pruneStale(long gameTime) {
        HOPS.values().removeIf(hop -> gameTime > hop.startTick() + hop.duration() + STALE_AFTER_TICKS);
    }

    public static Collection<TransitHopPayload> hops() {
        return HOPS.values();
    }

    public static Vec3 positionAt(TransitHopPayload hop, double time) {
        double progress = Mth.clamp((time - hop.startTick()) / hop.duration(), 0.0, 1.0);
        return hop.from().lerp(hop.to(), progress);
    }
}
