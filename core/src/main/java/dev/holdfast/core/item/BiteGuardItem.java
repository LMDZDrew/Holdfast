package dev.holdfast.core.item;

import dev.holdfast.core.HoldfastCore;
import dev.holdfast.core.rules.BiteRules;
import dev.holdfast.core.rules.BodyRegion;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Bite-rated protection. Its durability is counted in bites: each blocked bite costs one point, and the bite that
 * takes the last point is still blocked (BiteRules). At zero it stays worn and repairable but protects nothing.
 * Covered regions are explicit, so ordinary jackets (no BiteGuardItem) never stop a bite.
 */
public class BiteGuardItem extends ArmorItem {
    public final Set<BodyRegion> covers;

    public BiteGuardItem(Material material, Type slot, Set<BodyRegion> covers, Properties props) {
        super(material, slot, props.defaultDurability(material.bites));
        this.covers = EnumSet.copyOf(covers);
    }

    /** Remaining bites this stack can stop. */
    public static int condition(ItemStack stack) {
        return Math.max(0, stack.getMaxDamage() - stack.getDamageValue());
    }

    public BiteRules.Guard guard(ItemStack stack) {
        return new BiteRules.Guard(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(this).toString(), covers, condition(stack));
    }

    /** Apply one blocked bite: never removes the item. */
    public static void wear(ItemStack stack) {
        stack.setDamageValue(Math.min(stack.getMaxDamage(), stack.getDamageValue() + 1));
    }

    @Override
    public boolean isDamageable(ItemStack stack) {
        return true;
    }

    @Override
    public <T extends net.minecraft.world.entity.LivingEntity> int damageItem(ItemStack stack, int amount, T entity,
                                                                           java.util.function.Consumer<T> onBroken) {
        return 0;      // ordinary damage doesn't wear bite protection; only bites do (wear)
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tip, TooltipFlag flag) {
        int left = condition(stack);
        tip.add(Component.translatable(left > 0 ? "tooltip.holdfast_core.bite_guard" : "tooltip.holdfast_core.bite_guard_broken",
                left, covers.toString().toLowerCase(java.util.Locale.ROOT)));
    }

    /** Armor material with a bite capacity. Defence values are low: these stop teeth, not bullets. */
    public enum Material implements ArmorMaterial {
        LEATHER_PADDED("padded_leather", 2, 1, SoundEvents.ARMOR_EQUIP_LEATHER),
        RIOT("riot", 5, 2, SoundEvents.ARMOR_EQUIP_IRON),
        CHAIN("chain_mesh", 8, 2, SoundEvents.ARMOR_EQUIP_CHAIN);

        public final String id;
        public final int bites;
        private final int defense;
        private final SoundEvent sound;

        Material(String id, int bites, int defense, SoundEvent sound) {
            this.id = id;
            this.bites = bites;
            this.defense = defense;
            this.sound = sound;
        }

        @Override public int getDurabilityForType(Type type) { return bites; }
        @Override public int getDefenseForType(Type type) { return defense; }
        @Override public int getEnchantmentValue() { return 0; }
        @Override public SoundEvent getEquipSound() { return sound; }
        @Override public Ingredient getRepairIngredient() { return Ingredient.EMPTY; }
        @Override public String getName() { return HoldfastCore.MODID + ":" + id; }
        @Override public float getToughness() { return 0; }
        @Override public float getKnockbackResistance() { return 0; }
    }
}
