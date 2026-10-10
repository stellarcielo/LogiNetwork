package com.stellarcielo.loginetwork;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

// Client-only entry point. Not loaded on dedicated servers.
@Mod(value = LogiNetwork.MOD_ID, dist = Dist.CLIENT)
public class LogiNetworkClient {
    public LogiNetworkClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
