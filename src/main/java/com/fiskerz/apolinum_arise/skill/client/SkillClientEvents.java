package com.fiskerz.apolinum_arise.skill.client;

import com.fiskerz.apolinum_arise.skill.SkillLogic;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Client glue for the skill GUI shell: a hidden button on the survival inventory (only added when the
 * local player actually has access), the K keybind (both in-world and while the inventory is open), and
 * the shared open routine. Access is read from the self-synced skill attachment. When the player has
 * neither side unlocked, everything is a completely silent no-op - no button, no feedback.
 */
public final class SkillClientEvents {
    private SkillClientEvents() {}

    // Button placed just right of the player preview, aligned with the top (helmet-slot) row. Textures
    // pending, so exact placement is easy to nudge later; a plain Button renders as vanilla until then.
    private static final int BUTTON_X_OFFSET = 76;
    private static final int BUTTON_Y_OFFSET = 8;
    private static final int BUTTON_SIZE = 18;

    /** Add the hidden skill button to the inventory screen, only if the local player has access. */
    public static void onInventoryInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof InventoryScreen inventory)) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !SkillLogic.hasAnyAccess(player)) {
            return; // locked: no button at all, keeps the feature hidden
        }
        Button button = Button.builder(Component.translatable("gui.apolinumarise.skills"), b -> openSkills())
                .bounds(inventory.getGuiLeft() + BUTTON_X_OFFSET, inventory.getGuiTop() + BUTTON_Y_OFFSET, BUTTON_SIZE, BUTTON_SIZE)
                .build();
        event.addListener(button);
    }

    /** In-world K press (keybinds only fire when no screen is open). */
    public static void onClientTick(ClientTickEvent.Post event) {
        while (SkillKeybind.OPEN_SKILLS.consumeClick()) {
            openSkills();
        }
    }

    /** K press while a screen is open (e.g. the inventory): open the skill GUI, replacing it. */
    public static void onScreenKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        if (!(event.getScreen() instanceof InventoryScreen)) {
            return;
        }
        if (SkillKeybind.OPEN_SKILLS.matches(event.getKeyCode(), event.getScanCode())) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null && SkillLogic.hasAnyAccess(player)) {
                openSkills();
                event.setCanceled(true);
            }
        }
    }

    // Silent no-op when locked. When unlocked, setScreen replaces whatever is open (closing the
    // inventory container via its removal) with the skill panel.
    private static void openSkills() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || !SkillLogic.hasAnyAccess(player)) {
            return;
        }
        minecraft.setScreen(new SkillScreen());
    }
}
