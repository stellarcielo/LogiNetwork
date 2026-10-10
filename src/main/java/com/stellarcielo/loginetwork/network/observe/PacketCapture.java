package com.stellarcielo.loginetwork.network.observe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.stellarcielo.loginetwork.network.TransitPacket;
import com.stellarcielo.loginetwork.network.control.ControlMessage;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

// Packet capture started with the observation tool: records control messages and items passing one node
// for a while, then reports them to the player who started it. Not saved.
public final class PacketCapture {
    public static final int DURATION_TICKS = 600;
    private static final int MAX_ENTRIES = 200;
    private static final int MAX_REPORTED = 20;

    private static final class Session {
        final UUID player;
        final long start;
        final long end;
        final List<Component> entries = new ArrayList<>();
        int dropped;

        Session(UUID player, long start, long end) {
            this.player = player;
            this.start = start;
            this.end = end;
        }
    }

    private final Map<BlockPos, Session> sessions = new HashMap<>();

    public void start(UUID player, BlockPos node, long now, int duration) {
        sessions.put(node.immutable(), new Session(player, now, now + duration));
    }

    public boolean isCapturing(BlockPos node) {
        return sessions.containsKey(node);
    }

    public List<Component> entries(BlockPos node) {
        Session session = sessions.get(node);
        return session == null ? List.of() : List.copyOf(session.entries);
    }

    public void recordControl(BlockPos node, ControlMessage message, long now) {
        Session session = sessions.get(node);
        if (session != null) {
            add(session, Component.translatable("observe.loginetwork.capture.control", now - session.start, message.type.name(),
                    message.requestId, describeItem(message.item, message.count), address(message.sourceAddress)));
        }
    }

    public void recordData(BlockPos node, TransitPacket packet, long now) {
        Session session = sessions.get(node);
        if (session != null) {
            add(session, Component.translatable("observe.loginetwork.capture.data", now - session.start, packet.requestId(),
                    describeItem(packet.stack(), packet.stack().getCount()), address(packet.destinationAddress()),
                    Component.translatable("gui.loginetwork.qos." + packet.priority())));
        }
    }

    private static void add(Session session, Component entry) {
        if (session.entries.size() < MAX_ENTRIES) {
            session.entries.add(entry);
        } else {
            session.dropped++;
        }
    }

    // Reports finished captures to their players.
    public void tick(ServerLevel level, long now) {
        Iterator<Map.Entry<BlockPos, Session>> it = sessions.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, Session> entry = it.next();
            Session session = entry.getValue();
            if (now < session.end) {
                continue;
            }
            it.remove();
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(session.player);
            if (player == null) {
                continue;
            }
            BlockPos pos = entry.getKey();
            player.sendSystemMessage(Component.translatable("observe.loginetwork.capture.header", pos.getX(), pos.getY(), pos.getZ(),
                    session.entries.size() + session.dropped).withStyle(ChatFormatting.AQUA));
            int shown = Math.min(MAX_REPORTED, session.entries.size());
            for (int i = 0; i < shown; i++) {
                player.sendSystemMessage(session.entries.get(i));
            }
            int hidden = session.entries.size() + session.dropped - shown;
            if (hidden > 0) {
                player.sendSystemMessage(Component.translatable("observe.loginetwork.more", hidden).withStyle(ChatFormatting.GRAY));
            }
        }
    }

    static MutableComponent describeItem(net.minecraft.world.item.ItemStack item, int count) {
        return item.isEmpty() ? Component.literal("-") : Component.translatable("observe.loginetwork.item_count", item.getHoverName(), count);
    }

    static Component address(Object address) {
        return address == null ? Component.translatable("gui.loginetwork.none") : Component.literal(address.toString());
    }
}
