package dev.holdfast.core;

import com.mojang.logging.LogUtils;
import dev.holdfast.core.combat.CombatEvents;
import dev.holdfast.core.compat.tacz.TaczBridge;
import dev.holdfast.core.net.CoreNet;
import dev.holdfast.core.registry.CoreEntities;
import dev.holdfast.core.registry.CoreItems;
import dev.holdfast.core.registry.CoreSounds;
import dev.holdfast.core.state.ConditionEvents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Holdfast core: the server-owned combat slice (docs/core_combat.md). Rules live in {@code dev.holdfast.core.rules}
 * (plain Java, unit-tested); this mod translates game state into rule inputs and rule results into game actions.
 */
@Mod(HoldfastCore.MODID)
public final class HoldfastCore {
    public static final String MODID = "holdfast_core";
    /** Namespace of the shared exported character assets (art/characters/export/assets/holdfast). */
    public static final String ASSETS = "holdfast";
    public static final Logger LOG = LogUtils.getLogger();

    public HoldfastCore() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        CoreEntities.ENTITIES.register(modBus);
        CoreItems.ITEMS.register(modBus);
        CoreItems.TABS.register(modBus);
        CoreSounds.SOUNDS.register(modBus);
        modBus.addListener(CoreEntities::onAttributes);
        modBus.addListener(ConditionEvents::onRegisterCaps);
        modBus.addListener(this::onCommonSetup);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, CoreConfig.SPEC);
        MinecraftForge.EVENT_BUS.register(CombatEvents.class);
        MinecraftForge.EVENT_BUS.register(ConditionEvents.class);
        MinecraftForge.EVENT_BUS.addListener(dev.holdfast.core.command.HoldfastCommand::register);
        if (ModList.get().isLoaded("tacz")) {
            // Only touched when TaCZ is present: its classes are compile-only.
            TaczBridge.register();
            LOG.info("[holdfast] TaCZ found: gun hits on walkers are resolved by the core");
        }
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> dev.holdfast.core.client.ClientCore.init(modBus));
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(CoreNet::register);
    }
}
