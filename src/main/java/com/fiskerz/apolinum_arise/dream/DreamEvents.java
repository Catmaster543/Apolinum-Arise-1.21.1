package com.fiskerz.apolinum_arise.dream;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Server-side NeoForge glue for the Phase 10b dream system. Registered from the main mod class. */
public final class DreamEvents {
    private DreamEvents() {}

    /** Dream scripts are datapack content, so they reload with the server's data. */
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(DreamScripts.INSTANCE);
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        DreamManager.onServerTick(event.getServer());
    }

    // Login is one of the three category-check points (with infection completion and cure): it is what
    // catches players who joined after a broadcast was issued.
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DreamManager.onLogin(player);
        }
    }

    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DreamManager.onLogout(player);
        }
    }
}
