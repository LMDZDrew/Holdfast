package dev.holdfast.core.combat;

import dev.holdfast.core.CoreConfig;
import dev.holdfast.core.entity.WalkerEntity;
import dev.holdfast.core.item.BiteGuardItem;
import dev.holdfast.core.net.CoreNet;
import dev.holdfast.core.net.GrabPromptPacket;
import dev.holdfast.core.net.PlayClipPacket;
import dev.holdfast.core.registry.CoreSounds;
import dev.holdfast.core.rules.BiteRules;
import dev.holdfast.core.rules.BodyRegion;
import dev.holdfast.core.rules.CombatTimings;
import dev.holdfast.core.rules.GrabRules;
import dev.holdfast.core.state.ConditionCapability;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side grabs (S2-S6). One grab per victim and per walker. The rule state machine is {@link GrabRules};
 * this class snaps the pair, plays the clips, holds the victim, resolves bites against worn guards and records
 * infection. All timing is the server's tick count.
 */
public final class Grabs {
    private static final UUID HOLD_ID = UUID.fromString("d1e1e0b2-62f4-4b8b-8e73-6a3cfa0e5e21");

    private static final class Grab {
        final WalkerEntity walker;
        final UUID victim;
        final GrabRules.Session session;
        final Vec3 victimPos;
        final ServerPlayer ref;
        long holdAt;

        Grab(WalkerEntity walker, ServerPlayer victim, GrabRules.Session session, Vec3 victimPos) {
            this.walker = walker;
            this.victim = victim.getUUID();
            this.ref = victim;
            this.session = session;
            this.victimPos = victimPos;
            this.holdAt = session.windowStart() + (session.kind == GrabRules.Kind.UPRIGHT ? 4 : 10);
        }
    }

    private static final Map<UUID, Grab> BY_VICTIM = new ConcurrentHashMap<>();

    private Grabs() {}

    public static boolean isGrabbed(ServerPlayer p) {
        return BY_VICTIM.containsKey(p.getUUID());
    }

    public static boolean isGrabbing(WalkerEntity w) {
        for (Grab g : BY_VICTIM.values()) if (g.walker == w) return true;
        return false;
    }

    private record Clips(String walkerSeize, String walkerHold, String victimSeize, String victimHold, String walkerBite,
                         String walkerBlocked, String victimBitten, String victimBlocked, String walkerEscaped,
                         String victimEscape, String victimReleased, double distPx) {}

    private static Clips clips(GrabRules.Kind kind) {
        return switch (kind) {
            case UPRIGHT -> new Clips("grab_seize", "grab_hold", "grabbed_seize", "grabbed_struggle", "grab_bite", "grab_bite_blocked",
                    "grabbed_bitten", "grabbed_bite_blocked", "grab_shoved", "grabbed_shove", "grabbed_released", CombatTimings.GRAB_DIST_UPRIGHT);
            case PRONE_ANKLE -> new Clips("prone_ankle_grab", "prone_ankle_hold", "grabbed_ankle_seize", "grabbed_ankle_struggle",
                    "prone_ankle_bite", "prone_ankle_bite_blocked", "grabbed_ankle_bitten", "grabbed_ankle_bite_blocked",
                    "prone_ankle_kicked", "grabbed_ankle_kick", "grabbed_ankle_released", CombatTimings.GRAB_DIST_PRONE);
            case CRAWLER_ANKLE -> new Clips("crawler_reach", "crawler_ankle_hold", "grabbed_ankle_seize", "grabbed_ankle_struggle",
                    "crawler_ankle_bite", "crawler_ankle_bite_blocked", "grabbed_ankle_bitten", "grabbed_ankle_bite_blocked",
                    "crawler_ankle_kicked", "grabbed_ankle_kick", "grabbed_ankle_released", CombatTimings.GRAB_DIST_CRAWLER);
        };
    }

    /** S1/S2: the walker's lunge connected (the caller checked reach and sight at the server's hit tick). */
    public static boolean start(WalkerEntity w, ServerPlayer victim, GrabRules.Kind kind) {
        return start(w, victim, kind, false);
    }

    /** Continue the already playing seize; do not replay a second reach after the hands connect. */
    public static boolean startFromLunge(WalkerEntity w, ServerPlayer victim, GrabRules.Kind kind) {
        return start(w, victim, kind, true);
    }

    private static boolean start(WalkerEntity w, ServerPlayer victim, GrabRules.Kind kind, boolean continuing) {
        if (victim.isCreative() || victim.isSpectator() || !victim.isAlive()) return false;
        if (w.isCorpse() || !w.isAlive() || w.level() != victim.level()) return false;
        if (isGrabbed(victim) || isGrabbing(w) || Executions.isExecuting(victim)) return false;
        Clips c = clips(kind);
        long now = victim.serverLevel().getGameTime();
        // Snap the walker to the pairing distance in front of the victim, facing them.
        float yaw = (float) Math.toDegrees(Math.atan2(-(w.getX() - victim.getX()), w.getZ() - victim.getZ()));
        double rad = Math.toRadians(yaw), d = c.distPx / 16.0;
        // Never retreat a close walker to its pairing slot. Only close the remaining gap.
        d = Math.min(d, Math.hypot(w.getX() - victim.getX(), w.getZ() - victim.getZ()));
        Vec3 at = new Vec3(victim.getX() - Math.sin(rad) * d, w.getY(), victim.getZ() + Math.cos(rad) * d);
        victim.connection.teleport(victim.getX(), victim.getY(), victim.getZ(), yaw, victim.getXRot());
        victim.setYRot(yaw);
        victim.setYHeadRot(yaw);
        w.moveTo(at.x, at.y, at.z, yaw + 180.0F, 0.0F);
        w.setYBodyRot(yaw + 180.0F);
        w.setYHeadRot(yaw + 180.0F);
        GrabRules.Session s = new GrabRules.Session(kind, CoreConfig.grabWindowTicks(), now);
        Grab grab = new Grab(w, victim, s, victim.position());
        if (!continuing) grab.holdAt = now + 10;
        BY_VICTIM.put(victim.getUUID(), grab);
        if (!continuing) w.playFull(c.walkerSeize, 12);
        w.continueFull(12); // no timeout-to-gait frame before Grabs.tick installs the hold
        if (continuing) {
            // Partner stays on the original animation timeline; synced data must not reset it either.
            CoreNet.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> victim),
                    new PlayClipPacket(victim.getId(), c.victimSeize, w.getId(), true));
        } else clip(victim, c.victimSeize, w);
        hold(victim, true);
        CoreNet.CHANNEL.send(PacketDistributor.PLAYER.with(() -> victim), new GrabPromptPacket(s.windowTicks, kind != GrabRules.Kind.UPRIGHT, ""));
        w.vocal(CoreSounds.WALKER_SNARL.get(), 1.0F, 1.0F);
        return true;
    }

    /** The victim pressed the context key; the server judges it by its own arrival tick (S3). */
    public static void escape(ServerPlayer p) {
        Grab g = BY_VICTIM.get(p.getUUID());
        if (g == null) return;
        long now = p.serverLevel().getGameTime();
        if (!g.session.escape(now, p.latency)) {
            p.displayClientMessage(Component.translatable("message.holdfast_core.escape_late"), true);
            return;
        }
        Clips c = clips(g.session.kind);
        // Remove ownership before stagger releases victims, then install the paired escape AFTER the generic
        // stagger. Otherwise releaseByAttacker overwrites the player's escape and stagger overwrites the walker.
        end(p, g, "escaped");
        g.walker.stagger(null);
        g.walker.playFull(c.walkerEscaped, 18);
        clip(p, c.victimEscape, g.walker);
        Vec3 away = g.walker.position().subtract(p.position()).normalize().scale(GrabRules.ESCAPE_KNOCKBACK);
        g.walker.setDeltaMovement(away.x, 0.15, away.z);
        g.walker.hurtMarked = true;
        p.causeFoodExhaustion(1.0F);
    }

    /** A brain kill, stagger or shove on the walker by anyone releases its victim (S6). */
    public static void releaseByAttacker(WalkerEntity w) {
        for (Grab g : new ArrayList<>(BY_VICTIM.values())) {
            if (g.walker != w) continue;
            g.session.releaseByOther();
            ServerPlayer p = w.getServer() == null ? null : Players.resolve(w.getServer(), g.victim, g.ref);
            if (p != null) {
                clip(p, clips(g.session.kind).victimReleased, null);
                end(p, g, "released");
            } else {
                BY_VICTIM.remove(g.victim);
            }
        }
    }

    public static void abandon(ServerPlayer p) {
        Grab g = BY_VICTIM.get(p.getUUID());
        if (g == null) return;
        g.session.abandon();
        end(p, g, "");
    }

    public static void tick(MinecraftServer server) {
        for (Iterator<Grab> it = BY_VICTIM.values().iterator(); it.hasNext(); ) {
            Grab g = it.next();
            ServerPlayer p = Players.resolve(server, g.victim, g.ref);
            if (p == null || !p.isAlive() || !g.walker.isAlive() || g.walker.isCorpse()) {
                if (p != null) hold(p, false);
                it.remove();
                continue;
            }
            // Hold the victim in place; the walker's clip and the prompt do the rest.
            if (p.position().distanceToSqr(g.victimPos) > 0.04) p.teleportTo(g.victimPos.x, g.victimPos.y, g.victimPos.z);
            long now = p.serverLevel().getGameTime();
            Clips c = clips(g.session.kind);
            if (now >= g.holdAt) {
                g.walker.playFull(c.walkerHold, Integer.MAX_VALUE - 1);
                clip(p, c.victimHold, g.walker);
                g.holdAt = Long.MAX_VALUE;
            }
            if (g.session.biteDue(now, p.latency)) bite(p, g, now);
        }
    }

    private static List<BiteRules.Guard> guards(ServerPlayer p, List<ItemStack> stacks) {
        List<BiteRules.Guard> out = new ArrayList<>();
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack s = p.getItemBySlot(slot);
            if (s.getItem() instanceof BiteGuardItem guard) {
                out.add(guard.guard(s));
                stacks.add(s);
            }
        }
        return out;
    }

    /** S4/S5: window over with no accepted escape. */
    private static void bite(ServerPlayer p, Grab g, long now) {
        Clips c = clips(g.session.kind);
        BodyRegion region = BiteRules.biteRegion(g.session.kind, false);
        List<ItemStack> stacks = new ArrayList<>();
        BiteRules.Result r = BiteRules.resolve(region, guards(p, stacks));
        g.session.bite(r, now);
        if (r.blocked()) g.holdAt = now + 12;
        var cond = ConditionCapability.of(p);
        if (r.blocked()) {
            BiteGuardItem.wear(stacks.get(r.guardIndex()));
            cond.blockedBite();
            g.walker.playFull(c.walkerBlocked, 12);
            clip(p, c.victimBlocked, g.walker);
            p.level().playSound(null, p.blockPosition(), CoreSounds.BITE_BLOCKED.get(), SoundSource.HOSTILE, 1.0F, 1.0F);
            p.displayClientMessage(Component.translatable(r.kind() == BiteRules.Kind.BLOCKED_AND_BROKE
                    ? "message.holdfast_core.bite_blocked_broke" : "message.holdfast_core.bite_blocked"), true);
            CoreNet.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new GrabPromptPacket(g.session.windowTicks, g.session.kind != GrabRules.Kind.UPRIGHT, ""));
        } else {
            cond.bitten(region, CoreConfig.difficulty());
            g.walker.playFull(c.walkerBite, 20);
            clip(p, c.victimBitten, g.walker);
            g.walker.vocal(CoreSounds.WALKER_BITE.get(), 1.0F, 1.0F);
            p.hurt(p.damageSources().mobAttack(g.walker), 3.0F);    // the wound itself; infection decides the outcome
            end(p, g, "bitten_" + region.name().toLowerCase(java.util.Locale.ROOT));
            String confirm = switch (region) {
                case NECK, HEAD, TORSO -> "bite_confirm_neck";
                case ARM -> "bite_confirm_forearm";
                case LEG -> "bite_confirm_leg";
            };
            clip(p, confirm, null);
        }
    }

    private static void end(ServerPlayer p, Grab g, String result) {
        BY_VICTIM.remove(p.getUUID());
        hold(p, false);
        if (g.walker.fullClip().equals(clips(g.session.kind).walkerHold)) g.walker.playFull("", 0);
        CoreNet.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), GrabPromptPacket.clear(result));
    }

    private static void hold(ServerPlayer p, boolean on) {
        var speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        speed.removeModifier(HOLD_ID);
        if (on) speed.addTransientModifier(new AttributeModifier(HOLD_ID, "holdfast grabbed", -1.0, AttributeModifier.Operation.MULTIPLY_TOTAL));
    }

    static void clip(ServerPlayer p, String clip, WalkerEntity partner) {
        CoreNet.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p),
                new PlayClipPacket(p.getId(), clip, partner == null ? -1 : partner.getId(), true,
                        partner == null ? "" : partner.fullClip(), partner == null ? -1 : partner.clipNonce()));
    }
}
