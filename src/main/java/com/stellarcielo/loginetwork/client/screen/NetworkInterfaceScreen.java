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

// Shows an interface's identity and active requests. Left column: addressing (DHCP/static, DNS server).
// Right column: host settings (host name, extraction target, extract/supply/QoS).
public class NetworkInterfaceScreen extends Screen {
    private static final int PANEL_WIDTH = 320;
    private static final int COLUMN_WIDTH = 150;
    private static final int MAX_VISIBLE_REQUESTS = 3;
    private static final int LABEL_COLOR = 0xFFA0A0A0;
    private static final int VALUE_COLOR = 0xFFFFFFFF;

    private final OpenInterfaceScreenPayload data;
    private boolean dhcp;
    private boolean extract;
    private boolean supply;
    private int priority;
    private EditBox staticEdit;
    private EditBox dnsEdit;
    private EditBox hostnameEdit;
    private EditBox targetEdit;

    public NetworkInterfaceScreen(OpenInterfaceScreenPayload data) {
        super(Component.translatable("gui.loginetwork.interface.title"));
        this.data = data;
        this.dhcp = data.dhcp();
        this.extract = data.extract();
        this.supply = data.supply();
        this.priority = data.priority();
    }

    private int left() {
        return (this.width - PANEL_WIDTH) / 2;
    }

    private int right() {
        return left() + PANEL_WIDTH - COLUMN_WIDTH;
    }

    private int top() {
        return Math.max(4, this.height / 2 - 128);
    }

    // First row of the two columns.
    private int columnsTop() {
        return top() + 66;
    }

    private int requestListTop() {
        return columnsTop() + 122;
    }

    @Override
    protected void init() {
        int left = left();
        int right = right();
        int y = columnsTop();

        this.addRenderableWidget(Button.builder(modeLabel(), button -> {
            dhcp = !dhcp;
            button.setMessage(modeLabel());
            staticEdit.setEditable(!dhcp);
            dnsEdit.setEditable(!dhcp);
        }).bounds(left, y, COLUMN_WIDTH, 20).build());
        this.staticEdit = addField(left, y + 34, 18, data.staticConfig(), "10.0.0.10/24", "gui.loginetwork.interface.static");
        this.staticEdit.setEditable(!dhcp);
        this.dnsEdit = addField(left, y + 68, 15, data.names().staticDns(), "10.0.0.9", "gui.loginetwork.interface.dns");
        this.dnsEdit.setEditable(!dhcp);

        this.hostnameEdit = addField(right, y + 10, 32, data.names().hostname(), "warehouse", "gui.loginetwork.interface.hostname");
        this.targetEdit = addField(right, y + 44, 32, data.names().extractTarget(), "", "gui.loginetwork.interface.extract_target");
        int third = (COLUMN_WIDTH - 4) / 3;
        this.addRenderableWidget(Button.builder(extractLabel(), button -> {
            extract = !extract;
            button.setMessage(extractLabel());
        }).bounds(right, y + 68, third, 20).build());
        this.addRenderableWidget(Button.builder(supplyLabel(), button -> {
            supply = !supply;
            button.setMessage(supplyLabel());
        }).bounds(right + third + 2, y + 68, third, 20).build());
        this.addRenderableWidget(Button.builder(priorityLabel(), button -> {
            priority = (priority + 1) % 3;
            button.setMessage(priorityLabel());
        }).bounds(right + 2 * (third + 2), y + 68, third, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.loginetwork.interface.stock_keeper"), button -> {
            apply();
            ClientPacketDistributor.sendToServer(new OpenStockKeeperPayload(data.pos()));
        }).bounds(left, y + 96, PANEL_WIDTH, 20).build());

        int buttonY = requestListTop() + 12 * (MAX_VISIBLE_REQUESTS + 1) + 6;
        int half = PANEL_WIDTH / 2 - 2;
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> {
            apply();
            onClose();
        }).bounds(left, buttonY, half, 20).build());
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(left + half + 4, buttonY, half, 20).build());
    }

    private EditBox addField(int x, int y, int maxLength, String value, String hint, String narrationKey) {
        EditBox box = new EditBox(this.font, x, y, COLUMN_WIDTH, 20, Component.translatable(narrationKey));
        box.setMaxLength(maxLength);
        box.setHint(Component.literal(hint));
        box.setValue(value);
        return this.addRenderableWidget(box);
    }

    private Component modeLabel() {
        return Component.translatable("gui.loginetwork.interface.mode",
                Component.translatable(dhcp ? "gui.loginetwork.interface.mode.dhcp" : "gui.loginetwork.interface.mode.static"));
    }

    private Component extractLabel() {
        return Component.translatable("gui.loginetwork.interface.extract_short", CommonComponents.optionStatus(extract));
    }

    private Component supplyLabel() {
        return Component.translatable("gui.loginetwork.interface.supply_short", CommonComponents.optionStatus(supply));
    }

    private Component priorityLabel() {
        return Component.translatable("gui.loginetwork.interface.qos", Component.translatable("gui.loginetwork.qos." + priority));
    }

    private void apply() {
        ClientPacketDistributor.sendToServer(new ConfigureInterfacePayload(data.pos(), dhcp, staticEdit.getValue().trim(), extract, supply, priority,
                hostnameEdit.getValue().trim(), dnsEdit.getValue().trim(), targetEdit.getValue().trim()));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int left = left();
        int right = right();
        int top = top();
        int y = columnsTop();
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
        drawRow(graphics, left, top + 50, "gui.loginetwork.interface.dns_in_use",
                data.names().dnsServer().isEmpty() ? Component.translatable("gui.loginetwork.none") : Component.literal(data.names().dnsServer()));

        graphics.text(this.font, Component.translatable("gui.loginetwork.interface.static"), left, y + 24, LABEL_COLOR);
        graphics.text(this.font, Component.translatable("gui.loginetwork.interface.dns"), left, y + 58, LABEL_COLOR);
        graphics.text(this.font, Component.translatable("gui.loginetwork.interface.hostname"), right, y, LABEL_COLOR);
        graphics.text(this.font, Component.translatable("gui.loginetwork.interface.extract_target"), right, y + 34, LABEL_COLOR);

        int listY = requestListTop();
        graphics.text(this.font, Component.translatable("gui.loginetwork.interface.requests", data.requests().size()), left, listY, LABEL_COLOR);
        if (data.requests().isEmpty()) {
            graphics.text(this.font, Component.translatable("gui.loginetwork.none"), left + 8, listY + 12, VALUE_COLOR);
            return;
        }
        int shown = Math.min(MAX_VISIBLE_REQUESTS, data.requests().size());
        for (int i = 0; i < shown; i++) {
            OpenInterfaceScreenPayload.RequestView request = data.requests().get(i);
            graphics.text(this.font, Component.translatable("gui.loginetwork.interface.request_line", request.id(), request.item().getHoverName(),
                    request.received(), request.requested(), request.inTransit()), left + 8, listY + 12 * (i + 1), VALUE_COLOR);
        }
    }

    private void drawRow(GuiGraphicsExtractor graphics, int left, int y, String labelKey, Component value) {
        graphics.text(this.font, Component.translatable(labelKey), left, y, LABEL_COLOR);
        graphics.text(this.font, value, left + 80, y, VALUE_COLOR);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
