package com.fiskerz.apolinum_arise.downed.client;

import com.fiskerz.apolinum_arise.Apolinumarise;
import com.fiskerz.apolinum_arise.config.Config;
import com.fiskerz.apolinum_arise.downed.DownedAttachments;
import com.fiskerz.apolinum_arise.downed.DownedData;
import com.fiskerz.apolinum_arise.downed.DownedManager;
import com.fiskerz.apolinum_arise.infection.InfectionAttachments;
import com.fiskerz.apolinum_arise.infection.InfectionLogic;
import com.fiskerz.apolinum_arise.network.BiteAttemptPayload;
import com.fiskerz.apolinum_arise.network.DownedReviveInputPayload;
import com.fiskerz.apolinum_arise.sleep.SleepAttachments;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.function.Predicate;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Client-side behaviour of the downed/revive system: HUD hiding, the death-screen-style fade, forced
 * third-person camera, full input lock, the hold-to-revive channel + indicator, and hiding the base
 * player body so {@link DownedPoseLayer} is the only body drawn for a downed player.
 */
public final class DownedClientEvents {
    private DownedClientEvents() {}

    private static final ResourceLocation DOWNED_OVERLAY =
            ResourceLocation.fromNamespaceAndPath(Apolinumarise.MODID, "downed_overlay");
    // Sampled directly from DeathScreen.renderDeathBackground: fillGradient(..., 1615855616, -1602211792).
    private static final int DEATH_GRADIENT_TOP = 1615855616;
    private static final int DEATH_GRADIENT_BOTTOM = -1602211792;

    // Camera-preference save/restore.
    private static CameraType savedCameraType;
    private static boolean cameraForced;
    // Client-side revive-channel tracking; reviveTargetId is the target we are actively HOLDING toward (sent
    // to the server), -1 when not holding. The server is authoritative for the revive itself.
    private static int reviveTargetId = -1;
    private static int reviveProgressTicks;
    // Edge-tracking for diagnostic logging (Patch B5): only log on key/target transitions, never per-tick.
    private static boolean reviveKeyWasDown;

    // What the crosshair indicator should show this frame, computed each client tick and read at render.
    // Decoupled from reviveTargetId so the revive prompt appears on AIM alone, before any hold begins.
    private enum Indicator { NONE, REVIVE, BITE }
    private static Indicator indicatorMode = Indicator.NONE;

    // ------------------------------------------------------------------ registration

    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        // Below chat, so the fade never covers chat text.
        event.registerBelow(VanillaGuiLayers.CHAT, DOWNED_OVERLAY, DownedClientEvents::renderOverlay);
    }

    // ------------------------------------------------------------------ HUD hiding

    public static void onRenderGuiLayerPre(RenderGuiLayerEvent.Pre event) {
        if (!selfIncapacitated()) {
            return;
        }
        ResourceLocation name = event.getName();
        // Hide every vanilla HUD layer except chat; our own overlay (apolinumarise namespace) is untouched.
        if (name.getNamespace().equals("minecraft") && !name.equals(VanillaGuiLayers.CHAT)) {
            event.setCanceled(true);
        }
    }

    // ------------------------------------------------------------------ per-tick: camera + revive channel

    public static void onClientTickPost(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        // Downed or passed out from exhaustion: identical presentation and identical inability to act.
        boolean incapacitated = DownedManager.isIncapacitated(player);
        // Previewing a pose forces third person too (so the tester can see their own posed model), but
        // does NOT lock input or hide the HUD - they still need to move and type commands.
        updateCamera(minecraft, incapacitated || DownedPoses.previewVariant() >= 0);

        if (incapacitated) {
            resetChannel(false); // an incapacitated player can't revive or bite
            clearIndicator();
            drainClicks();
            return;
        }
        // The one revive key context-switches by the LOOKER's infection state, so there is never a
        // conflict: an infected player bites (single press), everyone else revives (hold). They are
        // mutually exclusive - an infected player is not revive-eligible, and biting requires infection.
        if (InfectionLogic.isInfected(player)) {
            resetChannel(false); // an infected player never holds a revive channel
            tickBite(minecraft, player);
        } else {
            drainClicks(); // ignore stray bite presses queued while out of the bite context
            tickReviveChannel(minecraft, player);
        }
    }

    // Discard any queued key presses so a press only ever fires a fresh bite while in the bite context.
    private static void drainClicks() {
        while (DownedKeybinds.REVIVE.consumeClick()) {
            // discard
        }
    }

    private static void clearIndicator() {
        indicatorMode = Indicator.NONE;
    }

    private static void updateCamera(Minecraft minecraft, boolean downed) {
        if (downed) {
            if (!cameraForced) {
                savedCameraType = minecraft.options.getCameraType();
                cameraForced = true;
            }
            // Re-assert every tick so an F5 press can't escape it.
            if (minecraft.options.getCameraType() != CameraType.THIRD_PERSON_BACK) {
                minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            }
        } else if (cameraForced) {
            minecraft.options.setCameraType(savedCameraType != null ? savedCameraType : CameraType.FIRST_PERSON);
            cameraForced = false;
            savedCameraType = null;
        }
    }

    private static void tickReviveChannel(Minecraft minecraft, LocalPlayer player) {
        boolean keyDown = DownedKeybinds.REVIVE.isDown();
        if (keyDown != reviveKeyWasDown) {
            Apolinumarise.LOGGER.debug("Revive: key {} (reviver eligible={}).",
                    keyDown ? "DOWN" : "UP", DownedManager.isEligible(player));
            reviveKeyWasDown = keyDown;
        }
        // Target acquisition runs EVERY tick regardless of the key, against the real (upright) hitbox.
        Player target = DownedManager.isEligible(player) ? pickReviveTarget(player) : null;
        if (target == null) {
            if (reviveTargetId != -1) {
                PacketDistributor.sendToServer(new DownedReviveInputPayload(-1)); // drop any channel we had
            }
            reviveTargetId = -1;
            reviveProgressTicks = 0;
            clearIndicator();
            return;
        }
        // BUGFIX: simply aiming at an eligible downed target shows the indicator, whether or not the key is
        // held yet. The hold-progress fill is layered on top of that, not a precondition for it.
        indicatorMode = Indicator.REVIVE;
        if (keyDown) {
            // Dispatch to the server every tick while held on a valid target (server is authoritative).
            PacketDistributor.sendToServer(new DownedReviveInputPayload(target.getId()));
            if (reviveTargetId != target.getId()) {
                Apolinumarise.LOGGER.debug("Revive: hold started on {} (id {}).",
                        target.getGameProfile().getName(), target.getId());
                reviveTargetId = target.getId();
                reviveProgressTicks = 0;
            }
            reviveProgressTicks++; // drives the hold-progress fill
        } else {
            // Aiming but not holding: keep the indicator (empty bar), but drop any channel we had going.
            if (reviveTargetId != -1) {
                PacketDistributor.sendToServer(new DownedReviveInputPayload(-1));
            }
            reviveTargetId = -1;
            reviveProgressTicks = 0;
        }
    }

    // Bite context (infected local player): single press on a downed HEALTHY target while the bar is full.
    private static void tickBite(Minecraft minecraft, LocalPlayer player) {
        boolean ready = player.getData(InfectionAttachments.INFECTION).biteBar() >= 100.0F;
        Player target = pickBiteTarget(player);
        // Only show the prompt once the bar is actually full AND a valid target is under the crosshair.
        if (ready && target != null) {
            indicatorMode = Indicator.BITE;
        } else {
            clearIndicator();
        }
        // Single discrete press (not hold): collapse any queued clicks into one attempt this tick.
        boolean pressed = false;
        while (DownedKeybinds.REVIVE.consumeClick()) {
            pressed = true;
        }
        if (pressed && ready && target != null) {
            PacketDistributor.sendToServer(new BiteAttemptPayload(target.getId()));
            Apolinumarise.LOGGER.debug("Bite: press -> attempt on {} (id {}).",
                    target.getGameProfile().getName(), target.getId());
        }
    }

    // Cancel the channel; tell the server (once) if we had been channeling.
    private static void resetChannel(boolean notifyServer) {
        if (reviveTargetId != -1 && notifyServer) {
            PacketDistributor.sendToServer(new DownedReviveInputPayload(-1));
        }
        reviveTargetId = -1;
        reviveProgressTicks = 0;
    }

    // A downed, REVIVABLE (not fully infected) player under the crosshair within reviveRange.
    private static Player pickReviveTarget(LocalPlayer player) {
        return pickDownedTarget(player, Config.REVIVE_RANGE.get(),
                entity -> entity instanceof Player other && other != player
                        && other.getData(DownedAttachments.DOWNED).downed()
                        && other.getData(DownedAttachments.DOWNED).revivable());
    }

    // An incapacitated, HEALTHY (clean) player under the crosshair within biteRange (Phase 8 bite target).
    // Either incapacitation qualifies: really downed (Phase 7) or passed out from exhaustion (Phase 10a).
    // Each state carries its own broadcast "healthy" flag, because infection is synced only to its owner.
    private static Player pickBiteTarget(LocalPlayer player) {
        return pickDownedTarget(player, Config.BITE_RANGE.get(),
                entity -> entity instanceof Player other && other != player
                        && (other.getData(DownedAttachments.DOWNED).downed()
                                ? other.getData(DownedAttachments.DOWNED).healthy()
                                : other.getData(SleepAttachments.SLEEP).passedOut()
                                        && other.getData(SleepAttachments.SLEEP).healthy()));
    }

    // Shared entity pick for downed players under the crosshair within range, with a proximity fallback.
    // The raycast tests the target's REAL (still-upright) hitbox, not the flat rendered corpse - so precise
    // aiming works once the pose model sits at the entity (Patch B4). Because a downed player lies low, a
    // pixel-precise ray can still skim past the standing box; the fallback mirrors the server's own accept
    // test (within range + roughly faced) so the client indicator and the server never disagree.
    private static Player pickDownedTarget(LocalPlayer player, double range, Predicate<Entity> eligible) {
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 look = player.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(range));

        AABB searchBox = player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0D);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, searchBox, eligible, range * range);
        if (hit != null && hit.getEntity() instanceof Player target) {
            return target;
        }

        // Fallback: best-aligned eligible downed player within range (dot threshold matches the server).
        Player best = null;
        double bestDot = 0.8D;
        for (Player other : player.level().getEntitiesOfClass(Player.class, player.getBoundingBox().inflate(range), eligible)) {
            Vec3 toTarget = other.getBoundingBox().getCenter().subtract(eye);
            if (toTarget.length() > range) {
                continue;
            }
            double dot = look.dot(toTarget.normalize());
            if (dot >= bestDot) {
                bestDot = dot;
                best = other;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ input lock

    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!selfIncapacitated()) {
            return;
        }
        Input input = event.getInput();
        input.up = false;
        input.down = false;
        input.left = false;
        input.right = false;
        input.jumping = false;
        input.shiftKeyDown = false;
        input.forwardImpulse = 0.0F;
        input.leftImpulse = 0.0F;
        // Camera look (mouse) is intentionally left alone.
    }

    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (selfIncapacitated()) {
            event.setCanceled(true); // no attacking / item use / interaction while downed or passed out
        }
    }

    // ------------------------------------------------------------------ hide base body for the pose layer

    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        var player = event.getEntity();
        boolean incapacitated = IncapacitatedRender.active(player);
        boolean preview = DownedPoses.previewVariant() >= 0 && Minecraft.getInstance().player == player;
        if (incapacitated || preview) {
            // The DownedPoseLayer re-enables + re-poses + renders the body itself.
            event.getRenderer().getModel().setAllVisible(false);
        }
        if (incapacitated) {
            // Patch B6: the client keeps recomputing yBodyRot from the look direction (especially for the
            // local player), so the body would rotate with the camera. Pin the rendered facing to the value
            // frozen when they went down / passed out. Both O and current => no interpolation.
            float bodyYaw = IncapacitatedRender.bodyYaw(player);
            player.yBodyRot = bodyYaw;
            player.yBodyRotO = bodyYaw;
        }
    }

    // ------------------------------------------------------------------ overlay: fade + revive indicator

    private static void renderOverlay(GuiGraphics guiGraphics, net.minecraft.client.DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        DownedData self = player.getData(DownedAttachments.DOWNED);
        if (self.downed()) {
            // Fade toward the vanilla death-screen background as the timer runs out; reads the live
            // attachment, so a revive (downed -> false) drops it to nothing the same frame.
            float progress = self.progress(player.level().getGameTime());
            guiGraphics.fillGradient(0, 0, guiGraphics.guiWidth(), guiGraphics.guiHeight(),
                    scaleAlpha(DEATH_GRADIENT_TOP, progress), scaleAlpha(DEATH_GRADIENT_BOTTOM, progress));
            return; // a downed player never shows the revive/bite indicator
        }
        // Prompt shows the ACTUAL bound key label (reflects any rebind), not a hardcoded "G". The revive
        // and bite prompts share one component; only the label and the hold-progress bar differ.
        Component keyLabel = DownedKeybinds.REVIVE.getTranslatedKeyMessage();
        if (indicatorMode == Indicator.REVIVE) {
            int holdTicks = Math.max(1, (int) Math.ceil(Config.REVIVE_HOLD_SECONDS.get() * 20.0D));
            float fraction = Math.min(1.0F, reviveProgressTicks / (float) holdTicks);
            renderKeyIndicator(guiGraphics, minecraft,
                    Component.translatable("hud.apolinumarise.revive_prompt", keyLabel), true, fraction);
        } else if (indicatorMode == Indicator.BITE) {
            // Single press, so no hold bar - the full bite bar (bottom HUD) is what gated this prompt.
            renderKeyIndicator(guiGraphics, minecraft,
                    Component.translatable("hud.apolinumarise.bite_prompt", keyLabel), false, 0.0F);
        }
    }

    // Shared crosshair-indicator component: a centered prompt, plus an optional hold-progress bar.
    private static void renderKeyIndicator(GuiGraphics guiGraphics, Minecraft minecraft, Component prompt,
                                           boolean showProgressBar, float fraction) {
        int centerX = guiGraphics.guiWidth() / 2;
        int centerY = guiGraphics.guiHeight() / 2;
        guiGraphics.drawCenteredString(minecraft.font, prompt, centerX, centerY - 32, 0xFFFFFFFF);
        if (!showProgressBar) {
            return;
        }
        int barWidth = 70;
        int barHeight = 6;
        int left = centerX - barWidth / 2;
        int top = centerY - 20;
        guiGraphics.fill(left - 1, top - 1, left + barWidth + 1, top + barHeight + 1, 0xC0000000);
        guiGraphics.fill(left, top, left + (int) (barWidth * fraction), top + barHeight, 0xFF55FF55);
    }

    // ------------------------------------------------------------------ helpers

    private static int scaleAlpha(int argb, float factor) {
        int alpha = Math.round(((argb >>> 24) & 0xFF) * Math.max(0.0F, Math.min(1.0F, factor)));
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }

    // Covers both the downed state and the Phase 10a pass-out: the HUD hiding, camera lock and input lock
    // are shared presentation, so they key off the same predicate.
    private static boolean selfIncapacitated() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && DownedManager.isIncapacitated(minecraft.player);
    }
}
