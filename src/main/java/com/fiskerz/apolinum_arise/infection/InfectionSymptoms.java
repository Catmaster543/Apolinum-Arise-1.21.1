package com.fiskerz.apolinum_arise.infection;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.fiskerz.apolinum_arise.bloodmoon.BloodMoonState;
import com.fiskerz.apolinum_arise.config.Config;
import com.fiskerz.apolinum_arise.network.MoleSyncPayload;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Server-side driver of the incubation symptom timeline (Phase 5). Everything here applies ONLY while
 * a player {@link InfectionData#incubating() is incubating}; the moment Phase 4 flips them to infected,
 * {@link #endSymptoms} clears all applied effects and mole growth stops (the day-rise hooks that grow
 * moles no longer run). The per-day shape comes from {@link DayProfile}; magnitudes come from
 * {@link Config} read live.
 */
public final class InfectionSymptoms {
    private InfectionSymptoms() {}

    private static final int REFRESH_FUDGE_TICKS = 20;
    private static final String WITHER_MESSAGE_KEY = "message.apolinumarise.symptom.day10";
    // Part B: infected-only constants.
    private static final float SUN_IGNITE_SECONDS = 8.0F; // vanilla undead sun ignition (Zombie)
    // Long enough that a night-effect never dips below Night Vision's 200-tick flash threshold between
    // refreshes, so the screen never pulses; expires shortly after dawn.
    private static final int NIGHT_EFFECT_DURATION_TICKS = 400;
    // Usable inventory space for force-unequipped armor: hotbar (0-8), main row closest to the hotbar
    // (27-35), offhand (40) - exactly the slots the restricted inventory keeps functional.
    private static final int[] USABLE_UNEQUIP_SLOTS = buildUsableUnequipSlots();

    // Incubation-symptom effects, cleared exactly at the infection transition.
    private static final List<Holder<MobEffect>> SYMPTOM_EFFECTS = List.of(
            MobEffects.CONFUSION, MobEffects.WEAKNESS, MobEffects.DIG_SLOWDOWN,
            MobEffects.BLINDNESS, MobEffects.POISON, MobEffects.HUNGER, MobEffects.WITHER);
    // Infected night buffs, cleared when infection is cleared back to healthy.
    private static final List<Holder<MobEffect>> INFECTED_EFFECTS = List.of(
            MobEffects.NIGHT_VISION, MobEffects.DAMAGE_BOOST, MobEffects.MOVEMENT_SPEED, MobEffects.DIG_SPEED);

    private static int[] buildUsableUnequipSlots() {
        int[] slots = new int[19];
        int i = 0;
        for (int hotbar = 0; hotbar <= 8; hotbar++) slots[i++] = hotbar;
        for (int mainRow = 27; mainRow <= 35; mainRow++) slots[i++] = mainRow;
        slots[i] = 40; // offhand
        return slots;
    }

    // Transient per-player runtime (not persisted): incubation edge-detection + sun-exposure timer.
    private static final Map<UUID, Runtime> RUNTIME = new ConcurrentHashMap<>();

    private static final class Runtime {
        boolean wasIncubating;
        int sunExposureTicks;
    }

    // ------------------------------------------------------------------ tick entry point

    public static void onServerTick(MinecraftServer server) {
        if (!Config.ENABLE_MOD.get()) {
            return;
        }
        ServerLevel overworld = server.overworld();
        int currentDay = (int) (overworld.getDayTime() / Level.TICKS_PER_DAY);
        long gameTime = overworld.getGameTime();
        boolean isNight = overworld.isNight();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            tickPlayer(player, overworld, currentDay, gameTime, isNight);
        }
    }

    private static void tickPlayer(ServerPlayer player, ServerLevel overworld, int currentDay, long gameTime, boolean isNight) {
        InfectionData infection = player.getData(InfectionAttachments.INFECTION);
        Runtime rt = RUNTIME.computeIfAbsent(player.getUUID(), u -> new Runtime());

        if (!infection.incubating()) {
            // Edge: incubation just ended. If it became infection, run the one-time transition; if it
            // was cleared to healthy, just drop the symptom effects. Moles freeze on their own.
            if (rt.wasIncubating) {
                rt.wasIncubating = false;
                if (infection.infected()) {
                    onBecameInfected(player);
                } else {
                    clearSymptomEffects(player);
                }
            }
            // Ongoing infected-only mechanics (Phase 6 Part B).
            if (infection.infected()) {
                tickInfected(player, overworld, gameTime, isNight);
            }
            return;
        }

        rt.wasIncubating = true;
        int dayNumber = dayNumber(infection, currentDay);
        DayProfile profile = DayProfile.forDay(dayNumber);

        processDayRises(player, dayNumber);
        handleSunsetDeaggro(player, dayNumber, currentDay, isNight);

        tickEarlyRoller(player, profile, gameTime);
        tickMainPool(player, profile, dayNumber, gameTime);
        tickContinuous(player, profile, dayNumber, rt, gameTime);
    }

    private static int dayNumber(InfectionData infection, int currentDay) {
        int delta = currentDay - infection.infectionStartDay();
        return Math.max(1, Math.min(DayProfile.maxDay(), delta + 1));
    }

    // ------------------------------------------------------------------ day-start ("rise") hooks

    // Fires each newly-entered day's start actions once. Seeds the tracker on first sighting so the
    // bite day never back-fires; replays any days skipped while offline so moles age correctly.
    private static void processDayRises(ServerPlayer player, int dayNumber) {
        SymptomTracker tracker = player.getData(SymptomAttachments.SYMPTOMS);
        int last = tracker.lastDayProcessed();
        if (last == 0) {
            player.setData(SymptomAttachments.SYMPTOMS, tracker.withLastDayProcessed(dayNumber));
            return;
        }
        if (dayNumber <= last) {
            return;
        }
        for (int day = last + 1; day <= dayNumber; day++) {
            processDayRise(player, day, day == dayNumber);
        }
        // Re-read: mole hooks may have rewritten the attachment.
        player.setData(SymptomAttachments.SYMPTOMS,
                player.getData(SymptomAttachments.SYMPTOMS).withLastDayProcessed(dayNumber));
    }

    private static void processDayRise(ServerPlayer player, int day, boolean isCurrentDay) {
        DayProfile profile = DayProfile.forDay(day);

        // 1) grow moles that already existed at the start of this day.
        growMoles(player);

        // 2) roll this day's chance to grow a brand-new mole.
        if (profile.rollsMole()) {
            double chance = Config.getMoleChanceForDay(day);
            if (player.getRandom().nextDouble() < chance) {
                addMole(player, day);
            }
        }

        // 3) day-start actionbar - only for the day the player is actually on (no spam for skipped days).
        if (isCurrentDay && profile.actionbarKey() != null) {
            player.displayClientMessage(Component.translatable(profile.actionbarKey()), true);
        }
    }

    // ------------------------------------------------------------------ day-9 sunset enemy de-aggro

    private static void handleSunsetDeaggro(ServerPlayer player, int dayNumber, int currentDay, boolean isNight) {
        if (dayNumber != 9 || !isNight) {
            return;
        }
        SymptomTracker tracker = player.getData(SymptomAttachments.SYMPTOMS);
        if (tracker.lastSunsetDay() == currentDay) {
            return; // already latched this calendar day's sunset
        }
        player.setData(SymptomAttachments.SYMPTOMS,
                tracker.withEnemiesIgnore(true).withLastSunsetDay(currentDay));
        // Drop any hostile that is already locked onto this player right now.
        clearCurrentAttackers(player);
    }

    private static void clearCurrentAttackers(ServerPlayer player) {
        List<Mob> attackers = player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(64.0D),
                mob -> mob instanceof Enemy && mob.getTarget() == player);
        for (Mob mob : attackers) {
            mob.setTarget(null);
        }
    }

    /**
     * True when hostile mobs (and mosquitoes) should ignore this player: permanently once fully infected
     * (Phase 6 B4, all dimensions), or from the day-9 sunset latch onward during incubation (Phase 5).
     * Takes a {@link net.minecraft.world.entity.player.Player} (server-side targets are always players)
     * so gametests can exercise it with a plain mock player.
     */
    public static boolean enemiesIgnore(net.minecraft.world.entity.player.Player player) {
        return player.getData(InfectionAttachments.INFECTION).infected()
                || player.getData(SymptomAttachments.SYMPTOMS).enemiesIgnore();
    }

    // ------------------------------------------------------------------ days 1-2 early roller

    private static void tickEarlyRoller(ServerPlayer player, DayProfile profile, long gameTime) {
        if (!profile.earlyRoller()) {
            return;
        }
        if (gameTime % Config.SYMPTOM_EARLY_ROLL_INTERVAL_TICKS.get() != 0L) {
            return;
        }
        RandomSource random = player.getRandom();
        if (random.nextDouble() >= Config.SYMPTOM_EARLY_ROLL_CHANCE.get()) {
            return;
        }
        Holder<MobEffect> chosen = switch (random.nextInt(3)) {
            case 0 -> MobEffects.CONFUSION;   // Nausea
            case 1 -> MobEffects.WEAKNESS;
            default -> MobEffects.DIG_SLOWDOWN; // Mining Fatigue
        };
        player.addEffect(new MobEffectInstance(chosen, Config.SYMPTOM_EARLY_DURATION_TICKS.get(), 0));
    }

    // ------------------------------------------------------------------ days 3+ main random pool

    private static void tickMainPool(ServerPlayer player, DayProfile profile, int dayNumber, long gameTime) {
        if (!profile.mainPool()) {
            return;
        }
        if (gameTime % Config.SYMPTOM_POOL_INTERVAL_TICKS.get() != 0L) {
            return;
        }
        double chance = Config.SYMPTOM_POOL_BASE_CHANCE.get();
        if (profile.poolChanceDoubled()) {
            chance *= Config.SYMPTOM_POOL_DAY7_CHANCE_MULTIPLIER.get();
        }
        RandomSource random = player.getRandom();
        if (random.nextDouble() >= chance) {
            return;
        }

        PoolEffect picked = pickPoolEffect(random, profile.poolIncludesHunger());
        int min = Config.SYMPTOM_POOL_MIN_DURATION_TICKS.get();
        int max = Config.SYMPTOM_POOL_MAX_DURATION_TICKS.get();
        int duration = max > min ? min + random.nextInt(max - min + 1) : min;
        if (picked == PoolEffect.HUNGER) {
            duration = (int) Math.round(duration * Config.SYMPTOM_POOL_HUNGER_DURATION_MULTIPLIER.get());
        }
        player.addEffect(new MobEffectInstance(picked.effect, Math.max(1, duration), 0));
    }

    private enum PoolEffect {
        NAUSEA(MobEffects.CONFUSION),
        WEAKNESS(MobEffects.WEAKNESS),
        MINING_FATIGUE(MobEffects.DIG_SLOWDOWN),
        BLINDNESS(MobEffects.BLINDNESS),
        POISON(MobEffects.POISON),
        HUNGER(MobEffects.HUNGER);

        private final Holder<MobEffect> effect;

        PoolEffect(Holder<MobEffect> effect) {
            this.effect = effect;
        }
    }

    private static PoolEffect pickPoolEffect(RandomSource random, boolean includeHunger) {
        double base = Config.SYMPTOM_POOL_BASE_WEIGHT.get();
        double poison = Config.SYMPTOM_POOL_POISON_WEIGHT.get();
        double hunger = includeHunger ? Config.SYMPTOM_POOL_HUNGER_WEIGHT.get() : 0.0D;
        double total = base * 4 + poison + hunger;
        double roll = random.nextDouble() * total;

        if ((roll -= base) < 0) return PoolEffect.NAUSEA;
        if ((roll -= base) < 0) return PoolEffect.WEAKNESS;
        if ((roll -= base) < 0) return PoolEffect.MINING_FATIGUE;
        if ((roll -= base) < 0) return PoolEffect.BLINDNESS;
        if ((roll -= poison) < 0) return PoolEffect.POISON;
        return PoolEffect.HUNGER;
    }

    // ------------------------------------------------------------------ continuous effects (sun / constant hunger)

    private static void tickContinuous(ServerPlayer player, DayProfile profile, int dayNumber, Runtime rt, long gameTime) {
        boolean exposed = isInSunlight(player);
        rt.sunExposureTicks = exposed ? rt.sunExposureTicks + 1 : 0;

        boolean refresh = gameTime % Config.SYMPTOM_REFRESH_INTERVAL_TICKS.get() == 0L;
        if (!refresh) {
            return;
        }
        int duration = Config.SYMPTOM_REFRESH_INTERVAL_TICKS.get() + REFRESH_FUDGE_TICKS;

        if (profile.constantHunger()) {
            player.addEffect(new MobEffectInstance(MobEffects.HUNGER, duration, 0));
        }

        if (!exposed) {
            return;
        }

        if (profile.sunWeakness()) {
            int amplifier = Config.SYMPTOM_SUN_WEAKNESS_BASE_AMPLIFIER.get()
                    + Config.SYMPTOM_SUN_WEAKNESS_PER_DAY_INCREMENT.get() * (dayNumber - 5);
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, duration, Math.max(0, amplifier)));
        }
        if (profile.sunMiningFatigue()) {
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, duration, Config.SYMPTOM_SUN_MINING_FATIGUE_AMPLIFIER.get()));
        }

        DayProfile.SunThreshold threshold = profile.sunThreshold();
        if (threshold != DayProfile.SunThreshold.NONE) {
            int thresholdSeconds = threshold == DayProfile.SunThreshold.BLINDNESS_AND_WITHER
                    ? Config.SYMPTOM_SUN_BLINDNESS_THRESHOLD_SECONDS_DAY10.get()
                    : Config.SYMPTOM_SUN_BLINDNESS_THRESHOLD_SECONDS.get();
            if (rt.sunExposureTicks >= thresholdSeconds * 20) {
                player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, duration, 0));
                if (threshold == DayProfile.SunThreshold.BLINDNESS_AND_WITHER) {
                    player.addEffect(new MobEffectInstance(MobEffects.WITHER, duration, Config.SYMPTOM_SUN_WITHER_AMPLIFIER.get()));
                    // Phase 6 A1: the Wither warning fires on every (re-)application of Wither, so leaving
                    // and re-entering sunlight past the threshold shows it again - not once at day start.
                    player.displayClientMessage(Component.translatable(WITHER_MESSAGE_KEY), true);
                }
            }
        }
    }

    // Deterministic sun exposure: the same daytime + sky-light + can-see-sky + not-in-water/powder-snow
    // conditions vanilla's Mob#isSunBurnTick uses, minus its random burn-chance roll (see Zombie/Mob).
    @SuppressWarnings("deprecation") // getLightLevelDependentMagicValue is exactly what vanilla sun-burn uses
    private static boolean isInSunlight(ServerPlayer player) {
        Level level = player.level();
        if (!level.isDay()) {
            return false;
        }
        if (player.isInWaterRainOrBubble() || player.isInPowderSnow || player.wasInPowderSnow) {
            return false;
        }
        if (player.getLightLevelDependentMagicValue() <= 0.5F) {
            return false;
        }
        BlockPos eyePos = BlockPos.containing(player.getX(), player.getEyeY(), player.getZ());
        return level.canSeeSky(eyePos);
    }

    // ------------------------------------------------------------------ moles

    private static void addMole(ServerPlayer player, int dayNumber) {
        List<MoleData> moles = new ArrayList<>(player.getData(SymptomAttachments.MOLES));
        if (moles.size() >= MoleSlot.count()) {
            return; // every slot occupied
        }
        int slot = pickUnusedSlot(moles, player.getRandom());
        if (slot < 0) {
            return;
        }
        RandomSource random = player.getRandom();
        double baseMin = Config.MOLE_BASE_SIZE_MIN.get();
        double baseMax = Config.MOLE_BASE_SIZE_MAX.get();
        float base = (float) (baseMin + random.nextDouble() * Math.max(0.0D, baseMax - baseMin));
        if (dayNumber >= DayProfile.maxDay()) {
            base *= (float) (double) Config.MOLE_DAY10_SIZE_MULTIPLIER.get(); // day-10 mole starts bigger
        }
        double capMin = Config.MOLE_CAP_MULTIPLIER_MIN.get();
        double capMax = Config.MOLE_CAP_MULTIPLIER_MAX.get();
        float cap = base * (float) (capMin + random.nextDouble() * Math.max(0.0D, capMax - capMin));

        moles.add(new MoleData(slot, base, base, cap));
        player.setData(SymptomAttachments.MOLES, List.copyOf(moles));
        syncMoles(player);
    }

    private static void growMoles(ServerPlayer player) {
        List<MoleData> moles = player.getData(SymptomAttachments.MOLES);
        if (moles.isEmpty()) {
            return;
        }
        double increment = Config.MOLE_DAILY_GROWTH_INCREMENT.get();
        double gMin = Config.MOLE_GROWTH_MULTIPLIER_MIN.get();
        double gMax = Config.MOLE_GROWTH_MULTIPLIER_MAX.get();
        RandomSource random = player.getRandom();

        List<MoleData> updated = new ArrayList<>(moles.size());
        boolean changed = false;
        for (MoleData mole : moles) {
            double factor = gMin + random.nextDouble() * Math.max(0.0D, gMax - gMin);
            float newSize = grownSize(mole.size(), mole.cap(), increment, factor);
            if (newSize != mole.size()) {
                updated.add(mole.grownTo(newSize));
                changed = true;
            } else {
                updated.add(mole);
            }
        }
        if (changed) {
            player.setData(SymptomAttachments.MOLES, List.copyOf(updated));
            syncMoles(player);
        }
    }

    // One mole's daily growth, capped: pure so the cap/freeze invariant is unit-testable.
    static float grownSize(float size, float cap, double increment, double factor) {
        if (size >= cap) {
            return size; // reached its own cap: frozen
        }
        return (float) Math.min(cap, size + increment * factor);
    }

    private static int pickUnusedSlot(List<MoleData> moles, RandomSource random) {
        List<Integer> free = new ArrayList<>();
        for (int i = 0; i < MoleSlot.count(); i++) {
            boolean used = false;
            for (MoleData mole : moles) {
                if (mole.slot() == i) {
                    used = true;
                    break;
                }
            }
            if (!used) {
                free.add(i);
            }
        }
        return free.isEmpty() ? -1 : free.get(random.nextInt(free.size()));
    }

    // Broadcast to every client tracking this player AND the player themselves (moles are public).
    private static void syncMoles(ServerPlayer player) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                new MoleSyncPayload(player.getId(), player.getData(SymptomAttachments.MOLES)));
    }

    // ------------------------------------------------------------------ lifecycle hooks

    /** A newly-tracking viewer needs the target's current moles. */
    public static void onStartTracking(ServerPlayer viewer, net.minecraft.world.entity.Entity target) {
        if (target instanceof ServerPlayer tracked) {
            List<MoleData> moles = tracked.getData(SymptomAttachments.MOLES);
            if (!moles.isEmpty()) {
                PacketDistributor.sendToPlayer(viewer, new MoleSyncPayload(tracked.getId(), moles));
            }
        }
    }

    public static void onLogin(ServerPlayer player) {
        // Resolve any overdue promotion first (idempotent) so the infected check below is order-independent
        // regardless of which login listener runs first.
        InfectionLogic.onLogin(player);
        // If they crossed the infection transition while offline, clear any lingering symptom effects.
        if (player.getData(InfectionAttachments.INFECTION).infected()) {
            clearSymptomEffects(player);
        }
        // Push own moles to self + any current trackers.
        if (!player.getData(SymptomAttachments.MOLES).isEmpty()) {
            syncMoles(player);
        }
    }

    public static void onLogout(ServerPlayer player) {
        RUNTIME.remove(player.getUUID());
    }

    // Player (not ServerPlayer) so gametests can exercise it with a mock player; callers pass ServerPlayer.
    private static void clearSymptomEffects(net.minecraft.world.entity.player.Player player) {
        for (Holder<MobEffect> effect : SYMPTOM_EFFECTS) {
            player.removeEffect(effect);
        }
    }

    // Test seam: the exact clear the infection transition performs on the incubating->infected edge.
    static void endSymptomsForTest(net.minecraft.world.entity.player.Player player) {
        clearSymptomEffects(player);
    }

    // ------------------------------------------------------------------ Part B: full infection (isInfected)

    /** One-time actions when a player becomes infected (natural transition OR /infection set infected). */
    static void onBecameInfected(ServerPlayer player) {
        clearSymptomEffects(player);
        forceUnequipHelmetBoots(player);   // B3
        clearCurrentAttackers(player);     // B4: drop any current lock (mosquitoes included - Monster is Enemy)
    }

    // Per-tick infected mechanics: sun ignition (any time) + Overworld-night buffs (on refresh cadence).
    private static void tickInfected(ServerPlayer player, ServerLevel overworld, long gameTime, boolean isNight) {
        // B1: reuse the exact undead sun mechanic - same detection the incubation checks used, now igniting.
        if (isInSunlight(player)) {
            player.igniteForSeconds(SUN_IGNITE_SECONDS);
        }

        // B5: night effects, Overworld night only, refreshed on the standard interval, off during the day.
        if (gameTime % Config.SYMPTOM_REFRESH_INTERVAL_TICKS.get() != 0L) {
            return;
        }
        if (player.level().dimension() != Level.OVERWORLD || !isNight) {
            return;
        }
        player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, NIGHT_EFFECT_DURATION_TICKS, 0));

        // Level II Strength/Speed if it is a Full Moon OR an active Blood Moon (either alone); else level I.
        boolean fullMoon = overworld.getMoonPhase() == 0;
        boolean bloodMoon = BloodMoonState.isActive(overworld);
        int amplifier = (fullMoon || bloodMoon) ? 1 : 0;
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, NIGHT_EFFECT_DURATION_TICKS, amplifier));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, NIGHT_EFFECT_DURATION_TICKS, amplifier));

        // Haste only when both conditions coincide: an active Blood Moon happening during a Full Moon.
        if (fullMoon && bloodMoon) {
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, NIGHT_EFFECT_DURATION_TICKS, 0));
        }
    }

    // B3: strip helmet + boots, placing each into usable space (hotbar / closest main row / offhand) or
    // dropping it at the player's feet if there is no room.
    private static void forceUnequipHelmetBoots(ServerPlayer player) {
        unequipToUsableOrDrop(player, EquipmentSlot.HEAD);
        unequipToUsableOrDrop(player, EquipmentSlot.FEET);
    }

    private static void unequipToUsableOrDrop(ServerPlayer player, EquipmentSlot slot) {
        ItemStack worn = player.getItemBySlot(slot);
        if (worn.isEmpty()) {
            return;
        }
        player.setItemSlot(slot, ItemStack.EMPTY);
        Inventory inventory = player.getInventory();
        for (int index : USABLE_UNEQUIP_SLOTS) {
            if (inventory.getItem(index).isEmpty()) {
                inventory.setItem(index, worn);
                return;
            }
        }
        player.drop(worn, false); // no usable room: drop at feet
    }

    private static void clearInfectedEffects(ServerPlayer player) {
        for (Holder<MobEffect> effect : INFECTED_EFFECTS) {
            player.removeEffect(effect);
        }
    }

    // ------------------------------------------------------------------ Part C: /infection set

    /** Clears all infection state back to clean, restoring full inventory/armor access. */
    public static void setHealthy(ServerPlayer player) {
        player.setData(InfectionAttachments.INFECTION, InfectionData.NONE);
        player.setData(SymptomAttachments.SYMPTOMS, SymptomTracker.NONE);
        setMoles(player, List.of());
        clearSymptomEffects(player);
        clearInfectedEffects(player);
        player.closeContainer(); // drop the restricted inventory if it is currently open
        resetRuntime(player, false);
    }

    /** Applies full infected state immediately (skipping incubation) and its one-time transition actions. */
    public static void setInfected(ServerPlayer player) {
        int currentDay = currentOverworldDay(player);
        player.setData(InfectionAttachments.INFECTION, new InfectionData(false, currentDay, true));
        player.setData(SymptomAttachments.SYMPTOMS, SymptomTracker.NONE);
        onBecameInfected(player);  // clears symptoms, unequips helmet/boots, drops current targeting
        resetRuntime(player, false);
    }

    /**
     * Sets the incubation day counter directly (1-10). Continuous rules for that day apply going forward;
     * one-time past-day events (mole rolls, day-start messages) are NOT replayed - lastDayProcessed is
     * seeded to the day so the engine treats it as already handled.
     */
    public static void setIncubating(ServerPlayer player, int dayNumber) {
        int currentDay = currentOverworldDay(player);
        int startDay = currentDay - (dayNumber - 1);
        player.setData(InfectionAttachments.INFECTION, new InfectionData(true, startDay, false));
        player.setData(SymptomAttachments.SYMPTOMS, SymptomTracker.NONE.withLastDayProcessed(dayNumber));
        setMoles(player, List.of());
        clearSymptomEffects(player);
        clearInfectedEffects(player);
        resetRuntime(player, true);
    }

    private static void setMoles(ServerPlayer player, List<MoleData> moles) {
        player.setData(SymptomAttachments.MOLES, List.copyOf(moles));
        syncMoles(player);
    }

    private static int currentOverworldDay(ServerPlayer player) {
        return (int) (player.server.overworld().getDayTime() / Level.TICKS_PER_DAY);
    }

    // Keep the transient edge-detection state consistent with a command-driven state change so the next
    // tick does not misfire the incubating->infected transition.
    private static void resetRuntime(ServerPlayer player, boolean incubating) {
        Runtime rt = RUNTIME.computeIfAbsent(player.getUUID(), u -> new Runtime());
        rt.wasIncubating = incubating;
        rt.sunExposureTicks = 0;
    }

    // ------------------------------------------------------------------ status query (command)

    /** Human-readable status for {@code /infection status}: Healthy, Incubating (day X of 10), or Infected. */
    public static Component statusFor(ServerPlayer player) {
        InfectionData infection = player.getData(InfectionAttachments.INFECTION);
        if (infection.infected()) {
            return Component.translatable("message.apolinumarise.infection.status.infected");
        }
        if (infection.incubating()) {
            int currentDay = (int) (player.server.overworld().getDayTime() / Level.TICKS_PER_DAY);
            int dayNumber = dayNumber(infection, currentDay);
            return Component.translatable("message.apolinumarise.infection.status.incubating", dayNumber, DayProfile.maxDay());
        }
        return Component.translatable("message.apolinumarise.infection.status.healthy");
    }
}
