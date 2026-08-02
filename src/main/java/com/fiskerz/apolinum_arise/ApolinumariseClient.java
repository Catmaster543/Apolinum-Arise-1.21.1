package com.fiskerz.apolinum_arise;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import com.fiskerz.apolinum_arise.bloodmoon.BloodMoonRegistry;
import com.fiskerz.apolinum_arise.bloodmoon.client.BloodMoonClientEvents;
import com.fiskerz.apolinum_arise.bloodmoon.client.BloodMoonClientState;
import com.fiskerz.apolinum_arise.downed.client.DownedClientEvents;
import com.fiskerz.apolinum_arise.downed.client.DownedKeybinds;
import com.fiskerz.apolinum_arise.downed.client.DownedPoseCommand;
import com.fiskerz.apolinum_arise.downed.client.DownedPoseLayer;
import com.fiskerz.apolinum_arise.downed.client.DownedPoses;
import com.fiskerz.apolinum_arise.infection.InfectionMenus;
import com.fiskerz.apolinum_arise.infection.client.BiteBarHud;
import com.fiskerz.apolinum_arise.infection.client.InfectionClientEvents;
import com.fiskerz.apolinum_arise.infection.client.MoleClientState;
import com.fiskerz.apolinum_arise.infection.client.MoleRenderLayer;
import com.fiskerz.apolinum_arise.infection.client.RestrictedInventoryScreen;
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
        modEventBus.addListener(ApolinumariseClient::onAddLayers);
        modEventBus.addListener(ApolinumariseClient::onRegisterMenuScreens);
        modEventBus.addListener(DownedKeybinds::register);
        modEventBus.addListener(DownedClientEvents::onRegisterGuiLayers);
        modEventBus.addListener(ApolinumariseClient::onRegisterGuiLayers);
        modEventBus.addListener(ApolinumariseClient::onRegisterReloadListeners);
        NeoForge.EVENT_BUS.addListener(BloodMoonClientEvents::onRenderLevelStage);
        NeoForge.EVENT_BUS.addListener(BloodMoonClientEvents::onComputeFogColor);
        NeoForge.EVENT_BUS.addListener(ApolinumariseClient::onLoggingOut);
        // Phase 6 B2: intercept the survival inventory for infected players and open the restricted menu.
        NeoForge.EVENT_BUS.addListener(InfectionClientEvents::onScreenOpening);
        // Phase 7 downed/revive client behaviour.
        NeoForge.EVENT_BUS.addListener(DownedClientEvents::onRenderGuiLayerPre);
        NeoForge.EVENT_BUS.addListener(DownedClientEvents::onClientTickPost);
        NeoForge.EVENT_BUS.addListener(DownedClientEvents::onMovementInput);
        NeoForge.EVENT_BUS.addListener(DownedClientEvents::onInteractionKey);
        NeoForge.EVENT_BUS.addListener(DownedClientEvents::onRenderPlayerPre);
        // Live pose-tuning tool for the downed poses (client-side command).
        NeoForge.EVENT_BUS.addListener(DownedPoseCommand::register);
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(BloodMoonRegistry.MOSQUITO.get(), MosquitoRenderer::new);
    }

    // Phase 8: the bite bar, drawn just above the vanilla food bar (BiteBarHud offsets it up further when
    // Tough As Nails' thirst bar is present). Registered ABOVE the food layer so it draws on top of it.
    private static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.FOOD_LEVEL,
                ResourceLocation.fromNamespaceAndPath(Apolinumarise.MODID, "bite_bar"), BiteBarHud::render);
    }

    // Load the downed pose files (assets/apolinumarise/downed_poses/pose_N.json) on startup and on every
    // resource reload (F3+T / the /downedpose reload command), so edited pose files hot-reload.
    private static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) DownedPoses::reload);
    }

    private static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(InfectionMenus.RESTRICTED_INVENTORY.get(), RestrictedInventoryScreen::new);
    }

    // Attach the mole layer to every player-skin renderer (default/wide and slim/Alex) so moles show
    // on both model variants.
    private static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model skin : event.getSkins()) {
            EntityRenderer<? extends Player> renderer = event.getSkin(skin);
            if (renderer instanceof PlayerRenderer playerRenderer) {
                playerRenderer.addLayer(new MoleRenderLayer(playerRenderer));
                playerRenderer.addLayer(new DownedPoseLayer(playerRenderer));
            }
        }
    }

    // The server re-syncs on every login; resetting here just prevents one stale red frame
    // (or a stuck ambient loop) between disconnecting and that first sync packet.
    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        BloodMoonClientState.reset();
        MoleClientState.clear();
    }
}
