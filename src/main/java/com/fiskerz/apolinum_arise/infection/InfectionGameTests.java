package com.fiskerz.apolinum_arise.infection;

import com.fiskerz.apolinum_arise.Apolinumarise;
import com.fiskerz.apolinum_arise.config.Config;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Apolinumarise.MODID)
@PrefixGameTestTemplate(false)
public class InfectionGameTests {

    // Patch 2 item 2: the clean -> incubating -> infected state machine, plus the "no re-roll" guard.
    // Own batch because it mutates the shared level day time. Uses a mock Player; attachments work on
    // any Entity, and onBite/promoteIfDue are Player-based for exactly this reason.
    @GameTest(template = "empty_3x3", batch = "infection")
    public static void infection_state_machine(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        int incubation = Config.INFECTION_INCUBATION_DAYS.get();

        // Clean start.
        player.setData(InfectionAttachments.INFECTION, InfectionData.NONE);
        helper.assertFalse(player.getData(InfectionAttachments.INFECTION).incubating(), "clean: not incubating");
        helper.assertFalse(player.getData(InfectionAttachments.INFECTION).infected(), "clean: not infected");

        // A bite can roll incubation (chance 0.10). Reset to clean each try; ~1-0.9^250 is a certainty.
        boolean everIncubated = false;
        for (int i = 0; i < 250 && !everIncubated; i++) {
            player.setData(InfectionAttachments.INFECTION, InfectionData.NONE);
            InfectionLogic.onBite(player, level);
            everIncubated = player.getData(InfectionAttachments.INFECTION).incubating();
        }
        helper.assertTrue(everIncubated, "A bite must be able to roll incubation");

        // Deterministic transition: start incubating on day 5.
        int startDay = 5;
        player.setData(InfectionAttachments.INFECTION, InfectionData.NONE.beginIncubating(startDay));

        // No re-roll while incubating: a bite must not change the recorded start day or flip anything.
        InfectionLogic.onBite(player, level);
        helper.assertTrue(player.getData(InfectionAttachments.INFECTION).incubating()
                        && player.getData(InfectionAttachments.INFECTION).infectionStartDay() == startDay,
                "Bite while incubating must not re-roll");

        // Before the window elapses: stays incubating.
        InfectionLogic.promoteIfDue(player, startDay + incubation - 1);
        helper.assertTrue(player.getData(InfectionAttachments.INFECTION).incubating(), "still incubating before window");
        helper.assertFalse(player.getData(InfectionAttachments.INFECTION).infected(), "not infected before window");

        // At the window: becomes infected, no longer incubating.
        InfectionLogic.promoteIfDue(player, startDay + incubation);
        helper.assertFalse(player.getData(InfectionAttachments.INFECTION).incubating(), "no longer incubating");
        helper.assertTrue(player.getData(InfectionAttachments.INFECTION).infected(), "now infected");

        // No re-roll while infected.
        InfectionLogic.onBite(player, level);
        helper.assertTrue(player.getData(InfectionAttachments.INFECTION).infected()
                        && !player.getData(InfectionAttachments.INFECTION).incubating(),
                "Bite while infected must not change state");

        Apolinumarise.LOGGER.info("Infection state machine verified (incubation window = {} days).", incubation);
        helper.succeed();
    }
}
