package com.fiskerz.apolinum_arise.infection;

import com.fiskerz.apolinum_arise.Apolinumarise;
import com.fiskerz.apolinum_arise.infection.inventory.RestrictedInventoryMenu;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class InfectionMenus {
    private InfectionMenus() {}

    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, Apolinumarise.MODID);

    // The menu is self-backed (player Inventory), so no extra server->client payload is needed.
    public static final DeferredHolder<MenuType<?>, MenuType<RestrictedInventoryMenu>> RESTRICTED_INVENTORY =
            MENU_TYPES.register("restricted_inventory",
                    () -> IMenuTypeExtension.create((windowId, inventory, buffer) -> new RestrictedInventoryMenu(windowId, inventory)));

    public static void register(IEventBus modEventBus) {
        MENU_TYPES.register(modEventBus);
    }
}
