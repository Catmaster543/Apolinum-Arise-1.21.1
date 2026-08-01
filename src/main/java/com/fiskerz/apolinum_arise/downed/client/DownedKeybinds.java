package com.fiskerz.apolinum_arise.downed.client;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

import org.lwjgl.glfw.GLFW;

/**
 * The hold-to-revive keybind (default G). Registered through {@link RegisterKeyMappingsEvent}, so it is
 * automatically remappable via the vanilla controls menu with no extra handling.
 */
public final class DownedKeybinds {
    private DownedKeybinds() {}

    public static final KeyMapping REVIVE = new KeyMapping(
            "key.apolinumarise.revive",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            "key.categories.apolinumarise");

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(REVIVE);
    }
}
