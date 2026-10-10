package com.stellarcielo.loginetwork.network;

import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

// An item stack travelling through cables.
// nodes: [source inventory?] cable... destination inventory. The packet moves from nodes[hop] to nodes[hop + 1].
public final class TransitPacket {
    public static final long NO_REQUEST = 0L;

    public static final Codec<TransitPacket> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("id").forGetter(p -> p.id),
            ItemStack.CODEC.fieldOf("stack").forGetter(p -> p.stack),
            BlockPos.CODEC.listOf().fieldOf("nodes").forGetter(p -> p.nodes),
            Codec.BOOL.fieldOf("starts_at_inventory").forGetter(p -> p.startsAtInventory),
            Codec.INT.fieldOf("hop").forGetter(p -> p.hop),
            Codec.LONG.fieldOf("hop_start").forGetter(p -> p.hopStart),
            Codec.INT.fieldOf("hop_duration").forGetter(p -> p.hopDuration),
            BlockPos.CODEC.fieldOf("source").forGetter(p -> p.source),
            Codec.BOOL.fieldOf("returning").forGetter(p -> p.returning),
            Codec.INT.fieldOf("reroutes").forGetter(p -> p.reroutes),
            Codec.LONG.optionalFieldOf("request_id", NO_REQUEST).forGetter(p -> p.requestId),
            BlockPos.CODEC.optionalFieldOf("reply_to").forGetter(p -> Optional.ofNullable(p.replyTo))
    ).apply(instance, (id, stack, nodes, startsAtInventory, hop, hopStart, hopDuration, source, returning, reroutes, requestId, replyTo) ->
            new TransitPacket(id, stack, nodes, startsAtInventory, hop, hopStart, hopDuration, source, returning, reroutes, requestId, replyTo.orElse(null))));

    final long id;
    ItemStack stack;
    List<BlockPos> nodes;
    boolean startsAtInventory;
    int hop;
    long hopStart;
    int hopDuration;
    final BlockPos source;
    boolean returning;
    int reroutes;
    // Set for deliveries made under the request protocol; replyTo is the supplying interface.
    final long requestId;
    final @Nullable BlockPos replyTo;

    TransitPacket(long id, ItemStack stack, List<BlockPos> nodes, boolean startsAtInventory, int hop, long hopStart,
            int hopDuration, BlockPos source, boolean returning, int reroutes, long requestId, @Nullable BlockPos replyTo) {
        this.id = id;
        this.stack = stack;
        this.nodes = List.copyOf(nodes);
        this.startsAtInventory = startsAtInventory;
        this.hop = hop;
        this.hopStart = hopStart;
        this.hopDuration = hopDuration;
        this.source = source;
        this.returning = returning;
        this.reroutes = reroutes;
        this.requestId = requestId;
        this.replyTo = replyTo;
    }

    public long requestId() {
        return requestId;
    }

    public ItemStack stack() {
        return stack;
    }

    public List<BlockPos> nodes() {
        return nodes;
    }

    void setRoute(List<BlockPos> nodes, boolean startsAtInventory) {
        this.nodes = List.copyOf(nodes);
        this.startsAtInventory = startsAtInventory;
        this.hop = 0;
    }

    int lastIndex() {
        return nodes.size() - 1;
    }

    BlockPos destination() {
        return nodes.get(lastIndex());
    }

    boolean isInventoryNode(int index) {
        return index == lastIndex() || (index == 0 && startsAtInventory);
    }

    // Inventory nodes are drawn at the face shared with the adjacent cable; cables at their centre.
    Vec3 pointOf(int index) {
        Vec3 center = Vec3.atCenterOf(nodes.get(index));
        if (!isInventoryNode(index)) {
            return center;
        }
        Vec3 neighbour = Vec3.atCenterOf(nodes.get(index == 0 ? 1 : index - 1));
        return center.add(neighbour).scale(0.5);
    }

    Vec3 currentPosition(long gameTime) {
        float progress = hopDuration <= 0 ? 1.0f : Mth.clamp((float) (gameTime - hopStart) / hopDuration, 0.0f, 1.0f);
        return pointOf(hop).lerp(pointOf(hop + 1), progress);
    }
}
