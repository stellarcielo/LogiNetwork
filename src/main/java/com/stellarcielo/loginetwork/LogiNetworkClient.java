package com.stellarcielo.loginetwork;

import com.stellarcielo.loginetwork.client.ClientControlTracker;
import com.stellarcielo.loginetwork.client.ClientTransitTracker;
import com.stellarcielo.loginetwork.client.TransitItemRenderer;
import com.stellarcielo.loginetwork.client.screen.AccessSwitchScreen;
import com.stellarcielo.loginetwork.client.screen.DhcpServerScreen;
import com.stellarcielo.loginetwork.client.screen.NetworkInterfaceScreen;
import com.stellarcielo.loginetwork.client.screen.StockKeeperScreen;
import com.stellarcielo.loginetwork.network.payload.ControlPathPayload;
import com.stellarcielo.loginetwork.network.payload.OpenDhcpScreenPayload;
import com.stellarcielo.loginetwork.network.payload.OpenInterfaceScreenPayload;
import com.stellarcielo.loginetwork.network.payload.OpenSwitchScreenPayload;
import com.stellarcielo.loginetwork.registry.ModMenus;
import com.stellarcielo.loginetwork.network.payload.TransitHopPayload;
import com.stellarcielo.loginetwork.network.payload.TransitRemovePayload;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;

// Client-only entry point. Not loaded on dedicated servers.
@Mod(value = LogiNetwork.MOD_ID, dist = Dist.CLIENT)
public class LogiNetworkClient {
    public LogiNetworkClient(IEventBus modEventBus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        modEventBus.addListener(LogiNetworkClient::registerPayloadHandlers);
        modEventBus.addListener(LogiNetworkClient::registerMenuScreens);
        NeoForge.EVENT_BUS.addListener(TransitItemRenderer::extract);
        NeoForge.EVENT_BUS.addListener(TransitItemRenderer::submit);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> {
            ClientTransitTracker.clear();
            ClientControlTracker.clear();
        });
    }

    private static void registerPayloadHandlers(RegisterClientPayloadHandlersEvent event) {
        event.register(TransitHopPayload.TYPE, (payload, context) -> ClientTransitTracker.onHop(payload));
        event.register(TransitRemovePayload.TYPE, (payload, context) -> ClientTransitTracker.onRemove(payload));
        event.register(OpenInterfaceScreenPayload.TYPE, (payload, context) -> Minecraft.getInstance().gui.setScreen(new NetworkInterfaceScreen(payload)));
        event.register(OpenDhcpScreenPayload.TYPE, (payload, context) -> Minecraft.getInstance().gui.setScreen(new DhcpServerScreen(payload)));
        event.register(OpenSwitchScreenPayload.TYPE, (payload, context) -> Minecraft.getInstance().gui.setScreen(new AccessSwitchScreen(payload)));
        event.register(ControlPathPayload.TYPE, (payload, context) -> ClientControlTracker.onPath(payload));
    }

    private static void registerMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.STOCK_KEEPER.get(), StockKeeperScreen::new);
    }
}
