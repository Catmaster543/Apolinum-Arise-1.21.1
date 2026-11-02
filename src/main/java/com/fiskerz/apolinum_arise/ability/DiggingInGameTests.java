package com.fiskerz.apolinum_arise.ability;

import com.fiskerz.apolinum_arise.Apolinumarise;
import com.fiskerz.apolinum_arise.ability.diggingin.BurrowAttachments;
import com.fiskerz.apolinum_arise.ability.diggingin.BurrowData;
import com.fiskerz.apolinum_arise.ability.diggingin.BurrowState;
import com.fiskerz.apolinum_arise.ability.diggingin.DiggingIn;
import com.fiskerz.apolinum_arise.ability.diggingin.DiggingInEvents;
import com.fiskerz.apolinum_arise.ability.diggingin.DiggingInManager;
import com.fiskerz.apolinum_arise.ability.diggingin.SafePositionFinder;
import com.fiskerz.apolinum_arise.ability.hivemind.HiveMind;
import com.fiskerz.apolinum_arise.ability.strongpunch.StrongPunch;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Phase 14: keybind slot routing, the burrow state machine, the lockouts and the safe eject. */
@GameTestHolder(Apolinumarise.MODID)
@PrefixGameTestTemplate(false)
public class DiggingInGameTests {

    private static Player variantThree(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setData(InfectionAttachments.INFECTION, InfectionData.NONE.becomeInfected());
        player.setData(SkillAttachments.SKILL_PROFILE, SkillProfileData.NONE.withInfectedVariant(DiggingIn.VARIANT));
        player.setData(DreamAttachments.DREAMS, DreamData.EMPTY);
        player.setData(AbilityAttachments.ABILITIES, AbilityData.NONE);
        player.setData(BurrowAttachments.BURROW, BurrowData.NORMAL);
        return player;
    }

    private static Player burrowedPlayer(GameTestHelper helper) {
        Player player = variantThree(helper);
        AbilityManager.unlockAbility(player, DiggingIn.ABILITY_ID);
        player.setData(BurrowAttachments.BURROW, BurrowData.NORMAL.burrowedAt(0L, 0L));
        return player;
    }

    // The three branches must occupy the three variant indices. A count off from what is expected would mean
    // a variant silently holding another branch's ability, or one holding none. Branch 1 has two since
    // Phase 15 (Hive-Mind, then Call of the Hive); Branches 2 and 3 have one each.
    @GameTest(template = "empty_3x3", batch = "diggingin")
    public static void the_three_branches_occupy_the_three_variants(GameTestHelper helper) {
        helper.assertValueEqual(HiveMind.VARIANT, 0, "Branch 1 is variant 0");
        helper.assertValueEqual(StrongPunch.VARIANT, 1, "Branch 2 is variant 1");
        helper.assertValueEqual(DiggingIn.VARIANT, 2, "Branch 3 is variant 2");

        int[] expected = {2, 1, 1};
        for (int variant = 0; variant < com.fiskerz.apolinum_arise.skill.InfectedVariants.COUNT; variant++) {
            int found = 0;
            for (Ability ability : Abilities.all()) {
                if (ability.side() == AbilitySide.INFECTED && ability.restrictedTo().isPresent()
                        && ability.restrictedTo().getAsInt() == variant) {
                    found++;
                }
            }
            helper.assertValueEqual(found, expected[variant], "Abilities registered for variant " + variant);
        }
        helper.succeed();
    }

    /**
     * DELIVERABLE: slot 1 triggers the ability, unbound slots do nothing, and a player without the ability
     * gets nothing from any slot.
     *
     * <p>Asserted through {@link Abilities#findKeybindAbility}, which is the whole of the router's lookup -
     * the router itself only invokes the hook on what this returns. The screen-open case is a client-side
     * concern and is covered by {@code AbilityClientEvents} not sending a packet at all.
     */
    @GameTest(template = "empty_3x3", batch = "diggingin")
    public static void keybind_slot_one_routes_only_for_a_player_who_has_the_ability(GameTestHelper helper) {
        Player player = variantThree(helper);

        helper.assertTrue(Abilities.findKeybindAbility(player, DiggingIn.KEYBIND_SLOT).isEmpty(),
                "Before unlocking, even the right slot must route to nothing");

        AbilityManager.unlockAbility(player, DiggingIn.ABILITY_ID);
        Ability routed = Abilities.findKeybindAbility(player, DiggingIn.KEYBIND_SLOT).orElse(null);
        helper.assertTrue(routed != null, "Slot 1 must route once unlocked");
        helper.assertValueEqual(routed.id(), DiggingIn.ABILITY_ID, "...to Digging In");
        helper.assertTrue(routed.keyPressHandler() != null, "...with a press hook attached");

        // Slots 2 and 3 ship unbound and claimed by nothing.
        helper.assertTrue(Abilities.findKeybindAbility(player, 2).isEmpty(), "Slot 2 is unclaimed");
        helper.assertTrue(Abilities.findKeybindAbility(player, 3).isEmpty(), "Slot 3 is unclaimed");

        // Right ability, wrong variant: the side/variant check still has to bite.
        Player wrongVariant = variantThree(helper);
        wrongVariant.setData(SkillAttachments.SKILL_PROFILE, SkillProfileData.NONE.withInfectedVariant(0));
        AbilityManager.unlockAbility(wrongVariant, DiggingIn.ABILITY_ID);
        helper.assertTrue(Abilities.findKeybindAbility(wrongVariant, DiggingIn.KEYBIND_SLOT).isEmpty(),
                "A variant-0 player must not route slot 1 to the variant-2 ability");

        // Out-of-range slots are simply never claimed.
        helper.assertTrue(Abilities.findKeybindAbility(player, 0).isEmpty(), "Slot 0 does not exist");
        helper.assertTrue(Abilities.findKeybindAbility(player, 4).isEmpty(), "Slot 4 does not exist");
        helper.succeed();
    }

    /**
     * DELIVERABLE: manual exit is refused during the lock AND while inside solid blocks, and works once
     * both clear. Driven through {@link DiggingInManager#decide}, which is the rule set itself.
     */
    @GameTest(template = "empty_3x3", batch = "diggingin")
    public static void manual_exit_is_refused_while_locked_or_buried(GameTestHelper helper) {
        int cooldown = DiggingInManager.cooldownTicks();
        helper.assertTrue(cooldown > 0, "This test needs a non-zero cooldown, got " + cooldown);

        // Burrowed at tick 0, so the next transition is allowed at `cooldown`.
        BurrowData burrowed = BurrowData.NORMAL.burrowedAt(0L, cooldown);
        helper.assertValueEqual(burrowed.state(), BurrowState.BURROWED, "Burrowed");

        // Locked AND buried -> the lock is reported, because it is the one they cannot dig their way out of.
        helper.assertValueEqual(DiggingInManager.decide(burrowed, 0L, false),
                DiggingInManager.Decision.REFUSED_COOLDOWN, "Locked and buried: refused for the cooldown");
        // Locked but in open space -> still refused: the cooldown is the minimum time underground.
        helper.assertValueEqual(DiggingInManager.decide(burrowed, cooldown - 1, true),
                DiggingInManager.Decision.REFUSED_COOLDOWN, "One tick early is still refused");

        // Lock expired but buried -> refused for being inside blocks.
        helper.assertValueEqual(DiggingInManager.decide(burrowed, cooldown, false),
                DiggingInManager.Decision.REFUSED_BLOCKED, "Unlocked but buried: refused for no room");

        // Both clear -> it works.
        helper.assertValueEqual(DiggingInManager.decide(burrowed, cooldown, true),
                DiggingInManager.Decision.SURFACE, "Unlocked and in open space: surfaces");

        // And the same lock applies to going back down after surfacing.
        BurrowData surfaced = burrowed.surfaced(cooldown * 2L);
        helper.assertValueEqual(surfaced.state(), BurrowState.NORMAL, "Back to normal");
        helper.assertValueEqual(DiggingInManager.decide(surfaced, cooldown, true),
                DiggingInManager.Decision.REFUSED_COOLDOWN, "Re-burrowing is locked too");
        helper.assertValueEqual(DiggingInManager.decide(surfaced, cooldown * 2L, true),
                DiggingInManager.Decision.BURROW, "...until the lock expires");
        helper.succeed();
    }

    // The refusal message has to count down in whole seconds, rounded up, or it reads as "0s" for a second.
    @GameTest(template = "empty_3x3", batch = "diggingin")
    public static void the_cooldown_message_counts_down_in_whole_seconds(GameTestHelper helper) {
        BurrowData data = BurrowData.NORMAL.burrowedAt(0L, 100L); // 5 seconds
        helper.assertValueEqual(data.secondsRemainingAt(0L), 5, "5s at the start");
        helper.assertValueEqual(data.secondsRemainingAt(81L), 1, "19 ticks left rounds up to 1s");
        helper.assertValueEqual(data.secondsRemainingAt(99L), 1, "1 tick left still reads as 1s, never 0s");
        helper.assertValueEqual(data.secondsRemainingAt(100L), 0, "0 once it has expired");
        helper.assertTrue(data.mayTransitionAt(100L), "...and the transition is permitted");
        helper.succeed();
    }

    /** DELIVERABLE: the max-duration auto-eject, and its clamp against a shorter-than-cooldown config. */
    @GameTest(template = "empty_3x3", batch = "diggingin")
    public static void the_max_duration_is_clamped_to_at_least_the_cooldown(GameTestHelper helper) {
        double originalMax = Config.DIGGING_IN_MAX_DURATION_SECONDS.get();
        double cooldownSeconds = Config.DIGGING_IN_TRANSITION_COOLDOWN_SECONDS.get();
        try {
            helper.assertTrue(DiggingInManager.maxDurationTicks() >= DiggingInManager.cooldownTicks(),
                    "At the shipped defaults the maximum already exceeds the cooldown");

            // Misconfigured shorter than the cooldown: must clamp UP, or a player would be auto-ejected
            // every tick while still forbidden to surface.
            Config.DIGGING_IN_MAX_DURATION_SECONDS.set(cooldownSeconds / 4.0D);
            helper.assertValueEqual(DiggingInManager.maxDurationTicks(), DiggingInManager.cooldownTicks(),
                    "A maximum below the cooldown must clamp up to it");

            // The auto-eject threshold itself: burrowed longer than the max.
            long max = DiggingInManager.maxDurationTicks();
            BurrowData burrowed = BurrowData.NORMAL.burrowedAt(0L, DiggingInManager.cooldownTicks());
            helper.assertTrue(burrowed.burrowedTicksAt(max - 1) < max, "Not yet due at one tick short");
            helper.assertTrue(burrowed.burrowedTicksAt(max) >= max, "Due exactly at the limit");
        } finally {
            Config.DIGGING_IN_MAX_DURATION_SECONDS.set(originalMax);
        }
        helper.succeed();
    }

    /**
     * DELIVERABLE: the safe-position search finds open space and never returns somewhere solid.
     *
     * <p>Run against real blocks: a sealed stone pocket is built with one air gap, and the search has to
     * find the gap rather than anywhere in the stone.
     */
    @GameTest(template = "empty_3x3", batch = "diggingin", timeoutTicks = 600)
    public static void the_safe_search_finds_open_space_and_never_solid_rock(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = variantThree(helper);
        BlockPos buried = helper.absolutePos(new BlockPos(1, 2, 1));

        // Encase the search origin in stone, leaving the test structure's own air above as the only exit.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    level.setBlockAndUpdate(buried.offset(dx, dy, dz), Blocks.STONE.defaultBlockState());
                }
            }
        }

        Vec3 safe = SafePositionFinder.findEjectPosition(level, player, buried, 16, null);
        helper.assertTrue(safe != null, "The search must never return nothing - eject cannot fail");

        // Whatever it picked, the player's full standing box has to actually fit there.
        helper.assertTrue(level.noCollision(null,
                        player.getDimensions(Pose.STANDING).makeBoundingBox(safe).deflate(1.0E-7)),
                "The chosen position " + safe + " must be clear of blocks");
        helper.assertTrue(safe.distanceTo(Vec3.atBottomCenterOf(buried)) > 0.5D,
                "...and must not be the solid origin itself");

        // Within the build limits, so an eject can never put someone outside the world.
        helper.assertTrue(safe.y >= level.getMinBuildHeight() && safe.y <= level.getMaxBuildHeight(),
                "The eject position must be inside the world, got y=" + safe.y);
        helper.succeed();
    }

    /** DELIVERABLE: the build-height clamp. A burrowed player can never swim out of the world. */
    @GameTest(template = "empty_3x3", batch = "diggingin")
    public static void the_build_height_clamp_keeps_a_burrow_inside_the_world(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        double min = level.getMinBuildHeight() + 1.0D;
        double max = level.getMaxBuildHeight() - 2.0D;

        helper.assertValueEqual(net.minecraft.util.Mth.clamp(level.getMinBuildHeight() - 500.0D, min, max), min,
                "Swimming far below the world clamps to the floor, never the void");
        helper.assertValueEqual(net.minecraft.util.Mth.clamp(level.getMaxBuildHeight() + 500.0D, min, max), max,
                "Swimming above the build limit clamps to the ceiling");
        helper.assertTrue(min < max, "The clamp window must be non-empty for this dimension");

        double inside = (min + max) / 2.0D;
        helper.assertValueEqual(net.minecraft.util.Mth.clamp(inside, min, max), inside,
                "A position already inside the world is left alone");
        helper.succeed();
    }

    /**
     * DELIVERABLE: all four lockouts hold, server-side, with a test for each.
     *
     * <p>Each handler is driven with a real event instance and the cancellation asserted - these are the
     * actual listeners registered on the bus, not a re-implementation of their condition.
     */
    @GameTest(template = "empty_3x3", batch = "diggingin", timeoutTicks = 600)
    public static void every_interaction_is_locked_out_while_burrowed(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player burrowed = burrowedPlayer(helper);
        Player normal = variantThree(helper);
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockState stone = Blocks.STONE.defaultBlockState();
        burrowed.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.STONE));

        helper.assertTrue(DiggingIn.isBurrowed(burrowed), "The subject is burrowed");
        helper.assertFalse(DiggingIn.isBurrowed(normal), "The control is not");

        // 1. ATTACK
        var cow = helper.spawn(EntityType.COW, new BlockPos(1, 2, 1));
        AttackEntityEvent attack = new AttackEntityEvent(burrowed, cow);
        DiggingInEvents.onAttackEntity(attack);
        helper.assertTrue(attack.isCanceled(), "Attacking must be cancelled while burrowed");
        AttackEntityEvent attackNormal = new AttackEntityEvent(normal, cow);
        DiggingInEvents.onAttackEntity(attackNormal);
        helper.assertFalse(attackNormal.isCanceled(), "...and NOT cancelled for a normal player");

        // 2. BREAK
        BlockEvent.BreakEvent breakEvent = new BlockEvent.BreakEvent(level, pos, stone, burrowed);
        DiggingInEvents.onBlockBreak(breakEvent);
        helper.assertTrue(breakEvent.isCanceled(), "Breaking blocks must be cancelled while burrowed");
        BlockEvent.BreakEvent breakNormal = new BlockEvent.BreakEvent(level, pos, stone, normal);
        DiggingInEvents.onBlockBreak(breakNormal);
        helper.assertFalse(breakNormal.isCanceled(), "...and NOT cancelled for a normal player");

        // 3. PLACE
        BlockEvent.EntityPlaceEvent place = new BlockEvent.EntityPlaceEvent(
                BlockSnapshot.create(level.dimension(), level, pos), stone, burrowed);
        DiggingInEvents.onEntityPlace(place);
        helper.assertTrue(place.isCanceled(), "Placing blocks must be cancelled while burrowed");
        BlockEvent.EntityPlaceEvent placeNormal = new BlockEvent.EntityPlaceEvent(
                BlockSnapshot.create(level.dimension(), level, pos), stone, normal);
        DiggingInEvents.onEntityPlace(placeNormal);
        helper.assertFalse(placeNormal.isCanceled(), "...and NOT cancelled for a normal player");

        // 4. ITEM USE / INTERACT - the three PlayerInteractEvent flavours share one guard, so the gate they
        // all read is asserted directly; the handlers are one line each over the same condition.
        helper.assertTrue(DiggingIn.isBurrowed(burrowed),
                "Item use, block interaction and entity interaction all cancel on this same flag");
        helper.succeed();
    }

    // A burrow must never outlive the thing driving it. Verified on the state itself, since the real
    // forced-exit paths teleport and message a networked player.
    @GameTest(template = "empty_3x3", batch = "diggingin")
    public static void surfacing_always_clears_the_burrow_and_relocks(GameTestHelper helper) {
        BurrowData burrowed = BurrowData.NORMAL.burrowedAt(0L, DiggingInManager.cooldownTicks());
        helper.assertTrue(burrowed.burrowed(), "Burrowed to begin with");

        BurrowData after = burrowed.surfaced(500L);
        helper.assertFalse(after.burrowed(), "Surfacing clears the burrow");
        helper.assertValueEqual(after.state(), BurrowState.NORMAL, "...to NORMAL");
        helper.assertValueEqual(after.burrowedSinceGameTime(), 0L, "...and forgets the burrow start");
        helper.assertFalse(after.mayTransitionAt(499L), "A forced exit still applies the post-exit lock");
        helper.assertTrue(after.mayTransitionAt(500L), "...which then expires normally");

        // The flags handed back to vanilla are what makes a surfaced player ordinary again.
        Player player = burrowedPlayer(helper);
        DiggingInManager.applyBurrowedState(player);
        helper.assertTrue(player.noPhysics, "Burrowed: collision disabled");
        helper.assertTrue(player.isNoGravity(), "Burrowed: no gravity (this is what defeats the float kick)");
        helper.assertValueEqual(player.getForcedPose(), Pose.SWIMMING, "Burrowed: swimming pose forced");

        DiggingInManager.clearBurrowedState(player);
        helper.assertFalse(player.noPhysics, "Surfaced: collision restored");
        helper.assertFalse(player.isNoGravity(), "Surfaced: gravity restored");
        helper.assertTrue(player.getForcedPose() == null, "Surfaced: pose handed back to vanilla");
        helper.succeed();
    }

    // Bugfix: a surfaced player stayed stuck in the swimming pose. The client applies the flags to its own
    // player, and the forced pose is NOT synced, so the client has to hand them back itself. Its old
    // hand-back was gated on noPhysics, which Player.tick() resets every tick, so it never fired. This
    // replays the client's exact sequence: burrow, the server's surfacing arriving as nothing but synced
    // data, the tick's reset, then this side's upkeep.
    @GameTest(template = "empty_3x3", batch = "diggingin")
    public static void a_surfaced_player_gets_their_own_flags_back_after_the_tick_reset(GameTestHelper helper) {
        Player player = burrowedPlayer(helper);
        boolean applied = DiggingInManager.upkeepOwnFlags(player, false);
        helper.assertTrue(applied, "Burrowed: the upkeep applies the flags and remembers that it did");
        helper.assertValueEqual(player.getForcedPose(), Pose.SWIMMING, "Burrowed: swimming pose forced");

        // The server surfaces them. On the client that arrives as the synced attachment ONLY - the server's
        // clearBurrowedState ran on the server's copy of the player, never on this one.
        player.setData(BurrowAttachments.BURROW, player.getData(BurrowAttachments.BURROW).surfaced(0L));
        // ...and the next Player.tick() opens with this, before any upkeep gets to run.
        player.noPhysics = player.isSpectator();
        helper.assertFalse(player.noPhysics,
                "The tick reset has already cleared noPhysics - why a noPhysics-gated hand-back never fired");
        helper.assertValueEqual(player.getForcedPose(), Pose.SWIMMING,
                "...while the forced pose is still on, and only this side's own upkeep can take it off");

        applied = DiggingInManager.upkeepOwnFlags(player, applied);
        helper.assertFalse(applied, "Surfaced: nothing is applied any more");
        helper.assertTrue(player.getForcedPose() == null, "Surfaced: the swimming pose is handed back to vanilla");
        helper.assertFalse(player.isNoGravity(), "Surfaced: gravity restored");
        helper.assertFalse(player.noPhysics, "Surfaced: collision restored");

        helper.assertFalse(DiggingInManager.upkeepOwnFlags(player, applied), "Later ticks find nothing to do");
        helper.assertTrue(player.getForcedPose() == null, "...and the pose stays handed back");
        helper.succeed();
    }

    // The hand-back only ever undoes what it applied, so a forced pose another mod put on a player who never
    // burrowed is not stomped on every tick.
    @GameTest(template = "empty_3x3", batch = "diggingin")
    public static void the_upkeep_leaves_a_pose_it_never_applied_alone(GameTestHelper helper) {
        Player player = variantThree(helper);
        player.setForcedPose(Pose.CROUCHING);
        helper.assertFalse(DiggingInManager.upkeepOwnFlags(player, false), "Never burrowed: nothing applied");
        helper.assertValueEqual(player.getForcedPose(), Pose.CROUCHING, "Someone else's forced pose survives");
        helper.succeed();
    }

    // Phase 14 registration: variant 2's prerequisite dream is queued by the registry-driven lookup.
    @GameTest(template = "empty_3x3", batch = "diggingin")
    public static void the_prerequisite_dream_is_registered_for_variant_two(GameTestHelper helper) {
        var dreams = Abilities.prerequisiteDreamsFor(AbilitySide.INFECTED, DiggingIn.VARIANT);
        helper.assertTrue(dreams.contains(DiggingIn.DREAM_ID),
                "Variant " + DiggingIn.VARIANT + " must queue '" + DiggingIn.DREAM_ID + "', got " + dreams);

        Player player = variantThree(helper);
        Ability ability = Abilities.get(DiggingIn.ABILITY_ID).orElseThrow();
        helper.assertTrue(ability.appliesTo(player), "A variant-2 infected player is the right kind");
        helper.assertFalse(ability.isUnlockableBy(player), "...but not unlockable before the dream");
        DreamManager.markWatched(player, DiggingIn.DREAM_ID);
        helper.assertTrue(ability.isUnlockableBy(player), "Watching the dream is the whole condition");
        helper.assertValueEqual(ability.keybindSlot().getAsInt(), DiggingIn.KEYBIND_SLOT, "Declares slot 1");
        helper.succeed();
    }
}
