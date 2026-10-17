package dev.holdfast.core.combat;

import dev.holdfast.core.HoldfastCore;
import dev.holdfast.core.compat.tacz.TaczGuns;
import dev.holdfast.core.item.MeleeWeaponItem;
import dev.holdfast.core.rules.WeaponClass;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraftforge.fml.ModList;

/** Maps a held stack to a {@link WeaponClass}. Holdfast items carry their class; others map by tag or vanilla type. */
public final class Weapons {
    public static final TagKey<Item> KNIVES = tag("knives"), MACHETES = tag("machetes"), HATCHETS = tag("hatchets"),
            AXES = tag("axes"), KATANAS = tag("katanas"), BATS = tag("bats"), HAMMERS = tag("hammers");

    private Weapons() {}

    private static TagKey<Item> tag(String name) {
        return ItemTags.create(new ResourceLocation(HoldfastCore.MODID, name));
    }

    public static WeaponClass of(ItemStack stack) {
        if (stack.isEmpty()) return WeaponClass.NONE;
        if (stack.getItem() instanceof MeleeWeaponItem m) return m.weaponClass;
        if (ModList.get().isLoaded("tacz")) {
            WeaponClass gun = TaczGuns.classOf(stack);
            if (gun != null) return gun;
        }
        if (stack.is(KNIVES)) return WeaponClass.KNIFE;
        if (stack.is(MACHETES)) return WeaponClass.MACHETE;
        if (stack.is(HATCHETS)) return WeaponClass.HATCHET;
        if (stack.is(AXES)) return WeaponClass.AXE;
        if (stack.is(KATANAS)) return WeaponClass.KATANA;
        if (stack.is(BATS)) return WeaponClass.BAT;
        if (stack.is(HAMMERS)) return WeaponClass.HAMMER;
        // Vanilla stand-ins use the matching dedicated animation family: swords/katana and short axes/hatchets.
        if (stack.getItem() instanceof AxeItem) return WeaponClass.HATCHET;
        if (stack.getItem() instanceof SwordItem) return WeaponClass.KATANA;
        return WeaponClass.NONE;
    }

    public static WeaponClass held(Player p) {
        return of(p.getMainHandItem());
    }
}
