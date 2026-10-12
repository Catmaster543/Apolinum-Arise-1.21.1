package com.fiskerz.apolinum_arise.ability.hivemind;

import java.util.EnumSet;

import com.fiskerz.apolinum_arise.config.Config;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;

/**
 * Makes a mob with no attack behaviour of its own chase something and bite it.
 *
 * <p>Attached to passive/animal mobs caught in the hive-mind radius - cows, sheep, villagers. Mobs that
 * already know how to fight get {@code setTarget} instead and use their own AI; this exists only for the
 * ones where that would do nothing because there is no attack goal to act on the target.
 *
 * <h2>Why one goal per mob, reused</h2>
 * {@link #goad} re-points an existing instance rather than adding another. That matters because the
 * ability has no cooldown - every qualifying hit re-triggers it - so attaching a fresh goal each time
 * would pile up hundreds on a single unlucky cow. Reuse also sidesteps removing a goal from inside the
 * goal selector's own iteration: the instance simply goes inert when it is done (its {@code canUse}
 * returns false), costing one dormant object per mob and nothing per tick.
 *
 * <h2>Giving up</h2>
 * The goal stops when the target dies or disappears, and when {@link #patienceTicks} pass without the mob
 * getting close enough to bite - which covers both "lost it" and "wandered off and cannot path there".
 * A mob that gives up drops the target entirely, so nothing keeps running on a mob that has lost interest.
 */
public class GoadGoal extends Goal {
    /** Time without a successful bite before the mob loses interest: 15 seconds. */
    private static final int PATIENCE_TICKS = 300;
    /** Gap between bites while in contact: 1 second, so a swarm is dangerous but not instant. */
    private static final int BITE_INTERVAL_TICKS = 20;
    private static final double CHASE_SPEED = 1.2D;
    /** Re-path this often rather than every tick; the navigator does not need spamming. */
    private static final int REPATH_INTERVAL_TICKS = 10;

    private final Mob mob;
    private LivingEntity target;
    private int ticksWithoutContact;
    private int biteCooldown;
    private int repathCooldown;

    public GoadGoal(Mob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    /**
     * Point this goal at a (new) victim and reset its patience. Called both when the goal is first attached
     * and on every later trigger, which is what makes re-triggering free.
     */
    public void goad(LivingEntity newTarget) {
        this.target = newTarget;
        this.ticksWithoutContact = 0;
        this.biteCooldown = 0;
        this.repathCooldown = 0;
    }

    /** Test/debug accessor: who this mob is currently being driven at, or null when inert. */
    public LivingEntity goadTarget() {
        return target;
    }

    private int patienceTicks() {
        return PATIENCE_TICKS;
    }

    @Override
    public boolean canUse() {
        return target != null && target.isAlive() && mob.isAlive()
                && target.level() == mob.level() && ticksWithoutContact < patienceTicks();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void stop() {
        // Drop the target so the goal is fully inert until the next goad(), rather than idling on a corpse.
        target = null;
        mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (target == null) {
            return;
        }
        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);

        double reach = mob.getBbWidth() * 2.0F + target.getBbWidth();
        if (mob.distanceToSqr(target) <= reach * reach) {
            ticksWithoutContact = 0;
            if (--biteCooldown <= 0) {
                biteCooldown = BITE_INTERVAL_TICKS;
                mob.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                target.hurt(mob.damageSources().mobAttack(mob),
                        (float) (double) Config.HIVE_MIND_PASSIVE_DAMAGE.get());
            }
        } else {
            ticksWithoutContact++;
            if (--repathCooldown <= 0) {
                repathCooldown = REPATH_INTERVAL_TICKS;
                mob.getNavigation().moveTo(target, CHASE_SPEED);
            }
        }
    }

    /**
     * Drive {@code mob} at {@code target}, attaching the goal the first time and re-pointing it after that.
     * Priority 0 so it outranks the wandering and panic goals a passive mob would otherwise follow.
     */
    public static void goad(Mob mob, LivingEntity target) {
        for (WrappedGoal wrapped : mob.goalSelector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof GoadGoal existing) {
                existing.goad(target);
                return;
            }
        }
        GoadGoal goal = new GoadGoal(mob);
        goal.goad(target);
        mob.goalSelector.addGoal(0, goal);
    }

    /** Test helper: the goad goal attached to this mob, or null if it has never been goaded. */
    public static GoadGoal attachedTo(Mob mob) {
        for (WrappedGoal wrapped : mob.goalSelector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof GoadGoal existing) {
                return existing;
            }
        }
        return null;
    }
}
