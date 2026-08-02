package com.fiskerz.apolinum_arise.downed;

import com.fiskerz.apolinum_arise.Apolinumarise;
import com.fiskerz.apolinum_arise.config.Config;
import com.fiskerz.apolinum_arise.infection.InfectionAttachments;
import com.fiskerz.apolinum_arise.infection.InfectionData;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Apolinumarise.MODID)
@PrefixGameTestTemplate(false)
public class DownedGameTests {

    // Revive eligibility (both directions): healthy or incubating yes, fully infected no.
    @GameTest(template = "empty_3x3", batch = "downed")
    public static void downed_eligibility(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        player.setData(InfectionAttachments.INFECTION, InfectionData.NONE);
        helper.assertTrue(DownedManager.isEligible(player), "healthy player is eligible");

        player.setData(InfectionAttachments.INFECTION, InfectionData.NONE.beginIncubating(0));
        helper.assertTrue(DownedManager.isEligible(player), "incubating player is eligible");

        player.setData(InfectionAttachments.INFECTION, new InfectionData(false, 0, true, 0.0F));
        helper.assertFalse(DownedManager.isEligible(player), "fully infected player is NOT eligible");
        helper.succeed();
    }

    // Death interception: a would-be death enters the downed state at the health floor instead, and a
    // second attempt while already downed is refused (so death can proceed at timeout).
    @GameTest(template = "empty_3x3", batch = "downed")
    public static void death_intercepts_into_downed(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setData(DownedAttachments.DOWNED, DownedData.NONE);

        boolean consumed = DownedManager.tryEnterDowned(player);
        helper.assertTrue(consumed, "a would-be death is consumed into downed");
        helper.assertTrue(DownedManager.isDowned(player), "player is now downed");
        float floor = (float) (double) Config.DOWNED_HEALTH_FLOOR.get();
        helper.assertTrue(player.getHealth() == floor, "downed player is pinned to the health floor");

        helper.assertFalse(DownedManager.tryEnterDowned(player), "already-downed player does not re-enter downed");
        helper.succeed();
    }

    // Revive: clears the downed state and restores exactly the configured revive health.
    @GameTest(template = "empty_3x3", batch = "downed")
    public static void revive_restores_health_and_clears_state(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setData(InfectionAttachments.INFECTION, InfectionData.NONE);
        DownedManager.tryEnterDowned(player);
        helper.assertTrue(DownedManager.isDowned(player), "downed before revive");

        DownedManager.revive(player);
        helper.assertFalse(DownedManager.isDowned(player), "no longer downed after revive");
        float reviveHealth = (float) (double) Config.REVIVE_HEALTH_ON_REVIVE.get();
        helper.assertTrue(player.getHealth() == reviveHealth, "revived to the configured health (one heart)");
        helper.succeed();
    }

    // A downed player is untargetable: a hostile's target change to them is redirected to null.
    @GameTest(template = "empty_3x3", batch = "downed")
    public static void downed_player_is_untargetable(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setData(InfectionAttachments.INFECTION, InfectionData.NONE);
        DownedManager.tryEnterDowned(player);
        helper.assertTrue(DownedManager.isDowned(player), "player is downed");

        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
        LivingChangeTargetEvent event = new LivingChangeTargetEvent(zombie, player, LivingChangeTargetEvent.LivingTargetType.MOB_TARGET);
        DownedEvents.onLivingChangeTarget(event);
        helper.assertTrue(event.getNewAboutToBeSetTarget() == null, "a downed player's would-be attacker is nulled");
        helper.succeed();
    }
}
