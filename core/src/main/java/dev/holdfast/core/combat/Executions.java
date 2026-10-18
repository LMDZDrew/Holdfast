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
        final net.minecraft.world.item.ItemStack held;
        final int slot;
        final Vec3 playerSnapped;
        boolean killed;
        /** The walker reaction this approach plays (front, left, right or rear variant). */
        String targetClip;
        net.minecraft.world.item.ItemStack gun = net.minecraft.world.item.ItemStack.EMPTY;
        ServerPlayer ref;

        Active(WalkerEntity target, ExecutionRules.Session session, Vec3 snapped, WeaponClass weapon,ServerPlayer player) {
            this.target = target;
            this.session = session;
            this.snapped = snapped;
            this.weapon = weapon;
            this.held=player.getMainHandItem();
            this.slot=player.getInventory().selected;
            this.playerSnapped=player.position();
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

    /**
     * S7 from any approach (Andrew 2026-10-17: front, rear, either side, diagonals). The server validates the
     * weapon, target and facing, then aligns the pair without moving the walker: an upright walker turns at most
     * ~45 degrees to its nearest authored approach (front, left, right, rear reaction), a lying one keeps its yaw;
     * the player steps along their own approach line to the slot the clip needs. The step must be short
     * (ExecutionRules.MAX_APPROACH_PX), the player's box must fit at the slot, nothing solid may lie on the step or
     * between the slot and the skull, and a lying walker is never finished from over its body.
     */
    public static void request(ServerPlayer p, Entity e) {
        if (!(e instanceof WalkerEntity w) || w.isCorpse() || !p.isAlive()) return;
        WeaponClass weapon = Weapons.held(p);
        boolean lying = w.posture().onGround();
        Vec3 head = skullPoint(w);
        double dx = head.x - p.getX(), dz = head.z - p.getZ();
        double facingErr = wrap(Math.toDegrees(Math.atan2(-dx, dz)) - p.getYRot());
        double charge = weapon.blunt() ? BluntCharge.holding(p) : 0;
        ExecutionRules.Request req = new ExecutionRules.Request(weapon, w.posture(), Math.hypot(dx, dz) * 16.0, facingErr, true,
                Grabs.isGrabbing(w), targeted(w), charge);
        Optional<ExecutionRules.Refusal> refusal = ExecutionRules.validate(req);
        // A pistol execution is a real shot (Andrew 2026-10-17: it cost no bullet): it needs a round to start.
        if (refusal.isEmpty() && weapon == WeaponClass.PISTOL && rounds(p.getMainHandItem()) < 1) {
            refusal = Optional.of(ExecutionRules.Refusal.NO_AMMO);
        }
        CombatTimings.Exec clip = refusal.isPresent() ? null : ExecutionRules.clipFor(weapon, w.posture()).orElseThrow();
        ExecutionRules.Alignment al = null;
        if (refusal.isEmpty()) {
            al = ExecutionRules.align(clip, lying, w.getX(), w.getZ(), w.getYRot(), p.getX(), p.getZ()).orElse(null);
            if (al == null) refusal = Optional.of(ExecutionRules.Refusal.NO_CLIP_FOR_POSTURE);
        }
        if (refusal.isEmpty()) {
            double angle = ExecutionRules.approachAngle(w.getYRot(), w.getX(), w.getZ(), p.getX(), p.getZ());
            refusal = ExecutionRules.checkAlignment(al, lying, angle);
        }
        if (refusal.isEmpty()) refusal = placeable(p, w, al);
        if (refusal.isPresent()) {
            p.displayClientMessage(Component.translatable("message.holdfast_core.execute_refused." + refusal.get().name().toLowerCase(java.util.Locale.ROOT)), true);
            LAST_REFUSAL.put(p.getUUID(), refusal.get());
            return;
        }
        LAST_REFUSAL.remove(p.getUUID());
        if (weapon.blunt()) BluntCharge.clear(p);
        p.moveTo(al.playerX(), p.getY(), al.playerZ(), al.playerYaw(), 0.0F);
        p.connection.teleport(al.playerX(), p.getY(), al.playerZ(), al.playerYaw(), 0.0F);   // tell the client
        p.setYHeadRot(al.playerYaw());
        float facing = al.walkerYaw();
        w.moveTo(w.getX(), w.getY(), w.getZ(), facing, 0.0F);   // turns in place; never moved
        w.setYBodyRot(facing);
        w.setYHeadRot(facing);
        w.getNavigation().stop();
        long now = p.serverLevel().getGameTime();
        Active active = new Active(w, new ExecutionRules.Session(clip, now), w.position(), weapon,p);
        active.ref = p;
        active.targetClip = al.targetClip();
        if (weapon == WeaponClass.PISTOL) active.gun = p.getMainHandItem().copy();
        ACTIVE.put(p.getUUID(), active);
        LAST_APPROACH.put(p.getUUID(), al.approach());
        w.playFull(al.targetClip(), clip.lengthTicks() + 40);
        Grabs.clip(p, clip.playerClip(), w);
    }

    private static int rounds(net.minecraft.world.item.ItemStack stack) {
        return net.minecraftforge.fml.ModList.get().isLoaded("tacz") ? dev.holdfast.core.compat.tacz.TaczGuns.rounds(stack) : 0;
    }

    private static boolean consumeRound(net.minecraft.world.item.ItemStack stack) {
        return net.minecraftforge.fml.ModList.get().isLoaded("tacz") && dev.holdfast.core.compat.tacz.TaczGuns.consumeRound(stack);
    }

    /** Last refusal / approach per player, for GameTests and the debug command. */
    private static final Map<UUID, ExecutionRules.Refusal> LAST_REFUSAL = new ConcurrentHashMap<>();
    private static final Map<UUID, ExecutionRules.Approach> LAST_APPROACH = new ConcurrentHashMap<>();

    public static Optional<ExecutionRules.Refusal> lastRefusal(ServerPlayer p) {
        return Optional.ofNullable(LAST_REFUSAL.get(p.getUUID()));
    }

    public static Optional<ExecutionRules.Approach> lastApproach(ServerPlayer p) {
        return Optional.ofNullable(LAST_APPROACH.get(p.getUUID()));
    }

    /** Source-measured entry on this reaction's skull at the server kill frame; never a fixed entity offset. */
    public static HitFeedbackPacket contactFeedback(WalkerEntity w, WeaponClass weapon, String reaction) {
        var c=CombatTimings.EXEC_CONTACT.get(reaction);
        if(c==null) throw new IllegalStateException("Missing generated execution contact: "+reaction);
        Vec3 entry=Strikes.world(w,new dev.holdfast.core.rules.ContactGeometry.Point(c.x(),c.y(),c.z()));
        Vec3 direction=Strikes.world(w,new dev.holdfast.core.rules.ContactGeometry.Point(c.dx(),c.dy(),c.dz())).subtract(w.position());
        HitFeedbackPacket packet=HitFeedbackPacket.of(w.getId(),entry,direction,weapon.contact,BodyRegion.HEAD,WalkerStrikeRules.Outcome.BRAIN_KILL);
        if(c.hasExit()) packet=packet.withExit(Strikes.world(w,new dev.holdfast.core.rules.ContactGeometry.Point(c.exitX(),c.exitY(),c.exitZ())));
        return packet;
    }

    /** The walker's skull, horizontally (upright: above its feet; lying: ahead of its origin, from the rig). */
    static Vec3 skullPoint(WalkerEntity w) {
        if (!w.posture().onGround()) return w.position().add(0, 29 / 16.0, 0);
        double ahead = (w.posture() == Posture.PRONE ? dev.holdfast.core.rules.HitRegions.PRONE_OFFSET
                : dev.holdfast.core.rules.HitRegions.CRAWLER_OFFSET) - 29.0;   // along-body 29 px = mid skull
        double r = Math.toRadians(w.getYRot());
        return w.position().add(-Math.sin(r) * -ahead / 16.0, 4 / 16.0, Math.cos(r) * -ahead / 16.0);
    }

    /** World checks for the aligned slot: room for the player, a clear step, and a clear line to the skull. */
    private static Optional<ExecutionRules.Refusal> placeable(ServerPlayer p, WalkerEntity w, ExecutionRules.Alignment al) {
        var level = p.level();
        Vec3 from = p.position(), to = new Vec3(al.playerX(), p.getY(), al.playerZ());
        net.minecraft.world.phys.AABB box = p.getBoundingBox().move(to.subtract(from));
        if (!level.noCollision(p, box)) return Optional.of(ExecutionRules.Refusal.NO_ROOM);
        if (box.intersects(w.getBoundingBox().deflate(0.05)) && !w.posture().onGround()) return Optional.of(ExecutionRules.Refusal.NO_ROOM);
        for (double up : new double[]{0.3, 1.0, p.getEyeHeight()}) {
            var hit = level.clip(new net.minecraft.world.level.ClipContext(from.add(0, up, 0), to.add(0, up, 0),
                    net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, p));
            if (hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS) return Optional.of(ExecutionRules.Refusal.OBSTRUCTED);
        }
        Vec3 eye = to.add(0, p.getEyeHeight(), 0), skull = skullPoint(w);
        var sight = level.clip(new net.minecraft.world.level.ClipContext(eye, skull,
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, p));
        if (sight.getType() != net.minecraft.world.phys.HitResult.Type.MISS) return Optional.of(ExecutionRules.Refusal.OBSTRUCTED);
        return Optional.empty();
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

    private static void abort(ServerPlayer p,Active a) {
        if(!a.target.isCorpse() && a.target.fullClip().equals(a.targetClip)) a.target.playFull("",0);
        CoreNet.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p),PlayClipPacket.stop(p.getId()));
    }

    public static void tick(MinecraftServer server) {
        for (Iterator<Map.Entry<UUID, Active>> it = ACTIVE.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Active> e = it.next();
            Active a = e.getValue();
            ServerPlayer p = Players.resolve(server, e.getKey(), a.ref);
            if (p == null || p!=a.ref || !p.isAlive()) {
                if (!a.target.isCorpse()) a.target.playFull("", 0);
                it.remove();
                continue;
            }
            if(!a.killed && (p.serverLevel()!=a.target.level() || p.getInventory().selected!=a.slot
                    || p.getMainHandItem()!=a.held || Weapons.held(p)!=a.weapon
                    || p.position().distanceTo(a.playerSnapped)>IN_PLACE)) {
                abort(p,a);
                it.remove();
                continue;
            }
            long now = p.serverLevel().getGameTime();
            boolean inPlace = a.target.isAlive() && !a.target.isCorpse() && a.target.position().distanceTo(a.snapped) <= IN_PLACE;
            switch (a.session.tick(now, inPlace)) {
                case KILL -> {
                    // the shot spends its round at the kill tick, from the gun still in hand
                    if (a.weapon == WeaponClass.PISTOL && !consumeRound(a.held)) {
                        abort(p,a);it.remove();continue; // ammo changed after the start: no free execution
                    }
                    a.killed=true;
                    if (!a.gun.isEmpty()) CoreNet.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p),
                            new dev.holdfast.core.net.GunShotPacket(p.getId(), a.gun));
                    HitFeedbackPacket contact=contactFeedback(a.target,a.weapon,a.targetClip);
                    a.target.brainKill(a.targetClip, 0);
                    Strikes.feedback(a.target,contact);
                    a.held.hurtAndBreak(1, p, x -> {});
                }
                case FAILED_TARGET_MOVED, CANCELLED -> { abort(p,a);it.remove(); }
                case DONE -> it.remove();
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
