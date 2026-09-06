package com.fiskerz.apolinum_arise.dream.client;

import com.fiskerz.apolinum_arise.dream.DreamCameraEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;

/**
 * Client-side input lock for an actively playing dream.
 *
 * <p>No syncing is needed: the client already knows it is dreaming, because the server attached its camera
 * to a {@link DreamCameraEntity}. Checking the camera entity type is therefore both the signal and the
 * exact duration of the lock - it lifts by itself the moment the camera is handed back.
 */
public final class DreamClientEvents {
    private DreamClientEvents() {}

    /** True while this client's view is attached to a dream camera. */
    public static boolean isDreaming() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && minecraft.cameraEntity instanceof DreamCameraEntity;
    }

    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!isDreaming()) {
            return;
        }
        Input input = event.getInput();
        input.up = false;
        input.down = false;
        input.left = false;
        input.right = false;
        input.jumping = false;
        input.shiftKeyDown = false;
        input.forwardImpulse = 0.0F;
        input.leftImpulse = 0.0F;
    }

    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (isDreaming()) {
            event.setCanceled(true); // no attacking / using / interacting mid-dream
        }
    }
}
