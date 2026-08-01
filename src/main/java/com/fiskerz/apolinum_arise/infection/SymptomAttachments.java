package com.fiskerz.apolinum_arise.infection;

import java.util.List;
import java.util.function.Supplier;

import com.fiskerz.apolinum_arise.Apolinumarise;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Phase 5 per-player attachments, kept separate from Phase 4's {@link InfectionAttachments} so that
 * package is untouched. Both persist across relog and copy through death, matching the infection state.
 */
public final class SymptomAttachments {
    private SymptomAttachments() {}

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Apolinumarise.MODID);

    // Server-only timeline bookkeeping.
    public static final Supplier<AttachmentType<SymptomTracker>> SYMPTOMS = ATTACHMENT_TYPES.register("symptoms",
            () -> AttachmentType.builder(() -> SymptomTracker.NONE)
                    .serialize(SymptomTracker.CODEC)
                    .copyOnDeath()
                    .build());

    // Authoritative mole list; broadcast-synced to every client tracking the player (see mole sync).
    public static final Supplier<AttachmentType<List<MoleData>>> MOLES = ATTACHMENT_TYPES.register("moles",
            () -> AttachmentType.<List<MoleData>>builder(() -> List.of())
                    .serialize(MoleData.CODEC.listOf())
                    .copyOnDeath()
                    .build());

    public static void register(IEventBus modEventBus) {
        ATTACHMENT_TYPES.register(modEventBus);
    }
}
