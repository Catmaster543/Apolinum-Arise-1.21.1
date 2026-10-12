package com.fiskerz.apolinum_arise.ability;

import java.util.function.Supplier;

import com.fiskerz.apolinum_arise.Apolinumarise;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class AbilityAttachments {
    private AbilityAttachments() {}

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Apolinumarise.MODID);

    // serialize + copyOnDeath: an unlocked ability is a permanent, account-level thing, exactly like skill
    // access - losing it to a death would be a bug, not a penalty.
    //
    // Synced to the OWNING client only, as of Phase 13. Phase 12 left this unsynced because every trigger
    // was server-side; Strong Punch is the first ability driven by a held client input, and the client has
    // to know whether to bother reporting its attack key at all. Self-only, so no other player learns
    // which abilities someone has. The server still re-validates everything - the sync is what stops every
    // player on the server streaming key-state packets, not a trust boundary.
    public static final Supplier<AttachmentType<AbilityData>> ABILITIES = ATTACHMENT_TYPES.register("abilities",
            () -> AttachmentType.builder(() -> AbilityData.NONE)
                    .serialize(AbilityData.CODEC)
                    .sync((holder, receiver) -> holder == receiver, AbilityData.STREAM_CODEC)
                    .copyOnDeath()
                    .build());

    public static void register(IEventBus modEventBus) {
        ATTACHMENT_TYPES.register(modEventBus);
    }
}
