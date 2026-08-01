package com.fiskerz.apolinum_arise.network;

import com.fiskerz.apolinum_arise.infection.InfectionAttachments;
import com.fiskerz.apolinum_arise.infection.inventory.RestrictedInventoryMenu;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server-side handling of {@link OpenRestrictedInventoryPayload}: opens the restricted menu, but only
 * for a genuinely infected player, so the server is the authority (a spoofed packet from a
 * non-infected client does nothing).
 */
public final class OpenRestrictedInventoryHandler {
    private OpenRestrictedInventoryHandler() {}

    public static void handle(OpenRestrictedInventoryPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && player.getData(InfectionAttachments.INFECTION).infected()
                    // Patch A2: never restrict Creative/Spectator, even if a client mistakenly asked.
                    && !player.isCreative() && !player.isSpectator()) {
                player.openMenu(new SimpleMenuProvider(
                        (containerId, inventory, owner) -> new RestrictedInventoryMenu(containerId, inventory),
                        Component.translatable("container.apolinumarise.restricted_inventory")));
            }
        });
    }
}
