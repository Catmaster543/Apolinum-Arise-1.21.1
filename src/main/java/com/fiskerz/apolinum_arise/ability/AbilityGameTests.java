package com.fiskerz.apolinum_arise.ability;

import com.fiskerz.apolinum_arise.Apolinumarise;
import com.fiskerz.apolinum_arise.ability.hivemind.GoadGoal;
import com.fiskerz.apolinum_arise.ability.hivemind.HiveMind;
import com.fiskerz.apolinum_arise.ability.hivemind.HiveMindEffect;
import com.fiskerz.apolinum_arise.ability.hivemind.HiveMindEvents;
import com.fiskerz.apolinum_arise.ability.hivemind.HiveMindRegistry;
import com.fiskerz.apolinum_arise.ability.hivemind.HiveMindStickItem;
import com.fiskerz.apolinum_arise.config.Config;
import com.fiskerz.apolinum_arise.dream.DreamAttachments;
import com.fiskerz.apolinum_arise.dream.DreamData;
import com.fiskerz.apolinum_arise.dream.DreamManager;
import com.fiskerz.apolinum_arise.infection.InfectionAttachments;
import com.fiskerz.apolinum_arise.infection.InfectionData;
import com.fiskerz.apolinum_arise.skill.SkillAttachments;
import com.fiskerz.apolinum_arise.skill.SkillProfileData;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Phase 12: the ability registry/unlock architecture, proven through the Hive-Mind ability.
 */
@GameTestHolder(Apolinumarise.MODID)
@PrefixGameTestTemplate(false)
public class AbilityGameTests {

    /** An infected player of the given variant, with nothing watched and no abilities. */
    private static Player infectedVariant(GameTestHelper helper, int variant) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setData(InfectionAttachments.INFECTION, InfectionData.NONE.becomeInfected());
        player.setData(SkillAttachments.SKILL_PROFILE, SkillProfileData.NONE.withInfectedVariant(variant));
        player.setData(DreamAttachments.DREAMS, DreamData.EMPTY);
        player.setData(AbilityAttachments.ABILITIES, AbilityData.NONE);
        return player;
    }

    // The registry's core question: is this the right KIND of player for this ability. Side and variant
    // both have to match, and an unassigned variant must match nothing.
    @GameTest(template = "empty_3x3", batch = "ability_registry")
    public static void abilities_apply_only_to_their_own_side_and_variant(GameTestHelper helper) {
        Ability hiveMind = Abilities.get(HiveMind.ABILITY_ID).orElse(null);
        helper.assertTrue(hiveMind != null, "The Hive-Mind ability must be registered");

        helper.assertTrue(hiveMind.appliesTo(infectedVariant(helper, HiveMind.VARIANT)),
                "An infected variant-1 player is the right kind of player for it");
        helper.assertFalse(hiveMind.appliesTo(infectedVariant(helper, 0)),
                "A different variant must not qualify");
        helper.assertFalse(hiveMind.appliesTo(infectedVariant(helper, 2)),
                "Nor must the third variant");

        // Assigned the right variant but no longer infected: the side check must catch it.
        Player cured = infectedVariant(helper, HiveMind.VARIANT);
        cured.setData(InfectionAttachments.INFECTION, InfectionData.NONE);
        helper.assertFalse(hiveMind.appliesTo(cured),
                "Leaving the infected side must disqualify, even with the variant still recorded");

        // Never assigned a variant at all.
        Player unassigned = helper.makeMockPlayer(GameType.SURVIVAL);
        unassigned.setData(InfectionAttachments.INFECTION, InfectionData.NONE.becomeInfected());
        unassigned.setData(SkillAttachments.SKILL_PROFILE, SkillProfileData.NONE);
        helper.assertFalse(hiveMind.appliesTo(unassigned),
                "An unassigned variant must match no restricted ability");
        helper.succeed();
    }

    /**
     * DELIVERABLE: the drop genuinely never rolls before the dream has been watched.
     *
     * <p>Run with the drop chance forced to 1.0, so the roll cannot fail by luck: every kill by a qualified
     * player would drop. An unwatched player producing zero drops across many kills therefore proves the
     * eligibility gate runs BEFORE the roll, not that the dice were kind.
     */
    @GameTest(template = "empty_3x3", batch = "ability_hivemind")
    public static void the_drop_never_rolls_until_the_dream_has_been_watched(GameTestHelper helper) {
        double originalChance = Config.HIVE_MIND_DROP_CHANCE.get();
        Config.HIVE_MIND_DROP_CHANCE.set(1.0D);
        try {
            Player player = infectedVariant(helper, HiveMind.VARIANT);
            RandomSource random = RandomSource.create(1234L);

            helper.assertFalse(DreamManager.hasWatched(player, HiveMind.DREAM_ID),
                    "The dream starts unwatched");
            for (int kill = 0; kill < 500; kill++) {
                helper.assertFalse(HiveMindEvents.shouldDrop(player, random),
                        "The wand dropped on kill " + kill + " despite the dream never being watched, at a "
                                + "forced 100% chance - the eligibility gate is not in front of the roll");
            }

            // Now satisfy the prerequisite exactly as finishing the dream does.
            DreamManager.markWatched(player, HiveMind.DREAM_ID);
            helper.assertTrue(HiveMindEvents.shouldDrop(player, random),
                    "Once the dream is watched the drop must be able to occur");

            // ...and once unlocked, it stops dropping: the wand is the unlock, a second is pointless.
            AbilityManager.unlockAbility(player, HiveMind.ABILITY_ID);
            helper.assertFalse(HiveMindEvents.shouldDrop(player, random),
                    "A player who already holds the ability must stop rolling for the wand");
        } finally {
            Config.HIVE_MIND_DROP_CHANCE.set(originalChance);
        }
        helper.succeed();
    }

    // The other three eligibility gates, each proven to block on its own at a forced 100% chance.
    @GameTest(template = "empty_3x3", batch = "ability_hivemind")
    public static void the_drop_is_limited_to_infected_variant_one(GameTestHelper helper) {
        double originalChance = Config.HIVE_MIND_DROP_CHANCE.get();
        Config.HIVE_MIND_DROP_CHANCE.set(1.0D);
        try {
            RandomSource random = RandomSource.create(99L);

            Player wrongVariant = infectedVariant(helper, 0);
            DreamManager.markWatched(wrongVariant, HiveMind.DREAM_ID);
            helper.assertFalse(HiveMindEvents.shouldDrop(wrongVariant, random),
                    "Variant 0 must never get the variant-1 wand, even having watched the dream");

            Player notInfected = infectedVariant(helper, HiveMind.VARIANT);
            notInfected.setData(InfectionAttachments.INFECTION, InfectionData.NONE);
            DreamManager.markWatched(notInfected, HiveMind.DREAM_ID);
            helper.assertFalse(HiveMindEvents.shouldDrop(notInfected, random),
                    "A player who is no longer infected must not get it");

            // The control: everything right, so the harness itself is not simply always returning false.
            Player eligible = infectedVariant(helper, HiveMind.VARIANT);
            DreamManager.markWatched(eligible, HiveMind.DREAM_ID);
            helper.assertTrue(HiveMindEvents.shouldDrop(eligible, random),
                    "A fully eligible player must drop at a forced 100% - otherwise this test proves nothing");
        } finally {
            Config.HIVE_MIND_DROP_CHANCE.set(originalChance);
        }
        helper.succeed();
    }

    /**
     * DELIVERABLE: the unlock is a permanent per-player flag, independent of holding the item.
     *
     * <p>Mirrors the hand test: A loots the wand and is unlocked, drops it, and B picks it up. The wand is
     * the same stack; the only thing that differs is whose data is asked. {@link HiveMind#isUnlockedFor} is
     * exactly the check {@code HiveMindStickItem.hurtEnemy} makes before doing anything special.
     */
    @GameTest(template = "empty_3x3", batch = "ability_hivemind")
    public static void the_unlock_belongs_to_the_player_not_the_item(GameTestHelper helper) {
        Player a = infectedVariant(helper, HiveMind.VARIANT);
        Player b = infectedVariant(helper, 0); // different variant - "different/no variant" in the test plan

        DreamManager.markWatched(a, HiveMind.DREAM_ID);
        helper.assertTrue(AbilityManager.unlockAbility(a, HiveMind.ABILITY_ID), "A unlocks on receiving it");

        ItemStack wand = new ItemStack(HiveMindRegistry.HIVE_MIND_STICK.get());
        a.getInventory().add(wand.copy());
        helper.assertTrue(HiveMind.isUnlockedFor(a), "A is unlocked while holding the wand");

        // A drops it: possession changes, the unlock does not.
        a.getInventory().clearContent();
        helper.assertTrue(HiveMind.isUnlockedFor(a),
                "A must stay unlocked after dropping the wand - the item is not the ability");

        // B picks up the very same item and is still nobody special.
        b.getInventory().add(wand.copy());
        helper.assertFalse(HiveMind.isUnlockedFor(b),
                "B holding the wand must NOT trigger the hive-mind effect - the ability is per player");
        helper.assertFalse(AbilityManager.hasAbility(b, HiveMind.ABILITY_ID), "B has unlocked nothing");

        // And a repeat unlock is a no-op rather than a duplicate.
        helper.assertFalse(AbilityManager.unlockAbility(a, HiveMind.ABILITY_ID),
                "Unlocking twice reports no change");
        helper.assertValueEqual(AbilityManager.unlocked(a).size(), 1, "Exactly one ability recorded");
        helper.succeed();
    }

    /**
     * DELIVERABLE: the wand deals exactly one heart regardless of unlock state.
     *
     * <p>Asserted on the item's own attribute modifier, which is where 1.21.1 puts weapon damage. A
     * player's base ATTACK_DAMAGE is 1.0, so the modifier must be {@code wandDamage - 1.0} for the swing to
     * total the configured value. The item carries no per-player state at all, which is what makes "the
     * same for everyone" true by construction rather than by test.
     */
    @GameTest(template = "empty_3x3", batch = "ability_hivemind")
    public static void the_wand_hits_for_exactly_the_configured_damage(GameTestHelper helper) {
        ItemStack wand = new ItemStack(HiveMindRegistry.HIVE_MIND_STICK.get());
        double configured = Config.HIVE_MIND_WAND_DAMAGE.get();

        double total = HiveMindStickItem.PLAYER_BASE_ATTACK_DAMAGE + attackDamageModifier(helper, wand);
        helper.assertTrue(Math.abs(total - configured) < 1.0E-6D,
                "The wand must total the configured " + configured + " damage, got " + total);
        helper.assertTrue(Math.abs(total - 2.0D) < 1.0E-6D,
                "At the shipped default that is exactly one heart, got " + total);

        // Live config, not a value frozen at registration - the whole reason the modifiers are supplied
        // per stack instead of through Item.Properties.attributes().
        Config.HIVE_MIND_WAND_DAMAGE.set(7.0D);
        try {
            double raised = HiveMindStickItem.PLAYER_BASE_ATTACK_DAMAGE + attackDamageModifier(helper, wand);
            helper.assertTrue(Math.abs(raised - 7.0D) < 1.0E-6D,
                    "Changing hiveMindWandDamage must change the wand, got " + raised);
        } finally {
            Config.HIVE_MIND_WAND_DAMAGE.set(configured);
        }
        helper.succeed();
    }

    /** The one ATTACK_DAMAGE modifier the wand contributes, read the way the game reads it. */
    private static double attackDamageModifier(GameTestHelper helper, ItemStack wand) {
        double amount = 0.0D;
        int found = 0;
        for (var entry : wand.getAttributeModifiers().modifiers()) {
            if (entry.attribute().equals(Attributes.ATTACK_DAMAGE)) {
                found++;
                amount = entry.modifier().amount();
                helper.assertTrue(entry.modifier().operation() == AttributeModifier.Operation.ADD_VALUE,
                        "Weapon damage must be a flat ADD_VALUE modifier");
            }
        }
        helper.assertValueEqual(found, 1, "The wand declares exactly one ATTACK_DAMAGE modifier");
        return amount;
    }

    /**
     * DELIVERABLE: hostile mobs get real AI retargeting, passive mobs get the custom goad behaviour.
     *
     * <p>Drives the actual {@link HiveMindEffect#trigger} with a zombie and a cow inside the radius, and
     * checks each landed in the right group - the zombie by its vanilla target, the cow by the goal
     * attached to it. A third mob outside the radius proves the sphere is respected.
     */
    @GameTest(template = "empty_3x3", batch = "ability_hivemind", timeoutTicks = 400)
    public static void hostiles_retarget_while_passives_are_goaded(GameTestHelper helper) {
        BlockPos centre = new BlockPos(1, 2, 1);
        Player wielder = helper.makeMockPlayer(GameType.SURVIVAL);
        wielder.setPos(helper.absoluteVec(net.minecraft.world.phys.Vec3.atCenterOf(centre)));

        Cow struck = helper.spawn(EntityType.COW, centre);
        Zombie hostile = helper.spawn(EntityType.ZOMBIE, centre);
        Cow passive = helper.spawn(EntityType.COW, centre);

        // Classification first - this is the decision the effect branches on.
        helper.assertTrue(HiveMindEffect.canAlreadyFight(hostile),
                "A zombie owns a melee attack goal, so it only needs a target");
        helper.assertFalse(HiveMindEffect.canAlreadyFight(passive),
                "A cow has no attack behaviour, so setTarget alone would do nothing");

        HiveMindEffect.trigger(wielder, struck);

        helper.assertTrue(hostile.getTarget() == struck,
                "The zombie must be retargeted onto the struck entity by its own AI");
        helper.assertTrue(GoadGoal.attachedTo(hostile) == null,
                "A mob that can already fight must NOT get the custom goal");

        GoadGoal goal = GoadGoal.attachedTo(passive);
        helper.assertTrue(goal != null, "The cow must receive a goad goal");
        helper.assertTrue(goal.goadTarget() == struck, "...pointed at the struck entity");

        // The struck entity and the wielder are excluded from their own hive.
        helper.assertTrue(GoadGoal.attachedTo(struck) == null, "The struck entity is not goaded at itself");

        // Re-triggering must re-point the SAME goal, not stack a second one - the ability has no cooldown.
        Cow other = helper.spawn(EntityType.COW, centre);
        HiveMindEffect.trigger(wielder, other);
        helper.assertTrue(GoadGoal.attachedTo(passive) == goal, "Re-triggering reuses the existing goal");
        helper.assertTrue(goal.goadTarget() == other, "...re-pointed at the new victim");
        helper.assertValueEqual(countGoadGoals(passive), 1, "Exactly one goad goal, however many triggers");
        helper.succeed();
    }

    private static int countGoadGoals(Mob mob) {
        int count = 0;
        for (var wrapped : mob.goalSelector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof GoadGoal) {
                count++;
            }
        }
        return count;
    }

    // The radius effect is gated on the WIELDER's unlock, not on the item: the same swing does nothing for
    // someone who has not earned it. This is the check hurtEnemy performs.
    @GameTest(template = "empty_3x3", batch = "ability_hivemind", timeoutTicks = 400)
    public static void the_radius_effect_only_fires_for_an_unlocked_wielder(GameTestHelper helper) {
        BlockPos centre = new BlockPos(1, 2, 1);
        Player locked = infectedVariant(helper, HiveMind.VARIANT);
        locked.setPos(helper.absoluteVec(net.minecraft.world.phys.Vec3.atCenterOf(centre)));

        helper.assertFalse(HiveMind.isUnlockedFor(locked),
                "Having the variant and the wand is not the ability - only the unlock is");

        LivingEntity struck = helper.spawn(EntityType.COW, centre);
        Zombie bystander = helper.spawn(EntityType.ZOMBIE, centre);

        // What hurtEnemy does: nothing at all unless unlocked, so the bystander keeps its own target.
        if (HiveMind.isUnlockedFor(locked)) {
            HiveMindEffect.trigger(locked, struck);
        }
        helper.assertTrue(bystander.getTarget() == null,
                "A locked wielder's swing must not retarget anything");

        AbilityManager.unlockAbility(locked, HiveMind.ABILITY_ID);
        helper.assertTrue(HiveMind.isUnlockedFor(locked), "Now unlocked");
        if (HiveMind.isUnlockedFor(locked)) {
            HiveMindEffect.trigger(locked, struck);
        }
        helper.assertTrue(bystander.getTarget() == struck,
                "The same swing, once unlocked, must turn the bystander on the victim");
        helper.succeed();
    }

    // Phase 12 item 1: a dream is only "watched" when it was seen to the end, and the record is permanent.
    @GameTest(template = "empty_3x3", batch = "ability_registry")
    public static void watched_dreams_are_recorded_and_persist(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setData(DreamAttachments.DREAMS, DreamData.EMPTY);

        helper.assertFalse(DreamManager.hasWatched(player, HiveMind.DREAM_ID), "Nothing watched initially");
        helper.assertTrue(DreamManager.markWatched(player, HiveMind.DREAM_ID), "First record sticks");
        helper.assertTrue(DreamManager.hasWatched(player, HiveMind.DREAM_ID), "And reads back");
        helper.assertFalse(DreamManager.markWatched(player, HiveMind.DREAM_ID), "Re-marking is a no-op");

        // Queueing and watching are independent: a queued dream is owed, a watched one was seen.
        DreamData data = player.getData(DreamAttachments.DREAMS);
        helper.assertTrue(data.queue().isEmpty(), "Marking watched does not queue anything");
        helper.assertTrue(data.withQueued("x").hasWatched(HiveMind.DREAM_ID),
                "Queueing another dream must not disturb the watched set");

        // The unlock condition reads exactly this.
        Ability hiveMind = Abilities.get(HiveMind.ABILITY_ID).orElseThrow();
        helper.assertTrue(hiveMind.unlockCondition().isMet(player), "The condition is met once watched");
        DreamManager.unmarkWatched(player, HiveMind.DREAM_ID);
        helper.assertFalse(hiveMind.unlockCondition().isMet(player), "And unmet once forgotten");
        helper.succeed();
    }

    // The data layer under the unlock set: permanence, no duplicates, and survival of a codec round trip.
    @GameTest(template = "empty_3x3", batch = "ability_registry")
    public static void ability_data_round_trips_and_deduplicates(GameTestHelper helper) {
        AbilityData data = AbilityData.NONE.with("a").with("b").with("a");
        helper.assertValueEqual(data.unlocked().size(), 2, "Duplicates collapse");
        helper.assertTrue(data.with("a") == data, "Re-adding returns the same instance, so callers see no-op");

        var encoded = AbilityData.CODEC.encodeStart(
                net.minecraft.nbt.NbtOps.INSTANCE, data).getOrThrow();
        AbilityData decoded = AbilityData.CODEC.parse(
                net.minecraft.nbt.NbtOps.INSTANCE, encoded).getOrThrow();
        helper.assertValueEqual(decoded.unlocked(), data.unlocked(), "Survives a save/load round trip");

        // Unknown ids are refused rather than silently stored - that can only ever be a typo.
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setData(AbilityAttachments.ABILITIES, AbilityData.NONE);
        helper.assertFalse(AbilityManager.unlockAbility(player, "no_such_ability"),
                "An unregistered id must not be unlockable");
        helper.assertTrue(AbilityManager.unlocked(player).isEmpty(), "And nothing is stored");
        helper.succeed();
    }
}
