package com.fiskerz.apolinum_arise.bloodmoon.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;

public final class BloodMoonClientState {
    private static boolean active;
    private static BloodMoonAmbientLoopSound ambientSound;

    private BloodMoonClientState() {}

    public static boolean isActive() {
        return active;
    }

    public static void setActive(boolean activeState) {
        active = activeState;
        if (activeState) {
            ensureAmbientSoundPlaying();
        } else {
            stopAmbientSound();
        }
    }

    /** Forget everything when leaving a server so stale state never bleeds into the next session. */
    public static void reset() {
        active = false;
        stopAmbientSound();
    }

    private static void ensureAmbientSoundPlaying() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.level.dimension() != Level.OVERWORLD) {
            return;
        }

        // A previous instance may have stopped itself (dimension switch, disconnect) - replace it then.
        if (ambientSound != null && !ambientSound.isStopped()) {
            return;
        }

        // No existence pre-check, matching the awakening sound's precedent: while the real .ogg is
        // missing, the sound engine logs a one-time "Unable to play empty soundEvent" warning and
        // plays nothing - graceful, no crash.
        ambientSound = new BloodMoonAmbientLoopSound();
        minecraft.getSoundManager().queueTickingSound(ambientSound);
    }

    private static void stopAmbientSound() {
        if (ambientSound == null) {
            return;
        }

        Minecraft.getInstance().getSoundManager().stop(ambientSound);
        ambientSound = null;
    }
}
