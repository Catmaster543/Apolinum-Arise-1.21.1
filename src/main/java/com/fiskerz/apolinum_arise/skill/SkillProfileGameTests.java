package com.fiskerz.apolinum_arise.skill;

import java.util.List;

import com.fiskerz.apolinum_arise.Apolinumarise;
import com.fiskerz.apolinum_arise.config.Config;
import com.fiskerz.apolinum_arise.dream.DreamAttachments;
import com.fiskerz.apolinum_arise.dream.DreamData;
import com.fiskerz.apolinum_arise.infection.InfectionAttachments;
import com.fiskerz.apolinum_arise.infection.InfectionData;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Phase 11 items 2-4: the two one-shot assignment moments and the one-shot branch choice.
 */
@GameTestHolder(Apolinumarise.MODID)
@PrefixGameTestTemplate(false)
public class SkillProfileGameTests {

    // Item 2: granting infected-side access - the exact thing onBloodMoonStart does per infected player -
    // must assign a variant, queue that variant's reveal dream, and never re-roll on a repeat grant.
    @GameTest(template = "empty_3x3", batch = "skill_profile")
    public static void blood_moon_access_assigns_a_variant_and_queues_its_dream(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setData(SkillAttachments.SKILL_ACCESS, SkillAccessData.NONE);
        player.setData(SkillAttachments.SKILL_PROFILE, SkillProfileData.NONE);
        player.setData(DreamAttachments.DREAMS, DreamData.EMPTY);
        player.setData(InfectionAttachments.INFECTION, InfectionData.NONE.becomeInfected());

        helper.assertFalse(SkillLogic.profile(player).hasInfectedVariant(),
                "An infected player has no variant until the Blood Moon grants access");

        helper.assertTrue(SkillLogic.grantInfectedAccess(player), "Blood Moon start grants infected-side access");
        SkillProfileData assigned = SkillLogic.profile(player);
        helper.assertTrue(assigned.hasInfectedVariant(), "Access granted must assign a variant at the same moment");
        helper.assertTrue(InfectedVariants.isValidIndex(assigned.infectedVariant()),
                "Variant must be 0..2, got " + assigned.infectedVariant());

        // The reveal dream for exactly that variant, queued at the same instant.
        String expectedDream = Config.getIndexed(Config.INFECTED_VARIANT_DREAM_IDS, assigned.infectedVariant());
        List<String> queue = player.getData(DreamAttachments.DREAMS).queue();
        helper.assertValueEqual(queue.size(), 1, "Exactly one dream queued");
        helper.assertValueEqual(queue.get(0), expectedDream, "The queued dream is this variant's reveal dream");

        // A repeat grant is a no-op, and even calling the assignment directly must not re-roll.
        helper.assertFalse(SkillLogic.grantInfectedAccess(player), "Second grant is a no-op");
        InfectedVariants.assignOnAccessGranted(player);
        helper.assertValueEqual(SkillLogic.profile(player).infectedVariant(), assigned.infectedVariant(),
                "The variant is permanent - never re-rolled");
        helper.assertValueEqual(player.getData(DreamAttachments.DREAMS).queue().size(), 1,
                "The reveal dream is queued exactly once");
        helper.succeed();
    }

    // Item 2: the weighted roll actually honours the weights - a zero-weighted variant is never picked,
    // and every reachable variant is a valid index. Uses the live config, so this also proves the shipped
    // default (equal weights) produces a usable spread rather than always returning the same variant.
    @GameTest(template = "empty_3x3", batch = "skill_profile")
    public static void variant_roll_is_weighted_and_always_in_range(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        boolean[] seen = new boolean[InfectedVariants.COUNT];
        for (int i = 0; i < 600; i++) {
            int variant = InfectedVariants.roll(player.getRandom());
            helper.assertTrue(InfectedVariants.isValidIndex(variant), "Rolled out of range: " + variant);
            seen[variant] = true;
        }
        // With the default weights all three must show up across 600 rolls; a weight of 0 would exclude one.
        List<? extends Double> weights = Config.INFECTED_VARIANT_WEIGHTS.get();
        for (int i = 0; i < InfectedVariants.COUNT; i++) {
            boolean enabled = i < weights.size() && weights.get(i) > 0.0D;
            if (enabled) {
                helper.assertTrue(seen[i], "Variant " + i + " has a positive weight but never came up in 600 rolls");
            } else {
                helper.assertFalse(seen[i], "Variant " + i + " has weight 0 but was still rolled");
            }
        }
        helper.succeed();
    }

    // Item 3: the book grant is where stats are rolled - once, independently, inside the configured range.
    @GameTest(template = "empty_3x3", batch = "skill_profile")
    public static void book_grant_rolls_three_stats_once(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setData(SkillAttachments.SKILL_ACCESS, SkillAccessData.NONE);
        player.setData(SkillAttachments.SKILL_PROFILE, SkillProfileData.NONE);

        helper.assertFalse(SkillLogic.profile(player).statsAssigned(), "No stats before the book is used");
        helper.assertTrue(SkillLogic.grantHealthyAccess(player), "Book grants healthy-side access");

        SkillProfileData rolled = SkillLogic.profile(player);
        helper.assertTrue(rolled.statsAssigned(), "The grant rolls stats at the same moment");
        int min = Config.HEALTHY_STAT_MIN.get();
        int max = Math.max(min, Config.HEALTHY_STAT_MAX.get());
        for (HealthyStat stat : HealthyStat.values()) {
            int value = rolled.stat(stat);
            helper.assertTrue(value >= min && value <= max,
                    stat + " rolled " + value + ", outside the configured " + min + ".." + max);
        }

        // Re-running the assignment must not re-roll: the numbers are permanent.
        HealthyStats.assignOnAccessGranted(player);
        SkillProfileData after = SkillLogic.profile(player);
        helper.assertValueEqual(after.intelligence(), rolled.intelligence(), "Intelligence is permanent");
        helper.assertValueEqual(after.strength(), rolled.strength(), "Strength is permanent");
        helper.assertValueEqual(after.creativity(), rolled.creativity(), "Creativity is permanent");
        helper.succeed();
    }

    // Item 4: the branch choice is one-time, server-validated, and only open to healthy-side players.
    @GameTest(template = "empty_3x3", batch = "skill_profile")
    public static void branch_choice_is_validated_and_permanent(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setData(SkillAttachments.SKILL_ACCESS, SkillAccessData.NONE);
        player.setData(SkillAttachments.SKILL_PROFILE, SkillProfileData.NONE);

        // No access: nothing to choose, and nothing is stored if something tries.
        helper.assertFalse(SkillLogic.needsBranchChoice(player), "A player without access is not owed a choice");
        helper.assertFalse(SkillLogic.chooseHealthyBranch(player, 0), "A player without access cannot choose");
        helper.assertFalse(SkillLogic.profile(player).hasHealthyBranch(), "Nothing was stored");

        SkillLogic.grantHealthyAccess(player);
        helper.assertTrue(SkillLogic.needsBranchChoice(player),
                "A fresh healthy player owes us the one-time branch choice");

        // Out of range is rejected on both ends without consuming the choice.
        helper.assertFalse(SkillLogic.chooseHealthyBranch(player, -1), "Negative index rejected");
        helper.assertFalse(SkillLogic.chooseHealthyBranch(player, HealthyBranches.COUNT), "Index past the end rejected");
        helper.assertTrue(SkillLogic.needsBranchChoice(player), "A rejected choice does not consume the choice");

        helper.assertTrue(SkillLogic.chooseHealthyBranch(player, 2), "A valid choice is accepted");
        helper.assertValueEqual(SkillLogic.profile(player).healthyBranch(), 2, "The chosen branch is stored");
        helper.assertFalse(SkillLogic.needsBranchChoice(player),
                "Once chosen, the screen is skipped forever - future opens go straight to the quest book");

        // Permanent: a second choice changes nothing.
        helper.assertFalse(SkillLogic.chooseHealthyBranch(player, 0), "The choice cannot be re-made");
        helper.assertValueEqual(SkillLogic.profile(player).healthyBranch(), 2, "Still the original branch");
        helper.succeed();
    }

    // Item 4: every branch's advisory annotation parses out of the shipped config, so the choice screen
    // always has something real to show. A malformed entry would silently degrade to "no preference".
    @GameTest(template = "empty_3x3", batch = "skill_profile")
    public static void every_branch_has_two_parsed_stat_preferences(GameTestHelper helper) {
        for (int branch = 0; branch < HealthyBranches.COUNT; branch++) {
            List<HealthyBranches.StatPreference> preferences = HealthyBranches.preferences(branch);
            helper.assertValueEqual(preferences.size(), 2,
                    "Branch " + branch + " must annotate exactly two stats, got " + preferences.size()
                            + " from '" + Config.getIndexed(Config.HEALTHY_BRANCH_STAT_PREFERENCES, branch) + "'");
            helper.assertTrue(preferences.get(0).stat() != preferences.get(1).stat(),
                    "Branch " + branch + " names the same stat twice");
        }
        helper.succeed();
    }

    // The profile survives a round trip through the sync codec - it is what the client reads to decide
    // between the choice screen and the quest book, so a mis-ordered field would route players wrongly.
    @GameTest(template = "empty_3x3", batch = "skill_profile")
    public static void profile_stream_codec_round_trips(GameTestHelper helper) {
        SkillProfileData original = new SkillProfileData(2, true, 7, 3, 9, 1);
        io.netty.buffer.ByteBuf buffer = io.netty.buffer.Unpooled.buffer();
        SkillProfileData.STREAM_CODEC.encode(buffer, original);
        SkillProfileData decoded = SkillProfileData.STREAM_CODEC.decode(buffer);
        helper.assertValueEqual(decoded, original, "Encoded and decoded profiles must match");
        helper.succeed();
    }
}
