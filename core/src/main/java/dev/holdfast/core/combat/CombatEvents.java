package dev.holdfast.core.combat;

import dev.holdfast.core.entity.WalkerEntity;
import dev.holdfast.core.item.MeleeWeaponItem;
import dev.holdfast.core.net.CoreNet;
import dev.holdfast.core.net.PlayClipPacket;
import dev.holdfast.core.rules.WeaponClass;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

/** Routes every way of hitting a walker through the core's rules and cancels vanilla damage to it. */
public final class CombatEvents {
    private CombatEvents() {}

    /** Player melee: the server measures swing strength and charge itself; the client only reported the click. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPlayerAttack(AttackEntityEvent event) {
        if (!(event.getTarget() instanceof WalkerEntity w) || !(event.getEntity() instanceof ServerPlayer p)) return;
        event.setCanceled(true);
        if (Grabs.isGrabbed(p) || Executions.isExecuting(p)) return;
        WeaponClass weapon = Weapons.held(p);
        if (weapon.firearm()) return;                         // pistol-whipping is TaCZ's melee, handled by its own event
        if (p.getMainHandItem().getItem() instanceof MeleeWeaponItem m && m.twoHanded && !p.getOffhandItem().isEmpty()) return;
        double swing = p.getAttackStrengthScale(0.5F);
        p.resetAttackStrengthTicker();
        double charge = weapon.blunt() ? BluntCharge.spend(p) : 0;
        var hit = Strikes.meleeHitPoint(p, w);
        Strikes.apply(p, w, weapon, hit, Strikes.region(w, hit), swing, charge);
        p.getMainHandItem().hurtAndBreak(1, p, x -> {});
        p.causeFoodExhaustion(0.08F);                         // light: a healthy survivor can drop several walkers
        // A teammate's hit on a walker that is grabbing someone releases them if it stuns or kills it (S6).
    }

    /** Arrows and other projectiles carry a position; anything else that would hurt a walker is ignored. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingAttack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof WalkerEntity w) || w.level().isClientSide) return;
        Entity direct = event.getSource().getDirectEntity();
        if (direct instanceof Projectile proj) {
            event.setCanceled(true);
            var hit = proj.position();
            var shooter = proj.getOwner() instanceof net.minecraft.world.entity.LivingEntity le ? le : null;
            // Arrows and bolts penetrate like a blade (Daryl's crossbow); thrown blunt objects only stagger.
            WeaponClass cls = proj instanceof net.minecraft.world.entity.projectile.AbstractArrow ? WeaponClass.KNIFE : WeaponClass.NONE;
            Strikes.apply(shooter, w, cls, hit, Strikes.region(w, hit), 1.0, 0);
        }
    }

    // ------------------------------------------------------------------ blunt charge (S10)

    @SubscribeEvent
    public static void onUseStart(LivingEntityUseItemEvent.Start event) {
        if (event.getEntity() instanceof ServerPlayer p && Weapons.of(event.getItem()).blunt()
                && event.getItem().getItem() instanceof MeleeWeaponItem) {
            BluntCharge.start(p);
            CoreNet.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), new PlayClipPacket(p.getId(), "bat_charge_hold", -1, true));
        }
    }

    @SubscribeEvent
    public static void onUseStop(LivingEntityUseItemEvent.Stop event) {
        if (event.getEntity() instanceof ServerPlayer p && event.getItem().getItem() instanceof MeleeWeaponItem) {
            BluntCharge.release(p);
            CoreNet.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), new PlayClipPacket(p.getId(), "bat_swing_quick", -1, true));
        }
    }

    // ------------------------------------------------------------------ server tick and lifecycle

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Grabs.tick(event.getServer());
        Executions.tick(event.getServer());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            Grabs.abandon(p);
            Executions.drop(p);
            BluntCharge.clear(p);
        }
    }
}
