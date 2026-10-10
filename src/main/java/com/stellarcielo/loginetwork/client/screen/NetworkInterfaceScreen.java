package com.stellarcielo.loginetwork.client.screen;

import com.stellarcielo.loginetwork.block.entity.InterfaceStatus;
import com.stellarcielo.loginetwork.network.payload.ConfigureInterfacePayload;
import com.stellarcielo.loginetwork.network.payload.OpenInterfaceScreenPayload;
import com.stellarcielo.loginetwork.network.payload.OpenStockKeeperPayload;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

// Shows an interface's identity and active requests, and edits addressing, extraction and supply.
public class NetworkInterfaceScreen extends Screen {
    private static final int PANEL_WIDTH = 240;
    private static final int MAX_VISIBLE_REQUESTS = 4;
    private static final int LABEL_COLOR = 0xFFA0A0A0;
    private static final int VALUE_COLOR = 0xFFFFFFFF;

    private final OpenInterfaceScreenPayload data;
    private boolean dhcp;
    private boolean extract;
    private boolean supply;
    private EditBox staticEdit;

    public NetworkInterfaceScreen(OpenInterfaceScreenPayload data) {
        super(Component.translatable("gui.loginetwork.interface.title"));
        this.data = data;
        this.dhcp = data.dhcp();
        this.extract = data.extract();
        this.supply = data.supply();
    }

    private int left() {
        return (this.width - PANEL_WIDTH) / 2;
    }

    private int top() {
        return Math.max(4, this.height / 2 - 120);
    }

    @Override
    protected void init() {
        int left = left();
        int half = PANEL_WIDTH / 2 - 2;
        int y = top() + 56;
        this.addRenderableWidget(Button.builder(modeLabel(), button -> {
            dhcp = !dhcp;
            button.setMessage(modeLabel());
            staticEdit.setEditable(!dhcp);
        }).bounds(left, y, PANEL_WIDTH, 20).build());

        this.staticEdit = new EditBox(this.font, left, y + 34, PANEL_WIDTH, 20, Component.translatable("gui.loginetwork.interface.static"));
        this.staticEdit.setMaxLength(18);
        this.staticEdit.setHint(Component.literal("10.0.0.10/24"));
        this.staticEdit.setValue(data.staticConfig());
        this.staticEdit.setEditable(!dhcp);
        this.addRenderableWidget(this.staticEdit);

        this.addRenderableWidget(Button.builder(extractLabel(), button -> {
            extract = !extract;
            button.setMessage(extractLabel());
        }).bounds(left, y + 60, half, 20).build());
        this.addRenderableWidget(Button.builder(supplyLabel(), button -> {
            supply = !supply;
            button.setMessage(supplyLabel());
        }).bounds(left + half + 4, y + 60, half, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.loginetwork.interface.stock_keeper"), button -> {
            apply();
            ClientPacketDistributor.sendToServer(new OpenStockKeeperPayload(data.pos()));
        }).bounds(left, y + 84, PANEL_WIDTH, 20).build());

        int buttonY = requestListTop() + 12 * (MAX_VISIBLE_REQUESTS + 1) + 6;
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> {
            apply();
            onClose();
        }).bounds(left, buttonY, half, 20).build());
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(left + half + 4, buttonY, half, 20).build());
    }

    private int requestListTop() {
        return top() + 56 + 112;
    }

    private Component modeLabel() {
        return Component.translatable("gui.loginetwork.interface.mode",
                Component.translatable(dhcp ? "gui.loginetwork.interface.mode.dhcp" : "gui.loginetwork.interface.mode.static"));
    }

    private Component extractLabel() {
        return Component.translatable("gui.loginetwork.interface.extract", CommonComponents.optionStatus(extract));
    }

    private Component supplyLabel() {
        return Component.translatable("gui.loginetwork.interface.supply", CommonComponents.optionStatus(supply));
    }

    private void apply() {
        ClientPacketDistributor.sendToServer(new ConfigureInterfacePayload(data.pos(), dhcp, staticEdit.getValue().trim(), extract, supply));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int left = left();
        int top = top();
        graphics.centeredText(this.font, this.title, this.width / 2, top, VALUE_COLOR);
        drawRow(graphics, left, top + 14, "gui.loginetwork.interface.mac", Component.literal(data.mac()));
        drawRow(graphics, left, top + 26, "gui.loginetwork.interface.address",
                data.assigned().isEmpty() ? Component.translatable("gui.loginetwork.none") : Component.literal(data.assigned()));
        InterfaceStatus status = InterfaceStatus.byOrdinal(data.status());
        Component statusText = Component.translatable(status.translationKey());
        if (status == InterfaceStatus.CONFLICT) {
            statusText = statusText.copy().withStyle(ChatFormatting.RED);
        }
        drawRow(graphics, left, top + 38, "gui.loginetwork.interface.status", statusText);
        graphics.text(this.font, Component.translatable("gui.loginetwork.interface.static"), left, top + 56 + 24, LABEL_COLOR);

        int y = requestListTop();
        graphics.text(this.font, Component.translatable("gui.loginetwork.interface.requests", data.requests().size()), left, y, LABEL_COLOR);
        if (data.requests().isEmpty()) {
            graphics.text(this.font, Component.translatable("gui.loginetwork.none"), left + 8, y + 12, VALUE_COLOR);
            return;
        }
        int shown = Math.min(MAX_VISIBLE_REQUESTS, data.requests().size());
        for (int i = 0; i < shown; i++) {
            OpenInterfaceScreenPayload.RequestView request = data.requests().get(i);
            graphics.text(this.font, Component.translatable("gui.loginetwork.interface.request_line", request.id(), request.item().getHoverName(),
                    request.received(), request.requested(), request.inTransit()), left + 8, y + 12 * (i + 1), VALUE_COLOR);
        }
    }

    private void drawRow(GuiGraphicsExtractor graphics, int left, int y, String labelKey, Component value) {
        graphics.text(this.font, Component.translatable(labelKey), left, y, LABEL_COLOR);
        graphics.text(this.font, value, left + 70, y, VALUE_COLOR);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
