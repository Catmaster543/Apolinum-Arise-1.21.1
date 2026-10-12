package com.fiskerz.apolinum_arise.ability.hivemind;

import com.fiskerz.apolinum_arise.Apolinumarise;
import com.fiskerz.apolinum_arise.ability.AbilityManager;
import com.fiskerz.apolinum_arise.config.Config;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/** The wand's drop: the one way the Hive-Mind ability is obtained in normal play. */
public final class HiveMindEvents {
    private HiveMindEvents() {}

    /**
     * Roll for the wand when an eligible player gets the kill.
     *
     * <p><b>Kill credit</b> is vanilla's own {@code LivingEntity.getKillCredit()}, which returns
     * {@code lastHurtByPlayer} - set in {@code LivingEntity.hurt} from {@code damageSource.getEntity()}.
     * For an arrow that entity is the shooter, so melee and ranged are attributed identically, and
     * identically to the death message. Nothing bespoke is needed to "cover ranged".
     *
     * <p><b>Eligibility gates the roll, not the result.</b> {@link HiveMind#canDropFor} is checked first
     * and the random number is never drawn for a player who does not qualify - in particular, a player who
     * has not watched the dream cannot produce a drop no matter how many mobs they kill.
     */
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!Config.ENABLE_MOD.get()) {
            return;
        }
        LivingEntity dead = event.getEntity();
        if (dead.level().isClientSide() || dead instanceof Player) {
            return; // mobs only; a player kill is not a mob drop
        }
        if (!(dead.getKillCredit() instanceof ServerPlayer killer)) {
            return;
        }
        if (shouldDrop(killer, killer.getRandom())) {
            grant(killer);
        }
    }

    /**
     * The whole drop decision, split out so a gametest can drive it with a mock player and a forced chance.
     *
     * <p>Order is the point: eligibility is checked BEFORE the random number is drawn, so a player who does
     * not qualify never rolls at all - not even at a chance of 1.0. That is what makes "the dream has not
     * been watched" a hard gate rather than a very unlikely one.
     */
    public static boolean shouldDrop(Player killer, RandomSource random) {
        if (!HiveMind.canDropFor(killer)) {
            return false;
        }
        return random.nextDouble() < Config.HIVE_MIND_DROP_CHANCE.get();
    }

    /**
     * Hand over the wand and unlock the ability in the same breath - receiving it IS the unlock, so the two
     * must not be separable. Also used by the debug command so the grant can be exercised without farming
     * a 0.1% drop.
     */
    public static void grant(ServerPlayer player) {
        ItemStack wand = new ItemStack(HiveMindRegistry.HIVE_MIND_STICK.get());
        if (!player.getInventory().add(wand)) {
            player.drop(wand, false);
        }
        AbilityManager.unlockAbility(player, HiveMind.ABILITY_ID);
        player.displayClientMessage(Component.translatable("message.apolinumarise.hivemind.unlocked"), true);
        Apolinumarise.LOGGER.info("[HiveMind] {} received the wand and unlocked '{}'.",
                player.getGameProfile().getName(), HiveMind.ABILITY_ID);
    }
}
