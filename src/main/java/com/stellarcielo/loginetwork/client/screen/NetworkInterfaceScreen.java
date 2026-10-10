package com.stellarcielo.loginetwork.client.screen;

import com.stellarcielo.loginetwork.block.entity.InterfaceStatus;
import com.stellarcielo.loginetwork.network.payload.ConfigureInterfacePayload;
import com.stellarcielo.loginetwork.network.payload.OpenInterfaceScreenPayload;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

// Shows an interface's identity and lets the player pick DHCP or a static address, and toggle extraction.
public class NetworkInterfaceScreen extends Screen {
    private static final int PANEL_WIDTH = 220;
    private static final int LABEL_COLOR = 0xFFA0A0A0;
    private static final int VALUE_COLOR = 0xFFFFFFFF;

    private final OpenInterfaceScreenPayload data;
    private boolean dhcp;
    private boolean extract;
    private EditBox staticEdit;
    private Button modeButton;
    private Button extractButton;

    public NetworkInterfaceScreen(OpenInterfaceScreenPayload data) {
        super(Component.translatable("gui.loginetwork.interface.title"));
        this.data = data;
        this.dhcp = data.dhcp();
        this.extract = data.extract();
    }

    private int left() {
        return (this.width - PANEL_WIDTH) / 2;
    }

    private int top() {
        return this.height / 2 - 80;
    }

    @Override
    protected void init() {
        int left = left();
        int y = top() + 60;
        this.modeButton = this.addRenderableWidget(Button.builder(modeLabel(), button -> {
            dhcp = !dhcp;
            button.setMessage(modeLabel());
            staticEdit.setEditable(!dhcp);
        }).bounds(left, y, PANEL_WIDTH, 20).build());

        this.staticEdit = new EditBox(this.font, left, y + 36, PANEL_WIDTH, 20, Component.translatable("gui.loginetwork.interface.static"));
        this.staticEdit.setMaxLength(18);
        this.staticEdit.setHint(Component.literal("10.0.0.10/24"));
        this.staticEdit.setValue(data.staticConfig());
        this.staticEdit.setEditable(!dhcp);
        this.addRenderableWidget(this.staticEdit);

        this.extractButton = this.addRenderableWidget(Button.builder(extractLabel(), button -> {
            extract = !extract;
            button.setMessage(extractLabel());
        }).bounds(left, y + 62, PANEL_WIDTH, 20).build());

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onDone())
                .bounds(left, y + 92, PANEL_WIDTH / 2 - 2, 20).build());
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(left + PANEL_WIDTH / 2 + 2, y + 92, PANEL_WIDTH / 2 - 2, 20).build());
    }

    private Component modeLabel() {
        return Component.translatable("gui.loginetwork.interface.mode",
                Component.translatable(dhcp ? "gui.loginetwork.interface.mode.dhcp" : "gui.loginetwork.interface.mode.static"));
    }

    private Component extractLabel() {
        return Component.translatable("gui.loginetwork.interface.extract", CommonComponents.optionStatus(extract));
    }

    private void onDone() {
        ClientPacketDistributor.sendToServer(new ConfigureInterfacePayload(data.pos(), dhcp, staticEdit.getValue().trim(), extract));
        onClose();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int left = left();
        int top = top();
        graphics.centeredText(this.font, this.title, this.width / 2, top, VALUE_COLOR);
        drawRow(graphics, left, top + 16, "gui.loginetwork.interface.mac", Component.literal(data.mac()));
        drawRow(graphics, left, top + 28, "gui.loginetwork.interface.address",
                data.assigned().isEmpty() ? Component.translatable("gui.loginetwork.none") : Component.literal(data.assigned()));
        InterfaceStatus status = InterfaceStatus.byOrdinal(data.status());
        Component statusText = Component.translatable(status.translationKey());
        if (status == InterfaceStatus.CONFLICT) {
            statusText = statusText.copy().withStyle(ChatFormatting.RED);
        }
        drawRow(graphics, left, top + 40, "gui.loginetwork.interface.status", statusText);
        graphics.text(this.font, Component.translatable("gui.loginetwork.interface.static"), left, top + 86, LABEL_COLOR);
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
