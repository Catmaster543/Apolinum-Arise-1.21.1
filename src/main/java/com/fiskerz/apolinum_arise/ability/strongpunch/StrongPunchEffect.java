package com.fiskerz.apolinum_arise.ability.strongpunch;

import java.util.List;

import com.fiskerz.apolinum_arise.Apolinumarise;
import com.fiskerz.apolinum_arise.config.Config;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/** The blow itself: one heavy hit to everything in a box around the charged target. */
public final class StrongPunchEffect {
    private StrongPunchEffect() {}

    /**
     * Hit every living entity in a {@code strongPunchAreaSize} cube centred on the target, once each.
     *
     * <p>The struck target is inside that box and is damaged BY the loop - never separately as well, which
     * would hit it twice. The wielder is the only exclusion: passive mobs and other players standing in
     * the blast are caught deliberately.
     *
     * <p>Damage is the player's live fist damage times the multiplier. {@code ATTACK_DAMAGE} is the right
     * attribute to read because buffs apply to it as modifiers - vanilla Strength is an {@code ADD_VALUE}
     * modifier on exactly this attribute - so the night-time Strength from the infection effects is
     * already folded in, and with an empty main hand no weapon modifier is.
     */
    /**
     * Who the blast damages: everything alive in the box except the person throwing the punch.
     *
     * <p>The single source of truth for the exclusion rule, and public so it can be asserted directly -
     * a second PLAYER caught in the blast cannot be staged in a gametest, because
     * {@code GameTestHelper.makeMockPlayer} builds a Player without ever adding it to the level, so no
     * area query can see one. Testing the predicate is therefore the only honest way to pin "other players
     * are hit, the wielder is not" without a live two-client session.
     */
    public static boolean isVictim(Player wielder, LivingEntity candidate) {
        return candidate != wielder && candidate.isAlive();
    }

    public static void detonate(Player wielder, LivingEntity target) {
        double fistDamage = wielder.getAttributeValue(Attributes.ATTACK_DAMAGE);
        float damage = (float) (fistDamage * Config.STRONG_PUNCH_DAMAGE_MULTIPLIER.get());
        double size = Config.STRONG_PUNCH_AREA_SIZE.get();

        AABB box = AABB.ofSize(target.position(), size, size, size);
        List<LivingEntity> caught = wielder.level().getEntitiesOfClass(LivingEntity.class, box,
                victim -> isVictim(wielder, victim));

        for (LivingEntity victim : caught) {
            // Normal attacks keep landing on the target throughout the 2s charge, so the punch would
            // otherwise arrive inside someone's invulnerability window and be swallowed. Clearing it makes
            // the payoff for a charged, cooldown-gated ability land every time, which is the point of it.
            victim.invulnerableTime = 0;
            victim.hurt(wielder.damageSources().playerAttack(wielder), damage);
        }

        wielder.level().playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0F, 0.6F);
        Apolinumarise.LOGGER.debug("[StrongPunch] {} hit {} entit(ies) for {} each ({} fist x {}), in a {} cube.",
                wielder.getGameProfile().getName(), caught.size(), damage, fistDamage,
                Config.STRONG_PUNCH_DAMAGE_MULTIPLIER.get(), size);
    }
}
