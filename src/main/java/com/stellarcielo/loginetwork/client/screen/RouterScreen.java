package com.stellarcielo.loginetwork.client.screen;

import java.util.List;

import com.stellarcielo.loginetwork.network.payload.ConfigureRouterPayload;
import com.stellarcielo.loginetwork.network.payload.OpenRouterScreenPayload;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

// Router: ports (with address editing), routing table, inventory table and static routes, one tab each.
// Every edit is sent to the server, which answers with a refreshed snapshot.
public class RouterScreen extends Screen {
    private static final int PANEL_WIDTH = 280;
    private static final int MAX_ROWS = 10;
    private static final int ROW_HEIGHT = 12;
    private static final int LABEL_COLOR = 0xFFA0A0A0;
    private static final int VALUE_COLOR = 0xFFFFFFFF;
    private static final String[] TABS = {"ports", "routes", "inventory", "static"};

    // Remembered across refreshes so an edit does not jump back to the first tab.
    private static int lastTab;
    private static int lastPort;

    private final OpenRouterScreenPayload data;
    private EditBox input;

    public RouterScreen(OpenRouterScreenPayload data) {
        super(Component.translatable("gui.loginetwork.router.title"));
        this.data = data;
    }

    private int left() {
        return (this.width - PANEL_WIDTH) / 2;
    }

    private int top() {
        return Math.max(4, this.height / 2 - 115);
    }

    private int listTop() {
        return top() + 40;
    }

    @Override
    protected void init() {
        int left = left();
        int tabWidth = PANEL_WIDTH / TABS.length;
        for (int i = 0; i < TABS.length; i++) {
            int tab = i;
            Button button = Button.builder(Component.translatable("gui.loginetwork.router.tab." + TABS[i]), b -> {
                lastTab = tab;
                rebuildWidgets();
            }).bounds(left + i * tabWidth, top() + 14, tabWidth - 2, 20).build();
            button.active = tab != lastTab;
            this.addRenderableWidget(button);
        }

        int formY = listTop() + ROW_HEIGHT * (MAX_ROWS + 1) + 4;
        if (lastTab == 0) {
            this.addRenderableWidget(Button.builder(portLabel(), b -> {
                lastPort = (lastPort + 1) % Direction.values().length;
                rebuildWidgets();
            }).bounds(left, formY, 80, 20).build());
            this.input = new EditBox(this.font, left + 84, formY, PANEL_WIDTH - 84 - 64, 20, Component.translatable("gui.loginetwork.router.port_address"));
            this.input.setMaxLength(18);
            this.input.setHint(Component.literal("10.0.9.1/24"));
            this.input.setValue(portAddress(Direction.from3DDataValue(lastPort)));
            this.addRenderableWidget(this.input);
            this.addRenderableWidget(Button.builder(Component.translatable("gui.loginetwork.router.apply"), b ->
                    send(ConfigureRouterPayload.SET_PORT, lastPort, input.getValue().trim()))
                    .bounds(left + PANEL_WIDTH - 60, formY, 60, 20).build());
        } else if (lastTab == 3) {
            this.input = new EditBox(this.font, left, formY, PANEL_WIDTH - 64, 20, Component.translatable("gui.loginetwork.router.static_route"));
            this.input.setMaxLength(40);
            this.input.setHint(Component.literal("10.2.0.0/24 via 10.0.9.2"));
            this.addRenderableWidget(this.input);
            this.addRenderableWidget(Button.builder(Component.translatable("gui.loginetwork.router.add"), b ->
                    send(ConfigureRouterPayload.ADD_STATIC, 0, input.getValue().trim()))
                    .bounds(left + PANEL_WIDTH - 60, formY, 60, 20).build());
            for (int i = 0; i < Math.min(MAX_ROWS, data.staticRoutes().size()); i++) {
                int index = i;
                this.addRenderableWidget(Button.builder(Component.literal("x"), b -> send(ConfigureRouterPayload.REMOVE_STATIC, index, ""))
                        .bounds(left + PANEL_WIDTH - 12, listTop() + ROW_HEIGHT * (i + 1) - 2, 12, 11).build());
            }
        }
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(left, formY + 26, PANEL_WIDTH, 20).build());
    }

    private Component portLabel() {
        return Component.translatable("gui.loginetwork.router.port", portName(Direction.from3DDataValue(lastPort).getSerializedName()));
    }

    private String portAddress(Direction port) {
        return data.ports().stream().filter(view -> view.port().equals(port.getSerializedName())).map(OpenRouterScreenPayload.PortView::address)
                .findFirst().orElse("");
    }

    private void send(int action, int index, String text) {
        ClientPacketDistributor.sendToServer(new ConfigureRouterPayload(data.pos(), action, index, text));
    }

    private static Component portName(String port) {
        return Component.translatable("gui.loginetwork.switch.port." + port);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int left = left();
        graphics.centeredText(this.font, Component.translatable("gui.loginetwork.router.title_with_id", data.routerId()), this.width / 2, top(), VALUE_COLOR);
        int y = listTop();
        switch (lastTab) {
            case 0 -> drawPorts(graphics, left, y);
            case 1 -> drawRoutes(graphics, left, y);
            case 2 -> drawInventory(graphics, left, y);
            default -> drawStatic(graphics, left, y);
        }
    }

    private void drawPorts(GuiGraphicsExtractor graphics, int left, int y) {
        header(graphics, left, y, "gui.loginetwork.router.col.port", "gui.loginetwork.router.col.address", "gui.loginetwork.router.col.neighbours");
        int row = 1;
        for (OpenRouterScreenPayload.PortView port : data.ports()) {
            int rowY = y + ROW_HEIGHT * row++;
            graphics.text(this.font, portName(port.port()), left, rowY, VALUE_COLOR);
            graphics.text(this.font, port.address().isEmpty() ? Component.translatable("gui.loginetwork.none") : Component.literal(port.address()),
                    left + 60, rowY, VALUE_COLOR);
            graphics.text(this.font, Component.translatable("gui.loginetwork.router.neighbours_value", port.routers(), port.hosts()), left + 170, rowY, VALUE_COLOR);
        }
        if (!data.duplicates().isEmpty()) {
            graphics.text(this.font, Component.translatable("gui.loginetwork.router.duplicates", String.join(", ", data.duplicates()))
                    .withStyle(ChatFormatting.RED), left, y + ROW_HEIGHT * row, VALUE_COLOR);
        }
    }

    private void drawRoutes(GuiGraphicsExtractor graphics, int left, int y) {
        header(graphics, left, y, "gui.loginetwork.router.col.destination", "gui.loginetwork.router.col.via", "gui.loginetwork.router.col.cost");
        List<OpenRouterScreenPayload.RouteView> routes = data.routes();
        drawRows(graphics, left, y, routes.size(), i -> {
            OpenRouterScreenPayload.RouteView route = routes.get(i);
            Component via = switch (route.kind()) {
                case OpenRouterScreenPayload.KIND_CONNECTED -> Component.translatable("gui.loginetwork.router.via_connected", portName(route.via()));
                case OpenRouterScreenPayload.KIND_STATIC -> Component.translatable("gui.loginetwork.router.via_static", route.via());
                default -> Component.translatable("gui.loginetwork.router.via_router", route.via());
            };
            return new Component[] {Component.literal(route.destination()), via, Component.literal(String.valueOf(route.cost()))};
        });
    }

    private void drawInventory(GuiGraphicsExtractor graphics, int left, int y) {
        header(graphics, left, y, "gui.loginetwork.router.col.item", "gui.loginetwork.router.col.host", "gui.loginetwork.router.col.cost");
        List<OpenRouterScreenPayload.InventoryView> inventory = data.inventory();
        drawRows(graphics, left, y, inventory.size(), i -> new Component[] {inventory.get(i).item().getHoverName(),
                Component.literal(inventory.get(i).host()), Component.literal(String.valueOf(inventory.get(i).cost()))});
    }

    private void drawStatic(GuiGraphicsExtractor graphics, int left, int y) {
        graphics.text(this.font, Component.translatable("gui.loginetwork.router.static_header"), left, y, LABEL_COLOR);
        List<String> routes = data.staticRoutes();
        if (routes.isEmpty()) {
            graphics.text(this.font, Component.translatable("gui.loginetwork.none"), left + 8, y + ROW_HEIGHT, VALUE_COLOR);
        }
        for (int i = 0; i < Math.min(MAX_ROWS, routes.size()); i++) {
            graphics.text(this.font, (i + 1) + ". " + routes.get(i), left + 8, y + ROW_HEIGHT * (i + 1), VALUE_COLOR);
        }
    }

    private void header(GuiGraphicsExtractor graphics, int left, int y, String first, String second, String third) {
        graphics.text(this.font, Component.translatable(first), left, y, LABEL_COLOR);
        graphics.text(this.font, Component.translatable(second), left + (lastTab == 0 ? 60 : 110), y, LABEL_COLOR);
        graphics.text(this.font, Component.translatable(third), left + (lastTab == 0 ? 170 : 240), y, LABEL_COLOR);
    }

    private interface RowSource {
        Component[] row(int index);
    }

    private void drawRows(GuiGraphicsExtractor graphics, int left, int y, int count, RowSource source) {
        if (count == 0) {
            graphics.text(this.font, Component.translatable("gui.loginetwork.none"), left + 8, y + ROW_HEIGHT, VALUE_COLOR);
            return;
        }
        int shown = Math.min(MAX_ROWS - 1, count);
        for (int i = 0; i < shown; i++) {
            Component[] row = source.row(i);
            int rowY = y + ROW_HEIGHT * (i + 1);
            graphics.text(this.font, row[0], left, rowY, VALUE_COLOR);
            graphics.text(this.font, row[1], left + 110, rowY, VALUE_COLOR);
            graphics.text(this.font, row[2], left + 240, rowY, VALUE_COLOR);
        }
        if (count > shown) {
            graphics.text(this.font, Component.translatable("gui.loginetwork.dhcp.more", count - shown), left + 8, y + ROW_HEIGHT * (shown + 1), LABEL_COLOR);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
