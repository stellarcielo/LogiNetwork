package com.stellarcielo.loginetwork.item;

import java.util.List;

import com.stellarcielo.loginetwork.network.NetworkManager;
import com.stellarcielo.loginetwork.network.NetworkNodes;
import com.stellarcielo.loginetwork.network.TransitPacket;
import com.stellarcielo.loginetwork.network.observe.Observation;
import com.stellarcielo.loginetwork.network.observe.PacketCapture;
import com.stellarcielo.loginetwork.network.payload.OpenTraceroutePayload;
import com.stellarcielo.loginetwork.registry.ModDataComponents;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

// Hand-held network analyser. Sneak + use cycles the mode.
// INFO: use on a node for its state, or at a travelling item for its details.
// TRACEROUTE: use on a network interface, then enter a destination address.
// CAPTURE: use on a node to record what passes it for 30 seconds.
public class ObservationToolItem extends Item {
    public enum Mode {
        INFO, TRACEROUTE, CAPTURE;

        public Component label() {
            return Component.translatable("item.loginetwork.observation_tool.mode." + name().toLowerCase(java.util.Locale.ROOT));
        }

        static Mode of(ItemStack stack) {
            int ordinal = stack.getOrDefault(ModDataComponents.TOOL_MODE.get(), 0);
            return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : INFO;
        }
    }

    private static final double PACKET_REACH = 8.0;

    public ObservationToolItem(Properties properties) {
        super(properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable("item.loginetwork.observation_tool.with_mode", super.getName(stack), Mode.of(stack).label());
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isSecondaryUseActive()) {
            cycleMode(stack, player);
            return InteractionResult.SUCCESS;
        }
        if (Mode.of(stack) == Mode.INFO && level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
            TransitPacket packet = Observation.packetInSight(serverLevel, player.getEyePosition(), player.getLookAngle(), PACKET_REACH);
            if (packet == null) {
                serverPlayer.sendOverlayMessage(Component.translatable("observe.loginetwork.packet.none"));
            } else {
                send(serverPlayer, Observation.describePacket(packet));
            }
        }
        return InteractionResult.SUCCESS;
    }

    // Runs before the block's own interaction, so network blocks do not open their screens.
    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (player.isSecondaryUseActive()) {
            cycleMode(stack, player);
            return InteractionResult.SUCCESS;
        }
        if (!(context.getLevel() instanceof ServerLevel level) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        BlockPos pos = context.getClickedPos();
        switch (Mode.of(stack)) {
            case INFO -> {
                // A travelling item in front of the clicked cable takes precedence over the cable itself.
                double reach = player.getEyePosition().distanceTo(context.getClickLocation()) + 0.5;
                TransitPacket packet = Observation.packetInSight(level, player.getEyePosition(), player.getLookAngle(), reach);
                send(serverPlayer, packet != null ? Observation.describePacket(packet) : Observation.describeNode(level, pos));
            }
            case TRACEROUTE -> {
                if (Observation.canTraceFrom(level, pos)) {
                    PacketDistributor.sendToPlayer(serverPlayer, new OpenTraceroutePayload(pos));
                } else {
                    serverPlayer.sendOverlayMessage(Component.translatable("observe.loginetwork.trace.need_interface"));
                }
            }
            case CAPTURE -> {
                if (NetworkNodes.isNode(level.getBlockState(pos))) {
                    NetworkManager.get(level).capture().start(serverPlayer.getUUID(), pos, level.getGameTime(), PacketCapture.DURATION_TICKS);
                    serverPlayer.sendOverlayMessage(Component.translatable("observe.loginetwork.capture.started", PacketCapture.DURATION_TICKS / 20));
                } else {
                    serverPlayer.sendOverlayMessage(Component.translatable("observe.loginetwork.node.not_network"));
                }
            }
        }
        return InteractionResult.SUCCESS;
    }

    private static void cycleMode(ItemStack stack, Player player) {
        Mode next = Mode.values()[(Mode.of(stack).ordinal() + 1) % Mode.values().length];
        stack.set(ModDataComponents.TOOL_MODE.get(), next.ordinal());
        if (!player.level().isClientSide() && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendOverlayMessage(Component.translatable("item.loginetwork.observation_tool.mode_changed", next.label()).withStyle(ChatFormatting.AQUA));
        }
    }

    private static void send(ServerPlayer player, List<Component> lines) {
        lines.forEach(player::sendSystemMessage);
    }
}
