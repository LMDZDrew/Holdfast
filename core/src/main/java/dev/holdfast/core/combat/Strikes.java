package dev.holdfast.core.combat;

import dev.holdfast.core.entity.WalkerEntity;
import dev.holdfast.core.net.CoreNet;
import dev.holdfast.core.net.HitFeedbackPacket;
import dev.holdfast.core.rules.BodyRegion;
import dev.holdfast.core.rules.HitRegions;
import dev.holdfast.core.rules.ContactGeometry;
import dev.holdfast.core.rules.WalkerStrikeRules;
import dev.holdfast.core.rules.WeaponClass;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.Optional;
import java.util.WeakHashMap;
import java.util.Map;

/** Resolves one hit on a walker with the rules, applies it, and tells nearby clients what happened. */
public final class Strikes {
    private Strikes() {}
    private static final Map<LivingEntity, HitFeedbackPacket> LAST_FEEDBACK = new WeakHashMap<>();
    public static Optional<HitFeedbackPacket> lastFeedback(LivingEntity target) {
        return Optional.ofNullable(LAST_FEEDBACK.get(target));
    }
    public record Contact(Vec3 point, Vec3 direction, BodyRegion region, Vec3 exit) {}

    public static ContactGeometry.Point point(Vec3 v) { return new ContactGeometry.Point(v.x,v.y,v.z); }
    public static Vec3 vec(ContactGeometry.Point p) { return new Vec3(p.x(),p.y(),p.z()); }
    public static ContactGeometry.Point localPoint(WalkerEntity w, Vec3 point) {
        double[] p=local(w,point); return new ContactGeometry.Point(p[0],p[1],p[2]);
    }
    public static Vec3 world(WalkerEntity w, ContactGeometry.Point p) {
        double r=Math.toRadians(w.yBodyRot),c=Math.cos(r),s=Math.sin(r);
        return w.position().add((-c*p.x()+s*p.z())/16,p.y()/16,(-s*p.x()-c*p.z())/16);
    }
    public static Contact trace(WalkerEntity w, Vec3 from, Vec3 to, boolean headOnly) {
        var volumes=ContactGeometry.volumes(w.posture());
        if(headOnly) volumes=volumes.stream().filter(v -> v.region()==BodyRegion.HEAD).toList();
        var hit=ContactGeometry.trace(volumes,localPoint(w,from),localPoint(w,to));
        return new Contact(world(w,hit.entry()),to.subtract(from).normalize(),hit.region(),
                hit.crossesExit()?world(w,hit.exit()):null);
    }
    public static AABB walkerBounds(WalkerEntity w) {
        AABB bounds=null;
        for(var volume:ContactGeometry.volumes(w.posture())) {
            var b=volume.box();
            for(double x:new double[]{b.x0(),b.x1()}) for(double y:new double[]{b.y0(),b.y1()})
                for(double z:new double[]{b.z0(),b.z1()}) {
                    Vec3 p=world(w,new ContactGeometry.Point(x,y,z));
                    AABB at=new AABB(p,p); bounds=bounds==null?at:bounds.minmax(at);
                }
        }
        return bounds;
    }

    /** A world point -> walker-local pixels (x across, y up from the feet, z along its facing; negative = in front). */
    public static double[] local(WalkerEntity w, Vec3 hit) {
        double yaw = Math.toRadians(w.yBodyRot);
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);         // facing
        double rx = -fz, rz = fx;                                // its right
        double dx = hit.x - w.getX(), dz = hit.z - w.getZ();
        return new double[]{(dx * rx + dz * rz) * 16.0, (hit.y - w.getY()) * 16.0, -(dx * fx + dz * fz) * 16.0};
    }

    public static BodyRegion region(WalkerEntity w, Vec3 hit) {
        double[] l = local(w, hit);
        return HitRegions.classify(w.posture(), l[0], l[1], l[2]);
    }

    /** Static regional ray approximation at the confirmed swing tick; not animated weapon/mesh collision. */
    public static Contact meleeContact(LivingEntity attacker, WalkerEntity w) {
        double reach=attacker instanceof net.minecraft.world.entity.player.Player p?p.getEntityReach():3.0;
        Vec3 eye=attacker.getEyePosition();
        Contact hit=trace(w,eye,eye.add(attacker.getViewVector(1).scale(reach)),false);
        return new Contact(hit.point(),hit.direction(),hit.region(),null);
    }

    public static Vec3 meleeHitPoint(LivingEntity attacker, WalkerEntity w) {
        return meleeContact(attacker,w).point();
    }

    public static void feedback(WalkerEntity target, HitFeedbackPacket packet) {
        LAST_FEEDBACK.put(target,packet);
        CoreNet.CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> target),packet);
    }

    /** Flesh response for a hit on a living thing that isn't a walker; its damage stays vanilla's or TaCZ's. */
    public static void feedbackOther(LivingEntity target, Vec3 hit, Vec3 direction, WeaponClass.Contact contact, boolean head) {
        feedbackOther(target,hit,direction,contact,head,null);
    }
    public static void feedbackOther(LivingEntity target, Vec3 hit, Vec3 direction, WeaponClass.Contact contact, boolean head, Vec3 exit) {
        WalkerStrikeRules.Outcome shown = target.isAlive() ? WalkerStrikeRules.Outcome.STAGGER : WalkerStrikeRules.Outcome.BRAIN_KILL;
        HitFeedbackPacket packet=HitFeedbackPacket.of(target.getId(),hit,direction,contact,head?BodyRegion.HEAD:BodyRegion.TORSO,shown);
        if(exit!=null) packet=packet.withExit(exit);
        LAST_FEEDBACK.put(target,packet);
        CoreNet.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> target),
                packet);
    }

    public static WalkerStrikeRules.Outcome apply(LivingEntity attacker, WalkerEntity w, WeaponClass weapon, Vec3 hit, BodyRegion region,
                                                  double swing, double charge) {
        Vec3 direction=attacker==null?Vec3.ZERO:hit.subtract(attacker.getEyePosition());
        return apply(attacker,w,weapon,new Contact(hit,direction,region,null),swing,charge);
    }
    public static WalkerStrikeRules.Outcome apply(LivingEntity attacker, WalkerEntity w, WeaponClass weapon, Contact hit,
                                                  double swing, double charge) {
        if (w.isCorpse()) return WalkerStrikeRules.Outcome.GLANCE;
        WalkerStrikeRules.Outcome outcome = WalkerStrikeRules.resolve(weapon, hit.region(), swing, charge);
        switch (outcome) {
            case BRAIN_KILL -> w.brainKill(null, 0);
            case STAGGER -> w.stagger(attacker);
            case GLANCE -> { }
        }
        HitFeedbackPacket packet=HitFeedbackPacket.of(w.getId(),hit.point(),hit.direction(),weapon.contact,hit.region(),outcome);
        if(weapon.firearm() && hit.exit()!=null) packet=packet.withExit(hit.exit());
        feedback(w,packet);
        return outcome;
    }
}
