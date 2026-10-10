package com.stellarcielo.loginetwork.client;

import java.util.ArrayList;
import java.util.List;

import com.stellarcielo.loginetwork.Config;
import com.stellarcielo.loginetwork.network.payload.ControlPathPayload;

import net.minecraft.world.phys.Vec3;

// Control messages currently travelling, kept only while the client option to draw them is on.
public final class ClientControlTracker {
    private static final List<ControlPathPayload> ACTIVE = new ArrayList<>();

    private ClientControlTracker() {}

    public static void onPath(ControlPathPayload payload) {
        if (Config.RENDER_CONTROL_PACKETS.getAsBoolean()) {
            ACTIVE.add(payload);
        }
    }

    public static void clear() {
        ACTIVE.clear();
    }

    public static List<ControlPathPayload> active(double time) {
        ACTIVE.removeIf(path -> progress(path, time) >= path.points().size() - 1);
        return ACTIVE;
    }

    public static Vec3 positionAt(ControlPathPayload path, double time) {
        double progress = Math.max(0, progress(path, time));
        int index = Math.min((int) Math.floor(progress), path.points().size() - 2);
        return path.points().get(index).lerp(path.points().get(index + 1), progress - index);
    }

    private static double progress(ControlPathPayload path, double time) {
        return (time - path.startTick()) / path.ticksPerHop();
    }
}
