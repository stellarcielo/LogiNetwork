package com.stellarcielo.loginetwork.client;

import java.util.ArrayList;
import java.util.List;

import com.stellarcielo.loginetwork.Config;
import com.stellarcielo.loginetwork.network.control.ControlMessageType;
import com.stellarcielo.loginetwork.network.payload.ControlPathPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.phys.Vec3;

// Draws control messages as coloured light particles moving along their path (client option, off by default).
public final class ClientControlTracker {
    private static final float PARTICLE_SCALE = 0.6f;
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

    public static void tick() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || ACTIVE.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        ACTIVE.removeIf(path -> {
            int hops = path.points().size() - 1;
            double progress = (double) (now - path.startTick()) / path.ticksPerHop();
            if (progress >= hops) {
                return true;
            }
            int index = (int) Math.max(0, Math.floor(progress));
            Vec3 pos = path.points().get(index).lerp(path.points().get(index + 1), Math.max(0, progress - index));
            level.addParticle(new DustParticleOptions(ControlMessageType.byOrdinal(path.messageType()).color(), PARTICLE_SCALE),
                    pos.x, pos.y, pos.z, 0, 0, 0);
            return false;
        });
    }
}
