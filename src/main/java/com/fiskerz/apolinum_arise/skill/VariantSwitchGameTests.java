package com.fiskerz.apolinum_arise.skill;

import java.util.List;

import com.fiskerz.apolinum_arise.Apolinumarise;
import com.fiskerz.apolinum_arise.ability.AbilityAttachments;
import com.fiskerz.apolinum_arise.ability.AbilityData;
import com.fiskerz.apolinum_arise.ability.AbilityManager;
import com.fiskerz.apolinum_arise.ability.diggingin.BurrowAttachments;
import com.fiskerz.apolinum_arise.ability.diggingin.BurrowData;
import com.fiskerz.apolinum_arise.ability.diggingin.DiggingIn;
import com.fiskerz.apolinum_arise.ability.hivemind.HiveMind;
import com.fiskerz.apolinum_arise.ability.strongpunch.StrongPunch;
import com.fiskerz.apolinum_arise.dream.DreamAttachments;
import com.fiskerz.apolinum_arise.dream.DreamData;
import com.fiskerz.apolinum_arise.dream.DreamManager;
import com.fiskerz.apolinum_arise.infection.InfectionAttachments;
import com.fiskerz.apolinum_arise.infection.InfectionData;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The admin variant switch ({@code /apolinumskills setvariant}) and the rule it depends on: an ability
 * only works while its owner is still the kind of player it belongs to.
 *
 * <p>Driven with mock players, so the quest-gate half (server players only) is not exercised here - the
 * gate reset and open reuse the same {@code QuestGates} calls the Phase 11 quest tests already cover.
 */
@GameTestHolder(Apolinumarise.MODID)
@PrefixGameTestTemplate(false)
public class VariantSwitchGameTests {

    /** An infected player on {@code variant} with nothing queued, watched or unlocked. */
    private static Player infectedOn(GameTestHelper helper, int variant) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setData(InfectionAttachments.INFECTION, InfectionData.NONE.becomeInfected());
        player.setData(SkillAttachments.SKILL_PROFILE, SkillProfileData.NONE.withInfectedVariant(variant));
        player.setData(DreamAttachments.DREAMS, DreamData.EMPTY);
        player.setData(AbilityAttachments.ABILITIES, AbilityData.NONE);
        player.setData(BurrowAttachments.BURROW, BurrowData.NORMAL);
        return player;
    }

    /** What the real roll leaves queued for a variant that has just landed. */
    private static void queueAsIfRolled(Player player, int variant) {
        for (String dreamId : InfectedVariants.dreamsFor(variant)) {
            DreamManager.queueDream(player, dreamId);
        }
    }

    private static List<String> queue(Player player) {
        return player.getData(DreamAttachments.DREAMS).queue();
    }

    // The main path: the old variant's unplayed dreams come back out, the new variant's go in, and abilities
    // earned on the old variant go dormant rather than being revoked - and wake up again on switching back.
    @GameTest(template = "empty_3x3", batch = "variant_switch")
    public static void switching_moves_the_dreams_and_leaves_old_abilities_dormant(GameTestHelper helper) {
        Player player = infectedOn(helper, HiveMind.VARIANT);
        queueAsIfRolled(player, HiveMind.VARIANT);
        AbilityManager.unlockAbility(player, HiveMind.ABILITY_ID);
        helper.assertTrue(HiveMind.isActiveFor(player), "Earned on its own variant: active");

        InfectedVariants.Switch result = InfectedVariants.switchTo(player, StrongPunch.VARIANT);
        helper.assertTrue(result.changed(), "A different variant is a change");
        helper.assertValueEqual(result.previous(), HiveMind.VARIANT, "Reports where they came from");
        helper.assertValueEqual(SkillLogic.profile(player).infectedVariant(), StrongPunch.VARIANT,
                "The profile now holds the new variant");

        helper.assertFalse(queue(player).contains(HiveMind.DREAM_ID),
                "The old variant's unplayed prerequisite dream is taken back out, got " + queue(player));
        helper.assertTrue(result.unqueuedDreams().contains(HiveMind.DREAM_ID), "...and reported as such");
        helper.assertTrue(queue(player).contains(StrongPunch.DREAM_ID),
                "The new variant's prerequisite dream is queued, got " + queue(player));
        helper.assertValueEqual(result.queuedDreams(), InfectedVariants.dreamsFor(StrongPunch.VARIANT),
                "Exactly what a real roll onto that variant queues");
        helper.assertTrue(result.gate() == null, "A mock player has no quest progress, so no gate was tried");

        helper.assertTrue(AbilityManager.hasAbility(player, HiveMind.ABILITY_ID),
                "The old variant's ability is NOT revoked...");
        helper.assertFalse(HiveMind.isActiveFor(player), "...but it is dormant: wrong variant now");

        AbilityManager.unlockAbility(player, StrongPunch.ABILITY_ID);
        helper.assertTrue(StrongPunch.isActiveFor(player), "The new variant's ability works once earned");

        InfectedVariants.switchTo(player, HiveMind.VARIANT);
        helper.assertTrue(HiveMind.isActiveFor(player), "Switching back wakes the dormant ability up");
        helper.assertFalse(StrongPunch.isActiveFor(player), "...and puts the other one to sleep");
        helper.assertFalse(queue(player).contains(StrongPunch.DREAM_ID),
                "The variant just left has its unplayed dream taken back too, got " + queue(player));
        helper.succeed();
    }

    // Switching to the variant already held must be a true no-op - not a second copy of every dream - and
    // an out-of-range index must never reach the profile.
    @GameTest(template = "empty_3x3", batch = "variant_switch")
    public static void switching_to_the_held_variant_changes_nothing(GameTestHelper helper) {
        Player player = infectedOn(helper, DiggingIn.VARIANT);
        queueAsIfRolled(player, DiggingIn.VARIANT);
        List<String> before = queue(player);

        InfectedVariants.Switch result = InfectedVariants.switchTo(player, DiggingIn.VARIANT);
        helper.assertFalse(result.changed(), "Same variant: nothing changed");
        helper.assertValueEqual(queue(player), before, "No dream queued twice");

        for (int bad : new int[] {-1, InfectedVariants.COUNT}) {
            try {
                InfectedVariants.switchTo(player, bad);
                helper.fail("Variant " + bad + " must be refused");
            } catch (IllegalArgumentException expected) {
                // the command's argument bounds stop this first; the method refuses it anyway
            }
        }
        helper.assertValueEqual(SkillLogic.profile(player).infectedVariant(), DiggingIn.VARIANT,
                "A refused index leaves the profile alone");
        helper.succeed();
    }

    // A player with no variant yet gets exactly what the roll would give them, and the next Blood Moon's
    // assignment respects it instead of rolling over the top.
    @GameTest(template = "empty_3x3", batch = "variant_switch")
    public static void a_first_variant_by_switch_lands_like_a_roll(GameTestHelper helper) {
        Player player = infectedOn(helper, SkillProfileData.UNASSIGNED);

        InfectedVariants.Switch result = InfectedVariants.switchTo(player, StrongPunch.VARIANT);
        helper.assertTrue(result.changed(), "Unassigned -> a variant is a change");
        helper.assertTrue(result.unqueuedDreams().isEmpty(), "No old variant, so nothing to take back");
        helper.assertValueEqual(queue(player), InfectedVariants.dreamsFor(StrongPunch.VARIANT),
                "Queued exactly what the roll would have");

        helper.assertValueEqual(InfectedVariants.assignOnAccessGranted(player), StrongPunch.VARIANT,
                "The Blood Moon assignment keeps the switched variant");
        helper.assertValueEqual(queue(player), InfectedVariants.dreamsFor(StrongPunch.VARIANT),
                "...and does not queue its dreams a second time");
        helper.succeed();
    }

    // The rule underneath: earned is not the same as usable. A cure or a variant switch makes an ability
    // dormant, which is what every behaviour hook - including the burrow tick's eject - keys on.
    @GameTest(template = "empty_3x3", batch = "variant_switch")
    public static void an_ability_goes_dormant_when_its_owner_stops_qualifying(GameTestHelper helper) {
        Player puncher = infectedOn(helper, StrongPunch.VARIANT);
        AbilityManager.unlockAbility(puncher, StrongPunch.ABILITY_ID);
        helper.assertTrue(StrongPunch.isActiveFor(puncher), "Infected, right variant, earned: active");

        puncher.setData(InfectionAttachments.INFECTION, InfectionData.NONE);
        helper.assertFalse(StrongPunch.isActiveFor(puncher), "Cured: dormant");
        helper.assertTrue(AbilityManager.hasAbility(puncher, StrongPunch.ABILITY_ID), "...but still earned");
        puncher.setData(InfectionAttachments.INFECTION, InfectionData.NONE.becomeInfected());
        helper.assertTrue(StrongPunch.isActiveFor(puncher), "Re-infected on the same variant: active again");

        // A burrowed variant-2 player switched away: Digging In stops applying, which is exactly the
        // condition the server's burrow tick force-surfaces on - nobody is left inside rock.
        Player digger = infectedOn(helper, DiggingIn.VARIANT);
        AbilityManager.unlockAbility(digger, DiggingIn.ABILITY_ID);
        digger.setData(BurrowAttachments.BURROW, BurrowData.NORMAL.burrowedAt(0L, 0L));
        helper.assertTrue(DiggingIn.isActiveFor(digger), "Burrowed and active before the switch");
        InfectedVariants.switchTo(digger, HiveMind.VARIANT);
        helper.assertFalse(DiggingIn.isActiveFor(digger),
                "After the switch Digging In is inactive, so the next server tick ejects them");
        helper.succeed();
    }
}
