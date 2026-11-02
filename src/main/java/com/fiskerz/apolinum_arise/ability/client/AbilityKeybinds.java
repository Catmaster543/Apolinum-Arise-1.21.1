package com.fiskerz.apolinum_arise.ability.client;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

import org.lwjgl.glfw.GLFW;

/**
 * The three shared ability keybinds (Phase 14). Every keybind-triggered ability in the game claims one of
 * these slots, so a player only ever learns three keys however many abilities exist across the branches.
 *
 * <p>Slot 1 defaults to R; slots 2 and 3 ship UNBOUND ({@code GLFW_KEY_UNKNOWN}) because nothing uses them
 * yet and a default would collide with whatever the player already has bound. All three are remappable in
 * the normal Controls menu like any other keybind.
 */
public final class AbilityKeybinds {
    private AbilityKeybinds() {}

    private static final String CATEGORY = "key.categories.apolinumarise";

    public static final KeyMapping ABILITY_1 = new KeyMapping(
            "key.apolinumarise.ability_1", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY);

    public static final KeyMapping ABILITY_2 = new KeyMapping(
            "key.apolinumarise.ability_2", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY);

    public static final KeyMapping ABILITY_3 = new KeyMapping(
            "key.apolinumarise.ability_3", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY);

    /** Index 0 is slot 1 - see {@link #slotOf}. */
    public static final KeyMapping[] SLOTS = {ABILITY_1, ABILITY_2, ABILITY_3};

    /** The 1-based slot number a mapping represents, matching the server-side slot ids. */
    public static int slotOf(int index) {
        return index + 1;
    }

    public static void register(RegisterKeyMappingsEvent event) {
        for (KeyMapping mapping : SLOTS) {
            event.register(mapping);
        }
    }
}
