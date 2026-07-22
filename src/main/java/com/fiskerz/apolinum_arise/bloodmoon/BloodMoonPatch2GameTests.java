package com.fiskerz.apolinum_arise.bloodmoon;

import com.fiskerz.apolinum_arise.Apolinumarise;
import com.fiskerz.apolinum_arise.config.Config;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Apolinumarise.MODID)
@PrefixGameTestTemplate(false)
public class BloodMoonPatch2GameTests {

    // Patch 2 item 4: reapply the Blood Moon Weakness across all 8 phases WITHOUT the effect expiring
    // between them (the realistic "refreshed as the phase changes" path) and assert the applied
    // amplifier tracks each phase's configured value. Own batch: it mutates the shared level day time.
    @GameTest(template = "empty_3x3", batch = "weakness_scaling")
    public static void weakness_scaling_tracks_moon_phase(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Apolinumarise.LOGGER.info("Weakness config table (index = moon phase) = {}", Config.WEAKNESS_AMPLIFIER_BY_PHASE.get());
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        for (int phase = 0; phase < 8; phase++) {
            helper.setDayTime(phase * 24000);
            BloodMoonEvents.debugApplyPlayerEffects(level, player);

            int expected = Config.getWeaknessAmplifierForPhase(phase);
            MobEffectInstance effect = player.getEffect(MobEffects.WEAKNESS);
            int actual = effect == null ? -1 : effect.getAmplifier();
            Apolinumarise.LOGGER.info("Weakness scaling: moonPhase={} expectedAmp={} actualAmp={}", phase, expected, actual);
            helper.assertTrue(actual == expected,
                    "Weakness at phase " + phase + " should be amplifier " + expected + " but applied " + actual);
        }
        helper.succeed();
    }

    // Patch 2 item 3: hostile-mob buff is the config value at Full Moon, scaling to 1.0 at New Moon.
    @GameTest(template = "empty_3x3", batch = "mob_buff_scaling")
    public static void mob_buffs_scale_with_moon_phase(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BloodMoonState.tryUnlock(level);
        BloodMoonState.setActive(level, false);
        double configCeiling = Config.MOB_DAMAGE_MULTIPLIER.get();

        // Full moon (phase 0): full ceiling.
        helper.setDayTime(0);
        Zombie full = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 2, 1));
        double base = full.getAttributeBaseValue(Attributes.ATTACK_DAMAGE);
        BloodMoonEvents.forceStart(level.getServer());
        double fullMoonDamage = full.getAttributeValue(Attributes.ATTACK_DAMAGE);
        Apolinumarise.LOGGER.info("Mob buff full moon: base {} -> {} (x{})", base, fullMoonDamage, fullMoonDamage / base);
        helper.assertTrue(Math.abs(fullMoonDamage - base * configCeiling) < 0.001,
                "Full-moon damage should be base*" + configCeiling + " but was " + fullMoonDamage);
        BloodMoonEvents.forceStop(level.getServer());
        full.discard();

        // New moon (phase 4): no buff.
        helper.setDayTime(4 * 24000);
        Zombie neww = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 2, 1));
        BloodMoonEvents.forceStart(level.getServer());
        double newMoonDamage = neww.getAttributeValue(Attributes.ATTACK_DAMAGE);
        Apolinumarise.LOGGER.info("Mob buff new moon: base {} -> {} (x{})", base, newMoonDamage, newMoonDamage / base);
        helper.assertTrue(Math.abs(newMoonDamage - base) < 0.001,
                "New-moon damage should equal base (no buff) but was " + newMoonDamage);
        BloodMoonEvents.forceStop(level.getServer());
        neww.discard();

        BloodMoonState.setActive(level, false);
        BloodMoonState.setUnlocked(level, false);
        helper.succeed();
    }
}
