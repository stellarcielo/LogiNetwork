package com.stellarcielo.loginetwork.network.control;

import net.minecraft.server.level.ServerLevel;

// A block entity that control messages can be delivered to.
public interface ControlEndpoint {
    void onControlMessage(ServerLevel level, ControlMessage message);
}
