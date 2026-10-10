package com.stellarcielo.loginetwork.block;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;

// Link media. Speed is propagation delay, bandwidth limits how fast packets can be sent onto the link,
// and cost weighs the link in route computation.
public enum CableType implements StringRepresentable {
    COPPER("copper", 4, 32, 4),
    FIBER("fiber", 1, 256, 1);

    public static final Codec<CableType> CODEC = StringRepresentable.fromEnum(CableType::values);

    private final String name;
    private final int ticksPerBlock;
    private final int itemsPerSecond;
    private final int costPerBlock;

    CableType(String name, int ticksPerBlock, int itemsPerSecond, int costPerBlock) {
        this.name = name;
        this.ticksPerBlock = ticksPerBlock;
        this.itemsPerSecond = itemsPerSecond;
        this.costPerBlock = costPerBlock;
    }

    public int ticksPerBlock() {
        return ticksPerBlock;
    }

    public int itemsPerSecond() {
        return itemsPerSecond;
    }

    public int costPerBlock() {
        return costPerBlock;
    }

    // Ticks the link stays busy while sending a packet of this many items.
    public int serializationTicks(int items) {
        return Math.max(1, (int) Math.ceil(items * 20.0 / itemsPerSecond));
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
