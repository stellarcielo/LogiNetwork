package com.stellarcielo.loginetwork.network.payload;

import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.stellarcielo.loginetwork.address.Ipv4Address;
import com.stellarcielo.loginetwork.address.Ipv4Interface;
import com.stellarcielo.loginetwork.block.NetworkInterfaceBlock;
import com.stellarcielo.loginetwork.block.entity.AccessSwitchBlockEntity;
import com.stellarcielo.loginetwork.block.entity.DhcpServerBlockEntity;
import com.stellarcielo.loginetwork.block.entity.DnsServerBlockEntity;
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
        Ipv4Address staticDns = null;
        if (!payload.staticDns().isBlank()) {
            Optional<Ipv4Address> parsed = Ipv4Address.parse(payload.staticDns());
            if (parsed.isEmpty()) {
                player.sendOverlayMessage(Component.translatable("message.loginetwork.invalid_address", payload.staticDns()));
                return;
            }
            staticDns = parsed.get();
        }
        String hostname = payload.hostname().trim();
        String extractTarget = payload.extractTarget().trim();
        if ((!hostname.isEmpty() && DnsServerBlockEntity.normalize(hostname).isEmpty())
                || (!extractTarget.isEmpty() && DnsServerBlockEntity.normalize(extractTarget).isEmpty())) {
            player.sendOverlayMessage(Component.translatable("message.loginetwork.invalid_hostname"));
            return;
        }
        blockEntity.configureNames(hostname, staticDns, extractTarget);

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
        Ipv4Address dns = null;
        if (!payload.dnsServer().isBlank()) {
            Optional<Ipv4Address> parsed = Ipv4Address.parse(payload.dnsServer());
            if (parsed.isEmpty()) {
                player.sendOverlayMessage(Component.translatable("message.loginetwork.invalid_address", payload.dnsServer()));
                return;
            }
            dns = parsed.get();
        }
        blockEntity.configure(start.get(), end.get(), payload.prefixLength());
        blockEntity.setDnsServer(dns);
    }

    public static void openStockKeeper(OpenStockKeeperPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player
                && reachableBlockEntity(player, payload.pos()) instanceof NetworkInterfaceBlockEntity blockEntity) {
            player.openMenu(new SimpleMenuProvider((containerId, inventory, p) -> new StockKeeperMenu(containerId, inventory, blockEntity),
                    Component.translatable("gui.loginetwork.stock_keeper.title")), buf -> StockKeeperMenu.writeOpenData(buf, blockEntity));
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
        if (destination.isPresent()) {
            runTrace(player, payload.pos(), destination.get());
            return;
        }
        // Not an address: look it up as a host name through the interface's DNS server.
        if (DnsServerBlockEntity.normalize(payload.destination()).isEmpty()
                || !(player.level().getBlockEntity(payload.pos()) instanceof NetworkInterfaceBlockEntity host)) {
            player.sendOverlayMessage(Component.translatable("message.loginetwork.invalid_address", payload.destination()));
            return;
        }
        host.resolver().resolve(player.level(), payload.destination(), address -> {
            if (address == null) {
                player.sendSystemMessage(Component.translatable("observe.loginetwork.trace.unresolved", payload.destination()));
            } else {
                runTrace(player, payload.pos(), address);
            }
        });
    }

    private static void runTrace(ServerPlayer player, BlockPos from, Ipv4Address destination) {
        Observation.Trace trace = Observation.trace(player.level(), from, destination);
        Observation.describeTrace(destination, trace).forEach(player::sendSystemMessage);
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

    public static void setFilterSource(SetFilterSourcePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)
                || !(reachableBlockEntity(player, payload.pos()) instanceof NetworkInterfaceBlockEntity blockEntity)
                || payload.slot() < 0 || payload.slot() >= StockKeeperMenu.FILTER_SLOTS) {
            return;
        }
        String source = payload.source().trim();
        if (!source.isEmpty() && DnsServerBlockEntity.normalize(source).isEmpty()) {
            player.sendOverlayMessage(Component.translatable("message.loginetwork.invalid_hostname"));
            return;
        }
        blockEntity.host().setSource(payload.slot(), source);
    }

    public static void configureDns(ConfigureDnsPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)
                || !(reachableBlockEntity(player, payload.pos()) instanceof DnsServerBlockEntity server)) {
            return;
        }
        if (payload.add()) {
            String[] parts = payload.text().trim().split("\\s+");
            Optional<Ipv4Address> address = parts.length == 2 ? Ipv4Address.parse(parts[1]) : Optional.empty();
            if (address.isEmpty() || !server.addManual(parts[0], address.get())) {
                player.sendOverlayMessage(Component.translatable("message.loginetwork.invalid_dns_record", payload.text()));
                return;
            }
        } else {
            server.remove(payload.index());
        }
        server.openScreen(player);
    }

    private static @Nullable BlockEntity reachableBlockEntity(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.level();
        if (!level.isLoaded(pos) || !player.isWithinBlockInteractionRange(pos, 1.0)) {
            return null;
        }
        return level.getBlockEntity(pos);
    }
}
