package com.stellarcielo.loginetwork.network;

import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.stellarcielo.loginetwork.address.Ipv4Address;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

// An item stack travelling through cables.
// nodes: [source inventory?] cable... destination inventory. The packet moves from nodes[hop] to nodes[hop + 1].
public final class TransitPacket {
    public static final long NO_REQUEST = 0L;
    // QoS classes; higher is sent first from a queue.
    public static final int PRIORITY_LOW = 0;
    public static final int PRIORITY_NORMAL = 1;
    public static final int PRIORITY_HIGH = 2;

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
            Ipv4Address.CODEC.optionalFieldOf("reply_address").forGetter(p -> Optional.ofNullable(p.replyAddress)),
            Ipv4Address.CODEC.optionalFieldOf("destination_address").forGetter(p -> Optional.ofNullable(p.destinationAddress)),
            Codec.BOOL.optionalFieldOf("waiting", false).forGetter(p -> p.waiting),
            Codec.LONG.optionalFieldOf("wait_since", 0L).forGetter(p -> p.waitSince),
            Codec.INT.optionalFieldOf("priority", PRIORITY_NORMAL).forGetter(p -> p.priority)
    ).apply(instance, (id, stack, nodes, startsAtInventory, hop, hopStart, hopDuration, source, returning, reroutes, requestId, replyAddress,
            destinationAddress, waiting, waitSince, priority) -> {
        TransitPacket packet = new TransitPacket(id, stack, nodes, startsAtInventory, hop, hopStart, hopDuration, source, returning, reroutes,
                requestId, replyAddress.orElse(null), destinationAddress.orElse(null));
        packet.waiting = waiting;
        packet.waitSince = waitSince;
        packet.priority = priority;
        return packet;
    }));


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
    // Set for deliveries made under the request protocol: the supplier's address (for RECEIVED and returns)
    // and the requester's address (for finding a new way after a link breaks).
    final long requestId;
    final @Nullable Ipv4Address replyAddress;
    final @Nullable Ipv4Address destinationAddress;
    // Stopped at nodes[hop], waiting to be sent on (port busy, next device full, or queued in a device).
    boolean waiting;
    long waitSince;
    int priority = PRIORITY_NORMAL;

    TransitPacket(long id, ItemStack stack, List<BlockPos> nodes, boolean startsAtInventory, int hop, long hopStart,
            int hopDuration, BlockPos source, boolean returning, int reroutes, long requestId, @Nullable Ipv4Address replyAddress,
            @Nullable Ipv4Address destinationAddress) {
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
        this.replyAddress = replyAddress;
        this.destinationAddress = destinationAddress;
    }

    public boolean isWaiting() {
        return waiting;
    }

    public int hopDuration() {
        return hopDuration;
    }

    public int priority() {
        return priority;
    }

    public BlockPos currentNode() {
        return nodes.get(hop);
    }

    public @Nullable Ipv4Address destinationAddress() {
        return destinationAddress;
    }

    public @Nullable Ipv4Address supplierAddress() {
        return replyAddress;
    }

    public long id() {
        return id;
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
        this.waiting = false;
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

    public Vec3 currentPosition(long gameTime) {
        float progress = hopDuration <= 0 ? 1.0f : Mth.clamp((float) (gameTime - hopStart) / hopDuration, 0.0f, 1.0f);
        return pointOf(hop).lerp(pointOf(hop + 1), progress);
    }
}
