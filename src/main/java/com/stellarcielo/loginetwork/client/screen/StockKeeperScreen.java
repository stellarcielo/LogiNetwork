package com.stellarcielo.loginetwork.client.screen;

import java.util.List;

import com.stellarcielo.loginetwork.menu.StockKeeperMenu;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

// Ghost slots for stock keeping. Scroll over a slot to change its target (Shift: by 16).
public class StockKeeperScreen extends AbstractContainerScreen<StockKeeperMenu> {
    // The dispenser background already has a 3x3 grid in the right place.
    private static final Identifier BACKGROUND = Identifier.withDefaultNamespace("textures/gui/container/dispenser.png");
    private static final int TARGET_COLOR = 0xFFFFE070;

    public StockKeeperScreen(StockKeeperMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = (this.imageWidth - this.font.width(this.title)) / 2;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, this.leftPos, this.topPos, 0.0F, 0.0F, this.imageWidth, this.imageHeight, 256, 256);
    }

    @Override
    public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractContents(graphics, mouseX, mouseY, partialTick);
        graphics.nextStratum();
        for (int slot = 0; slot < StockKeeperMenu.FILTER_SLOTS; slot++) {
            Slot ghost = this.menu.slots.get(slot);
            if (!ghost.hasItem()) {
                continue;
            }
            String text = String.valueOf(this.menu.target(slot));
            int x = this.leftPos + ghost.x + 17 - this.font.width(text);
            int y = this.topPos + ghost.y + 9;
            graphics.text(this.font, text, x, y, TARGET_COLOR, true);
        }
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (this.hoveredSlot != null && this.hoveredSlot.index < StockKeeperMenu.FILTER_SLOTS && this.menu.getCarried().isEmpty()) {
            List<Component> lines = this.hoveredSlot.hasItem()
                    ? List.of(this.hoveredSlot.getItem().getHoverName(),
                            Component.translatable("gui.loginetwork.stock_keeper.target", this.menu.target(this.hoveredSlot.index)),
                            Component.translatable("gui.loginetwork.stock_keeper.hint_scroll"),
                            Component.translatable("gui.loginetwork.stock_keeper.hint_clear"))
                    : List.of(Component.translatable("gui.loginetwork.stock_keeper.hint_set"));
            graphics.setComponentTooltipForNextFrame(this.font, lines, mouseX, mouseY);
            return;
        }
        super.extractTooltip(graphics, mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (this.hoveredSlot != null && this.hoveredSlot.index < StockKeeperMenu.FILTER_SLOTS && this.hoveredSlot.hasItem() && scrollY != 0) {
            boolean shift = this.minecraft.hasShiftDown();
            int op = scrollY > 0
                    ? (shift ? StockKeeperMenu.OP_PLUS_STACK : StockKeeperMenu.OP_PLUS_ONE)
                    : (shift ? StockKeeperMenu.OP_MINUS_STACK : StockKeeperMenu.OP_MINUS_ONE);
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, this.hoveredSlot.index * 4 + op);
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }
}
