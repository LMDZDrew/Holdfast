package dev.holdfast.core.compat.firstperson;

import dev.tr7zw.firstperson.FirstPersonModelCore;
import dev.tr7zw.firstperson.api.ActivationHandler;
import dev.tr7zw.firstperson.api.FirstPersonAPI;
import dev.tr7zw.firstperson.api.PlayerOffsetHandler;

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
        // FPM adds its default PA support after client setup. Wrap it after login, once it exists.
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingIn event) -> preservePairedAdvance());
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

    private record PairedOffset(PlayerOffsetHandler delegate) implements PlayerOffsetHandler {
        @Override public net.minecraft.world.phys.Vec3 applyOffset(net.minecraft.client.player.AbstractClientPlayer player,
                float partial, net.minecraft.world.phys.Vec3 original, net.minecraft.world.phys.Vec3 current) {
            // The built-in support subtracts the animated body's horizontal advance to keep its head at the eye.
            // A paired stab needs that advance to reach the same skull as in third person; do not cancel it.
            if(dev.holdfast.core.client.ClientClips.pairedMeleeExecution(player)) {
                double advance=dev.holdfast.core.client.ClientClips.pairedViewAdvance(player,partial);
                return current.add(net.minecraft.world.phys.Vec3.directionFromRotation(0,player.getYRot()).scale(advance));
            }
            return delegate.applyOffset(player, partial, original, current);
        }
    }

    private static void preservePairedAdvance() {
        var handlers=FirstPersonAPI.getPlayerOffsetHandlers();
        for(int i=0;i<handlers.size();i++) {
            var handler=handlers.get(i);
            if(handler.getClass().getName().equals("dev.tr7zw.firstperson.modsupport.PlayerAnimatorSupport")) {
                handlers.set(i,new PairedOffset(handler));
                dev.holdfast.core.HoldfastCore.LOG.info("[holdfast] FPM paired melee advance preserved; other offset handlers retained");
            }
        }
    }
}
