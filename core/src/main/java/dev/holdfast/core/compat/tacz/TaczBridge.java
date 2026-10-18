package dev.holdfast.core.compat.tacz;

import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import dev.holdfast.core.combat.Strikes;
import dev.holdfast.core.entity.WalkerEntity;
import dev.holdfast.core.rules.WeaponClass;
import dev.holdfast.core.rules.ContactGeometry;
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
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, EntityHurtByGunEvent.Post.class, TaczBridge::onGunHurtOther);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST,false,com.tacz.guns.api.event.common.GunMeleeEvent.class,event -> {
            if(event.getLogicalSide()==LogicalSide.SERVER && event.getShooter() instanceof net.minecraft.server.level.ServerPlayer p
                    && (dev.holdfast.core.combat.Grabs.isGrabbed(p) || dev.holdfast.core.combat.Executions.isExecuting(p)))
                event.setCanceled(true); // Native shove cannot bypass a server-owned grab/paired execution.
        });
    }

    /** Any other living target keeps TaCZ's own damage; clients get the gunshot flesh response (decisions.md). */
    private static void onGunHurtOther(EntityHurtByGunEvent.Post event) {
        if (event.getLogicalSide() != LogicalSide.SERVER || !(event.getHurtEntity() instanceof net.minecraft.world.entity.LivingEntity le)
                || le instanceof WalkerEntity) return;
        Vec3[] ray=bulletSegment(event,le);
        var b=le.getBoundingBox();
        var box=new ContactGeometry.Box(b.minX,event.isHeadShot()?b.maxY-(b.maxY-b.minY)*.25:b.minY,b.minZ,b.maxX,b.maxY,b.maxZ);
        var hit=ContactGeometry.segment(Strikes.point(ray[0]),Strikes.point(ray[1]),box);
        Vec3 entry=hit.map(h -> Strikes.vec(h.entry())).orElseGet(() -> Strikes.vec(
                ContactGeometry.nearestToSegment(Strikes.point(ray[0]),Strikes.point(ray[1]),box)));
        Vec3 exit=hit.filter(ContactGeometry.Segment::crossesExit).map(h -> Strikes.vec(h.exit())).orElse(null);
        Strikes.feedbackOther(le,entry,ray[1].subtract(ray[0]),WeaponClass.Contact.GUNSHOT,event.isHeadShot(),exit);
    }

    private static void onGunHurt(EntityHurtByGunEvent.Pre event) {
        if (event.getLogicalSide() != LogicalSide.SERVER || !(event.getHurtEntity() instanceof WalkerEntity w)) return;
        event.setCanceled(true);
        Vec3[] ray=bulletSegment(event,w);
        Strikes.Contact hit=Strikes.trace(w,ray[0],ray[1],event.isHeadShot());
        WeaponClass cls = event.getGunId() != null ? TaczGuns.classOf(event.getGunId()) : WeaponClass.RIFLE;
        Strikes.apply(event.getAttacker(), w, cls, hit, 1.0, 0);
    }

    private static Vec3[] bulletSegment(EntityHurtByGunEvent event, net.minecraft.world.entity.LivingEntity target) {
        var bullet=event.getBullet();
        // TaCZ onBulletTick casts position -> position + deltaMovement before posting Pre/Post and before moving.
        // EntityHurtByGunEvent carries no TacHitResult or hit location. Do not use the previous tick's stale segment.
        if(bullet!=null && bullet.getDeltaMovement().lengthSqr()>1e-12)
            return new Vec3[]{bullet.position(),bullet.position().add(bullet.getDeltaMovement())};
        Vec3 from=event.getAttacker()==null?target.getEyePosition().add(0,0,-1):event.getAttacker().getEyePosition();
        return new Vec3[]{from,target.getBoundingBox().getCenter()};
    }
}
