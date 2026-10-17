package dev.holdfast.core.client;

import dev.holdfast.core.HoldfastCore;
import dev.holdfast.core.entity.WalkerEntity;
import dev.holdfast.core.rules.Posture;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/** The exported walker and crawler rigs, unchanged (art/characters/export/assets/holdfast). */
public class WalkerModel extends GeoModel<WalkerEntity> {
    private static final ResourceLocation GEO = rl("geo/walker.geo.json"), GEO_CRAWLER = rl("geo/walker_crawler.geo.json");
    private static final ResourceLocation ANIM = rl("animations/walker.animation.json"), ANIM_CRAWLER = rl("animations/walker_crawler.animation.json");
    private static final ResourceLocation[] SKINS = {rl("textures/entity/walker/farmer.png"), rl("textures/entity/walker/tshirt.png"),
            rl("textures/entity/walker/scrubs.png"), rl("textures/entity/walker/office.png")};

    private static ResourceLocation rl(String path) {
        return new ResourceLocation(HoldfastCore.ASSETS, path);
    }

    @Override
    public ResourceLocation getModelResource(WalkerEntity w) {
        return w.posture() == Posture.CRAWLER ? GEO_CRAWLER : GEO;
    }

    @Override
    public ResourceLocation getTextureResource(WalkerEntity w) {
        return SKINS[Math.floorMod(w.skin(), SKINS.length)];
    }

    @Override
    public ResourceLocation getAnimationResource(WalkerEntity w) {
        return w.posture() == Posture.CRAWLER ? ANIM_CRAWLER : ANIM;
    }
}
