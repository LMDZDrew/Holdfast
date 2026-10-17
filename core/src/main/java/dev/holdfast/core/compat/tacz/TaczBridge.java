package dev.holdfast.core.compat.tacz;

import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import dev.holdfast.core.combat.Strikes;
import dev.holdfast.core.entity.WalkerEntity;
import dev.holdfast.core.rules.BodyRegion;
import dev.holdfast.core.rules.WeaponClass;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.LogicalSide;

/**
 * TaCZ 1.1.8-hotfix2 integration (compile-only; loaded only when TaCZ is present). TaCZ's bullet damage to a walker
 * is cancelled and replaced by the core's outcome: TaCZ's own headshot test or a skull-region bullet position is a
 * brain kill; anything else only staggers (walkers have no damage pool). API read from the jar with javap:
 * {@code EntityHurtByGunEvent.Pre} is {@code @Cancelable} and carries the bullet, attacker, gun id and headshot flag.
 */
public final class TaczBridge {
    private TaczBridge() {}

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, false, EntityHurtByGunEvent.Pre.class, TaczBridge::onGunHurt);
    }

    private static void onGunHurt(EntityHurtByGunEvent.Pre event) {
        if (event.getLogicalSide() != LogicalSide.SERVER || !(event.getHurtEntity() instanceof WalkerEntity w)) return;
        event.setCanceled(true);
        Vec3 hit = event.getBullet() != null ? event.getBullet().position() : w.getEyePosition();
        BodyRegion region = event.isHeadShot() ? BodyRegion.HEAD : Strikes.region(w, hit);
        WeaponClass cls = event.getGunId() != null ? TaczGuns.classOf(event.getGunId()) : WeaponClass.RIFLE;
        Strikes.apply(event.getAttacker(), w, cls, hit, region, 1.0, 0);
    }
}
