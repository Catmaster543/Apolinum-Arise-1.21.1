package com.fiskerz.apolinum_arise.network;

import com.fiskerz.apolinum_arise.bloodmoon.BloodMoonRegistry;
import com.fiskerz.apolinum_arise.config.Config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client-only handling of {@link AwakeningSoundPayload}. Referenced from ModNetworking strictly
 * through a lambda so this class (and Minecraft) never classloads on a dedicated server.
 */
public final class AwakeningSoundClientHandler {
    private AwakeningSoundClientHandler() {}

    public static void handle(AwakeningSoundPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            LocalPlayer player = minecraft.player;
            if (player == null) {
                return;
            }

            // Full volume up close, linear falloff to the floor volume at falloffDistance,
            // floor volume beyond that or from another dimension. Config is the synced SERVER config.
            float minVolume = (float) (double) Config.SOUND_MIN_VOLUME.get();
            double falloff = Config.SOUND_FALLOFF_DISTANCE.get();
            float volume;
            if (!player.level().dimension().location().equals(payload.dimension())) {
                volume = minVolume;
            } else {
                double distance = Math.sqrt(payload.pos().distToCenterSqr(player.position()));
                volume = distance >= falloff
                        ? minVolume
                        : (float) (minVolume + (1.0 - minVolume) * (1.0 - distance / falloff));
            }

            // Non-positional playback: the sound engine would re-attenuate a positional sound
            // within ~16 blocks, defeating the manual distance scaling.
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(BloodMoonRegistry.AWAKENING_SOUND.get(), 1.0F, volume));
        });
    }
}
