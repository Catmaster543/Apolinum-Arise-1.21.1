package com.fiskerz.apolinum_arise.infection;

import com.fiskerz.apolinum_arise.Apolinumarise;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Apolinumarise.MODID)
@PrefixGameTestTemplate(false)
public class SymptomGameTests {

    // The data-driven 10-day table is the spec; lock the whole shape of the progression against it.
    @GameTest(template = "empty_3x3", batch = "infection")
    public static void day_profile_table_matches_spec(GameTestHelper helper) {
        // Days 1-2: shared early roller only, no main pool, no message.
        for (int day = 1; day <= 2; day++) {
            DayProfile p = DayProfile.forDay(day);
            helper.assertTrue(p.earlyRoller() && !p.mainPool() && p.actionbarKey() == null,
                    "Day " + day + " is early-roller-only");
        }
        // Day 3: main pool begins with a message; no Hunger in pool yet; no sun effects.
        DayProfile d3 = DayProfile.forDay(3);
        helper.assertTrue(!d3.earlyRoller() && d3.mainPool() && !d3.poolIncludesHunger()
                && !d3.sunWeakness() && d3.actionbarKey() != null, "Day 3 opens the main pool");
        // Day 4: Hunger joins the pool.
        helper.assertTrue(DayProfile.forDay(4).poolIncludesHunger(), "Day 4 adds Hunger to the pool");
        // Day 5: sun Weakness begins, with a message.
        helper.assertTrue(DayProfile.forDay(5).sunWeakness() && DayProfile.forDay(5).actionbarKey() != null,
                "Day 5 begins sun Weakness");
        // Day 6: first mole-roll day; still no constant hunger / doubled pool.
        helper.assertTrue(DayProfile.forDay(6).rollsMole() && !DayProfile.forDay(6).constantHunger()
                && !DayProfile.forDay(6).poolChanceDoubled(), "Day 6 starts mole rolls");
        // Day 7: constant Hunger + doubled pool chance + message.
        DayProfile d7 = DayProfile.forDay(7);
        helper.assertTrue(d7.constantHunger() && d7.poolChanceDoubled() && d7.actionbarKey() != null,
                "Day 7 adds constant Hunger and doubles the pool");
        // Day 8: sun Mining Fatigue + Blindness threshold.
        DayProfile d8 = DayProfile.forDay(8);
        helper.assertTrue(d8.sunMiningFatigue() && d8.sunThreshold() == DayProfile.SunThreshold.BLINDNESS,
                "Day 8 adds sun Mining Fatigue and the Blindness threshold");
        // Day 9: no new symptom systems beyond day 8's; still a mole day; threshold unchanged.
        DayProfile d9 = DayProfile.forDay(9);
        helper.assertTrue(d9.sunThreshold() == DayProfile.SunThreshold.BLINDNESS && d9.rollsMole(),
                "Day 9 keeps day-8 sun behaviour");
        // Day 10: threshold escalates to Blindness+Wither. Its message is the Wither warning, now fired on
        // Wither (re-)application (Phase 6 A1), so the day-start actionbarKey is null here.
        DayProfile d10 = DayProfile.forDay(10);
        helper.assertTrue(d10.sunThreshold() == DayProfile.SunThreshold.BLINDNESS_AND_WITHER
                && d10.actionbarKey() == null, "Day 10 adds Wither (message moved to Wither application)");
        helper.succeed();
    }

    // Mole growth is bounded by each mole's own cap and freezes there (independent of day 10).
    @GameTest(template = "empty_3x3", batch = "infection")
    public static void mole_growth_caps_and_freezes(GameTestHelper helper) {
        float cap = 3.0F;
        float size = 1.0F;
        // One step grows by increment * factor (0.15 * 2.0 = 0.30).
        float afterOne = InfectionSymptoms.grownSize(size, cap, 0.15D, 2.0D);
        helper.assertTrue(Math.abs(afterOne - 1.30F) < 1.0e-4F, "One growth step adds increment*factor");
        // Many steps must never exceed the cap and must reach it.
        for (int i = 0; i < 100; i++) {
            size = InfectionSymptoms.grownSize(size, cap, 0.15D, 2.0D);
            helper.assertTrue(size <= cap + 1.0e-4F, "Mole never grows past its cap");
        }
        helper.assertTrue(Math.abs(size - cap) < 1.0e-4F, "Mole eventually reaches its cap");
        // At the cap it is frozen: further growth is a no-op.
        helper.assertTrue(InfectionSymptoms.grownSize(cap, cap, 0.15D, 2.0D) == cap, "At cap the mole is frozen");
        helper.succeed();
    }

    // The hard requirement: the infection transition clears exactly the symptom effects (incl. constant
    // Hunger) and nothing unrelated.
    @GameTest(template = "empty_3x3", batch = "infection")
    public static void transition_clears_symptom_effects(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 600, 0));
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 600, 2));
        player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 600, 0));
        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 600, 0));
        player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 600, 0));
        player.addEffect(new MobEffectInstance(MobEffects.WITHER, 600, 0));
        // An unrelated effect that must survive.
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 600, 0));

        InfectionSymptoms.endSymptomsForTest(player);

        helper.assertFalse(player.hasEffect(MobEffects.CONFUSION), "Nausea cleared at transition");
        helper.assertFalse(player.hasEffect(MobEffects.WEAKNESS), "Weakness cleared at transition");
        helper.assertFalse(player.hasEffect(MobEffects.DIG_SLOWDOWN), "Mining Fatigue cleared at transition");
        helper.assertFalse(player.hasEffect(MobEffects.BLINDNESS), "Blindness cleared at transition");
        helper.assertFalse(player.hasEffect(MobEffects.HUNGER), "Constant Hunger cleared at transition");
        helper.assertFalse(player.hasEffect(MobEffects.WITHER), "Wither cleared at transition");
        helper.assertTrue(player.hasEffect(MobEffects.MOVEMENT_SPEED), "Unrelated effects are left intact");
        helper.succeed();
    }
}
