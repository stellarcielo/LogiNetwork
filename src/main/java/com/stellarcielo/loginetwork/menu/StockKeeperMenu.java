package com.stellarcielo.loginetwork.menu;

import com.stellarcielo.loginetwork.block.entity.NetworkInterfaceBlockEntity;
import com.stellarcielo.loginetwork.network.host.HostAgent;
import com.stellarcielo.loginetwork.registry.ModBlocks;
import com.stellarcielo.loginetwork.registry.ModMenus;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

// Nine ghost slots (item + target amount) for an interface's stock keeping. Ghost slots hold copies only:
// clicking with an item sets the filter (target = held count), clicking with an empty hand clears it.
public class StockKeeperMenu extends AbstractContainerMenu {
    public static final int FILTER_SLOTS = HostAgent.FILTER_SLOTS;
    // Button ids are slot * 4 + operation.
    public static final int OP_PLUS_ONE = 0;
    public static final int OP_MINUS_ONE = 1;
    public static final int OP_PLUS_STACK = 2;
    public static final int OP_MINUS_STACK = 3;
    private static final int[] OP_DELTAS = {1, -1, 16, -16};

    private final ContainerLevelAccess access;
    private final Container filters;
    private final ContainerData targets;
    private final BlockPos pos;
    private final HostAgent host;

    // Client side.
    public StockKeeperMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, buf.readBlockPos(), new SimpleContainer(FILTER_SLOTS), new SimpleContainerData(FILTER_SLOTS), ContainerLevelAccess.NULL, null);
    }

    // Server side.
    public StockKeeperMenu(int containerId, Inventory inventory, NetworkInterfaceBlockEntity blockEntity) {
        this(containerId, inventory, blockEntity.getBlockPos(), blockEntity.host().filters(), targetsOf(blockEntity.host()),
                ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos()), blockEntity.host());
    }

    private StockKeeperMenu(int containerId, Inventory inventory, BlockPos pos, Container filters, ContainerData targets,
            ContainerLevelAccess access, HostAgent host) {
        super(ModMenus.STOCK_KEEPER.get(), containerId);
        this.pos = pos;
        this.filters = filters;
        this.targets = targets;
        this.access = access;
        this.host = host;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                this.addSlot(new GhostSlot(filters, row * 3 + col, 62 + col * 18, 17 + row * 18));
            }
        }
        this.addStandardInventorySlots(inventory, 8, 84);
        this.addDataSlots(targets);
    }

    private static ContainerData targetsOf(HostAgent host) {
        return new ContainerData() {
            @Override
            public int get(int slot) {
                return host.target(slot);
            }

            @Override
            public void set(int slot, int value) {
                host.setTarget(slot, value);
            }

            @Override
            public int getCount() {
                return FILTER_SLOTS;
            }
        };
    }

    public BlockPos pos() {
        return pos;
    }

    public int target(int slot) {
        return targets.get(slot);
    }

    @Override
    public void clicked(int slotIndex, int buttonNum, ContainerInput input, Player player) {
        if (slotIndex < 0 || slotIndex >= FILTER_SLOTS) {
            super.clicked(slotIndex, buttonNum, input, player);
            return;
        }
        if (input != ContainerInput.PICKUP && input != ContainerInput.QUICK_MOVE) {
            return;
        }
        ItemStack carried = getCarried();
        if (host != null) {
            host.setFilter(slotIndex, input == ContainerInput.QUICK_MOVE ? ItemStack.EMPTY : carried, carried.getCount());
        } else {
            filters.setItem(slotIndex, carried.isEmpty() || input == ContainerInput.QUICK_MOVE ? ItemStack.EMPTY : carried.copyWithCount(1));
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        int slot = buttonId / 4;
        int op = buttonId % 4;
        if (slot < 0 || slot >= FILTER_SLOTS || filters.getItem(slot).isEmpty()) {
            return false;
        }
        targets.set(slot, Math.max(1, Math.min(HostAgent.MAX_TARGET, targets.get(slot) + OP_DELTAS[op])));
        return true;
    }

    // Shift-clicking an item in the player inventory copies it into the first free filter slot.
    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        if (slotIndex >= FILTER_SLOTS && host != null) {
            ItemStack stack = this.slots.get(slotIndex).getItem();
            for (int slot = 0; slot < FILTER_SLOTS; slot++) {
                if (filters.getItem(slot).isEmpty()) {
                    host.setFilter(slot, stack, stack.getCount());
                    break;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.NETWORK_INTERFACE.get());
    }

    private static final class GhostSlot extends Slot {
        GhostSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }
    }
}
