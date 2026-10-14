package com.stellarcielo.loginetwork.client.screen;

import com.stellarcielo.loginetwork.network.payload.ConfigureDnsPayload;
import com.stellarcielo.loginetwork.network.payload.OpenDnsScreenPayload;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

// DNS records: dynamic ones registered by hosts and manual ones added here ("name a.b.c.d").
public class DnsServerScreen extends Screen {
    private static final int PANEL_WIDTH = 260;
    private static final int MAX_ROWS = 10;
    private static final int ROW_HEIGHT = 12;
    private static final int LABEL_COLOR = 0xFFA0A0A0;
    private static final int VALUE_COLOR = 0xFFFFFFFF;

    private final OpenDnsScreenPayload data;
    private EditBox input;

    public DnsServerScreen(OpenDnsScreenPayload data) {
        super(Component.translatable("gui.loginetwork.dns.title"));
        this.data = data;
    }

    private int left() {
        return (this.width - PANEL_WIDTH) / 2;
    }

    private int top() {
        return Math.max(4, this.height / 2 - 100);
    }

    @Override
    protected void init() {
        int left = left();
        int listTop = top() + 16;
        for (int i = 0; i < Math.min(MAX_ROWS, data.records().size()); i++) {
            int index = i;
            this.addRenderableWidget(Button.builder(Component.literal("x"), b -> send(false, "", index))
                    .bounds(left + PANEL_WIDTH - 12, listTop + ROW_HEIGHT * (i + 1) - 2, 12, 11).build());
        }
        int formY = listTop + ROW_HEIGHT * (MAX_ROWS + 1) + 4;
        this.input = new EditBox(this.font, left, formY, PANEL_WIDTH - 64, 20, Component.translatable("gui.loginetwork.dns.record"));
        this.input.setMaxLength(50);
        this.input.setHint(Component.literal("warehouse 10.1.0.5"));
        this.addRenderableWidget(this.input);
        this.addRenderableWidget(Button.builder(Component.translatable("gui.loginetwork.router.add"), b -> send(true, input.getValue().trim(), 0))
                .bounds(left + PANEL_WIDTH - 60, formY, 60, 20).build());
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).bounds(left, formY + 26, PANEL_WIDTH, 20).build());
    }

    private void send(boolean add, String text, int index) {
        ClientPacketDistributor.sendToServer(new ConfigureDnsPayload(data.pos(), add, text, index));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int left = left();
        int top = top();
        graphics.centeredText(this.font, this.title, this.width / 2, top, VALUE_COLOR);
        int y = top + 16;
        graphics.text(this.font, Component.translatable("gui.loginetwork.dns.col.name"), left, y, LABEL_COLOR);
        graphics.text(this.font, Component.translatable("gui.loginetwork.dns.col.address"), left + 120, y, LABEL_COLOR);
        graphics.text(this.font, Component.translatable("gui.loginetwork.dns.col.kind"), left + 200, y, LABEL_COLOR);
        if (data.records().isEmpty()) {
            graphics.text(this.font, Component.translatable("gui.loginetwork.none"), left + 8, y + ROW_HEIGHT, VALUE_COLOR);
        }
        int shown = Math.min(MAX_ROWS, data.records().size());
        for (int i = 0; i < shown; i++) {
            OpenDnsScreenPayload.RecordView record = data.records().get(i);
            int rowY = y + ROW_HEIGHT * (i + 1);
            graphics.text(this.font, record.name(), left, rowY, VALUE_COLOR);
            graphics.text(this.font, record.address(), left + 120, rowY, VALUE_COLOR);
            graphics.text(this.font, Component.translatable(record.dynamic() ? "gui.loginetwork.dns.dynamic" : "gui.loginetwork.dns.manual"),
                    left + 200, rowY, LABEL_COLOR);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
