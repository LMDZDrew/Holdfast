package dev.holdfast.core.item;

import dev.holdfast.core.rules.WeaponClass;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;

/**
 * An ordinary melee weapon. Its vanilla damage only matters against non-walkers; against walkers the core resolves
 * every hit by region and {@link #weaponClass} (body hits never kill). Repairable; at zero condition it stays in the
 * inventory at zero rather than breaking (decisions.md), see {@link #canBeDepleted()}.
 */
public class MeleeWeaponItem extends SwordItem {
    public final WeaponClass weaponClass;
    /** Two-handed weapons need both hands free (the off hand must be empty to swing). */
    public final boolean twoHanded;

    public MeleeWeaponItem(WeaponClass weaponClass, boolean twoHanded, Tier tier, int damage, float speed, Properties props) {
        super(tier, damage, speed, props);
        this.weaponClass = weaponClass;
        this.twoHanded = twoHanded;
    }

    /** Blunt weapons charge while use is held (S10); the server measures the hold (BluntCharge). */
    @Override
    public net.minecraft.world.InteractionResultHolder<net.minecraft.world.item.ItemStack> use(net.minecraft.world.level.Level level,
            net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand) {
        if (weaponClass.blunt() && hand == net.minecraft.world.InteractionHand.MAIN_HAND) {
            player.startUsingItem(hand);
            return net.minecraft.world.InteractionResultHolder.consume(player.getItemInHand(hand));
        }
        return super.use(level, player, hand);
    }

    @Override
    public int getUseDuration(net.minecraft.world.item.ItemStack stack) {
        return weaponClass.blunt() ? 72000 : 0;
    }

    @Override
    public net.minecraft.world.item.UseAnim getUseAnimation(net.minecraft.world.item.ItemStack stack) {
        return weaponClass.blunt() ? net.minecraft.world.item.UseAnim.NONE : super.getUseAnimation(stack);
    }

    @Override
    public boolean canBeDepleted() {
        return true;
    }

    @Override
    public <T extends net.minecraft.world.entity.LivingEntity> int damageItem(net.minecraft.world.item.ItemStack stack, int amount,
                                                                           T entity, java.util.function.Consumer<T> onBroken) {
        // Never destroy the item: stop one point short of breaking (it remains at zero condition, repairable).
        int left = stack.getMaxDamage() - 1 - stack.getDamageValue();
        return Math.max(0, Math.min(amount, left));
    }
}
