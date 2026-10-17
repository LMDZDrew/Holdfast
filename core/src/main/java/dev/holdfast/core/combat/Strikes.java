package dev.holdfast.core.combat;

import dev.holdfast.core.entity.WalkerEntity;
import dev.holdfast.core.net.CoreNet;
import dev.holdfast.core.net.HitFeedbackPacket;
import dev.holdfast.core.rules.BodyRegion;
import dev.holdfast.core.rules.HitRegions;
import dev.holdfast.core.rules.WalkerStrikeRules;
import dev.holdfast.core.rules.WeaponClass;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.Optional;

/** Resolves one hit on a walker with the rules, applies it, and tells nearby clients what happened. */
public final class Strikes {
    private Strikes() {}

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

    /** Where a melee swing from {@code attacker}'s eyes meets the walker's box, or the nearest point to the look ray. */
    public static Vec3 meleeHitPoint(LivingEntity attacker, WalkerEntity w) {
        Vec3 eye = attacker.getEyePosition();
        Vec3 look = attacker.getViewVector(1.0F);
        AABB box = w.getBoundingBox().inflate(0.05);
        Optional<Vec3> hit = box.clip(eye, eye.add(look.scale(6.0)));
        if (hit.isPresent()) return hit.get();
        Vec3 c = box.getCenter();
        double t = Math.max(0, c.subtract(eye).dot(look));
        Vec3 p = eye.add(look.scale(t));
        return new Vec3(clamp(p.x, box.minX, box.maxX), clamp(p.y, box.minY, box.maxY), clamp(p.z, box.minZ, box.maxZ));
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** Flesh response for a hit on a living thing that isn't a walker; its damage stays vanilla's or TaCZ's. */
    public static void feedbackOther(LivingEntity target, Vec3 hit, Vec3 direction, WeaponClass.Contact contact, boolean head) {
        WalkerStrikeRules.Outcome shown = target.isAlive() ? WalkerStrikeRules.Outcome.STAGGER : WalkerStrikeRules.Outcome.BRAIN_KILL;
        CoreNet.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> target),
                HitFeedbackPacket.of(target.getId(), hit, direction, contact, head ? BodyRegion.HEAD : BodyRegion.TORSO, shown));
    }

    public static WalkerStrikeRules.Outcome apply(LivingEntity attacker, WalkerEntity w, WeaponClass weapon, Vec3 hit, BodyRegion region,
                                                  double swing, double charge) {
        if (w.isCorpse()) return WalkerStrikeRules.Outcome.GLANCE;
        WalkerStrikeRules.Outcome outcome = WalkerStrikeRules.resolve(weapon, region, swing, charge);
        switch (outcome) {
            case BRAIN_KILL -> w.brainKill(null, 0);
            case STAGGER -> w.stagger(attacker);
            case GLANCE -> { }
        }
        Vec3 dir = attacker == null ? Vec3.ZERO : hit.subtract(attacker.getEyePosition());
        CoreNet.CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> w),
                HitFeedbackPacket.of(w.getId(), hit, dir, weapon.contact, region, outcome));
        return outcome;
    }
}
