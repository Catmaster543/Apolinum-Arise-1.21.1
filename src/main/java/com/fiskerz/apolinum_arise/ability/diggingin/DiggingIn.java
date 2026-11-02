package com.fiskerz.apolinum_arise.ability.diggingin;

import com.fiskerz.apolinum_arise.ability.AbilityManager;

import net.minecraft.world.entity.player.Player;

/**
 * Digging In: burrow into the ground and swim freely through solid rock.
 *
 * <p>Phase 14's ability, and the first triggered by one of the three shared ability keybind slots. The
 * whole state machine is server-authoritative - see {@link DiggingInManager}; the client only reports the
 * key press and applies the synced state to movement and visuals.
 */
public final class DiggingIn {
    private DiggingIn() {}

    /** Registered ability id. */
    public static final String ABILITY_ID = "infected_branch3_diggingin";

    /** The dream whose completion unlocks it, queued when the variant is assigned. */
    public static final String DREAM_ID = "diggingin_whispers";

    /** The infected variant this belongs to: Branch 3, which is variant index 2 (Branch N -&gt; index N-1). */
    public static final int VARIANT = 2;

    /** Which of the three shared ability keybind slots toggles it. Slot 1 defaults to R. */
    public static final int KEYBIND_SLOT = 1;

    /** Whether this player can burrow at all right now: unlocked, infected, and still variant {@link #VARIANT}. */
    public static boolean isActiveFor(Player player) {
        return AbilityManager.canUse(player, ABILITY_ID);
    }

    /** Whether this player is underground right now - the one query the rest of the mod needs. */
    public static boolean isBurrowed(Player player) {
        return player.getData(BurrowAttachments.BURROW).burrowed();
    }
}
