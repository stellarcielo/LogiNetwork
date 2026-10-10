package com.stellarcielo.loginetwork.client;

import java.util.ArrayList;
import java.util.List;

import com.stellarcielo.loginetwork.network.payload.HighlightPathPayload;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;

// Lights up a traceroute path with particles for a few seconds.
public final class ClientHighlights {
    private static final int DURATION_TICKS = 100;
    private static final int INTERVAL_TICKS = 5;

    private record Highlight(List<Vec3> points, long until) {}

    private static final List<Highlight> ACTIVE = new ArrayList<>();

    private ClientHighlights() {}

    public static void onPath(HighlightPathPayload payload, ClientLevel level) {
        ACTIVE.add(new Highlight(payload.points(), level.getGameTime() + DURATION_TICKS));
    }

    public static void clear() {
        ACTIVE.clear();
    }

    public static void tick(ClientLevel level) {
        long now = level.getGameTime();
        ACTIVE.removeIf(highlight -> now > highlight.until());
        if (now % INTERVAL_TICKS != 0) {
            return;
        }
        for (Highlight highlight : ACTIVE) {
            for (Vec3 point : highlight.points()) {
                level.addParticle(ParticleTypes.HAPPY_VILLAGER, point.x, point.y + 0.3, point.z, 0, 0, 0);
            }
        }
    }
}
