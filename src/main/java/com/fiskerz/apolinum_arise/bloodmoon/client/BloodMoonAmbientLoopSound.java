package com.fiskerz.apolinum_arise.bloodmoon.client;

import com.fiskerz.apolinum_arise.bloodmoon.BloodMoonRegistry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;

public final class BloodMoonAmbientLoopSound extends AbstractTickableSoundInstance {
    public BloodMoonAmbientLoopSound() {
        super(BloodMoonRegistry.BLOODMOON_AMBIENT_SOUND.get(), SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
        this.volume = 1.0F;
        this.pitch = 1.0F;
        this.looping = true;
        this.relative = true;
        this.attenuation = Attenuation.NONE;
        this.delay = 0;
        this.x = 0.0D;
        this.y = 0.0D;
        this.z = 0.0D;
    }

    @Override
    public void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.level.dimension() != Level.OVERWORLD || !BloodMoonClientState.isActive()) {
            stop();
        }
    }
}