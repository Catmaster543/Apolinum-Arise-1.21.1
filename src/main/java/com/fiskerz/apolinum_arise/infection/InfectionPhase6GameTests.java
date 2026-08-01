package com.fiskerz.apolinum_arise.infection;

import com.fiskerz.apolinum_arise.Apolinumarise;
import com.fiskerz.apolinum_arise.bloodmoon.BloodMoonRegistry;
import com.fiskerz.apolinum_arise.config.Config;
import com.fiskerz.apolinum_arise.infection.inventory.InertSlot;
import com.fiskerz.apolinum_arise.infection.inventory.RestrictedInventoryMenu;
import com.fiskerz.apolinum_arise.mosquito.MosquitoEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Apolinumarise.MODID)
@PrefixGameTestTemplate(false)
public class InfectionPhase6GameTests {

    // B2: the restricted menu must disable exactly the right slots server-side (mayPlace/mayPickup),
    // not merely visually. Backed by the real player inventory via a mock player.
    @GameTest(template = "empty_3x3", batch = "infection")
    public static void restricted_inventory_disables_correct_slots(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        RestrictedInventoryMenu menu = new RestrictedInventoryMenu(1, player.getInventory());

        // Inert: craft result+grid (0-4), helmet (5), boots (8), top two main rows (9-26).
        int[] inert = {0, 1, 2, 3, 4, 5, 8,
                9, 10, 11, 12, 13, 14, 15, 16, 17,
                18, 19, 20, 21, 22, 23, 24, 25, 26};
        for (int i : inert) {
            helper.assertTrue(menu.slots.get(i) instanceof InertSlot, "menu slot " + i + " must be inert");
            helper.assertFalse(menu.slots.get(i).mayPlace(new ItemStack(Items.STONE)), "inert slot " + i + " rejects place");
            helper.assertFalse(menu.slots.get(i).mayPickup(player), "inert slot " + i + " rejects pickup");
        }
        // Usable: chest (6), legs (7), closest main row (27-35), hotbar (36-44), offhand (45).
        int[] usable = {6, 7, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45};
        for (int i : usable) {
            helper.assertFalse(menu.slots.get(i) instanceof InertSlot, "menu slot " + i + " must be usable");
        }
        // Usable armor still type-restricts like vanilla.
        helper.assertTrue(menu.slots.get(6).mayPlace(new ItemStack(Items.DIAMOND_CHESTPLATE)), "chest slot accepts a chestplate");
        helper.assertFalse(menu.slots.get(6).mayPlace(new ItemStack(Items.STONE)), "chest slot rejects non-armor");
        helper.succeed();
    }

    // A2 diagnostics: log the delta at which promoteIfDue flips a player to infected. This exercises the
    // exact boundary logic the natural dusk path uses; the [InfectionDay] lines are the before/after
    // evidence for the day-count fix.
    @GameTest(template = "empty_3x3", batch = "infection")
    public static void incubation_promotion_boundary(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        int incubation = Config.INFECTION_INCUBATION_DAYS.get();
        int startDay = 100;

        Integer firstInfectedDelta = null;
        for (int delta = 0; delta <= incubation + 2; delta++) {
            player.setData(InfectionAttachments.INFECTION, InfectionData.NONE.beginIncubating(startDay));
            InfectionLogic.promoteIfDue(player, startDay + delta);
            boolean infected = player.getData(InfectionAttachments.INFECTION).infected();
            if (infected && firstInfectedDelta == null) {
                firstInfectedDelta = delta;
            }
        }
        Apolinumarise.LOGGER.info("[InfectionDayTest] incubationDays={} firstInfectedDelta={} (dayNumber={})",
                incubation, firstInfectedDelta, firstInfectedDelta == null ? "none" : (firstInfectedDelta + 1));

        // After the A2 fix, incubation completes at "day N moonrise" = the moonrise when the player is on
        // day N == incubationDays, i.e. delta incubationDays-1 (dayNumber == incubationDays).
        helper.assertTrue(firstInfectedDelta != null && firstInfectedDelta == incubation - 1,
                "incubation should complete at delta " + (incubation - 1) + " (day " + incubation + " moonrise), was " + firstInfectedDelta);
        helper.succeed();
    }

    // B4: an infected player is targeting-exempt in all dimensions, and a hostile mob's (or mosquito's)
    // attempt to target them is redirected to null. Verifies the mosquito is covered too (it is an Enemy).
    @GameTest(template = "empty_3x3", batch = "infection")
    public static void infected_players_are_targeting_exempt(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setData(InfectionAttachments.INFECTION, new InfectionData(false, 0, true));
        helper.assertTrue(InfectionSymptoms.enemiesIgnore(player), "an infected player is targeting-exempt");

        // A hostile mob's target change to the infected player is redirected to null by the handler.
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
        LivingChangeTargetEvent zombieEvent = new LivingChangeTargetEvent(zombie, player, LivingChangeTargetEvent.LivingTargetType.MOB_TARGET);
        InfectionEvents.onLivingChangeTarget(zombieEvent);
        helper.assertTrue(zombieEvent.getNewAboutToBeSetTarget() == null, "zombie's target must be nulled");

        // Explicit mosquito check: it is a Monster (implements Enemy), so the same handler covers it.
        MosquitoEntity mosquito = helper.spawn(BloodMoonRegistry.MOSQUITO.get(), new BlockPos(1, 1, 2));
        helper.assertTrue(mosquito instanceof Enemy, "mosquito must be an Enemy so the exemption covers it");
        LivingChangeTargetEvent mosquitoEvent = new LivingChangeTargetEvent(mosquito, player, LivingChangeTargetEvent.LivingTargetType.MOB_TARGET);
        InfectionEvents.onLivingChangeTarget(mosquitoEvent);
        helper.assertTrue(mosquitoEvent.getNewAboutToBeSetTarget() == null, "mosquito's target must be nulled");
        helper.succeed();
    }
}
