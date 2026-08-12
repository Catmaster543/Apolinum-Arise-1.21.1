package com.fiskerz.apolinum_arise.skill.client;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

import org.lwjgl.glfw.GLFW;

/** Default-K keybind for opening the skill GUI. Remappable via the vanilla controls menu. */
public final class SkillKeybind {
    private SkillKeybind() {}

    public static final KeyMapping OPEN_SKILLS = new KeyMapping(
            "key.apolinumarise.open_skills",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            "key.categories.apolinumarise");

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN_SKILLS);
    }
}
