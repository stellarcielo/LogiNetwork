package com.stellarcielo.loginetwork.network.routing;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

// What a router does with traffic for one destination address.
public sealed interface RouteDecision {
    // Cost of the rest of the way, in cable blocks between routers.
    int cost();

    // The destination is in a segment attached to this port.
    record Deliver(Direction port, int cost) implements RouteDecision {}

    // Hand the traffic to a neighbouring router through this port.
    record Forward(Direction port, BlockPos nextRouter, int cost) implements RouteDecision {}
}
