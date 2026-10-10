package com.stellarcielo.loginetwork.client.screen;

import com.stellarcielo.loginetwork.network.payload.OpenSwitchScreenPayload;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

// Read-only view of a switch's learned MAC address table.
public class AccessSwitchScreen extends Screen {
    private static final int PANEL_WIDTH = 240;
    private static final int MAX_VISIBLE_ENTRIES = 12;
    private static final int LABEL_COLOR = 0xFFA0A0A0;
    private static final int VALUE_COLOR = 0xFFFFFFFF;

    private final OpenSwitchScreenPayload data;

    public AccessSwitchScreen(OpenSwitchScreenPayload data) {
        super(Component.translatable("gui.loginetwork.switch.title"));
        this.data = data;
    }

    private int left() {
        return (this.width - PANEL_WIDTH) / 2;
    }

    private int top() {
        return Math.max(4, this.height / 2 - 110);
    }

    @Override
    protected void init() {
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(left(), top() + 30 + 12 * (MAX_VISIBLE_ENTRIES + 1), PANEL_WIDTH, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int left = left();
        int top = top();
        graphics.centeredText(this.font, this.title, this.width / 2, top, VALUE_COLOR);
        graphics.text(this.font, Component.translatable("gui.loginetwork.switch.mac"), left, top + 16, LABEL_COLOR);
        graphics.text(this.font, Component.translatable("gui.loginetwork.switch.port"), left + 120, top + 16, LABEL_COLOR);
        graphics.text(this.font, Component.translatable("gui.loginetwork.switch.age"), left + 180, top + 16, LABEL_COLOR);
        if (data.entries().isEmpty()) {
            graphics.text(this.font, Component.translatable("gui.loginetwork.none"), left, top + 30, VALUE_COLOR);
            return;
        }
        int shown = Math.min(MAX_VISIBLE_ENTRIES, data.entries().size());
        for (int i = 0; i < shown; i++) {
            OpenSwitchScreenPayload.MacEntry entry = data.entries().get(i);
            int y = top + 30 + 12 * i;
            graphics.text(this.font, entry.mac(), left, y, VALUE_COLOR);
            graphics.text(this.font, Component.translatable("gui.loginetwork.switch.port." + entry.port()), left + 120, y, VALUE_COLOR);
            graphics.text(this.font, Component.translatable("gui.loginetwork.switch.age_value", entry.ageSeconds()), left + 180, y, VALUE_COLOR);
        }
        if (data.entries().size() > shown) {
            graphics.text(this.font, Component.translatable("gui.loginetwork.dhcp.more", data.entries().size() - shown), left, top + 30 + 12 * shown, LABEL_COLOR);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
