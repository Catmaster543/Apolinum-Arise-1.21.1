package com.fiskerz.apolinum_arise.ability.client;

import com.fiskerz.apolinum_arise.ability.strongpunch.StrongPunch;
import com.fiskerz.apolinum_arise.network.AttackHeldPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Reports the raw attack-key state to the server so a held input can drive a charge (Phase 13).
 *
 * <p>Only the key state is sent - never a target, a duration, or a "done" flag - because the server
 * decides all of that. See {@code AttackHeldPayload} for why this has to be reported at all.
 *
 * <p>Sending is gated on the local player actually having the ability and an empty main hand, so for
 * everyone else this costs nothing: no packet is ever sent. That gate is a bandwidth optimisation only -
 * the server re-checks the hand, the target, the range and the timing regardless, so a client that
 * ignored the gate and spammed the packet would gain nothing.
 */
public final class StrongPunchClientEvents {
    private StrongPunchClientEvents() {}

    /** Mirrors what the server last heard, so the packet is only sent when the state actually matters. */
    private static boolean lastSentHeld;

    public static void onClientTickPost(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            lastSentHeld = false;
            return;
        }

        boolean eligible = StrongPunch.isUnlockedFor(player) && player.getMainHandItem().isEmpty();
        boolean held = eligible && minecraft.options.keyAttack.isDown() && minecraft.screen == null;

        if (held) {
            // Every tick while down, matching the Phase 7 revive channel: the server treats a report older
            // than a couple of ticks as a release, so a dropped packet or a disconnect frees the charge
            // instead of leaving the server convinced a key is still held.
            PacketDistributor.sendToServer(new AttackHeldPayload(true));
            lastSentHeld = true;
        } else if (lastSentHeld) {
            // One explicit release so the server reacts immediately rather than waiting out the grace.
            PacketDistributor.sendToServer(new AttackHeldPayload(false));
            lastSentHeld = false;
        }
    }
}
