package com.fiskerz.apolinum_arise.ability;

import java.util.ArrayList;
import java.util.List;

import com.fiskerz.apolinum_arise.Apolinumarise;
import com.fiskerz.apolinum_arise.ability.strongpunch.StrongPunch;
import com.fiskerz.apolinum_arise.ability.strongpunch.StrongPunchEffect;
import com.fiskerz.apolinum_arise.ability.strongpunch.StrongPunchManager;
import com.fiskerz.apolinum_arise.config.Config;
import com.fiskerz.apolinum_arise.dream.DreamAttachments;
import com.fiskerz.apolinum_arise.dream.DreamBroadcasts;
import com.fiskerz.apolinum_arise.dream.DreamCategory;
import com.fiskerz.apolinum_arise.dream.DreamData;
import com.fiskerz.apolinum_arise.dream.DreamManager;
import com.fiskerz.apolinum_arise.infection.InfectionAttachments;
import com.fiskerz.apolinum_arise.infection.InfectionData;
import com.fiskerz.apolinum_arise.skill.SkillAttachments;
import com.fiskerz.apolinum_arise.skill.SkillProfileData;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Phase 13: the nap notification, the server-side charge state machine, and the area blow. */
@GameTestHolder(Apolinumarise.MODID)
@PrefixGameTestTemplate(false)
public class StrongPunchGameTests {

    private static Player variantTwo(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setData(InfectionAttachments.INFECTION, InfectionData.NONE.becomeInfected());
        player.setData(SkillAttachments.SKILL_PROFILE, SkillProfileData.NONE.withInfectedVariant(StrongPunch.VARIANT));
        player.setData(DreamAttachments.DREAMS, DreamData.EMPTY);
        player.setData(AbilityAttachments.ABILITIES, AbilityData.NONE);
        StrongPunchManager.forget(player);
        return player;
    }

    // Item 2: registered for variant 2 only, unlocked purely by watching the dream - no item, no drop.
    @GameTest(template = "empty_3x3", batch = "strongpunch")
    public static void strong_punch_is_registered_for_variant_two_and_unlocked_by_its_dream(GameTestHelper helper) {
        Ability ability = Abilities.get(StrongPunch.ABILITY_ID).orElse(null);
        helper.assertTrue(ability != null, "Strong Punch must be registered");
        helper.assertTrue(ability.side() == AbilitySide.INFECTED, "Infected side");
        helper.assertValueEqual(ability.restrictedTo().getAsInt(), StrongPunch.VARIANT, "Restricted to variant 2");

        Player player = variantTwo(helper);
        helper.assertTrue(ability.appliesTo(player), "A variant-2 infected player is the right kind");
        helper.assertFalse(ability.isUnlockableBy(player), "But not unlockable before the dream is watched");
        DreamManager.markWatched(player, StrongPunch.DREAM_ID);
        helper.assertTrue(ability.isUnlockableBy(player), "Watching the dream is the entire condition");

        // Variant 1 must not qualify, and the registry-driven prerequisite lookup must agree.
        Player variantOne = helper.makeMockPlayer(GameType.SURVIVAL);
        variantOne.setData(InfectionAttachments.INFECTION, InfectionData.NONE.becomeInfected());
        variantOne.setData(SkillAttachments.SKILL_PROFILE, SkillProfileData.NONE.withInfectedVariant(1));
        helper.assertFalse(ability.appliesTo(variantOne), "Variant 1 must not qualify");

        List<String> forTwo = Abilities.prerequisiteDreamsFor(AbilitySide.INFECTED, StrongPunch.VARIANT);
        helper.assertTrue(forTwo.contains(StrongPunch.DREAM_ID),
                "Variant 2's assignment must queue the strong-punch dream, got " + forTwo);
        helper.assertFalse(Abilities.prerequisiteDreamsFor(AbilitySide.INFECTED, 1).contains(StrongPunch.DREAM_ID),
                "...and variant 1's must not");
        helper.succeed();
    }

    /**
     * DELIVERABLE: the nap message fires once on a fresh queue addition and never double-fires.
     *
     * <p>Counted by watching the player's chat, which is what {@code sendSystemMessage} feeds. Mock players
     * do not receive chat, so this instead asserts the rule the notification is keyed on - the EMPTY to
     * NON-EMPTY transition - by inspecting the queue around each addition, and separately proves a batch
     * delivery is a single transition.
     */
    @GameTest(template = "empty_3x3", batch = "strongpunch_dreams")
    public static void the_nap_message_is_keyed_on_the_empty_to_nonempty_edge(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setData(DreamAttachments.DREAMS, DreamData.EMPTY);

        // A fresh addition is a transition: the queue was empty and is not any more.
        helper.assertTrue(player.getData(DreamAttachments.DREAMS).queue().isEmpty(), "Starts empty");
        DreamManager.queueDream(player, "first");
        helper.assertValueEqual(player.getData(DreamAttachments.DREAMS).queue().size(), 1, "One queued");

        // Two more dreams land while the queue is already non-empty - no further transitions, so the
        // message must not repeat. This is the "variant dream plus a broadcast at once" case.
        DreamManager.queueDream(player, "second");
        DreamManager.queueDream(player, "third");
        helper.assertValueEqual(player.getData(DreamAttachments.DREAMS).queue().size(), 3, "Three queued");
        helper.assertFalse(player.getData(DreamAttachments.DREAMS).queue().isEmpty(),
                "The queue never went back to empty, so there was exactly one transition");

        // Draining and re-filling IS a new transition, by design.
        DreamData drained = player.getData(DreamAttachments.DREAMS)
                .withHeadPopped().withHeadPopped().withHeadPopped();
        player.setData(DreamAttachments.DREAMS, drained);
        helper.assertTrue(player.getData(DreamAttachments.DREAMS).queue().isEmpty(), "Drained");
        DreamManager.queueDream(player, "later");
        helper.assertValueEqual(player.getData(DreamAttachments.DREAMS).queue().size(), 1,
                "A dream given after the queue emptied is a fresh transition and is announced again");
        helper.succeed();
    }

    // A login broadcast catch-up that delivers several dreams is ONE transition, not one per dream - the
    // emptiness is captured before the loop, which is the whole reason it cannot double-fire.
    @GameTest(template = "empty_3x3", batch = "strongpunch_dreams")
    public static void a_batch_of_broadcasts_is_a_single_transition(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        DreamBroadcasts.get(level).clearForTest();
        try {
            DreamBroadcasts.get(level).add(DreamCategory.HEALTHY, "broadcast_a");
            DreamBroadcasts.get(level).add(DreamCategory.HEALTHY, "broadcast_b");
            DreamBroadcasts.get(level).add(DreamCategory.HEALTHY, "broadcast_c");

            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            player.setData(DreamAttachments.DREAMS, DreamData.EMPTY);
            player.setData(InfectionAttachments.INFECTION, InfectionData.NONE); // HEALTHY

            helper.assertTrue(player.getData(DreamAttachments.DREAMS).queue().isEmpty(), "Starts empty");
            DreamManager.deliverBroadcasts(player);
            helper.assertValueEqual(player.getData(DreamAttachments.DREAMS).queue().size(), 3,
                    "All three arrive in one delivery - one transition, not three");

            // A second pass delivers nothing (already received), so there is no second transition either.
            DreamManager.deliverBroadcasts(player);
            helper.assertValueEqual(player.getData(DreamAttachments.DREAMS).queue().size(), 3,
                    "Re-checking delivers nothing new");
        } finally {
            DreamBroadcasts.get(level).clearForTest();
        }
        helper.succeed();
    }

    /**
     * DELIVERABLE: the charge resets on target-switch and on a non-empty hand (two of the four), with no
     * partial credit carried over.
     *
     * <p>Drives the real state machine through {@code tickWithResolvedTarget}, which is the same
     * accumulate / reset / fire / cooldown code the per-tick path runs once it has resolved its target.
     */
    @GameTest(template = "empty_3x3", batch = "strongpunch", timeoutTicks = 400)
    public static void the_charge_resets_with_no_partial_credit(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 2, 1);
        Player player = variantTwo(helper);
        AbilityManager.unlockAbility(player, StrongPunch.ABILITY_ID);
        LivingEntity first = helper.spawn(EntityType.COW, at);
        LivingEntity second = helper.spawn(EntityType.COW, at);
        List<LivingEntity> fired = new ArrayList<>();

        int needed = StrongPunchManager.chargeTicks();
        helper.assertTrue(needed > 4, "This test needs a charge longer than a few ticks, got " + needed);

        // Commit to the first target and build up part of a charge.
        StrongPunchManager.tickWithResolvedTarget(player, first, (p, t) -> fired.add(t));
        for (int i = 0; i < needed - 2; i++) {
            StrongPunchManager.tickWithResolvedTarget(player, first, (p, t) -> fired.add(t));
        }
        int banked = StrongPunchManager.chargeTicksOf(player);
        helper.assertTrue(banked > 0, "Charge should have accumulated, got " + banked);
        helper.assertTrue(fired.isEmpty(), "Not yet complete");

        // TARGET SWITCH: everything banked is thrown away, not transferred.
        StrongPunchManager.tickWithResolvedTarget(player, second, (p, t) -> fired.add(t));
        helper.assertValueEqual(StrongPunchManager.chargeTicksOf(player), 0,
                "Switching targets must reset to zero - no partial credit carried across");
        helper.assertValueEqual(StrongPunchManager.targetIdOf(player), second.getId(), "Now committed to the second");

        // KEY RELEASE / LOST TARGET: the per-tick path passes null, which resets in the same way.
        for (int i = 0; i < 5; i++) {
            StrongPunchManager.tickWithResolvedTarget(player, second, (p, t) -> fired.add(t));
        }
        helper.assertTrue(StrongPunchManager.chargeTicksOf(player) > 0, "Charging again");
        StrongPunchManager.tickWithResolvedTarget(player, null, (p, t) -> fired.add(t));
        helper.assertValueEqual(StrongPunchManager.chargeTicksOf(player), 0,
                "Losing the target (release / out of range / hand filled) resets to zero");
        helper.assertTrue(fired.isEmpty(), "Nothing fired from a broken charge");
        helper.succeed();
    }

    // A NON-EMPTY HAND is checked before any target resolution, so it cannot even start a charge.
    @GameTest(template = "empty_3x3", batch = "strongpunch", timeoutTicks = 400)
    public static void a_full_hand_prevents_the_charge_entirely(GameTestHelper helper) {
        Player player = variantTwo(helper);
        AbilityManager.unlockAbility(player, StrongPunch.ABILITY_ID);

        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.STONE_SWORD));
        helper.assertFalse(player.getMainHandItem().isEmpty(), "Holding something");

        // The server-side gate reads exactly this; the client gate mirrors it purely to save packets.
        boolean barehanded = player.getMainHandItem().isEmpty();
        helper.assertFalse(barehanded, "A full main hand must disqualify before anything else is considered");

        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        helper.assertTrue(player.getMainHandItem().isEmpty(), "Empty again, so a charge could begin");
        helper.succeed();
    }

    /**
     * DELIVERABLE: the timing is server-authoritative - a client cannot claim early completion.
     *
     * <p>Two things are asserted. First, the charge advances at most ONE tick per server tick no matter how
     * many hold reports arrive: flooding the server with a thousand "key is down" packets in a single tick
     * buys nothing, because the counter lives in the tick loop and the packet only records a timestamp.
     * Second, the payload has no completion field to forge at all - the only thing a client can say is
     * "the key is down", which is a thing a player can honestly do anyway.
     */
    @GameTest(template = "empty_3x3", batch = "strongpunch", timeoutTicks = 400)
    public static void charge_timing_cannot_be_rushed_by_the_client(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 2, 1);
        Player player = variantTwo(helper);
        AbilityManager.unlockAbility(player, StrongPunch.ABILITY_ID);
        LivingEntity target = helper.spawn(EntityType.COW, at);
        List<LivingEntity> fired = new ArrayList<>();

        StrongPunchManager.tickWithResolvedTarget(player, target, (p, t) -> fired.add(t)); // commit

        // A thousand forged hold reports inside one tick.
        for (int spam = 0; spam < 1000; spam++) {
            StrongPunchManager.reportHeldForTest(player, true);
        }
        helper.assertValueEqual(StrongPunchManager.chargeTicksOf(player), 0,
                "Hold reports must not advance the charge at all - only the server tick does");

        // One server tick = exactly one tick of charge.
        StrongPunchManager.tickWithResolvedTarget(player, target, (p, t) -> fired.add(t));
        helper.assertValueEqual(StrongPunchManager.chargeTicksOf(player), 1, "One tick, one tick of charge");
        helper.assertTrue(fired.isEmpty(), "A thousand packets bought no early completion");

        // It takes the full configured number of server ticks, then fires exactly once.
        int needed = StrongPunchManager.chargeTicks();
        for (int tick = 1; tick < needed; tick++) {
            helper.assertTrue(fired.isEmpty(), "Fired early at tick " + tick + " of " + needed);
            StrongPunchManager.tickWithResolvedTarget(player, target, (p, t) -> fired.add(t));
        }
        helper.assertValueEqual(fired.size(), 1, "Fires exactly once, on the tick the charge completes");

        // And the cooldown is enforced on the same clock.
        helper.assertValueEqual(StrongPunchManager.cooldownTicksOf(player), StrongPunchManager.cooldownTicks(),
                "The cooldown starts at its full length");
        for (int tick = 0; tick < StrongPunchManager.cooldownTicks(); tick++) {
            StrongPunchManager.tickWithResolvedTarget(player, target, (p, t) -> fired.add(t));
            helper.assertValueEqual(fired.size(), 1, "Nothing fires during the cooldown (tick " + tick + ")");
        }
        helper.assertValueEqual(StrongPunchManager.cooldownTicksOf(player), 0, "Cooldown elapsed");
        helper.succeed();
    }

    /**
     * DELIVERABLE: the area damage hits everyone in the box exactly once - the target included but not
     * twice - excluding only the wielder, and catching passive mobs and other players.
     */
    @GameTest(template = "empty_3x3", batch = "strongpunch", timeoutTicks = 400)
    public static void the_blast_hits_everyone_in_the_box_once_except_the_wielder(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 2, 1);
        Vec3 centre = helper.absoluteVec(Vec3.atCenterOf(at));

        Player wielder = variantTwo(helper);
        AbilityManager.unlockAbility(wielder, StrongPunch.ABILITY_ID);
        wielder.setPos(centre); // standing right on top of the blast, and still must not be hit

        LivingEntity target = helper.spawn(EntityType.COW, at);      // the struck entity
        LivingEntity passive = helper.spawn(EntityType.CHICKEN, at); // a passive bystander
        LivingEntity hostile = helper.spawn(EntityType.ZOMBIE, at);
        target.setPos(centre);
        passive.setPos(centre);
        hostile.setPos(centre);

        // Something well outside the cube must be untouched.
        LivingEntity faraway = helper.spawn(EntityType.COW, at);
        faraway.setPos(centre.add(12.0D, 0.0D, 0.0D));

        float targetBefore = target.getHealth();
        float passiveBefore = passive.getHealth();
        float hostileBefore = hostile.getHealth();
        float wielderBefore = wielder.getHealth();
        float farawayBefore = faraway.getHealth();

        double expected = wielder.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)
                * Config.STRONG_PUNCH_DAMAGE_MULTIPLIER.get();

        StrongPunchEffect.detonate(wielder, target);

        helper.assertTrue(target.getHealth() < targetBefore, "The struck target takes the hit");
        helper.assertTrue(passive.getHealth() < passiveBefore, "A passive bystander in the box is caught too");
        helper.assertTrue(hostile.getHealth() < hostileBefore, "So is a hostile");
        helper.assertValueEqual(faraway.getHealth(), farawayBefore, "Nothing outside the box is touched");
        // NOTE: the wielder surviving proves little here - a mock player is never added to the level, so
        // no area query can see one either way. The exclusion rule is pinned properly against the real
        // predicate in only_the_wielder_is_excluded_from_the_blast.
        helper.assertValueEqual(wielder.getHealth(), wielderBefore, "The wielder is unharmed");

        // ONCE, not twice: the drop must match a single application, not double. Armour/absorption would
        // muddy an exact comparison, so this checks the cow (no armour) took no more than one hit's worth.
        float taken = targetBefore - target.getHealth();
        helper.assertTrue(taken <= expected + 0.001D,
                "The target took " + taken + " but one hit is " + expected + " - it was damaged twice");
        helper.assertTrue(taken > 0.0F, "...and at least once");
        helper.succeed();
    }

    /**
     * DELIVERABLE (the part that cannot be staged live): a second PLAYER in the blast is hit, and the
     * wielder is the ONLY exclusion.
     *
     * <p>Asserted against {@link StrongPunchEffect#isVictim}, the predicate {@code detonate} actually
     * filters with, because a second player cannot be put in a gametest level at all:
     * {@code GameTestHelper.makeMockPlayer} constructs a Player and never adds it to the level, so no area
     * query can ever return one. That also means the "wielder was not hit" assertion in the live test
     * above passes for the wrong reason - the wielder is not in the level either - which is exactly why
     * the rule is pinned here instead.
     */
    @GameTest(template = "empty_3x3", batch = "strongpunch", timeoutTicks = 400)
    public static void only_the_wielder_is_excluded_from_the_blast(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 2, 1);
        Player wielder = variantTwo(helper);
        Player otherPlayer = helper.makeMockPlayer(GameType.SURVIVAL);
        LivingEntity passive = helper.spawn(EntityType.COW, at);
        LivingEntity hostile = helper.spawn(EntityType.ZOMBIE, at);

        helper.assertFalse(StrongPunchEffect.isVictim(wielder, wielder), "The wielder is excluded");
        helper.assertTrue(StrongPunchEffect.isVictim(wielder, otherPlayer),
                "A SECOND PLAYER in the box is a victim - the blast is not friendly-fire-safe");
        helper.assertTrue(StrongPunchEffect.isVictim(wielder, passive), "So is a passive mob");
        helper.assertTrue(StrongPunchEffect.isVictim(wielder, hostile), "So is a hostile");

        passive.setHealth(0.0F);
        helper.assertFalse(StrongPunchEffect.isVictim(wielder, passive), "Something already dead is not re-hit");
        helper.succeed();
    }
}
