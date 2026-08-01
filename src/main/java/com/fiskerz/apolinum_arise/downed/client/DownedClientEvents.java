package com.fiskerz.apolinum_arise.downed.client;

import com.fiskerz.apolinum_arise.Apolinumarise;
import com.fiskerz.apolinum_arise.config.Config;
import com.fiskerz.apolinum_arise.downed.DownedAttachments;
import com.fiskerz.apolinum_arise.downed.DownedData;
import com.fiskerz.apolinum_arise.downed.DownedManager;
import com.fiskerz.apolinum_arise.network.DownedReviveInputPayload;

import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
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
    // Client-side revive-channel tracking (for the indicator fill); the server is authoritative for the revive.
    private static int reviveTargetId = -1;
    private static int reviveProgressTicks;
    // Edge-tracking for diagnostic logging (Patch B5): only log on key/target transitions, never per-tick.
    private static boolean reviveKeyWasDown;

    // ------------------------------------------------------------------ registration

    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        // Below chat, so the fade never covers chat text.
        event.registerBelow(VanillaGuiLayers.CHAT, DOWNED_OVERLAY, DownedClientEvents::renderOverlay);
    }

    // ------------------------------------------------------------------ HUD hiding

    public static void onRenderGuiLayerPre(RenderGuiLayerEvent.Pre event) {
        if (!selfDowned()) {
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
        boolean downed = DownedManager.isDowned(player);
        // Previewing a pose forces third person too (so the tester can see their own posed model), but
        // does NOT lock input or hide the HUD - they still need to move and type commands.
        updateCamera(minecraft, downed || DownedPoses.previewVariant() >= 0);

        if (downed) {
            resetChannel(false); // a downed player can't revive
            return;
        }
        tickReviveChannel(minecraft, player);
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
        // Link 1 (keybind press/hold/release): log only on the edge so the transcript shows the press
        // without per-tick spam.
        boolean keyDown = DownedKeybinds.REVIVE.isDown();
        if (keyDown != reviveKeyWasDown) {
            Apolinumarise.LOGGER.debug("Revive: key {} (reviver eligible={}).",
                    keyDown ? "DOWN" : "UP", DownedManager.isEligible(player));
            reviveKeyWasDown = keyDown;
        }
        if (!keyDown || !DownedManager.isEligible(player)) {
            resetChannel(true);
            return;
        }
        // Link 2 (target acquisition against the real hitbox).
        Player target = pickReviveTarget(player);
        if (target == null) {
            if (reviveTargetId != -1) {
                Apolinumarise.LOGGER.debug("Revive: target lost (crosshair no longer on a downed player).");
            }
            resetChannel(true);
            return;
        }
        // Link 4 (dispatch to server every tick while held on a valid target).
        PacketDistributor.sendToServer(new DownedReviveInputPayload(target.getId()));
        if (reviveTargetId != target.getId()) {
            Apolinumarise.LOGGER.debug("Revive: target acquired {} (id {}); indicator + hold timer starting.",
                    target.getGameProfile().getName(), target.getId());
            reviveTargetId = target.getId();
            reviveProgressTicks = 0;
        }
        reviveProgressTicks++; // Link 3: drives the indicator fill in renderReviveIndicator.
    }

    // Cancel the channel; tell the server (once) if we had been channeling.
    private static void resetChannel(boolean notifyServer) {
        if (reviveTargetId != -1 && notifyServer) {
            PacketDistributor.sendToServer(new DownedReviveInputPayload(-1));
        }
        reviveTargetId = -1;
        reviveProgressTicks = 0;
    }

    // Entity raycast for a downed, revivable player under the crosshair within reviveRange, with a
    // proximity fallback. The raycast tests the target's REAL (still-upright) hitbox, not the flat
    // rendered corpse - so precise aiming works once the pose model sits at the entity (Patch B4). Because
    // a downed player lies low, a pixel-precise ray can still skim past the standing box; the fallback
    // then mirrors the server's own accept test (within range + roughly faced) so the client indicator and
    // the server's revive never disagree.
    private static Player pickReviveTarget(LocalPlayer player) {
        double range = Config.REVIVE_RANGE.get();
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 look = player.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(range));
        Predicate<Entity> eligible = entity -> entity instanceof Player other && other != player
                && other.getData(DownedAttachments.DOWNED).downed()
                && other.getData(DownedAttachments.DOWNED).revivable();

        AABB searchBox = player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0D);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, searchBox, eligible, range * range);
        if (hit != null && hit.getEntity() instanceof Player target) {
            return target;
        }

        // Fallback: best-aligned downed player within range (dot threshold matches withinReviveReach).
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
        if (!selfDowned()) {
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
        if (selfDowned()) {
            event.setCanceled(true); // no attacking / item use / interaction while downed
        }
    }

    // ------------------------------------------------------------------ hide base body for the pose layer

    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        var player = event.getEntity();
        DownedData data = player.getData(DownedAttachments.DOWNED);
        boolean preview = DownedPoses.previewVariant() >= 0 && Minecraft.getInstance().player == player;
        if (data.downed() || preview) {
            // The DownedPoseLayer re-enables + re-poses + renders the body itself.
            event.getRenderer().getModel().setAllVisible(false);
        }
        if (data.downed()) {
            // Patch B6: the client keeps recomputing yBodyRot from the look direction (especially for the
            // local player), so the corpse would rotate with the camera. Pin the rendered body facing to
            // the value frozen at down-time (synced via DownedData). Both O and current => no interpolation.
            player.yBodyRot = data.bodyYaw();
            player.yBodyRotO = data.bodyYaw();
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
            return; // a downed player never shows the revive indicator
        }
        if (reviveTargetId != -1) {
            renderReviveIndicator(guiGraphics, minecraft);
        }
    }

    private static void renderReviveIndicator(GuiGraphics guiGraphics, Minecraft minecraft) {
        int holdTicks = Math.max(1, (int) Math.ceil(Config.REVIVE_HOLD_SECONDS.get() * 20.0D));
        float fraction = Math.min(1.0F, reviveProgressTicks / (float) holdTicks);
        int centerX = guiGraphics.guiWidth() / 2;
        int centerY = guiGraphics.guiHeight() / 2;

        // Prompt shows the ACTUAL bound key label (reflects any rebind), not a hardcoded "G".
        KeyMapping key = DownedKeybinds.REVIVE;
        Component prompt = Component.translatable("hud.apolinumarise.revive_prompt", key.getTranslatedKeyMessage());
        guiGraphics.drawCenteredString(minecraft.font, prompt, centerX, centerY - 32, 0xFFFFFFFF);

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

    private static boolean selfDowned() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && DownedManager.isDowned(minecraft.player);
    }
}
