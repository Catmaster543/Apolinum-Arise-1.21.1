package com.fiskerz.apolinum_arise.skill;

import com.fiskerz.apolinum_arise.Apolinumarise;
import com.fiskerz.apolinum_arise.config.Config;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The healthy-side access book. It only overrides use-when-held (right-click in hand): a player who
 * takes it out of a lectern and uses it, while the healthy skill system is unlocked and they don't
 * already have access, is granted healthy-side access and one copy is consumed. It is deliberately NOT
 * consumed by a lectern (that path never calls {@link #use}), so the book must be removed and held.
 *
 * <p>Lectern / Chiseled Bookshelf placement is TAG-based in 1.21.1 (verified against LecternBlock:
 * {@code stack.is(ItemTags.LECTERN_BOOKS)}), so this is a plain Item added to the lectern_books and
 * bookshelf_books tags via datapack - no vanilla book subclass required.
 */
public class SkillBookItem extends Item {
    public SkillBookItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        if (level.isClientSide || !(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            // Let the client predict a successful hand-swing; the grant/consume happens server-side.
            return InteractionResultHolder.success(stack);
        }
        // Defensive: books only ever exist after the flag flips, but never grant if it somehow isn't set,
        // and never re-grant to a player who already has healthy access. Both cases are a silent no-op by
        // design (the feature stays hidden while locked), which looks exactly like "the book is broken" when
        // testing with a creative-tab copy before the unlock - so say which branch was taken.
        if (!HealthySkillState.isUnlocked(serverLevel)) {
            Apolinumarise.LOGGER.debug("[Skill] {} used the book but the healthy system is still LOCKED "
                            + "(everInfectedCount={} of {} needed) - not granted, not consumed.",
                    serverPlayer.getGameProfile().getName(), HealthySkillState.everInfectedCount(serverLevel),
                    Config.HEALTHY_UNLOCK_INFECTED_THRESHOLD.get());
            return InteractionResultHolder.pass(stack);
        }
        if (SkillLogic.hasHealthyAccess(serverPlayer)) {
            Apolinumarise.LOGGER.debug("[Skill] {} used the book but already holds healthy-side access "
                    + "- not consumed.", serverPlayer.getGameProfile().getName());
            return InteractionResultHolder.pass(stack);
        }
        SkillLogic.grantHealthyAccess(serverPlayer);
        stack.shrink(1);
        // Confirm the grant on the actionbar. The locked/already-held cases stay silent (the feature is
        // meant to be invisible until unlocked); this one only fires once access actually exists, so it
        // reveals nothing new - it just makes "did that work?" answerable without reading the log.
        serverPlayer.displayClientMessage(Component.translatable("message.apolinumarise.skill.book_used"), true);
        Apolinumarise.LOGGER.debug("[Skill] {} consumed a skill book and gained healthy-side access.",
                serverPlayer.getGameProfile().getName());
        return InteractionResultHolder.success(stack);
    }
}
