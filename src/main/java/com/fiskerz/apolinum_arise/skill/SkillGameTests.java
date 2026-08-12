package com.fiskerz.apolinum_arise.skill;

import com.fiskerz.apolinum_arise.Apolinumarise;
import com.fiskerz.apolinum_arise.bloodmoon.ShrineEffects;
import com.fiskerz.apolinum_arise.config.Config;
import com.fiskerz.apolinum_arise.infection.InfectionAttachments;
import com.fiskerz.apolinum_arise.infection.InfectionData;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Apolinumarise.MODID)
@PrefixGameTestTemplate(false)
public class SkillGameTests {

    // Item 1 + 5: the global one-way unlock latch flips exactly when the cumulative completion count
    // reaches the threshold, never re-locks, and the isIncubating->isInfected hook clears healthy-side
    // access (mutual exclusivity). Own batch: it mutates the shared global HealthySkillState.
    @GameTest(template = "empty_3x3", batch = "skill_global")
    public static void healthy_unlock_threshold_and_exclusivity(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        HealthySkillState.resetForTest(level);
        int threshold = Config.HEALTHY_UNLOCK_INFECTED_THRESHOLD.get();

        for (int i = 1; i < threshold; i++) {
            boolean flipped = HealthySkillState.recordIncubationCompletion(level);
            helper.assertFalse(flipped, "Should not unlock before the threshold (completion " + i + ")");
            helper.assertFalse(HealthySkillState.isUnlocked(level), "Still locked before the threshold");
        }
        helper.assertTrue(HealthySkillState.recordIncubationCompletion(level), "Threshold completion must flip the latch");
        helper.assertTrue(HealthySkillState.isUnlocked(level), "Unlocked at the threshold");
        // One-way: further completions never re-flip, and it stays unlocked.
        helper.assertFalse(HealthySkillState.recordIncubationCompletion(level), "No re-flip after already unlocked");
        helper.assertTrue(HealthySkillState.isUnlocked(level), "Stays unlocked (one-way latch)");

        // Mutual exclusivity through the real hook: a healthy-access player who completes incubation loses it.
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setData(SkillAttachments.SKILL_ACCESS, SkillAccessData.NONE.grantHealthy());
        helper.assertTrue(SkillLogic.hasHealthyAccess(player), "Player starts with healthy access");
        SkillLogic.onIncubationComplete(player);
        helper.assertFalse(SkillLogic.hasHealthyAccess(player), "Healthy access cleared on becoming infected");

        HealthySkillState.resetForTest(level);
        helper.succeed();
    }

    // Item 2/4/5: per-player access primitives - grant idempotency, mutual exclusivity in the data,
    // and the cure stub clearing ONLY the infected side. Mock-only, no global state touched.
    @GameTest(template = "empty_3x3", batch = "skill_access")
    public static void access_grants_exclusive_and_cure_stub(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setData(SkillAttachments.SKILL_ACCESS, SkillAccessData.NONE);

        helper.assertTrue(SkillLogic.grantHealthyAccess(player), "Healthy grant succeeds first time");
        helper.assertTrue(SkillLogic.hasHealthyAccess(player) && SkillLogic.hasAnyAccess(player), "Has healthy access");
        helper.assertFalse(SkillLogic.grantHealthyAccess(player), "Second healthy grant is a no-op");

        // Granting infected is mutually exclusive: it clears healthy at the same time.
        helper.assertTrue(SkillLogic.grantInfectedAccess(player), "Infected grant succeeds");
        helper.assertTrue(SkillLogic.hasInfectedAccess(player), "Has infected access");
        helper.assertFalse(SkillLogic.hasHealthyAccess(player), "Infected grant cleared healthy (mutual exclusivity)");

        // Cure stub clears infected only; a cured player does NOT regain healthy.
        SkillLogic.onPlayerCured(player);
        helper.assertFalse(SkillLogic.hasInfectedAccess(player), "Cure cleared infected access");
        helper.assertFalse(SkillLogic.hasHealthyAccess(player), "Cure does NOT restore healthy access");
        helper.assertFalse(SkillLogic.hasAnyAccess(player), "Cured player has neither side");
        helper.succeed();
    }

    // Item 4: becoming infected must NOT grant infected-side access - that only happens at the next
    // Blood Moon start (grantInfectedAccess, the exact action onBloodMoonStart performs per infected).
    @GameTest(template = "empty_3x3", batch = "skill_bloodmoon")
    public static void infected_access_waits_for_blood_moon(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setData(SkillAttachments.SKILL_ACCESS, SkillAccessData.NONE);
        player.setData(InfectionAttachments.INFECTION, InfectionData.NONE.beginIncubating(0));

        // Simulate the incubation->infection transition. Infected-side access must NOT be granted here.
        SkillLogic.onIncubationComplete(player);
        player.setData(InfectionAttachments.INFECTION, InfectionData.NONE.becomeInfected());
        helper.assertFalse(SkillLogic.hasInfectedAccess(player),
                "Becoming infected must not grant infected-side access (it waits for the next Blood Moon)");

        // The Blood Moon start hook's per-infected action then grants it.
        helper.assertTrue(SkillLogic.grantInfectedAccess(player), "Blood Moon start grants infected-side access");
        helper.assertTrue(SkillLogic.hasInfectedAccess(player), "Infected-side access now held");

        HealthySkillState.resetForTest(helper.getLevel());
        helper.succeed();
    }

    // Item 3: the two components of shrine book placement - (a) the skill book is accepted into an empty
    // lectern via the vanilla tag-gated API, and (b) the shrine-gating correctly rejects a lectern that
    // is not inside a shrine. The full integration (a real shrine containing a lectern) needs shipped
    // shrine content, which isn't part of this task.
    @GameTest(template = "empty_3x3", batch = "skill_lectern")
    public static void book_inserts_into_lectern_with_shrine_gating(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos rel = new BlockPos(1, 1, 1);
        helper.setBlock(rel, Blocks.LECTERN);
        BlockPos abs = helper.absolutePos(rel);

        // (b) gating: a lectern outside any generated shrine is never a placement target.
        helper.assertFalse(ShrineEffects.isWithinShrine(level, abs, 0), "A lectern outside any shrine must not be targeted");

        // (a) insertion + tag acceptance.
        BlockState state = level.getBlockState(abs);
        helper.assertFalse(state.getValue(LecternBlock.HAS_BOOK), "Lectern starts empty");
        boolean placed = LecternBlock.tryPlaceBook(null, level, abs, state, new ItemStack(SkillRegistry.SKILL_BOOK.get()));
        helper.assertTrue(placed, "tryPlaceBook accepts the skill book into an empty lectern");
        helper.assertTrue(level.getBlockState(abs).getValue(LecternBlock.HAS_BOOK), "Lectern now HAS_BOOK");
        helper.assertTrue(level.getBlockEntity(abs) instanceof LecternBlockEntity lectern
                        && lectern.getBook().is(SkillRegistry.SKILL_BOOK.get()),
                "The inserted book is the skill book");
        helper.succeed();
    }
}
