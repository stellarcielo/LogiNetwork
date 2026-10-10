package com.stellarcielo.loginetwork.network.payload;

import java.util.Optional;

import com.stellarcielo.loginetwork.address.Ipv4Address;
import com.stellarcielo.loginetwork.address.Ipv4Interface;
import com.stellarcielo.loginetwork.block.NetworkInterfaceBlock;
import com.stellarcielo.loginetwork.block.entity.DhcpServerBlockEntity;
import com.stellarcielo.loginetwork.block.entity.NetworkInterfaceBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// Validates and applies settings sent from the configuration screens.
public final class ServerPayloadHandlers {
    private static final int MAX_POOL_SIZE = 65536;

    private ServerPayloadHandlers() {}

    public static void configureInterface(ConfigureInterfacePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)
                || !(reachableBlockEntity(player, payload.pos()) instanceof NetworkInterfaceBlockEntity blockEntity)) {
            return;
        }
        Ipv4Interface staticConfig = null;
        if (!payload.staticConfig().isBlank()) {
            Optional<Ipv4Interface> parsed = Ipv4Interface.parse(payload.staticConfig());
            if (parsed.isEmpty()) {
                player.sendOverlayMessage(Component.translatable("message.loginetwork.invalid_address", payload.staticConfig()));
                return;
            }
            staticConfig = parsed.get();
        }
        blockEntity.configure(payload.dhcp(), staticConfig);

        ServerLevel level = player.level();
        BlockState state = level.getBlockState(payload.pos());
        if (state.getValue(NetworkInterfaceBlock.EXTRACT) != payload.extract()) {
            level.setBlock(payload.pos(), state.setValue(NetworkInterfaceBlock.EXTRACT, payload.extract()), Block.UPDATE_ALL);
        }
    }

    public static void configureDhcp(ConfigureDhcpPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)
                || !(reachableBlockEntity(player, payload.pos()) instanceof DhcpServerBlockEntity blockEntity)) {
            return;
        }
        Optional<Ipv4Address> start = Ipv4Address.parse(payload.rangeStart());
        Optional<Ipv4Address> end = Ipv4Address.parse(payload.rangeEnd());
        if (start.isEmpty() || end.isEmpty() || !Ipv4Interface.isValidPrefixLength(payload.prefixLength())) {
            player.sendOverlayMessage(Component.translatable("message.loginetwork.invalid_pool"));
            return;
        }
        long size = Integer.toUnsignedLong(end.get().bits()) - Integer.toUnsignedLong(start.get().bits()) + 1;
        if (size <= 0 || size > MAX_POOL_SIZE || !end.get().inSubnet(start.get(), payload.prefixLength())) {
            player.sendOverlayMessage(Component.translatable("message.loginetwork.invalid_pool"));
            return;
        }
        blockEntity.configure(start.get(), end.get(), payload.prefixLength());
    }

    private static BlockEntity reachableBlockEntity(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.level();
        if (!level.isLoaded(pos) || !player.isWithinBlockInteractionRange(pos, 1.0)) {
            return null;
        }
        return level.getBlockEntity(pos);
    }
}
