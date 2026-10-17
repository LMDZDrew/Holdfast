package dev.holdfast.animtest.client;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.model.functional.MuzzleFlashRender;
import com.tacz.guns.client.sound.SoundPlayManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** Loaded only for a TaCZ execution shot. Uses the exact installed 1.1.8-hotfix2 presentation API. */
public final class TaczShotVisual {
    private TaczShotVisual() { }
    public static void play(int shooterId, ItemStack stack) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getEntity(shooterId) instanceof LivingEntity shooter)) return;
        IGun gun = IGun.getIGunOrNull(stack);
        if (gun == null) return;
        TimelessAPI.getGunDisplay(stack).ifPresent(display ->
            TimelessAPI.getCommonGunIndex(gun.getGunId(stack)).ifPresent(index -> {
                SoundPlayManager.playShootSound(shooter, display, index.getGunData());
                if (shooter == mc.player) MuzzleFlashRender.onShoot();
                dev.holdfast.animtest.HoldfastAnimTest.LOG.info("[holdfast-test] execution shot gun {} sound {} local flash hook {}",
                        gun.getGunId(stack), display.getSounds(com.tacz.guns.sound.SoundManager.SHOOT_SOUND), shooter == mc.player);
            }));
    }
}
