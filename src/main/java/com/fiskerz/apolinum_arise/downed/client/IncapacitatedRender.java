package com.fiskerz.apolinum_arise.downed.client;

import com.fiskerz.apolinum_arise.downed.DownedAttachments;
import com.fiskerz.apolinum_arise.downed.DownedData;
import com.fiskerz.apolinum_arise.downed.DownedManager;
import com.fiskerz.apolinum_arise.sleep.SleepAttachments;
import com.fiskerz.apolinum_arise.sleep.SleepData;

import net.minecraft.world.entity.player.Player;

/**
 * Resolves which incapacitation a player's laid-out body should be drawn from, so the pose layer and the
 * render hook that hides the standing body never disagree. The Phase 10a pass-out reuses the whole Phase 7
 * downed presentation through here rather than shipping a second renderer; the real downed state wins when
 * (improbably) both are set.
 */
public final class IncapacitatedRender {
    private IncapacitatedRender() {}

    /** Downed or passed out: the body is laid out on the ground either way. */
    public static boolean active(Player player) {
        return DownedManager.isIncapacitated(player);
    }

    /** Which of the pose files to draw (both systems seed a random variant when they take effect). */
    public static int poseVariant(Player player) {
        DownedData downed = player.getData(DownedAttachments.DOWNED);
        return downed.downed() ? downed.poseVariant() : player.getData(SleepAttachments.SLEEP).poseVariant();
    }

    /** The facing frozen when the player went down / passed out, so the body never spins with the camera. */
    public static float bodyYaw(Player player) {
        DownedData downed = player.getData(DownedAttachments.DOWNED);
        if (downed.downed()) {
            return downed.bodyYaw();
        }
        SleepData sleep = player.getData(SleepAttachments.SLEEP);
        return sleep.passedOut() ? sleep.bodyYaw() : player.yBodyRot;
    }
}
