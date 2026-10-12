package com.fiskerz.apolinum_arise.ability.hivemind;

import com.fiskerz.apolinum_arise.config.Config;

import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;

/**
 * The Hive-Mind wand: a stick that hits for exactly {@code hiveMindWandDamage} (default 2.0, one heart),
 * and - only in the hands of someone who has actually unlocked the ability - turns every nearby mob on
 * whatever it just struck.
 *
 * <h2>How the damage is set (verified against the 1.21.1 sources)</h2>
 * Weapon damage in 1.21.1 is an {@link ItemAttributeModifiers} component, not an overridable method.
 * {@code SwordItem.createAttributes} builds one with an {@code ADD_VALUE} modifier on
 * {@link Attributes#ATTACK_DAMAGE} under {@link Item#BASE_ATTACK_DAMAGE_ID} for
 * {@link EquipmentSlotGroup#MAINHAND}. A player's BASE attack damage attribute is <b>1.0</b>
 * ({@code Player.createAttributes}), and the final hit is base + modifier - so the modifier here is
 * {@code wandDamage - 1.0} to make the total come out at exactly the configured value.
 *
 * <p>The modifiers are supplied through {@link #getDefaultAttributeModifiers(ItemStack)} rather than
 * {@code Item.Properties.attributes(...)} <b>on purpose</b>: {@code Properties.attributes} bakes the
 * component onto every stack at registration time, which happens long before a SERVER config is readable,
 * and NeoForge's {@code IItemStackExtension.getAttributeModifiers} only falls back to this method when the
 * stack's component is empty. Leaving the component off is therefore what makes {@code hiveMindWandDamage}
 * a live config value instead of a number frozen at mod load.
 *
 * <p>Attack SPEED is deliberately not modified, so the wand swings at the player's default rate rather
 * than a sword's slower one - it is a stick, not a sword.
 *
 * <p>Texture/model pending: {@code assets/apolinumarise/models/item/hive_mind_stick.json} ->
 * {@code textures/item/hive_mind_stick.png}. A missing model renders as the magenta/black placeholder and
 * does not crash, so the item is fully usable before the art lands.
 */
public class HiveMindStickItem extends Item {
    public HiveMindStickItem(Properties properties) {
        super(properties);
    }

    @Override
    public ItemAttributeModifiers getDefaultAttributeModifiers(ItemStack stack) {
        double bonus = Config.HIVE_MIND_WAND_DAMAGE.get() - PLAYER_BASE_ATTACK_DAMAGE;
        return ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(BASE_ATTACK_DAMAGE_ID, bonus, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .build();
    }

    /** {@code Player.createAttributes()} registers ATTACK_DAMAGE at 1.0; the wand's modifier is relative. */
    public static final double PLAYER_BASE_ATTACK_DAMAGE = 1.0D;

    /**
     * Fires server-side straight after the damage has been applied ({@code Player.attack} calls this once
     * the target has already been hurt), which is exactly where the radius effect belongs.
     *
     * <p>The ability check is on the WIELDER, not the item. An unlocked player's wand does the hive-mind
     * thing; the same stack in anyone else's hand is just a stick that hits for one heart.
     */
    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (attacker.level() instanceof Level level && !level.isClientSide()
                && attacker instanceof net.minecraft.world.entity.player.Player player
                && HiveMind.isUnlockedFor(player)) {
            HiveMindEffect.trigger(player, target);
        }
        return true;
    }
}
