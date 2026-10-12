package com.fiskerz.apolinum_arise.ability.hivemind;

import java.util.List;

import com.fiskerz.apolinum_arise.Apolinumarise;
import com.fiskerz.apolinum_arise.config.Config;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.ai.goal.RangedCrossbowAttackGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/**
 * The hive-mind swing: everything alive near the wielder turns on whatever they just hit.
 *
 * <h2>The two groups</h2>
 * Mobs that can already fight are simply handed the victim via {@code setTarget} and left to their own AI -
 * no bespoke attack logic, which is the whole point of routing them this way. Mobs with no attack
 * behaviour would ignore a target entirely, so they get a {@link GoadGoal} that walks them over and bites.
 *
 * <p>{@link #canAlreadyFight} decides which group a mob is in by looking for a real attack GOAL first,
 * which is the behavioural question actually being asked. The marker interfaces are a fallback for
 * brain-based mobs (piglins, hoglins, the warden) that have no goals to inspect but plainly do fight.
 */
public final class HiveMindEffect {
    private HiveMindEffect() {}

    /**
     * Centred on the WIELDER, not the struck target - per the Phase 12 spec. Swap {@code wielder} for
     * {@code struck} in the AABB and the distance check below if the other reading was intended.
     */
    public static void trigger(Player wielder, LivingEntity struck) {
        double radius = Config.HIVE_MIND_RADIUS.get();
        double radiusSqr = radius * radius;
        AABB box = wielder.getBoundingBox().inflate(radius);

        List<LivingEntity> nearby = wielder.level().getEntitiesOfClass(LivingEntity.class, box,
                candidate -> candidate != wielder && candidate != struck && candidate.isAlive()
                        // getEntitiesOfClass takes a BOX; this makes the area an actual sphere.
                        && candidate.distanceToSqr(wielder) <= radiusSqr);

        int retargeted = 0;
        int goaded = 0;
        for (LivingEntity entity : nearby) {
            if (!(entity instanceof Mob mob)) {
                continue; // other players and armour stands are not part of the hive
            }
            if (canAlreadyFight(mob)) {
                mob.setTarget(struck);
                if (mob instanceof NeutralMob neutral) {
                    // Without this a neutral mob drops the target again the moment its anger times out.
                    neutral.setPersistentAngerTarget(struck.getUUID());
                    neutral.startPersistentAngerTimer();
                }
                retargeted++;
            } else {
                GoadGoal.goad(mob, struck);
                goaded++;
            }
        }
        Apolinumarise.LOGGER.debug("[HiveMind] {} struck {}: {} mob(s) retargeted, {} goaded, within {} blocks.",
                wielder.getGameProfile().getName(), struck.getName().getString(), retargeted, goaded, radius);
    }

    /**
     * Whether this mob already has melee or ranged attack behaviour, and so only needs a target.
     *
     * <p>Checked by looking at its actual goals first - a mob that owns a {@link MeleeAttackGoal} or one of
     * the ranged goals will act on {@code setTarget} without any help. The interface checks afterwards
     * catch mobs whose combat lives in a {@code Brain} rather than a goal selector, where the goal scan
     * would wrongly report "harmless" for something like a piglin.
     */
    public static boolean canAlreadyFight(Mob mob) {
        for (WrappedGoal wrapped : mob.goalSelector.getAvailableGoals()) {
            if (isAttackGoal(wrapped.getGoal())) {
                return true;
            }
        }
        return mob instanceof Enemy || mob instanceof NeutralMob || mob instanceof RangedAttackMob;
    }

    private static boolean isAttackGoal(net.minecraft.world.entity.ai.goal.Goal goal) {
        return goal instanceof MeleeAttackGoal
                || goal instanceof RangedAttackGoal
                || goal instanceof RangedBowAttackGoal
                || goal instanceof RangedCrossbowAttackGoal;
    }
}
