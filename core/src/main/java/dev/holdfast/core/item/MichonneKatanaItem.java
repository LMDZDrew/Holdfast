package dev.holdfast.core.item;

import dev.holdfast.core.HoldfastCore;
import dev.holdfast.core.rules.WeaponClass;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Tier;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.function.Consumer;

/**
 * Michonne's katana: an ordinary katana-family weapon (same swings, contact, executions and sounds as
 * {@link WeaponClass#KATANA}) drawn by GeckoLib from the box model built by art/weapons/michonne_katana. The geometry
 * sits in the existing katana's held-item frame, so the katana clips need no changes. Not unique per world yet.
 */
public class MichonneKatanaItem extends MeleeWeaponItem implements GeoItem {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public MichonneKatanaItem(Tier tier, int damage, float speed, Properties props) {
        super(WeaponClass.KATANA, true, tier, damage, speed, props);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {}

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private GeoItemRenderer<MichonneKatanaItem> renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null)
                    renderer = new GeoItemRenderer<>(new DefaultedItemGeoModel<>(
                            new ResourceLocation(HoldfastCore.MODID, "michonne_katana")));
                return renderer;
            }
        });
    }
}
