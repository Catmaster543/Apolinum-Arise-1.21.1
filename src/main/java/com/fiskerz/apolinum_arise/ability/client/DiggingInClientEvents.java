package com.fiskerz.apolinum_arise.ability.client;

import com.fiskerz.apolinum_arise.ability.diggingin.DiggingIn;
import com.fiskerz.apolinum_arise.ability.diggingin.DiggingInManager;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderBlockScreenEffectEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Client-side half of Digging In: free 3D movement and the suppressed in-wall overlay.
 *
 * <p>Movement is computed on the client so it feels immediate, and the server accepts it without
 * correction because a {@code noPhysics} player short-circuits the moved-wrongly branch in
 * {@code ServerGamePacketListenerImpl} before it can rubber-band. The server still owns the STATE - whether
 * the player is burrowed at all - and still clamps the height; this only decides where inside those rules
 * the player goes.
 */
public final class DiggingInClientEvents {
    private DiggingInClientEvents() {}

    /**
     * Whether this client has put the burrowed flags on its own player and not yet taken them off. The
     * forced pose in particular is client-local and unsynced, so the server surfacing the player does
     * nothing to it - this is what lets the client notice the burrow ended and undo its own work.
     */
    private static boolean flagsAppliedLocally;

    /**
     * Replace vanilla movement with free swimming along the look vector.
     *
     * <p>{@code MovementInputUpdateEvent} fires inside {@code LocalPlayer.aiStep} after the input has been
     * read but BEFORE {@code travel()} applies it, which is what makes this same-tick responsive. The
     * vanilla input is zeroed afterwards so it contributes nothing on top of the velocity set here -
     * otherwise walking acceleration would fight the swim.
     */
    public static void onMovementInput(MovementInputUpdateEvent event) {
        Player player = event.getEntity();
        if (!(player instanceof LocalPlayer local) || !DiggingIn.isBurrowed(local)) {
            return;
        }

        float forward = event.getInput().forwardImpulse;
        float strafe = event.getInput().leftImpulse;
        // Vanilla must not also move them; read first, then silence it.
        event.getInput().forwardImpulse = 0.0F;
        event.getInput().leftImpulse = 0.0F;
        event.getInput().jumping = false;
        event.getInput().shiftKeyDown = false;

        // Mirror the server's flags locally so the local prediction matches: collision off, no gravity.
        DiggingInManager.applyBurrowedState(local);
        flagsAppliedLocally = true;

        Vec3 look = local.getLookAngle();
        // Strafe perpendicular to the look direction, flattened so sideways never pitches up or down.
        Vec3 right = new Vec3(-look.z, 0.0D, look.x).normalize();
        Vec3 heading = look.scale(forward).add(right.scale(strafe));
        Vec3 velocity = heading.lengthSqr() < 1.0E-6D
                ? Vec3.ZERO
                : heading.normalize().scale(DiggingInManager.speedPerTick());

        // Keep inside the build limits, so a burrowed player can never swim out of the world or into the
        // void. The server clamps too and is the authority; doing it here means it never has to correct.
        double minY = local.level().getMinBuildHeight() + 1.0D;
        double maxY = local.level().getMaxBuildHeight() - 2.0D;
        double nextY = local.getY() + velocity.y;
        if (nextY < minY || nextY > maxY) {
            double clamped = Mth.clamp(nextY, minY, maxY);
            velocity = new Vec3(velocity.x, clamped - local.getY(), velocity.z);
        }
        local.setDeltaMovement(velocity);
    }

    /**
     * Re-apply the flags after the client-side {@code Player.tick()} has reset {@code noPhysics} from its
     * spectator check, for the same reason the server does - and, on the first tick the synced state says
     * the burrow is over, hand them back. See {@code DiggingInManager.upkeepOwnFlags} for why that has to
     * key on the transition rather than on the flags.
     */
    public static void onPlayerTickPost(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof LocalPlayer local) {
            flagsAppliedLocally = DiggingInManager.upkeepOwnFlags(local, flagsAppliedLocally);
        }
    }

    /**
     * Suppress the full-screen "block texture in your face" overlay while burrowed.
     *
     * <p>{@code RenderBlockScreenEffectEvent} is NeoForge's cancelable hook around
     * {@code ScreenEffectRenderer}, and only the BLOCK overlay is suppressed - the fire and water overlays
     * are left alone, because being on fire or underwater while burrowed should still read as such.
     *
     * <p>Suffocation needs no handling at all: {@code Entity.isInWall()} returns false outright when
     * {@code noPhysics} is set, so a burrowed player is never considered inside a block for damage.
     */
    public static void onRenderBlockScreenEffect(RenderBlockScreenEffectEvent event) {
        if (event.getOverlayType() == RenderBlockScreenEffectEvent.OverlayType.BLOCK
                && DiggingIn.isBurrowed(event.getPlayer())) {
            event.setCanceled(true);
        }
    }

    /**
     * Forget the local bookkeeping once there is no local player - a disconnect, or the gap while a world
     * loads. The next player object starts with clean flags of its own, so there is nothing to hand back.
     */
    public static void onClientTickPost(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        if (Minecraft.getInstance().player == null) {
            flagsAppliedLocally = false;
        }
    }
}
