package com.stellarcielo.loginetwork.client.screen;

import com.stellarcielo.loginetwork.network.payload.TraceroutePayload;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

// Asks for the destination address of a traceroute; the result arrives in chat.
public class TracerouteScreen extends Screen {
    private static final int PANEL_WIDTH = 200;
    private static String lastDestination = "";

    private final BlockPos pos;
    private EditBox destination;

    public TracerouteScreen(BlockPos pos) {
        super(Component.translatable("gui.loginetwork.traceroute.title"));
        this.pos = pos;
    }

    @Override
    protected void init() {
        int left = (this.width - PANEL_WIDTH) / 2;
        int top = this.height / 2 - 30;
        this.destination = new EditBox(this.font, left, top + 16, PANEL_WIDTH, 20, Component.translatable("gui.loginetwork.traceroute.destination"));
        this.destination.setMaxLength(15);
        this.destination.setHint(Component.literal("10.1.0.2"));
        this.destination.setValue(lastDestination);
        this.addRenderableWidget(this.destination);
        this.setInitialFocus(this.destination);
        this.addRenderableWidget(Button.builder(Component.translatable("gui.loginetwork.traceroute.run"), button -> run())
                .bounds(left, top + 42, PANEL_WIDTH / 2 - 2, 20).build());
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(left + PANEL_WIDTH / 2 + 2, top + 42, PANEL_WIDTH / 2 - 2, 20).build());
    }

    private void run() {
        lastDestination = destination.getValue().trim();
        ClientPacketDistributor.sendToServer(new TraceroutePayload(pos, lastDestination));
        onClose();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(this.font, this.title, this.width / 2, this.height / 2 - 30, 0xFFFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
