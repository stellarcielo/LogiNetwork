package com.stellarcielo.loginetwork.network.control;

import java.util.List;

import com.stellarcielo.loginetwork.address.MacAddress;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

// A virtual control packet travelling hop by hop between two network interfaces. Never an ItemStack in the world.
// item is a template (count ignored); count/window meaning depends on the type.
public final class ControlMessage {
    public final ControlMessageType type;
    public final long requestId;
    public final ItemStack item;
    public final int count;
    public final int window;
    public final MacAddress sourceMac;
    public final BlockPos sourcePos;
    public final List<BlockPos> path;
    int hop;
    long hopStart;

    ControlMessage(ControlMessageType type, long requestId, ItemStack item, int count, int window, MacAddress sourceMac,
            BlockPos sourcePos, List<BlockPos> path, long now) {
        this.type = type;
        this.requestId = requestId;
        this.item = item;
        this.count = count;
        this.window = window;
        this.sourceMac = sourceMac;
        this.sourcePos = sourcePos;
        this.path = List.copyOf(path);
        this.hop = 0;
        this.hopStart = now;
    }

    public BlockPos destination() {
        return path.get(path.size() - 1);
    }

    // Number of links the message crosses.
    public int hopCount() {
        return path.size() - 1;
    }
}
