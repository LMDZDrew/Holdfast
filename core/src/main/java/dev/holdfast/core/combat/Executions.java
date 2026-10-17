package dev.holdfast.core.combat;

import dev.holdfast.core.entity.WalkerEntity;
import dev.holdfast.core.net.CoreNet;
import dev.holdfast.core.net.HitFeedbackPacket;
import dev.holdfast.core.net.PlayClipPacket;
import dev.holdfast.core.rules.BodyRegion;
import dev.holdfast.core.rules.CombatTimings;
import dev.holdfast.core.rules.ExecutionRules;
import dev.holdfast.core.rules.Posture;
import dev.holdfast.core.rules.WalkerStrikeRules;
import dev.holdfast.core.rules.WeaponClass;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side executions (S7-S9). The client only asks; the server validates with {@link ExecutionRules}, snaps
 * both participants, starts both clips, accepts a cancel before the commit tick, and applies the brain kill at the
 * clip's kill tick if the target is still in place. No invulnerability: other walkers keep acting.
 */
public final class Executions {
    /** How far the target may drift from its snapped position before the kill is refused, blocks. */
    private static final double IN_PLACE = 0.4;

    private static final class Active {
        final WalkerEntity target;
        final ExecutionRules.Session session;
        final Vec3 snapped;
        final WeaponClass weapon;
        ServerPlayer ref;

        Active(WalkerEntity target, ExecutionRules.Session session, Vec3 snapped, WeaponClass weapon) {
            this.target = target;
            this.session = session;
            this.snapped = snapped;
            this.weapon = weapon;
        }
    }

    private static final Map<UUID, Active> ACTIVE = new ConcurrentHashMap<>();

    private Executions() {}

    public static boolean isExecuting(ServerPlayer p) {
        return ACTIVE.containsKey(p.getUUID());
    }

    private static boolean targeted(WalkerEntity w) {
        for (Active a : ACTIVE.values()) if (a.target == w) return true;
        return false;
    }

    public static void request(ServerPlayer p, Entity e) {
        if (!(e instanceof WalkerEntity w) || w.isCorpse() || !p.isAlive()) return;
        WeaponClass weapon = Weapons.held(p);
        double dx = w.getX() - p.getX(), dz = w.getZ() - p.getZ();
        double distPx = Math.sqrt(dx * dx + dz * dz) * 16.0;
        double toTarget = Math.toDegrees(Math.atan2(-dx, dz));
        double facingErr = wrap(toTarget - p.getYRot());
        double charge = weapon.blunt() ? BluntCharge.holding(p) : 0;
        ExecutionRules.Request req = new ExecutionRules.Request(weapon, w.posture(), distPx, facingErr, true,
                Grabs.isGrabbing(w), targeted(w), charge);
        Optional<ExecutionRules.Refusal> refusal = ExecutionRules.validate(req);
        if (refusal.isPresent()) {
            p.displayClientMessage(Component.translatable("message.holdfast_core.execute_refused." + refusal.get().name().toLowerCase(java.util.Locale.ROOT)), true);
            return;
        }
        CombatTimings.Exec clip = ExecutionRules.clipFor(weapon, w.posture()).orElseThrow();
        if (weapon.blunt()) BluntCharge.clear(p);
        // Snap: level the view, place the target at the clip's pairing distance in front, facing the player.
        float yaw = p.getYRot();
        p.connection.teleport(p.getX(), p.getY(), p.getZ(), yaw, 0.0F);
        double rad = Math.toRadians(yaw), d = clip.distPx() / 16.0;
        Vec3 at = new Vec3(p.getX() - Math.sin(rad) * d, w.getY(), p.getZ() + Math.cos(rad) * d);
        float facing = w.posture() == Posture.UPRIGHT ? yaw + 180.0F : yaw + 180.0F;
        w.moveTo(at.x, at.y, at.z, facing, 0.0F);
        w.setYBodyRot(facing);
        w.setYHeadRot(facing);
        w.getNavigation().stop();
        long now = p.serverLevel().getGameTime();
        Active active = new Active(w, new ExecutionRules.Session(clip, now), at, weapon);
        active.ref = p;
        ACTIVE.put(p.getUUID(), active);
        w.playFull(clip.targetClip(), clip.lengthTicks() + 40);
        Grabs.clip(p, clip.playerClip(), w);
    }

    /** S8: cancel accepted only before commit; the target returns to its idle. */
    public static void cancel(ServerPlayer p) {
        Active a = ACTIVE.get(p.getUUID());
        if (a == null) return;
        if (a.session.cancel(p.serverLevel().getGameTime())) {
            ACTIVE.remove(p.getUUID());
            a.target.playFull("", 0);
            CoreNet.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), PlayClipPacket.stop(p.getId()));
        }
    }

    /** Logout or shutdown: dropped without a kill. */
    public static void drop(ServerPlayer p) {
        Active a = ACTIVE.remove(p.getUUID());
        if (a != null && !a.target.isCorpse()) a.target.playFull("", 0);
    }

    public static void tick(MinecraftServer server) {
        for (Iterator<Map.Entry<UUID, Active>> it = ACTIVE.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Active> e = it.next();
            Active a = e.getValue();
            ServerPlayer p = Players.resolve(server, e.getKey(), a.ref);
            if (p == null || !p.isAlive()) {
                if (!a.target.isCorpse()) a.target.playFull("", 0);
                it.remove();
                continue;
            }
            long now = p.serverLevel().getGameTime();
            boolean inPlace = a.target.isAlive() && !a.target.isCorpse() && a.target.position().distanceTo(a.snapped) <= IN_PLACE;
            switch (a.session.tick(now, inPlace)) {
                case KILL -> {
                    a.target.brainKill(a.session.clip.targetClip(), 0);
                    Vec3 head = a.target.position().add(0, a.target.posture().onGround() ? 0.3 : 1.8, 0);
                    CoreNet.CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> a.target),
                            HitFeedbackPacket.of(a.target.getId(), head, head.subtract(p.getEyePosition()), a.weapon.contact,
                                    BodyRegion.HEAD, WalkerStrikeRules.Outcome.BRAIN_KILL));
                    p.getMainHandItem().hurtAndBreak(1, p, x -> {});
                }
                case FAILED_TARGET_MOVED, CANCELLED, DONE -> it.remove();
                case RUNNING -> { }
            }
        }
    }

    private static double wrap(double deg) {
        deg %= 360.0;
        if (deg > 180) deg -= 360;
        if (deg < -180) deg += 360;
        return deg;
    }
}
