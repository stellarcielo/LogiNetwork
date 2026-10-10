package com.stellarcielo.loginetwork.client.screen;

import com.stellarcielo.loginetwork.network.payload.ConfigureDhcpPayload;
import com.stellarcielo.loginetwork.network.payload.OpenDhcpScreenPayload;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

// Edits the address pool and lists current leases.
public class DhcpServerScreen extends Screen {
    private static final int PANEL_WIDTH = 240;
    private static final int MAX_VISIBLE_LEASES = 8;
    private static final int LABEL_COLOR = 0xFFA0A0A0;
    private static final int VALUE_COLOR = 0xFFFFFFFF;

    private final OpenDhcpScreenPayload data;
    private EditBox startEdit;
    private EditBox endEdit;
    private EditBox prefixEdit;

    public DhcpServerScreen(OpenDhcpScreenPayload data) {
        super(Component.translatable("gui.loginetwork.dhcp.title"));
        this.data = data;
    }

    private int left() {
        return (this.width - PANEL_WIDTH) / 2;
    }

    private int top() {
        return this.height / 2 - 100;
    }

    @Override
    protected void init() {
        int left = left();
        int y = top() + 26;
        int fieldWidth = 96;
        this.startEdit = addField(left, y, fieldWidth, data.rangeStart(), "gui.loginetwork.dhcp.range_start");
        this.endEdit = addField(left + fieldWidth + 8, y, fieldWidth, data.rangeEnd(), "gui.loginetwork.dhcp.range_end");
        this.prefixEdit = addField(left + 2 * (fieldWidth + 8), y, PANEL_WIDTH - 2 * (fieldWidth + 8), String.valueOf(data.prefixLength()), "gui.loginetwork.dhcp.prefix");
        this.prefixEdit.setMaxLength(2);

        int buttonY = top() + 60 + 12 * (MAX_VISIBLE_LEASES + 1) + 8;
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onDone())
                .bounds(left, buttonY, PANEL_WIDTH / 2 - 2, 20).build());
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(left + PANEL_WIDTH / 2 + 2, buttonY, PANEL_WIDTH / 2 - 2, 20).build());
    }

    private EditBox addField(int x, int y, int width, String value, String narrationKey) {
        EditBox box = new EditBox(this.font, x, y, width, 20, Component.translatable(narrationKey));
        box.setMaxLength(15);
        box.setValue(value);
        return this.addRenderableWidget(box);
    }

    private void onDone() {
        int prefixLength;
        try {
            prefixLength = Integer.parseInt(prefixEdit.getValue().trim());
        } catch (NumberFormatException e) {
            prefixLength = -1;
        }
        ClientPacketDistributor.sendToServer(new ConfigureDhcpPayload(data.pos(), startEdit.getValue().trim(), endEdit.getValue().trim(), prefixLength));
        onClose();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int left = left();
        int top = top();
        graphics.centeredText(this.font, this.title, this.width / 2, top, VALUE_COLOR);
        graphics.text(this.font, Component.translatable("gui.loginetwork.dhcp.range_start"), left, top + 14, LABEL_COLOR);
        graphics.text(this.font, Component.translatable("gui.loginetwork.dhcp.range_end"), left + 104, top + 14, LABEL_COLOR);
        graphics.text(this.font, Component.translatable("gui.loginetwork.dhcp.prefix"), left + 208, top + 14, LABEL_COLOR);

        int y = top + 60;
        graphics.text(this.font, Component.translatable("gui.loginetwork.dhcp.leases", data.leases().size()), left, y, LABEL_COLOR);
        if (data.leases().isEmpty()) {
            graphics.text(this.font, Component.translatable("gui.loginetwork.none"), left + 8, y + 12, VALUE_COLOR);
            return;
        }
        int shown = Math.min(MAX_VISIBLE_LEASES, data.leases().size());
        for (int i = 0; i < shown; i++) {
            OpenDhcpScreenPayload.LeaseView lease = data.leases().get(i);
            graphics.text(this.font, Component.literal(lease.address()), left + 8, y + 12 * (i + 1), VALUE_COLOR);
            graphics.text(this.font, Component.literal(lease.mac()), left + 110, y + 12 * (i + 1), LABEL_COLOR);
        }
        if (data.leases().size() > shown) {
            graphics.text(this.font, Component.translatable("gui.loginetwork.dhcp.more", data.leases().size() - shown), left + 8,
                    y + 12 * (shown + 1), LABEL_COLOR);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
