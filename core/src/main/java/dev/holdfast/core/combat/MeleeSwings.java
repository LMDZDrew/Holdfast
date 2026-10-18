package dev.holdfast.core.combat;

import dev.holdfast.core.entity.WalkerEntity;
import dev.holdfast.core.item.MeleeWeaponItem;
import dev.holdfast.core.net.CoreNet;
import dev.holdfast.core.net.PlayClipPacket;
import dev.holdfast.core.rules.CombatTimings;
import dev.holdfast.core.rules.SwingRules;
import dev.holdfast.core.rules.WalkerStrikeRules;
import dev.holdfast.core.rules.WeaponClass;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-owned free melee swings (docs/core_combat.md, "Melee contact timing"). A click on a walker, a click into the
 * air, or a charged blunt release starts one {@link SwingRules.Session}; nothing touches a walker then. At the contact
 * tick the server casts the player's current view ray (with a small arc margin around walker boxes), refuses hits
 * through blocks, and resolves the first walker it meets with the rules: one resolution per swing, or a miss.
 */
public final class MeleeSwings {
    public enum Result { PENDING, HIT, MISS, OBSTRUCTED, CANCELLED }

    private static final class Active {
        final SwingRules.Session session;
        final WeaponClass weapon;
        final double strength, charge;
        final int slot;
        final ItemStack stack;
        final ServerPlayer ref;
        Result result = Result.PENDING;
        WalkerStrikeRules.Outcome outcome;

        Active(SwingRules.Session session, WeaponClass weapon, double strength, double charge, int slot, ItemStack stack, ServerPlayer ref) {
            this.session = session;
            this.weapon = weapon;
            this.strength = strength;
            this.charge = charge;
            this.slot = slot;
            this.stack = stack;
            this.ref = ref;
        }
    }

    private static final Map<UUID, Active> ACTIVE = new ConcurrentHashMap<>();
    /** Last resolution per player, for GameTests and the debug command. */
    private static final Map<UUID, Result> LAST = new ConcurrentHashMap<>();

    private MeleeSwings() {}

    /** Why a player can't start a swing now, or null. Firearms are TaCZ's; two-handed weapons need an empty off hand. */
    static String refusal(ServerPlayer p, WeaponClass weapon) {
        if (!p.isAlive() || p.isSpectator()) return "dead";
        if (weapon.firearm()) return "firearm";
        if (Grabs.isGrabbed(p) || Executions.isExecuting(p)) return "busy";
        if (p.getMainHandItem().getItem() instanceof MeleeWeaponItem m && m.twoHanded && !p.getOffhandItem().isEmpty()) return "off hand";
        Active a = ACTIVE.get(p.getUUID());
        if (a != null && a.session.blocksNewSwing(p.serverLevel().getGameTime())) return "swinging";
        return null;
    }

    /** A left click (on a walker or into the air): reads and spends vanilla attack strength, then starts the swing. */
    public static boolean click(ServerPlayer p) {
        WeaponClass weapon = Weapons.held(p);
        if (refusal(p, weapon) != null) return false;
        double strength = p.getAttackStrengthScale(0.5F);
        p.resetAttackStrengthTicker();
        String clip = SwingRules.clipFor(weapon, SwingRules.heavy(strength));
        if (clip == null) return false;
        p.causeFoodExhaustion(0.08F);                         // light: a healthy survivor can drop several walkers
        return start(p, weapon, clip, strength, 0);
    }

    /** A released blunt charge is the swing (S10); the clip is chosen from what is in reach at release. */
    public static boolean release(ServerPlayer p, WeaponClass weapon, double charge) {
        if (refusal(p, weapon) != null) return false;
        p.resetAttackStrengthTicker();
        String clip = SwingRules.chargedClip(charge, contact(p).walker() != null);
        return start(p, weapon, clip, 1.0, charge);
    }

    /** Starts an accepted swing and shows its clip to other players (the swinging client already plays it). */
    public static boolean start(ServerPlayer p, WeaponClass weapon, String clip, double strength, double charge) {
        if (refusal(p, weapon) != null) return false;
        CombatTimings.Swing timing = CombatTimings.swing(clip);
        long now = p.serverLevel().getGameTime();
        Active a = new Active(new SwingRules.Session(timing, now, p.latency), weapon, strength, charge,
                p.getInventory().selected, p.getMainHandItem().copy(), p);
        ACTIVE.put(p.getUUID(), a);
        LAST.put(p.getUUID(), Result.PENDING);
        CoreNet.CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> p), new PlayClipPacket(p.getId(), clip, -1, true));
        return true;
    }

    /** The clip of the player's current swing, or "" (the charged release reports its choice to the swinger). */
    public static String clip(ServerPlayer p) {
        Active a = ACTIVE.get(p.getUUID());
        return a == null ? "" : a.session.swing.clip();
    }

    public static boolean pending(ServerPlayer p) {
        Active a = ACTIVE.get(p.getUUID());
        return a != null && a.session.state() == SwingRules.State.PENDING;
    }

    public static Result lastResult(ServerPlayer p) {
        return LAST.getOrDefault(p.getUUID(), Result.MISS);
    }

    /** Logout, death or shutdown: dropped without a hit. */
    public static void drop(ServerPlayer p) {
        Active a = ACTIVE.remove(p.getUUID());
        if (a != null && a.session.cancel()) LAST.put(p.getUUID(), Result.CANCELLED);
    }

    public static void tick(MinecraftServer server) {
        for (Iterator<Map.Entry<UUID, Active>> it = ACTIVE.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Active> e = it.next();
            Active a = e.getValue();
            ServerPlayer p = Players.resolve(server, e.getKey(), a.ref);
            if (p == null || !p.isAlive() || p != a.ref) {          // logged out, died or respawned: no stale hit
                a.session.cancel();
                LAST.put(e.getKey(), Result.CANCELLED);
                it.remove();
                continue;
            }
            long now = p.serverLevel().getGameTime();
            if (a.session.state() == SwingRules.State.PENDING && interrupted(p, a)) {
                a.session.cancel();
                a.result = Result.CANCELLED;
                LAST.put(e.getKey(), Result.CANCELLED);
                CoreNet.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), PlayClipPacket.stop(p.getId()));
            }
            if (a.session.contactDue(now)) resolve(p, a);
            if (a.session.finished(now)) it.remove();
        }
    }

    /** Weapon switched (slot or item), grabbed, executing, or started using an item: the swing never lands. */
    private static boolean interrupted(ServerPlayer p, Active a) {
        if (Grabs.isGrabbed(p) || Executions.isExecuting(p)) return true;
        if (p.getInventory().selected != a.slot || !ItemStack.isSameItem(p.getMainHandItem(), a.stack)) return true;
        return a.charge == 0 && p.isUsingItem();
    }

    private static void resolve(ServerPlayer p, Active a) {
        Contact c = contact(p);
        if (c.walker() == null) {
            a.result = c.obstructed() ? Result.OBSTRUCTED : Result.MISS;
        } else {
            WalkerEntity w = c.walker();
            Strikes.Contact hit = Strikes.meleeContact(p, w);
            a.outcome = Strikes.apply(p, w, a.weapon, hit, a.strength, a.charge);
            if (!p.getMainHandItem().isEmpty()) p.getMainHandItem().hurtAndBreak(1, p, x -> {});
            a.result = Result.HIT;
        }
        LAST.put(p.getUUID(), a.result);
    }

    /** The walker a swing meets now, or none (and whether a block was the reason). */
    public record Contact(WalkerEntity walker, boolean obstructed) {}

    /**
     * Validated ray: the player's current eye and view, their entity reach, each live walker's regional bounds grown by
     * {@link SwingRules#ARC_MARGIN}. The nearest box entered wins; a block hit before it means no hit.
     */
    public static Contact contact(ServerPlayer p) {
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getViewVector(1.0F);
        double reach = p.getEntityReach();
        Vec3 end = eye.add(look.scale(reach));
        AABB search = p.getBoundingBox().expandTowards(look.scale(reach)).inflate(1.0 + SwingRules.ARC_MARGIN);
        WalkerEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (WalkerEntity w : p.level().getEntitiesOfClass(WalkerEntity.class, search, w -> w.isAlive() && !w.isCorpse())) {
            AABB box = Strikes.walkerBounds(w).inflate(SwingRules.ARC_MARGIN);
            Optional<Vec3> hit = box.contains(eye) ? Optional.of(eye) : box.clip(eye, end);
            if (hit.isEmpty()) continue;
            double d = hit.get().distanceToSqr(eye);
            if (d < bestD) {
                bestD = d;
                best = w;
            }
        }
        if (best == null) return new Contact(null, false);
        Vec3 aim = Strikes.meleeHitPoint(p, best);
        var block = p.level().clip(new ClipContext(eye, aim, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        if (block.getType() != HitResult.Type.MISS && block.getLocation().distanceToSqr(eye) < aim.distanceToSqr(eye) - 1.0E-6) {
            return new Contact(null, true);
        }
        return new Contact(best, false);
    }
}
