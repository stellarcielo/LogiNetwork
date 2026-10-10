package com.stellarcielo.loginetwork.registry;

import com.stellarcielo.loginetwork.LogiNetwork;
import com.stellarcielo.loginetwork.block.entity.AccessSwitchBlockEntity;
import com.stellarcielo.loginetwork.block.entity.DhcpServerBlockEntity;
import com.stellarcielo.loginetwork.block.entity.NetworkInterfaceBlockEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, LogiNetwork.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<NetworkInterfaceBlockEntity>> NETWORK_INTERFACE =
            BLOCK_ENTITY_TYPES.register("network_interface", () -> new BlockEntityType<>(NetworkInterfaceBlockEntity::new, ModBlocks.NETWORK_INTERFACE.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DhcpServerBlockEntity>> DHCP_SERVER =
            BLOCK_ENTITY_TYPES.register("dhcp_server", () -> new BlockEntityType<>(DhcpServerBlockEntity::new, ModBlocks.DHCP_SERVER.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AccessSwitchBlockEntity>> ACCESS_SWITCH =
            BLOCK_ENTITY_TYPES.register("access_switch", () -> new BlockEntityType<>(AccessSwitchBlockEntity::new, ModBlocks.ACCESS_SWITCH.get()));

    private ModBlockEntities() {}

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITY_TYPES.register(modEventBus);
    }
}
