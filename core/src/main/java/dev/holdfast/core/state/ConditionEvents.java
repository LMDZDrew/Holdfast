package dev.holdfast.core.state;

import dev.holdfast.core.HoldfastCore;
import dev.holdfast.core.net.ConditionSyncPacket;
import dev.holdfast.core.net.CoreNet;
import dev.holdfast.core.rules.InfectionRules;
import dev.holdfast.core.rules.InjuryRules;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.UUID;

/** Attaches, saves, syncs and ticks {@link PlayerCondition}. All of it runs on the logical server. */
public final class ConditionEvents {
    private static final UUID INJURY_SPEED_ID = UUID.fromString("0b6c7f3e-6a0e-4b25-9b55-6f1d2c3c9e11");
    public static final ResourceKey<DamageType> WALKER_INFECTION =
            ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(HoldfastCore.MODID, "walker_infection"));

    private ConditionEvents() {}

    /** Mod bus. */
    public static void onRegisterCaps(RegisterCapabilitiesEvent event) {
        event.register(PlayerCondition.class);
    }

    @SubscribeEvent
    public static void onAttach(AttachCapabilitiesEvent<net.minecraft.world.entity.Entity> event) {
        if (event.getObject() instanceof Player) event.addCapability(ConditionCapability.ID, new ConditionCapability());
    }

    /** Dimension travel keeps the condition. Death is final (decisions.md), so a respawn starts fresh. */
    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (event.isWasDeath()) return;
        event.getOriginal().reviveCaps();
        PlayerCondition old = ConditionCapability.of(event.getOriginal());
        ConditionCapability.of(event.getEntity()).load(old.save());
        event.getOriginal().invalidateCaps();
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            ConditionCapability.of(sp).markDirty();
            applySpeed(sp, ConditionCapability.of(sp));
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) applySpeed(sp, ConditionCapability.of(sp));
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer sp) || !sp.isAlive()) return;
        PlayerCondition c = ConditionCapability.of(sp);
        c.tick();
        if (sp.tickCount % 20 == 0) applySpeed(sp, c);
        if (!c.movement().sprintAllowed && sp.isSprinting()) sp.setSprinting(false);
        InfectionRules.Bite bite = c.bite();
        if (bite != null && InfectionRules.fatal(bite, c.onlineTicks())) {
            DamageSource src = new DamageSource(sp.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(WALKER_INFECTION));
            sp.hurt(src, Float.MAX_VALUE);
        }
        if (c.takeDirty() || sp.tickCount % 100 == 0) {
            CoreNet.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), ConditionSyncPacket.of(c));
        }
    }

    public static void applySpeed(ServerPlayer sp, PlayerCondition c) {
        AttributeInstance speed = sp.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        AttributeModifier current = speed.getModifier(INJURY_SPEED_ID);
        double want = c.movement().attributeMultiplier() - 1.0;
        if (current != null && Math.abs(current.getAmount() - want) < 1e-6) return;
        speed.removeModifier(INJURY_SPEED_ID);
        if (c.movement() != InjuryRules.Movement.NORMAL) {
            speed.addTransientModifier(new AttributeModifier(INJURY_SPEED_ID, "holdfast injury", want, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }
}
