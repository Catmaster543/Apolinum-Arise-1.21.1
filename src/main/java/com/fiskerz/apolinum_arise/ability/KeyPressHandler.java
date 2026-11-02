package com.fiskerz.apolinum_arise.ability;

import net.minecraft.server.level.ServerPlayer;

/**
 * What an ability does when its keybind slot is pressed (Phase 14).
 *
 * <p>The general hook the slot router calls, so a future keybind ability is a registration plus one
 * implementation of this - the routing in {@link AbilityManager#onKeybindSlotPressed} never needs to learn
 * about it. Always invoked on the server, after the router has confirmed the player actually holds the
 * ability and is the right side/variant for it.
 */
@FunctionalInterface
public interface KeyPressHandler {
    void onKeyPress(ServerPlayer player);
}
