package com.stellarcielo.loginetwork.client.screen;

import com.stellarcielo.loginetwork.network.payload.ConfigureSwitchPortPayload;
import com.stellarcielo.loginetwork.network.payload.OpenSwitchScreenPayload;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

// Switch: the learned MAC address table, and per-port VLAN settings (access VLAN or trunk with allowed VLANs).
public class AccessSwitchScreen extends Screen {
    private static final int PANEL_WIDTH = 260;
    private static final int MAX_VISIBLE_ENTRIES = 10;
    private static final int ROW_HEIGHT = 12;
    private static final int LABEL_COLOR = 0xFFA0A0A0;
    private static final int VALUE_COLOR = 0xFFFFFFFF;

    // Remembered across refreshes after an edit.
    private static boolean vlanTab;
    private static int selectedPort;

    private final OpenSwitchScreenPayload data;
    private boolean trunk;
    private EditBox value;

    public AccessSwitchScreen(OpenSwitchScreenPayload data) {
        super(Component.translatable("gui.loginetwork.switch.title"));
        this.data = data;
        this.trunk = selected().trunk();
    }

    private OpenSwitchScreenPayload.PortView selected() {
        String name = Direction.from3DDataValue(selectedPort).getSerializedName();
        return data.ports().stream().filter(port -> port.port().equals(name)).findFirst()
                .orElse(new OpenSwitchScreenPayload.PortView(name, false, 1, "all"));
    }

    private int left() {
        return (this.width - PANEL_WIDTH) / 2;
    }

    private int top() {
        return Math.max(4, this.height / 2 - 110);
    }

    private int listTop() {
        return top() + 40;
    }

    @Override
    protected void init() {
        int left = left();
        int half = PANEL_WIDTH / 2 - 1;
        Button macTab = Button.builder(Component.translatable("gui.loginetwork.switch.tab.mac"), b -> {
            vlanTab = false;
            rebuildWidgets();
        }).bounds(left, top() + 14, half, 20).build();
        macTab.active = vlanTab;
        this.addRenderableWidget(macTab);
        Button portsTab = Button.builder(Component.translatable("gui.loginetwork.switch.tab.vlan"), b -> {
            vlanTab = true;
            rebuildWidgets();
        }).bounds(left + half + 2, top() + 14, half, 20).build();
        portsTab.active = !vlanTab;
        this.addRenderableWidget(portsTab);

        int formY = listTop() + ROW_HEIGHT * (MAX_VISIBLE_ENTRIES + 1) + 4;
        if (vlanTab) {
            this.addRenderableWidget(Button.builder(Component.translatable("gui.loginetwork.router.port",
                    Component.translatable("gui.loginetwork.switch.port." + selected().port())), b -> {
                selectedPort = (selectedPort + 1) % Direction.values().length;
                trunk = selected().trunk();
                rebuildWidgets();
            }).bounds(left, formY, 70, 20).build());
            this.addRenderableWidget(Button.builder(modeLabel(), b -> {
                trunk = !trunk;
                b.setMessage(modeLabel());
                value.setValue(currentValue());
            }).bounds(left + 72, formY, 70, 20).build());
            this.value = new EditBox(this.font, left + 144, formY, PANEL_WIDTH - 144 - 52, 20, Component.translatable("gui.loginetwork.switch.vlan"));
            this.value.setMaxLength(60);
            this.value.setValue(currentValue());
            this.addRenderableWidget(this.value);
            this.addRenderableWidget(Button.builder(Component.translatable("gui.loginetwork.router.apply"), b -> apply())
                    .bounds(left + PANEL_WIDTH - 50, formY, 50, 20).build());
        }
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).bounds(left, formY + 26, PANEL_WIDTH, 20).build());
    }

    private Component modeLabel() {
        return Component.translatable(trunk ? "gui.loginetwork.switch.mode.trunk" : "gui.loginetwork.switch.mode.access");
    }

    // Access VLAN for access mode, allowed list for trunk mode.
    private String currentValue() {
        OpenSwitchScreenPayload.PortView port = selected();
        return trunk ? (port.trunk() ? port.allowed() : "all") : String.valueOf(port.accessVlan());
    }

    private void apply() {
        String text = value.getValue().trim();
        int accessVlan = selected().accessVlan();
        String allowed = selected().allowed();
        if (trunk) {
            allowed = text;
        } else {
            try {
                accessVlan = Integer.parseInt(text);
            } catch (NumberFormatException e) {
                accessVlan = -1;
            }
        }
        ClientPacketDistributor.sendToServer(new ConfigureSwitchPortPayload(data.pos(), selectedPort, trunk, accessVlan, allowed));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int left = left();
        graphics.centeredText(this.font, this.title, this.width / 2, top(), VALUE_COLOR);
        if (vlanTab) {
            drawPorts(graphics, left, listTop());
        } else {
            drawMacTable(graphics, left, listTop());
        }
    }

    private void drawPorts(GuiGraphicsExtractor graphics, int left, int y) {
        graphics.text(this.font, Component.translatable("gui.loginetwork.switch.port"), left, y, LABEL_COLOR);
        graphics.text(this.font, Component.translatable("gui.loginetwork.switch.mode"), left + 60, y, LABEL_COLOR);
        graphics.text(this.font, Component.translatable("gui.loginetwork.switch.vlan"), left + 130, y, LABEL_COLOR);
        int row = 1;
        for (OpenSwitchScreenPayload.PortView port : data.ports()) {
            int rowY = y + ROW_HEIGHT * row++;
            graphics.text(this.font, Component.translatable("gui.loginetwork.switch.port." + port.port()), left, rowY, VALUE_COLOR);
            graphics.text(this.font, Component.translatable(port.trunk() ? "gui.loginetwork.switch.mode.trunk" : "gui.loginetwork.switch.mode.access"),
                    left + 60, rowY, VALUE_COLOR);
            graphics.text(this.font, port.trunk() ? Component.translatable("gui.loginetwork.switch.allowed", port.allowed())
                    : Component.literal(String.valueOf(port.accessVlan())), left + 130, rowY, VALUE_COLOR);
        }
        graphics.text(this.font, Component.translatable("gui.loginetwork.switch.vlan_hint"), left, y + ROW_HEIGHT * (row + 1), LABEL_COLOR);
    }

    private void drawMacTable(GuiGraphicsExtractor graphics, int left, int y) {
        graphics.text(this.font, Component.translatable("gui.loginetwork.switch.mac"), left, y, LABEL_COLOR);
        graphics.text(this.font, Component.translatable("gui.loginetwork.switch.port"), left + 120, y, LABEL_COLOR);
        graphics.text(this.font, Component.translatable("gui.loginetwork.switch.age"), left + 190, y, LABEL_COLOR);
        if (data.entries().isEmpty()) {
            graphics.text(this.font, Component.translatable("gui.loginetwork.none"), left, y + ROW_HEIGHT, VALUE_COLOR);
            return;
        }
        int shown = Math.min(MAX_VISIBLE_ENTRIES, data.entries().size());
        for (int i = 0; i < shown; i++) {
            OpenSwitchScreenPayload.MacEntry entry = data.entries().get(i);
            int rowY = y + ROW_HEIGHT * (i + 1);
            graphics.text(this.font, entry.mac(), left, rowY, VALUE_COLOR);
            graphics.text(this.font, Component.translatable("gui.loginetwork.switch.port." + entry.port()), left + 120, rowY, VALUE_COLOR);
            graphics.text(this.font, Component.translatable("gui.loginetwork.switch.age_value", entry.ageSeconds()), left + 190, rowY, VALUE_COLOR);
        }
        if (data.entries().size() > shown) {
            graphics.text(this.font, Component.translatable("gui.loginetwork.dhcp.more", data.entries().size() - shown), left, y + ROW_HEIGHT * (shown + 1), LABEL_COLOR);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
