package com.stellarcielo.loginetwork.network.payload;

import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.stellarcielo.loginetwork.address.Ipv4Address;
import com.stellarcielo.loginetwork.address.Ipv4Interface;
import com.stellarcielo.loginetwork.block.NetworkInterfaceBlock;
import com.stellarcielo.loginetwork.block.entity.AccessSwitchBlockEntity;
import com.stellarcielo.loginetwork.block.entity.DhcpServerBlockEntity;
import com.stellarcielo.loginetwork.block.entity.NetworkInterfaceBlockEntity;
import com.stellarcielo.loginetwork.block.entity.RouterBlockEntity;
import com.stellarcielo.loginetwork.menu.StockKeeperMenu;
import com.stellarcielo.loginetwork.network.SwitchPort;
import com.stellarcielo.loginetwork.network.observe.Observation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
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
        blockEntity.host().setSupply(payload.supply());
        blockEntity.setPriority(payload.priority());

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

    public static void openStockKeeper(OpenStockKeeperPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player
                && reachableBlockEntity(player, payload.pos()) instanceof NetworkInterfaceBlockEntity blockEntity) {
            player.openMenu(new SimpleMenuProvider((containerId, inventory, p) -> new StockKeeperMenu(containerId, inventory, blockEntity),
                    Component.translatable("gui.loginetwork.stock_keeper.title")), payload.pos());
        }
    }

    public static void configureRouter(ConfigureRouterPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)
                || !(reachableBlockEntity(player, payload.pos()) instanceof RouterBlockEntity router)) {
            return;
        }
        switch (payload.action()) {
            case ConfigureRouterPayload.SET_PORT -> {
                if (payload.index() < 0 || payload.index() >= Direction.values().length) {
                    return;
                }
                Ipv4Interface address = null;
                if (!payload.text().isBlank()) {
                    Optional<Ipv4Interface> parsed = Ipv4Interface.parse(payload.text());
                    if (parsed.isEmpty()) {
                        player.sendOverlayMessage(Component.translatable("message.loginetwork.invalid_address", payload.text()));
                        return;
                    }
                    address = parsed.get();
                }
                router.setPortAddress(Direction.from3DDataValue(payload.index()), address);
            }
            case ConfigureRouterPayload.ADD_STATIC -> {
                Optional<RouterBlockEntity.StaticRoute> route = RouterBlockEntity.StaticRoute.parse(payload.text());
                if (route.isEmpty()) {
                    player.sendOverlayMessage(Component.translatable("message.loginetwork.invalid_static_route", payload.text()));
                    return;
                }
                if (!router.addStaticRoute(route.get())) {
                    player.sendOverlayMessage(Component.translatable("message.loginetwork.too_many_static_routes", RouterBlockEntity.MAX_STATIC_ROUTES));
                    return;
                }
            }
            case ConfigureRouterPayload.REMOVE_STATIC -> router.removeStaticRoute(payload.index());
            default -> {
                return;
            }
        }
        router.openScreen(player);
    }

    public static void traceroute(TraceroutePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || reachableBlockEntity(player, payload.pos()) == null
                || !Observation.canTraceFrom(player.level(), payload.pos())) {
            return;
        }
        Optional<Ipv4Address> destination = Ipv4Address.parse(payload.destination());
        if (destination.isEmpty()) {
            player.sendOverlayMessage(Component.translatable("message.loginetwork.invalid_address", payload.destination()));
            return;
        }
        Observation.Trace trace = Observation.trace(player.level(), payload.pos(), destination.get());
        Observation.describeTrace(destination.get(), trace).forEach(player::sendSystemMessage);
        if (trace.path() != null) {
            PacketDistributor.sendToPlayer(player, new HighlightPathPayload(trace.path().stream().map(Vec3::atCenterOf).toList()));
        }
    }

    public static void configureSwitchPort(ConfigureSwitchPortPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)
                || !(reachableBlockEntity(player, payload.pos()) instanceof AccessSwitchBlockEntity accessSwitch)
                || payload.port() < 0 || payload.port() >= Direction.values().length) {
            return;
        }
        Optional<Set<Integer>> allowed = SwitchPort.parseAllowed(payload.allowed());
        if (!SwitchPort.isValidVlan(payload.accessVlan()) || allowed == null) {
            player.sendOverlayMessage(Component.translatable("message.loginetwork.invalid_vlan"));
            return;
        }
        accessSwitch.setPort(Direction.from3DDataValue(payload.port()), new SwitchPort(payload.trunk(), payload.accessVlan(), allowed.orElse(null)));
        accessSwitch.openScreen(player);
    }

    private static @Nullable BlockEntity reachableBlockEntity(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.level();
        if (!level.isLoaded(pos) || !player.isWithinBlockInteractionRange(pos, 1.0)) {
            return null;
        }
        return level.getBlockEntity(pos);
    }
}
