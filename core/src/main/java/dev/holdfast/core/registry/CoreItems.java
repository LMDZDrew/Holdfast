package dev.holdfast.core.registry;

import dev.holdfast.core.HoldfastCore;
import dev.holdfast.core.item.BiteGuardItem;
import dev.holdfast.core.item.MeleeWeaponItem;
import dev.holdfast.core.item.MichonneKatanaItem;
import dev.holdfast.core.rules.BodyRegion;
import dev.holdfast.core.rules.WeaponClass;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tiers;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public final class CoreItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, HoldfastCore.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, HoldfastCore.MODID);
    private static final List<RegistryObject<? extends Item>> ALL = new ArrayList<>();

    // Ordinary melee weapons, one per animation family (decisions.md). Damage numbers only affect non-walkers.
    public static final RegistryObject<Item> KNIFE = melee("knife", WeaponClass.KNIFE, false, Tiers.IRON, 2, -1.6F);
    public static final RegistryObject<Item> MACHETE = melee("machete", WeaponClass.MACHETE, false, Tiers.IRON, 4, -2.2F);
    public static final RegistryObject<Item> HATCHET = melee("hatchet", WeaponClass.HATCHET, false, Tiers.IRON, 4, -2.4F);
    public static final RegistryObject<Item> FIRE_AXE = melee("fire_axe", WeaponClass.AXE, true, Tiers.IRON, 7, -3.1F);
    public static final RegistryObject<Item> BASEBALL_BAT = melee("baseball_bat", WeaponClass.BAT, true, Tiers.WOOD, 5, -2.8F);
    public static final RegistryObject<Item> KATANA = melee("katana", WeaponClass.KATANA, true, Tiers.IRON, 5, -2.2F);
    // Iconic weapons: an existing family's stats and animations with their own model (decisions.md).
    public static final RegistryObject<Item> MICHONNE_KATANA = reg("michonne_katana",
            () -> new MichonneKatanaItem(Tiers.IRON, 5, -2.2F, new Item.Properties()));
    public static final RegistryObject<Item> SLEDGEHAMMER = melee("sledgehammer", WeaponClass.HAMMER, true, Tiers.IRON, 8, -3.3F);

    // Bite protection (regions per slot: neck guard on the head slot, forearm/torso guard on the chest, shin guards on the feet).
    public static final RegistryObject<Item> PADDED_COLLAR = guard("padded_collar", BiteGuardItem.Material.LEATHER_PADDED, ArmorItem.Type.HELMET, EnumSet.of(BodyRegion.NECK));
    public static final RegistryObject<Item> PADDED_SLEEVES = guard("padded_sleeves", BiteGuardItem.Material.LEATHER_PADDED, ArmorItem.Type.CHESTPLATE, EnumSet.of(BodyRegion.ARM));
    public static final RegistryObject<Item> RIOT_COLLAR = guard("riot_collar", BiteGuardItem.Material.RIOT, ArmorItem.Type.HELMET, EnumSet.of(BodyRegion.NECK));
    public static final RegistryObject<Item> RIOT_VEST = guard("riot_vest", BiteGuardItem.Material.RIOT, ArmorItem.Type.CHESTPLATE, EnumSet.of(BodyRegion.ARM, BodyRegion.TORSO));
    public static final RegistryObject<Item> RIOT_SHIN_GUARDS = guard("riot_shin_guards", BiteGuardItem.Material.RIOT, ArmorItem.Type.BOOTS, EnumSet.of(BodyRegion.LEG));
    public static final RegistryObject<Item> CHAIN_SLEEVES = guard("chain_sleeves", BiteGuardItem.Material.CHAIN, ArmorItem.Type.CHESTPLATE, EnumSet.of(BodyRegion.ARM));

    public static final RegistryObject<Item> WALKER_SPAWN_EGG = reg("walker_spawn_egg",
            () -> new ForgeSpawnEggItem(CoreEntities.WALKER, 0x5a6b4e, 0x3a2a24, new Item.Properties()));

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("holdfast", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.holdfast_core"))
            .icon(() -> new ItemStack(MACHETE.get()))
            .displayItems((params, out) -> ALL.forEach(r -> out.accept(r.get())))
            .build());

    private CoreItems() {}

    private static <T extends Item> RegistryObject<T> reg(String name, java.util.function.Supplier<T> sup) {
        RegistryObject<T> r = ITEMS.register(name, sup);
        ALL.add(r);
        return r;
    }

    private static RegistryObject<Item> melee(String name, WeaponClass c, boolean twoHanded, Tiers tier, int dmg, float speed) {
        return reg(name, () -> new MeleeWeaponItem(c, twoHanded, tier, dmg, speed, new Item.Properties()));
    }

    private static RegistryObject<Item> guard(String name, BiteGuardItem.Material m, ArmorItem.Type slot, EnumSet<BodyRegion> covers) {
        return reg(name, () -> new BiteGuardItem(m, slot, covers, new Item.Properties()));
    }
}
