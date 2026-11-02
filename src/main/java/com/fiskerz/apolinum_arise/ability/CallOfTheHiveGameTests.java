package com.fiskerz.apolinum_arise.ability;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fiskerz.apolinum_arise.Apolinumarise;
import com.fiskerz.apolinum_arise.ability.hivemind.CallOfTheHive;
import com.fiskerz.apolinum_arise.ability.hivemind.CallOfTheHiveData;
import com.fiskerz.apolinum_arise.ability.hivemind.CallOfTheHiveEffect;
import com.fiskerz.apolinum_arise.ability.hivemind.HiveMind;
import com.fiskerz.apolinum_arise.ability.hivemind.HiveMindAttachments;
import com.fiskerz.apolinum_arise.ability.hivemind.HiveMindRegistry;
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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Phase 15: Call of the Hive - its unlock chain, the teleport and its exemptions, and the cooldown.
 *
 * <p>Each test is its own batch: the effect sweeps a whole 3x3-chunk area, so it must never run alongside
 * another test whose mobs it would pull in.
 */
@GameTestHolder(Apolinumarise.MODID)
@PrefixGameTestTemplate(false)
public class CallOfTheHiveGameTests {

    private static Player variantZero(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setData(InfectionAttachments.INFECTION, InfectionData.NONE.becomeInfected());
        player.setData(SkillAttachments.SKILL_PROFILE, SkillProfileData.NONE.withInfectedVariant(CallOfTheHive.VARIANT));
        player.setData(DreamAttachments.DREAMS, DreamData.EMPTY);
        player.setData(AbilityAttachments.ABILITIES, AbilityData.NONE);
        player.setData(HiveMindAttachments.CALL_OF_THE_HIVE, CallOfTheHiveData.READY);
        return player;
    }

    /** Where the caster stands, relative to the test: well outside the framework's barrier box (see arena). */
    private static final BlockPos CASTER_AT = new BlockPos(1, 0, -13);
    private static final int ARENA_RADIUS = 10;
    private static final int ARENA_HEIGHT = 7;

    /**
     * A variant-0 caster holding the wand with both Branch 1 abilities, standing in an open arena.
     *
     * <p>Why not inside the test structure: the framework walls every test in barriers with a barrier ceiling
     * at the template's top (StructureUtils.encaseStructure), so a caster standing in it is in a sealed
     * 3-block-high box - no line of sight out, and no room for a tall mob such as the Wither. That is the test
     * rig, not the world, so the caster stands outside it on cleared ground with a solid floor and open air.
     */
    private static Player caster(GameTestHelper helper) {
        Player caster = variantZero(helper);
        AbilityManager.unlockAbility(caster, HiveMind.ABILITY_ID);
        AbilityManager.unlockAbility(caster, CallOfTheHive.ABILITY_ID);
        BlockPos at = helper.absolutePos(CASTER_AT);
        ServerLevel level = helper.getLevel();
        for (int dx = -ARENA_RADIUS; dx <= ARENA_RADIUS; dx++) {
            for (int dz = -ARENA_RADIUS; dz <= ARENA_RADIUS; dz++) {
                level.setBlock(at.offset(dx, -1, dz), Blocks.STONE.defaultBlockState(), 2);
                for (int dy = 0; dy < ARENA_HEIGHT; dy++) {
                    level.setBlock(at.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
        caster.moveTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D, 0.0F, 0.0F);
        caster.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(HiveMindRegistry.HIVE_MIND_STICK.get()));
        return caster;
    }

    /** Clear every mob out of the caster's sweep, so leftovers from earlier batches can't skew a test. */
    private static void clearSweep(ServerLevel level, Player caster) {
        AABB sweep = CallOfTheHiveEffect.sweepArea(level, caster, Config.CALL_OF_THE_HIVE_CHUNK_RADIUS.get());
        level.getEntitiesOfClass(Mob.class, sweep, mob -> true).forEach(Entity::discard);
    }

    private static <T extends Mob> T spawnAt(GameTestHelper helper, EntityType<T> type, Player caster,
                                             double dx, double dy, double dz) {
        T mob = type.create(helper.getLevel());
        mob.moveTo(caster.getX() + dx, caster.getY() + dy, caster.getZ() + dz, 0.0F, 0.0F);
        mob.setPersistenceRequired();
        helper.getLevel().addFreshEntity(mob);
        return mob;
    }

    private static double horizontal(Entity entity, Player caster) {
        return Math.sqrt(Math.pow(entity.getX() - caster.getX(), 2) + Math.pow(entity.getZ() - caster.getZ(), 2));
    }

    // Registered for Branch 1 (variant 0); its dream is NOT queued at variant assignment but by the Hive-Mind
    // unlock, and watching it is the whole unlock - while the Hive-Mind itself is still NOT unlocked by its
    // own dream (the wand drop stays its unlock).
    @GameTest(template = "empty_3x3", batch = "callofthehive_unlock")
    public static void call_of_the_hive_is_queued_by_the_hivemind_unlock_and_unlocked_by_its_dream(GameTestHelper helper) {
        Ability ability = Abilities.get(CallOfTheHive.ABILITY_ID).orElse(null);
        helper.assertTrue(ability != null, "Call of the Hive must be registered");
        helper.assertTrue(ability.side() == AbilitySide.INFECTED, "Infected side");
        helper.assertValueEqual(ability.restrictedTo().getAsInt(), 0, "Branch 1 = variant index 0");
        helper.assertTrue(ability.unlocksWhenMet(), "Watching the dream IS the unlock");
        helper.assertFalse(Abilities.prerequisiteDreamsFor(AbilitySide.INFECTED, CallOfTheHive.VARIANT)
                        .contains(CallOfTheHive.DREAM_ID),
                "Variant assignment must NOT queue this dream - the Hive-Mind unlock does");

        // Watching the dream without the Hive-Mind grants nothing.
        Player early = variantZero(helper);
        DreamManager.markWatched(early, CallOfTheHive.DREAM_ID);
        helper.assertFalse(AbilityManager.hasAbility(early, CallOfTheHive.ABILITY_ID),
                "The dream alone is not enough - the Hive-Mind has to be unlocked first");

        // The real order: unlocking the Hive-Mind queues the dream; watching it unlocks the ability.
        Player player = variantZero(helper);
        AbilityManager.unlockAbility(player, HiveMind.ABILITY_ID);
        helper.assertTrue(player.getData(DreamAttachments.DREAMS).queue().contains(CallOfTheHive.DREAM_ID),
                "Unlocking the Hive-Mind queues the Call of the Hive dream");
        helper.assertFalse(CallOfTheHive.isActiveFor(player), "Not usable before the dream is watched");
        DreamManager.markWatched(player, CallOfTheHive.DREAM_ID);
        helper.assertTrue(AbilityManager.hasAbility(player, CallOfTheHive.ABILITY_ID), "Watching it unlocks it");
        helper.assertTrue(CallOfTheHive.isActiveFor(player), "...and it is usable");

        // A different variant that somehow has both still does not qualify.
        Player otherVariant = variantZero(helper);
        otherVariant.setData(SkillAttachments.SKILL_PROFILE, SkillProfileData.NONE.withInfectedVariant(1));
        AbilityManager.unlockAbility(otherVariant, HiveMind.ABILITY_ID);
        DreamManager.markWatched(otherVariant, CallOfTheHive.DREAM_ID);
        helper.assertFalse(AbilityManager.hasAbility(otherVariant, CallOfTheHive.ABILITY_ID),
                "Only Branch 1 can earn it");

        // Regression: the new auto-grant must not hand out the Hive-Mind on its own dream.
        Player wandless = variantZero(helper);
        DreamManager.markWatched(wandless, HiveMind.DREAM_ID);
        helper.assertFalse(AbilityManager.hasAbility(wandless, HiveMind.ABILITY_ID),
                "The Hive-Mind is still unlocked by the wand, not by its dream");
        helper.succeed();
    }

    // DELIVERABLE: the effect and its exemptions - including a tamed pet and a boss - plus riders, leashes,
    // full height, the chunk boundary, and safe, non-overlapping arrival.
    @GameTest(template = "empty_3x3", batch = "callofthehive_effect")
    public static void call_of_the_hive_pulls_mobs_but_spares_pets_and_players(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player caster = caster(helper);
        clearSweep(level, caster);
        AABB sweep = CallOfTheHiveEffect.sweepArea(level, caster, Config.CALL_OF_THE_HIVE_CHUNK_RADIUS.get());
        List<Entity> cleanup = new ArrayList<>();

        // Called: an ordinary hostile, a villager, a boss, a mob far overhead, a jockey pair.
        Zombie zombie = spawnAt(helper, EntityType.ZOMBIE, caster, 10, 0, 4);
        Villager villager = spawnAt(helper, EntityType.VILLAGER, caster, -9, 0, 7);
        WitherBoss wither = spawnAt(helper, EntityType.WITHER, caster, 6, 0, -11);
        wither.setNoAi(true); // keep the boss inert for the test; NoAI does not exempt it
        Zombie high = spawnAt(helper, EntityType.ZOMBIE, caster, 2, 120, 2);
        Chicken chicken = spawnAt(helper, EntityType.CHICKEN, caster, -7, 0, -6);
        Zombie jockey = spawnAt(helper, EntityType.ZOMBIE, caster, -7, 0, -6);
        jockey.setBaby(true);
        jockey.startRiding(chicken, true);
        // Leashed to the caster: keeps the leash. Leashed to a fence: released, the lead left at the fence.
        Cow cow = spawnAt(helper, EntityType.COW, caster, 4, 0, -3);
        cow.setLeashedTo(caster, true);
        BlockPos fence = BlockPos.containing(caster.getX() - 12, caster.getY(), caster.getZ() - 2);
        level.setBlock(fence, Blocks.OAK_FENCE.defaultBlockState(), 3);
        LeashFenceKnotEntity knot = LeashFenceKnotEntity.getOrCreateKnot(level, fence);
        Sheep sheep = spawnAt(helper, EntityType.SHEEP, caster, -11, 0, -2);
        sheep.setLeashedTo(knot, true);
        Vec3 sheepWasAt = sheep.position();

        // Exempt: a pet tamed by someone else, and a mob a player is riding.
        Wolf wolf = spawnAt(helper, EntityType.WOLF, caster, 3, 0, 9);
        wolf.setTame(true, false);
        wolf.setOwnerUUID(UUID.randomUUID());
        Pig pig = spawnAt(helper, EntityType.PIG, caster, 12, 0, -4);
        Player rider = helper.makeMockPlayer(GameType.SURVIVAL);
        rider.startRiding(pig, true);
        helper.assertTrue(rider.getVehicle() == pig, "Setup: the player is riding the pig");
        // Outside the 3x3 chunk area: untouched.
        BlockPos outsideAt = BlockPos.containing(sweep.maxX + 2, caster.getY(), caster.getZ());
        level.getChunk(outsideAt.getX() >> 4, outsideAt.getZ() >> 4);
        Zombie outside = spawnAt(helper, EntityType.ZOMBIE, caster,
                outsideAt.getX() + 0.5 - caster.getX(), 0, outsideAt.getZ() + 0.5 - caster.getZ());

        cleanup.addAll(List.of(zombie, villager, wither, high, chicken, jockey, cow, sheep, wolf, pig, outside, knot));
        Vec3 wolfWasAt = wolf.position();
        Vec3 pigWasAt = pig.position();
        Vec3 outsideWasAt = outside.position();
        helper.assertFalse(sweep.contains(outside.position()), "Setup: the far zombie is outside the area");

        InteractionResult result = HiveMindRegistry.HIVE_MIND_STICK.get()
                .use(level, caster, InteractionHand.MAIN_HAND).getResult();
        helper.assertTrue(result.consumesAction(), "The call succeeds");

        int spread = Config.CALL_OF_THE_HIVE_SPREAD_RADIUS.get();
        List<Mob> called = List.of(zombie, villager, wither, high, chicken, cow, sheep);
        for (Mob mob : called) {
            helper.assertTrue(horizontal(mob, caster) <= spread + 1.0D
                            && Math.abs(mob.getY() - caster.getY()) <= 4.0D,
                    mob.getType().getDescriptionId() + " should arrive around the caster, is at " + mob.position());
            helper.assertTrue(level.noCollision(mob), mob.getType().getDescriptionId() + " must not arrive inside a block");
            helper.assertFalse(mob.getBoundingBox().intersects(caster.getBoundingBox()),
                    mob.getType().getDescriptionId() + " must not land on the caster");
        }
        // Spread: no two arrivals share space (the jockey rides its chicken, so it is part of that unit).
        for (int i = 0; i < called.size(); i++) {
            for (int j = i + 1; j < called.size(); j++) {
                helper.assertFalse(called.get(i).getBoundingBox().intersects(called.get(j).getBoundingBox()),
                        called.get(i).getType().getDescriptionId() + " and "
                                + called.get(j).getType().getDescriptionId() + " overlap");
            }
        }
        helper.assertTrue(jockey.getVehicle() == chicken, "The rider arrives still riding its mount");
        helper.assertTrue(cow.getLeashHolder() == caster, "A mob leashed to the caster keeps its leash");
        helper.assertFalse(sheep.isLeashed(), "A mob tied to a fence is released rather than dragged");
        helper.assertFalse(level.getEntitiesOfClass(ItemEntity.class, new AABB(sheepWasAt, sheepWasAt).inflate(2.0D),
                        item -> item.getItem().is(Items.LEAD)).isEmpty(),
                "...and its lead is dropped where it was tied");

        helper.assertTrue(wolf.position().distanceTo(wolfWasAt) < 0.01D, "A tamed pet (any owner) is not called");
        helper.assertTrue(pig.position().distanceTo(pigWasAt) < 0.01D && rider.getVehicle() == pig,
                "A mob carrying a player is left alone, so the player is never dragged");
        helper.assertTrue(outside.position().distanceTo(outsideWasAt) < 0.01D, "Outside the chunk area: untouched");

        rider.stopRiding();
        level.getEntitiesOfClass(ItemEntity.class, sweep, item -> item.getItem().is(Items.LEAD)).forEach(Entity::discard);
        cleanup.forEach(Entity::discard);
        helper.succeed();
    }

    // DELIVERABLE: an empty call costs nothing; a real one starts the cooldown, shown on the wand; the cooldown
    // blocks the next call and survives a relog (the player NBT save/load that logout/login performs).
    @GameTest(template = "empty_3x3", batch = "callofthehive_cooldown")
    public static void call_of_the_hive_cooldown_skips_empty_calls_and_survives_relog(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player caster = caster(helper);
        clearSweep(level, caster);

        // Nothing eligible: no effect, no cooldown.
        helper.assertValueEqual(CallOfTheHive.call(level, caster), 0, "Nothing to call");
        helper.assertTrue(caster.getData(HiveMindAttachments.CALL_OF_THE_HIVE).readyAt(level.getGameTime()),
                "An empty call does not consume the cooldown");
        helper.assertFalse(caster.getCooldowns().isOnCooldown(HiveMindRegistry.HIVE_MIND_STICK.get()),
                "...and puts nothing on the wand");

        // A real call starts it, on the deadline and on the wand.
        Zombie first = spawnAt(helper, EntityType.ZOMBIE, caster, 8, 0, 5);
        helper.assertValueEqual(CallOfTheHive.call(level, caster), 1, "One zombie called");
        long deadline = caster.getData(HiveMindAttachments.CALL_OF_THE_HIVE).nextAllowedGameTime();
        helper.assertValueEqual(deadline, level.getGameTime() + CallOfTheHive.cooldownTicks(),
                "Cooldown is " + Config.CALL_OF_THE_HIVE_COOLDOWN_SECONDS.get() + "s from now");
        helper.assertTrue(caster.getCooldowns().isOnCooldown(HiveMindRegistry.HIVE_MIND_STICK.get()),
                "Shown as the vanilla cooldown on the wand");

        // While it runs, nothing is called.
        Zombie second = spawnAt(helper, EntityType.ZOMBIE, caster, -8, 0, -5);
        Vec3 secondWasAt = second.position();
        helper.assertValueEqual(CallOfTheHive.call(level, caster), 0, "On cooldown");
        helper.assertTrue(second.position().distanceTo(secondWasAt) < 0.01D, "Nothing moved during the cooldown");

        // Relog: save the player the way logout does, load it into a brand-new player entity the way login
        // does. The vanilla cooldown is gone (it lives on the old entity); the persisted deadline is not.
        CompoundTag saved = caster.saveWithoutId(new CompoundTag());
        Player relogged = helper.makeMockPlayer(GameType.SURVIVAL);
        relogged.load(saved);
        helper.assertValueEqual(relogged.getData(HiveMindAttachments.CALL_OF_THE_HIVE).nextAllowedGameTime(), deadline,
                "The cooldown deadline survives the save/load round trip");
        helper.assertFalse(relogged.getCooldowns().isOnCooldown(HiveMindRegistry.HIVE_MIND_STICK.get()),
                "Setup: the fresh entity has lost the vanilla overlay");
        CallOfTheHive.restoreCooldownDisplay(relogged); // what the login handler runs
        helper.assertTrue(relogged.getCooldowns().isOnCooldown(HiveMindRegistry.HIVE_MIND_STICK.get()),
                "Login puts the remaining cooldown back on the wand");
        helper.assertValueEqual(CallOfTheHive.call(level, relogged), 0, "...and it is still enforced after relog");
        helper.assertTrue(second.position().distanceTo(secondWasAt) < 0.01D, "Still nothing moved");

        // A player without the ability: the wand is the plain stick it always was.
        Player plain = variantZero(helper);
        plain.moveTo(caster.getX(), caster.getY(), caster.getZ(), 0.0F, 0.0F);
        plain.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(HiveMindRegistry.HIVE_MIND_STICK.get()));
        AbilityManager.unlockAbility(plain, HiveMind.ABILITY_ID); // has the wand's ability, not this one
        helper.assertTrue(HiveMindRegistry.HIVE_MIND_STICK.get().use(level, plain, InteractionHand.MAIN_HAND)
                .getResult() == InteractionResult.PASS, "Without the ability, right-click does nothing");
        helper.assertTrue(second.position().distanceTo(secondWasAt) < 0.01D, "...and calls nothing");
        helper.assertTrue(plain.getData(HiveMindAttachments.CALL_OF_THE_HIVE).readyAt(level.getGameTime()),
                "...and starts no cooldown");

        first.discard();
        second.discard();
        helper.succeed();
    }
}
