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

    public static boolean holdingGun(net.minecraft.world.entity.LivingEntity e) {
        return IGun.mainHandHoldGun(e);
    }
}
