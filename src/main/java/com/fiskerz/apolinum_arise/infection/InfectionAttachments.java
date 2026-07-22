package com.fiskerz.apolinum_arise.infection;

import java.util.function.Supplier;

import com.fiskerz.apolinum_arise.Apolinumarise;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class InfectionAttachments {
    private InfectionAttachments() {}

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Apolinumarise.MODID);

    // serialize(CODEC) persists across relog; copyOnDeath() keeps it through death/respawn.
    public static final Supplier<AttachmentType<InfectionData>> INFECTION = ATTACHMENT_TYPES.register("infection",
            () -> AttachmentType.builder(() -> InfectionData.NONE)
                    .serialize(InfectionData.CODEC)
                    .copyOnDeath()
                    .build());

    public static void register(IEventBus modEventBus) {
        ATTACHMENT_TYPES.register(modEventBus);
    }
}
