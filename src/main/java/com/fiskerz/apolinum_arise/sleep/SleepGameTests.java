package com.fiskerz.apolinum_arise.sleep;

import com.fiskerz.apolinum_arise.Apolinumarise;
import com.fiskerz.apolinum_arise.config.Config;
import com.fiskerz.apolinum_arise.downed.DownedManager;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Apolinumarise.MODID)
@PrefixGameTestTemplate(false)
public class SleepGameTests {

    private static final float TOLERANCE = 0.01F;

    // The headline calibration after the 1.5x rebalance: one full night of lying restores 100/2 = 50%, so
    // 2 nights out of every 3 exactly cover the 3-day drain (rather than needing every single night).
    @GameTest(template = "empty_3x3", batch = "sleep_rates")
    public static void refill_calibration_two_nights_sustain_three_days(GameTestHelper helper) {
        float drainPerDay = SleepManager.drainPerTick() * Level.TICKS_PER_DAY;
        float refillPerNight = SleepManager.nightRefillPerTick() * SleepManager.NIGHT_DURATION_TICKS;

        helper.assertTrue(Math.abs(drainPerDay - (float) (100.0D / 3.0D)) < TOLERANCE,
                "One full day of drain must be 100/3 = 33.33% at the default 3-day drain, got " + drainPerDay);

        // The rate formula must hold for whatever multiplier this world is actually running, since an
        // already-generated server config keeps its own value rather than picking up a new code default.
        double liveMultiplier = Config.SLEEP_BAR_NIGHT_REFILL_MULTIPLIER.get();
        float expectedLive = (float) (100.0D / 3.0D * liveMultiplier);
        helper.assertTrue(Math.abs(refillPerNight - expectedLive) < TOLERANCE,
                "A full night must restore 100/3 x multiplier(" + liveMultiplier + ") = " + expectedLive
                        + "%, got " + refillPerNight);

        // The rebalance itself is the SHIPPED DEFAULT: 1.5 -> 50% a night, so 2 nights cover 3 days' drain.
        double defaultMultiplier = Config.SLEEP_BAR_NIGHT_REFILL_MULTIPLIER.getDefault();
        helper.assertTrue(Math.abs(defaultMultiplier - 1.5D) < 0.0001D,
                "The shipped sleepBarNightRefillMultiplier default must be 1.5, got " + defaultMultiplier);
        float defaultPerNight = (float) (100.0D / 3.0D * defaultMultiplier);
        helper.assertTrue(Math.abs(defaultPerNight - 50.0F) < TOLERANCE,
                "At the default multiplier one night must restore 100/2 = 50%, got " + defaultPerNight);
        float twoNights = 2.0F * defaultPerNight;
        float threeDays = 3.0F * drainPerDay;
        helper.assertTrue(Math.abs(twoNights - 100.0F) < TOLERANCE,
                "2 full nights must restore a whole bar (100%), got " + twoNights);
        helper.assertTrue(Math.abs(twoNights - threeDays) < TOLERANCE,
                "2 nights of refill must exactly cancel 3 days of drain, got " + twoNights + " vs " + threeDays);

        // And the whole bar really does take sleepBarDrainDays to empty from full.
        float ticksToEmpty = 100.0F / SleepManager.drainPerTick();
        float expectedTicks = (float) (Config.SLEEP_BAR_DRAIN_DAYS.get() * Level.TICKS_PER_DAY);
        helper.assertTrue(Math.abs(ticksToEmpty - expectedTicks) < 1.0F,
                "100% -> 0% must take exactly sleepBarDrainDays; got " + ticksToEmpty + " ticks");
        helper.succeed();
    }

    // The escalation clock is measured in whole days at zero, and the two thresholds are ordered.
    @GameTest(template = "empty_3x3", batch = "sleep_rates")
    public static void zero_escalation_thresholds(GameTestHelper helper) {
        int slowness = SleepManager.slownessTicks();
        int passOut = SleepManager.passOutTicks();
        helper.assertValueEqual(slowness,
                (int) Math.round(Config.SLEEP_ZERO_SLOWNESS_DAYS.get() * Level.TICKS_PER_DAY),
                "Slowness threshold in ticks");
        helper.assertValueEqual(passOut,
                (int) Math.round(Config.SLEEP_ZERO_PASS_OUT_DAYS.get() * Level.TICKS_PER_DAY),
                "Pass-out threshold in ticks");
        helper.assertTrue(slowness < passOut, "Slowness must escalate before the pass-out");
        helper.succeed();
    }

    // Pass-out reuses the downed PRESENTATION (isIncapacitated) without becoming the downed MECHANIC
    // (isDowned stays false: no timer, no revive, no death interception, still fully vulnerable).
    @GameTest(template = "empty_3x3", batch = "sleep_passout")
    public static void pass_out_is_incapacitated_but_not_downed(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setData(SleepAttachments.SLEEP, SleepData.FULL);
        helper.assertFalse(DownedManager.isIncapacitated(player), "A rested player is not incapacitated");

        player.setData(SleepAttachments.SLEEP, SleepData.FULL.with(0.0F, 0, true, 2, 45.0F, true));
        helper.assertTrue(SleepManager.isPassedOut(player), "Player is passed out");
        helper.assertTrue(DownedManager.isIncapacitated(player),
                "Pass-out must drive the shared presentation predicate (camera/HUD/input lock/pose)");
        helper.assertFalse(DownedManager.isDowned(player),
                "Pass-out must NOT be the downed mechanic (no timer, no revive, still killable)");

        // Coming round clears the shared presentation again.
        player.setData(SleepAttachments.SLEEP, SleepData.FULL.with(5.0F, 0, false, 2, 45.0F, true));
        helper.assertFalse(DownedManager.isIncapacitated(player), "Waking up releases the presentation lock");
        helper.succeed();
    }

    // "Rejoining is of no use": the pass-out flag (and the bar/clock behind it) survive a serialize round
    // trip, which is exactly what the attachment does across a logout/login.
    @GameTest(template = "empty_3x3", batch = "sleep_passout")
    public static void pass_out_survives_serialization(GameTestHelper helper) {
        SleepData original = new SleepData(0.0F, 72_000, true, 1, 123.5F, true);
        Tag encoded = SleepData.CODEC.encodeStart(NbtOps.INSTANCE, original)
                .getOrThrow(error -> new AssertionError("encode failed: " + error));
        SleepData restored = SleepData.CODEC.parse(NbtOps.INSTANCE, encoded)
                .getOrThrow(error -> new AssertionError("decode failed: " + error));

        helper.assertTrue(restored.passedOut(), "passedOut must persist across a relog");
        helper.assertValueEqual(restored.ticksAtZero(), original.ticksAtZero(), "ticksAtZero persists");
        helper.assertValueEqual(restored.poseVariant(), original.poseVariant(), "poseVariant persists (same pose on rejoin)");
        helper.assertTrue(Math.abs(restored.sleepBar() - original.sleepBar()) < TOLERANCE, "sleepBar persists");
        helper.assertTrue(Math.abs(restored.bodyYaw() - original.bodyYaw()) < TOLERANCE, "bodyYaw persists (frozen facing)");
        helper.assertTrue(restored.healthy(), "healthy (bite eligibility) persists");
        helper.succeed();
    }

    // A passed-out player is now a valid bite target alongside a really-downed one, and a player who is
    // merely lying in a bed is NOT incapacitated - so lying grants no bite-target status and, crucially,
    // no immunity either: they stay an ordinary, fully vulnerable player.
    @GameTest(template = "empty_3x3", batch = "sleep_passout")
    public static void pass_out_is_bitable_and_lying_grants_no_immunity(GameTestHelper helper) {
        Player target = helper.makeMockPlayer(GameType.SURVIVAL);
        target.setData(SleepAttachments.SLEEP, SleepData.FULL);
        helper.assertFalse(DownedManager.isIncapacitated(target),
                "A rested, awake player is not a bite target");

        // Passed out + clean = the state the bite check accepts.
        target.setData(SleepAttachments.SLEEP, SleepData.FULL.with(0.0F, 0, true, 0, 0.0F, true));
        helper.assertTrue(DownedManager.isIncapacitated(target),
                "A passed-out player must satisfy the bite target's incapacitation check");
        helper.assertTrue(target.getData(SleepAttachments.SLEEP).healthy(),
                "The broadcast healthy flag is what a biter's client reads for eligibility");

        // An infected/incubating passed-out player is NOT bitable (already in the state machine).
        target.setData(SleepAttachments.SLEEP, SleepData.FULL.with(0.0F, 0, true, 0, 0.0F, false));
        helper.assertFalse(target.getData(SleepAttachments.SLEEP).healthy(),
                "A non-clean passed-out player is not an eligible bite target");

        // Sleeping in a bed is not an incapacitation: no special status in either direction.
        target.setData(SleepAttachments.SLEEP, SleepData.FULL);
        target.startSleeping(helper.absolutePos(new BlockPos(1, 1, 1)));
        helper.assertTrue(target.isSleeping(), "Target is lying down");
        helper.assertFalse(DownedManager.isIncapacitated(target),
                "Lying in a bed must NOT grant incapacitated status - no bite-immunity, no de-aggro");
        // The bite's interrupt: ending the sleep is what hands camera/control back (and will end a dream).
        target.stopSleeping();
        helper.assertFalse(target.isSleeping(), "stopSleeping ends the lie-down/dream and restores control");
        helper.succeed();
    }
}
