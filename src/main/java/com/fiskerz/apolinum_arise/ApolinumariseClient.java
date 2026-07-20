package com.fiskerz.apolinum_arise;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

import com.fiskerz.apolinum_arise.bloodmoon.BloodMoonRegistry;
import com.fiskerz.apolinum_arise.bloodmoon.client.BloodMoonClientEvents;
import com.fiskerz.apolinum_arise.bloodmoon.client.BloodMoonClientState;
import com.fiskerz.apolinum_arise.mosquito.client.MosquitoRenderer;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = Apolinumarise.MODID, dist = Dist.CLIENT)
public class ApolinumariseClient {
    public ApolinumariseClient(ModContainer container, IEventBus modEventBus) {
        // Allows NeoForge to create a config screen for this mod's configs.
        // The config screen is accessed by going to the Mods screen > clicking on your mod > clicking on config.
        // Do not forget to add translations for your config options to the en_us.json file.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modEventBus.addListener(ApolinumariseClient::onRegisterRenderers);
        NeoForge.EVENT_BUS.addListener(BloodMoonClientEvents::onRenderLevelStage);
        NeoForge.EVENT_BUS.addListener(BloodMoonClientEvents::onComputeFogColor);
        NeoForge.EVENT_BUS.addListener(ApolinumariseClient::onLoggingOut);
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(BloodMoonRegistry.MOSQUITO.get(), MosquitoRenderer::new);
    }

    // The server re-syncs on every login; resetting here just prevents one stale red frame
    // (or a stuck ambient loop) between disconnecting and that first sync packet.
    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        BloodMoonClientState.reset();
    }
}
