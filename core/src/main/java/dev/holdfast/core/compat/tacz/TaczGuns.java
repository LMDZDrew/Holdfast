package dev.holdfast.core.compat.tacz;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import dev.holdfast.core.rules.WeaponClass;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Gun class from TaCZ's common gun index {@code type}. Only call when TaCZ is loaded. */
public final class TaczGuns {
    private TaczGuns() {}

    /** Null if the stack isn't a TaCZ gun. */
    public static WeaponClass classOf(ItemStack stack) {
        IGun gun = IGun.getIGunOrNull(stack);
        if (gun == null) return null;
        return classOf(gun.getGunId(stack));
    }

    public static WeaponClass classOf(ResourceLocation gunId) {
        return TimelessAPI.getCommonGunIndex(gunId).map(i -> WeaponClass.fromTaczType(i.getType())).orElse(WeaponClass.RIFLE);
    }

    /** Rounds the gun can fire now: its magazine plus a chambered round (closed-bolt guns). 0 if not a gun. */
    public static int rounds(ItemStack stack) {
        IGun gun = IGun.getIGunOrNull(stack);
        if (gun == null) return 0;
        return Math.max(0, gun.getCurrentAmmoCount(stack)) + (gun.hasBulletInBarrel(stack) ? 1 : 0);
    }

    /**
     * Spend one round as a shot would: the chambered round if there is one (re-chambering from the magazine when it
     * has rounds), else one from the magazine. False if the gun was empty.
     */
    public static boolean consumeRound(ItemStack stack) {
        IGun gun = IGun.getIGunOrNull(stack);
        if (gun == null) return false;
        int mag = gun.getCurrentAmmoCount(stack);
        if (gun.hasBulletInBarrel(stack)) {
            if (mag > 0) gun.reduceCurrentAmmoCount(stack);
            else gun.setBulletInBarrel(stack, false);
            return true;
        }
        if (mag > 0) {
            gun.reduceCurrentAmmoCount(stack);
            return true;
        }
        return false;
    }

    public static boolean holdingGun(net.minecraft.world.entity.LivingEntity e) {
        return IGun.mainHandHoldGun(e);
    }
}
