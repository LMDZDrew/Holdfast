package dev.holdfast.animtest.client;

import dev.holdfast.animtest.HoldfastAnimTest;
import dev.holdfast.animtest.TestWalker;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/** Points GeckoLib at the exported files, unchanged: geo/, animations/ and textures/entity/walker/ under assets/holdfast. */
public class WalkerModel extends GeoModel<TestWalker> {
    private static final ResourceLocation GEO = rl("geo/walker.geo.json");
    private static final ResourceLocation GEO_CRAWLER = rl("geo/walker_crawler.geo.json");
    private static final ResourceLocation ANIM = rl("animations/walker.animation.json");
    private static final ResourceLocation ANIM_CRAWLER = rl("animations/walker_crawler.animation.json");
    private static final ResourceLocation[] SKINS = {
            rl("textures/entity/walker/farmer.png"), rl("textures/entity/walker/tshirt.png"),
            rl("textures/entity/walker/scrubs.png"), rl("textures/entity/walker/office.png")};

    private static ResourceLocation rl(String path) {
        return new ResourceLocation(HoldfastAnimTest.ASSETS, path);
    }

    @Override
    public ResourceLocation getModelResource(TestWalker walker) {
        return walker.isCrawler() ? GEO_CRAWLER : GEO;
    }

    @Override
    public ResourceLocation getTextureResource(TestWalker walker) {
        return SKINS[Math.floorMod(walker.skin(), SKINS.length)];
    }

    @Override
    public ResourceLocation getAnimationResource(TestWalker walker) {
        return walker.isCrawler() ? ANIM_CRAWLER : ANIM;
    }
}
