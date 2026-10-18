package dev.holdfast.core.compat.firstperson;

import dev.tr7zw.firstperson.FirstPersonModelCore;
import dev.tr7zw.firstperson.api.ActivationHandler;
import dev.tr7zw.firstperson.api.FirstPersonAPI;

/** Optional client-only FPM integration. No dependency class is touched when the mod is absent. */
public final class FirstPersonBody {
    private FirstPersonBody() {}

    public static boolean active() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        var fpm = FirstPersonModelCore.instance;
        return mc.player != null && fpm != null && fpm.getLogicHandler() != null
                && fpm.getLogicHandler().shouldApplyThirdPerson(false);
    }

    public static void register() {
        // Harness inspections retain their own renderer, independently of normal core animation layers.
        FirstPersonAPI.registerPlayerHandler((ActivationHandler) () -> {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player == null) return true;
            Object test = dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess.getPlayerAssociatedData(mc.player)
                    .get(new net.minecraft.resources.ResourceLocation("holdfast_animtest", "test_layer"));
            return test instanceof dev.kosmx.playerAnim.api.layered.IAnimation a && a.isActive();
        });
        dev.holdfast.core.HoldfastCore.LOG.info("[holdfast] First Person Model integration registered; FPM body / PA animation / TaCZ gun ownership");
    }
}
