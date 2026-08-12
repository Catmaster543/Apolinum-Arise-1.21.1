package com.fiskerz.apolinum_arise.skill;

import java.util.function.Supplier;

import com.fiskerz.apolinum_arise.Apolinumarise;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class SkillAttachments {
    private SkillAttachments() {}

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Apolinumarise.MODID);

    // serialize(CODEC) persists across relog; copyOnDeath() keeps access through death/respawn (it is an
    // account-level unlock, not tied to a life). sync(self-only) mirrors it to the owning client so the
    // hidden inventory button/keybind can read "do I have access".
    public static final Supplier<AttachmentType<SkillAccessData>> SKILL_ACCESS = ATTACHMENT_TYPES.register("skill_access",
            () -> AttachmentType.builder(() -> SkillAccessData.NONE)
                    .serialize(SkillAccessData.CODEC)
                    .sync((holder, receiver) -> holder == receiver, SkillAccessData.STREAM_CODEC)
                    .copyOnDeath()
                    .build());

    public static void register(IEventBus modEventBus) {
        ATTACHMENT_TYPES.register(modEventBus);
    }
}
