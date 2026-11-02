package com.fiskerz.apolinum_arise.ability.strongpunch;

import com.fiskerz.apolinum_arise.ability.AbilityManager;

import net.minecraft.world.entity.player.Player;

/**
 * Strong Punch: hold the attack key on one target, bare-fisted, and land a single heavy blow on everything
 * around it.
 *
 * <p>Phase 13's proving ability, and the first that is driven by a HELD vanilla input rather than an item.
 * There is no loot and no activation step - watching {@value #DREAM_ID} is the entire unlock, so the
 * ability exists for a player the moment its condition is met.
 */
public final class StrongPunch {
    private StrongPunch() {}

    /** Registered ability id. */
    public static final String ABILITY_ID = "infected_branch2_strongpunch";

    /** The dream whose completion unlocks it, queued when the variant is assigned. */
    public static final String DREAM_ID = "strongpunch_whispers";

    /** The infected variant this belongs to: Branch 2, which is variant index 1 (Branch N -&gt; index N-1). */
    public static final int VARIANT = 1;

    /** Whether this player's held attack should charge a strong punch: unlocked AND still variant {@link #VARIANT}. */
    public static boolean isActiveFor(Player player) {
        return AbilityManager.canUse(player, ABILITY_ID);
    }
}
