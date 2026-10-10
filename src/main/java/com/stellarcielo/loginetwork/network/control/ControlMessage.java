package com.stellarcielo.loginetwork.network.control;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.stellarcielo.loginetwork.address.Ipv4Address;
import com.stellarcielo.loginetwork.address.MacAddress;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

// A virtual control packet travelling hop by hop along a path of nodes. Never an ItemStack in the world.
public final class ControlMessage {
    // What the message says. item is a template (count ignored); count/window/body meaning depends on the type.
    // The source fields name the original sender, which routers keep when they forward a message.
    public record Content(ControlMessageType type, long requestId, ItemStack item, int count, int window, MacAddress sourceMac,
            @Nullable Ipv4Address sourceAddress, BlockPos sourcePos, @Nullable Object body) {
        public static Content of(ControlMessageType type, MacAddress sourceMac, @Nullable Ipv4Address sourceAddress, BlockPos sourcePos) {
            return new Content(type, 0L, ItemStack.EMPTY, 0, 0, sourceMac, sourceAddress, sourcePos, null);
        }

        public Content request(long requestId, ItemStack item, int count, int window) {
            return new Content(type, requestId, item, count, window, sourceMac, sourceAddress, sourcePos, body);
        }

        public Content withBody(Object body) {
            return new Content(type, requestId, item, count, window, sourceMac, sourceAddress, sourcePos, body);
        }
    }

    public final Content content;
    public final ControlMessageType type;
    public final long requestId;
    public final ItemStack item;
    public final int count;
    public final int window;
    public final MacAddress sourceMac;
    public final @Nullable Ipv4Address sourceAddress;
    public final BlockPos sourcePos;
    public final List<BlockPos> path;
    int hop;
    long hopStart;

    ControlMessage(Content content, List<BlockPos> path, long now) {
        this.content = content;
        this.type = content.type();
        this.requestId = content.requestId();
        this.item = content.item();
        this.count = content.count();
        this.window = content.window();
        this.sourceMac = content.sourceMac();
        this.sourceAddress = content.sourceAddress();
        this.sourcePos = content.sourcePos();
        this.path = List.copyOf(path);
        this.hop = 0;
        this.hopStart = now;
    }

    public @Nullable Object body() {
        return content.body();
    }

    public BlockPos destination() {
        return path.get(path.size() - 1);
    }

    // The node the message came from on its last link, i.e. the previous node before the destination.
    public BlockPos previousHop() {
        return path.get(path.size() - 2);
    }

    // Number of links the message crosses.
    public int hopCount() {
        return path.size() - 1;
    }
}
