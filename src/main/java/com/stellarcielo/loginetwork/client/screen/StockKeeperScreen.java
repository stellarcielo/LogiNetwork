package com.stellarcielo.loginetwork.client.screen;

import java.util.List;

import com.stellarcielo.loginetwork.menu.StockKeeperMenu;
import com.stellarcielo.loginetwork.network.payload.SetFilterSourcePayload;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

// Ghost slots for stock keeping. Scroll over a slot to change its target (Shift: by 16).
// Right-click a filled slot to select it, then set its supplier host name in the field above the panel.
public class StockKeeperScreen extends AbstractContainerScreen<StockKeeperMenu> {
    // The dispenser background already has a 3x3 grid in the right place.
    private static final Identifier BACKGROUND = Identifier.withDefaultNamespace("textures/gui/container/dispenser.png");
    private static final int TARGET_COLOR = 0xFFFFE070;
    private static final int SELECTED_COLOR = 0x8040A0FF;

    private int selectedSlot = -1;
    private EditBox sourceEdit;
    private Button applyButton;

    public StockKeeperScreen(StockKeeperMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = (this.imageWidth - this.font.width(this.title)) / 2;
        int y = Math.max(2, this.topPos - 24);
        this.sourceEdit = new EditBox(this.font, this.leftPos, y, this.imageWidth - 54, 20, Component.translatable("gui.loginetwork.stock_keeper.source"));
        this.sourceEdit.setMaxLength(32);
        this.sourceEdit.setHint(Component.translatable("gui.loginetwork.stock_keeper.source_hint"));
        this.addRenderableWidget(this.sourceEdit);
        this.applyButton = this.addRenderableWidget(Button.builder(Component.translatable("gui.loginetwork.router.apply"), b -> applySource())
                .bounds(this.leftPos + this.imageWidth - 50, y, 50, 20).build());
        select(selectedSlot);
    }

    private void select(int slot) {
        selectedSlot = slot >= 0 && slot < StockKeeperMenu.FILTER_SLOTS && this.menu.slots.get(slot).hasItem() ? slot : -1;
        sourceEdit.setEditable(selectedSlot >= 0);
        applyButton.active = selectedSlot >= 0;
        sourceEdit.setValue(selectedSlot >= 0 ? this.menu.source(selectedSlot) : "");
    }

    private void applySource() {
        if (selectedSlot < 0) {
            return;
        }
        String source = sourceEdit.getValue().trim();
        this.menu.setSourceLocally(selectedSlot, source);
        ClientPacketDistributor.sendToServer(new SetFilterSourcePayload(this.menu.pos(), selectedSlot, source));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 1 && this.hoveredSlot != null && this.hoveredSlot.index < StockKeeperMenu.FILTER_SLOTS && this.menu.getCarried().isEmpty()) {
            select(this.hoveredSlot.index);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        // Typing a name must not close the screen with the inventory key.
        if (sourceEdit.isFocused() && sourceEdit.keyPressed(event)) {
            return true;
        }
        if (sourceEdit.isFocused() && !event.isEscape()) {
            return true;
        }
        return super.keyPressed(event);
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
            if (slot == selectedSlot) {
                graphics.fill(this.leftPos + ghost.x, this.topPos + ghost.y, this.leftPos + ghost.x + 16, this.topPos + ghost.y + 16, SELECTED_COLOR);
            }
        }
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (this.hoveredSlot != null && this.hoveredSlot.index < StockKeeperMenu.FILTER_SLOTS && this.menu.getCarried().isEmpty()) {
            List<Component> lines = this.hoveredSlot.hasItem()
                    ? List.of(this.hoveredSlot.getItem().getHoverName(),
                            Component.translatable("gui.loginetwork.stock_keeper.target", this.menu.target(this.hoveredSlot.index)),
                            Component.translatable("gui.loginetwork.stock_keeper.source_value", this.menu.source(this.hoveredSlot.index).isEmpty()
                                    ? Component.translatable("gui.loginetwork.stock_keeper.source_any") : Component.literal(this.menu.source(this.hoveredSlot.index))),
                            Component.translatable("gui.loginetwork.stock_keeper.hint_scroll"),
                            Component.translatable("gui.loginetwork.stock_keeper.hint_select"),
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
