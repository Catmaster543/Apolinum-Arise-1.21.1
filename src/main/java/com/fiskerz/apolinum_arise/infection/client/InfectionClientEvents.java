package com.fiskerz.apolinum_arise.infection.client;

import com.fiskerz.apolinum_arise.infection.InfectionAttachments;
import com.fiskerz.apolinum_arise.network.OpenRestrictedInventoryPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Client-side interception of the vanilla survival inventory for infected players (Phase 6 B2). Uses
 * {@link ScreenEvent.Opening} - the NeoForge hook for a screen about to open - to cancel the vanilla
 * {@link InventoryScreen} and ask the server to open the real, server-enforced restricted menu instead.
 * The isInfected flag is available client-side via the self-synced infection attachment.
 */
public final class InfectionClientEvents {
    private InfectionClientEvents() {}

    public static void onScreenOpening(ScreenEvent.Opening event) {
        Screen opening = event.getNewScreen();
        // Only the survival inventory. CreativeModeInventoryScreen does not extend InventoryScreen, so
        // creative-mode admins keep full access; the restricted screen is not an InventoryScreen either.
        if (!(opening instanceof InventoryScreen)) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !player.getData(InfectionAttachments.INFECTION).infected()) {
            return;
        }
        // Patch A2: only restrict in Survival/Adventure. In Creative/Spectator, leave the screen alone.
        if (player.isCreative() || player.isSpectator()) {
            return;
        }
        event.setCanceled(true);
        PacketDistributor.sendToServer(OpenRestrictedInventoryPayload.INSTANCE);
    }
}
