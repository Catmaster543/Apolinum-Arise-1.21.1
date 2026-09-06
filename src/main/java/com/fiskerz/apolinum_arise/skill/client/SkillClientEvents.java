package com.fiskerz.apolinum_arise.skill.client;

import com.fiskerz.apolinum_arise.Apolinumarise;
import com.fiskerz.apolinum_arise.infection.InfectionAttachments;
import com.fiskerz.apolinum_arise.infection.InfectionData;
import com.fiskerz.apolinum_arise.infection.client.RestrictedInventoryScreen;
import com.fiskerz.apolinum_arise.quests.client.QuestBookOpener;
import com.fiskerz.apolinum_arise.skill.SkillAccessData;
import com.fiskerz.apolinum_arise.skill.SkillAttachments;
import com.fiskerz.apolinum_arise.skill.SkillLogic;
import com.fiskerz.apolinum_arise.skill.SkillProfileData;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Client glue for the skill GUI shell: a hidden button on the player's inventory (only added when the
 * local player actually has access), the K keybind (both in-world and while the inventory is open), and
 * the shared open routine. Access is read from the self-synced skill attachment. When the player has
 * neither side unlocked, everything is a completely silent no-op - no button, no feedback.
 *
 * <p>"The inventory" means EITHER player-inventory screen: the vanilla one, or the Phase 6
 * {@link RestrictedInventoryScreen} that infected players get instead. That second case matters a lot -
 * infected players are the only ones who can hold infected-side access, so matching on
 * {@code InventoryScreen} alone hid the button from exactly the players it was meant for.
 */
public final class SkillClientEvents {
    private SkillClientEvents() {}

    // Button placed just right of the player preview, aligned with the top (helmet-slot) row.
    private static final int BUTTON_X_OFFSET = 76;
    private static final int BUTTON_Y_OFFSET = 8;

    /**
     * Add the skill button to the inventory screen, ONLY if the local player has access to one of the two
     * sides. With neither side unlocked no widget is created at all - it is not added-but-disabled, because
     * the button's own icon now reveals which side exists, so its mere presence would leak the feature.
     */
    public static void onInventoryInit(ScreenEvent.Init.Post event) {
        AbstractContainerScreen<?> inventory = playerInventoryScreen(event.getScreen());
        if (inventory == null) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        // Naming the screen here makes a "which screen did we actually match?" mismatch obvious in the log.
        logAccess("inventory-button on " + inventory.getClass().getSimpleName());
        if (!SkillLogic.hasAnyAccess(player)) {
            return; // locked: no button at all, keeps the feature hidden
        }
        // Mutually exclusive by construction, so exactly one quadrant pair applies whenever we get here.
        boolean healthySide = SkillLogic.hasHealthyAccess(player);
        event.addListener(new SkillTreeButton(
                inventory.getGuiLeft() + BUTTON_X_OFFSET,
                inventory.getGuiTop() + BUTTON_Y_OFFSET,
                healthySide,
                b -> openSkills()));
    }

    /** In-world K press (keybinds only fire when no screen is open). */
    public static void onClientTick(ClientTickEvent.Post event) {
        while (SkillKeybind.OPEN_SKILLS.consumeClick()) {
            openSkills();
        }
    }

    /**
     * The player's inventory screen, whichever variant it is, or null for any other screen. Both extend
     * {@link AbstractContainerScreen} and use the same 176x166 vanilla inventory layout, so the button's
     * offsets land identically on either.
     */
    private static AbstractContainerScreen<?> playerInventoryScreen(Screen screen) {
        return screen instanceof InventoryScreen || screen instanceof RestrictedInventoryScreen
                ? (AbstractContainerScreen<?>) screen
                : null;
    }

    /** K press while a screen is open (either inventory variant): open the skill GUI, replacing it. */
    public static void onScreenKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        if (playerInventoryScreen(event.getScreen()) == null) {
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

    /**
     * Where the skill button and the K key actually go (Phase 11 item 5). Three destinations:
     *
     * <ul>
     *   <li><b>Infected with access:</b> straight into FTB Quests' own book. No chapter navigation of our
     *       own - the book lists only the chapters visible to this player's team data, and their variant's
     *       gate is the only one we completed, so it already shows them their variant and nothing else.</li>
     *   <li><b>Healthy with access, no branch chosen:</b> the one-time branch-choice screen.</li>
     *   <li><b>Healthy with access, branch chosen:</b> the book, same as the infected side.</li>
     * </ul>
     *
     * With neither side unlocked this stays the completely silent no-op it has always been.
     */
    private static void openSkills() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        logAccess("open");
        if (!SkillLogic.hasAnyAccess(player)) {
            return;
        }
        if (SkillLogic.needsBranchChoice(player)) {
            minecraft.setScreen(new BranchChoiceScreen());
            return;
        }
        if (QuestBookOpener.open()) {
            return;
        }
        // FTB Quests missing, or its file has not synced yet. Fall back to the empty Phase 9 shell rather
        // than swallowing the keypress, so the button still visibly does something.
        Apolinumarise.LOGGER.debug("[Skill] Falling back to the plain skill panel - the quest book was "
                + "unavailable.");
        minecraft.setScreen(new SkillScreen());
    }

    /**
     * Diagnostic for "the GUI opens when it shouldn't": dumps the ACTUAL access flags the check reads (as
     * synced to this client) alongside the infection state they are supposed to correspond to, at the exact
     * moment of the check. A line reading {@code infectedAccess=true} while {@code infected=false} is the
     * stale-flag case fixed by {@link SkillLogic#onNoLongerInfected}.
     */
    private static void logAccess(String where) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        SkillAccessData access = player.getData(SkillAttachments.SKILL_ACCESS);
        InfectionData infection = player.getData(InfectionAttachments.INFECTION);
        SkillProfileData profile = player.getData(SkillAttachments.SKILL_PROFILE);
        Apolinumarise.LOGGER.debug("[Skill] access check ({}): healthyAccess={} infectedAccess={} -> hasAny={} "
                        + "| infection: infected={} incubating={} | profile: variant={} branch={} "
                        + "stats={}/{}/{} (assigned={})",
                where, access.healthyAccess(), access.infectedAccess(), access.hasAny(),
                infection.infected(), infection.incubating(), profile.infectedVariant(), profile.healthyBranch(),
                profile.intelligence(), profile.strength(), profile.creativity(), profile.statsAssigned());
    }
}
