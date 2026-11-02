package com.fiskerz.apolinum_arise.ability.client;

import com.fiskerz.apolinum_arise.network.AbilityKeyPressPayload;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Turns a press of one of the three ability keybinds into a packet naming the slot (Phase 14).
 *
 * <p>Deliberately dumb: it knows nothing about which abilities exist or whether the player has any. The
 * server owns that lookup, so this stays correct as abilities are added and leaks nothing about them to a
 * client that should not know.
 *
 * <p>Presses are read with {@code consumeClick}, which only yields queued presses and so cannot fire while
 * a GUI is open - vanilla does not feed keybind clicks to mappings while a screen has focus. The explicit
 * {@code screen == null} check is belt and braces for that, and also drains any press that was queued the
 * instant before a screen opened rather than letting it fire on close.
 */
public final class AbilityClientEvents {
    private AbilityClientEvents() {}

    public static void onClientTickPost(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean deliverable = minecraft.player != null && minecraft.level != null && minecraft.screen == null;

        for (int index = 0; index < AbilityKeybinds.SLOTS.length; index++) {
            KeyMapping mapping = AbilityKeybinds.SLOTS[index];
            int slot = AbilityKeybinds.slotOf(index);
            while (mapping.consumeClick()) {
                // Drained either way: a press made while a screen was opening must not arrive later.
                if (deliverable) {
                    PacketDistributor.sendToServer(new AbilityKeyPressPayload(slot));
                }
            }
        }
    }
}
