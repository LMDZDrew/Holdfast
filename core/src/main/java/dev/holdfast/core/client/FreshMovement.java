package dev.holdfast.core.client;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;

/** Optional resource-driven locomotion. No EMF/ETF dependency is loaded by dedicated-server code. */
final class FreshMovement {
    private static final ResourceLocation PLAYER = new ResourceLocation("minecraft", "emf/cem/player.jem");
    private static final ResourceLocation SLIM = new ResourceLocation("minecraft", "emf/cem/player_slim.jem");

    private FreshMovement() {}

    static boolean active() {
        var mods = ModList.get();
        if (!mods.isLoaded("entity_model_features") || !mods.isLoaded("entity_texture_features")
                || !mods.isLoaded("entity_player_compat")) return false;
        // Guns need their separate EMF bridge; do not yield gait while their pose compatibility is absent.
        if (mods.isLoaded("tacz") && (!mods.isLoaded("emf_compat_core") || !mods.isLoaded("emf_compat_tacz"))) return false;
        var resources = Minecraft.getInstance().getResourceManager();
        // Require the selected pack, not merely jars or a disabled zip in the resourcepacks directory.
        return resources.getResource(PLAYER).map(r -> r.sourcePackId().contains("FA+Player")).orElse(false)
                && resources.getResource(SLIM).isPresent();
    }
}
