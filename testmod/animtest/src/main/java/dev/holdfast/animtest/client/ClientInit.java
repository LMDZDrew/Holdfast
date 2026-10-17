package dev.holdfast.animtest.client;

import dev.holdfast.animtest.HoldfastAnimTest;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** Client-only wiring. Referenced from common code only through DistExecutor. */
public final class ClientInit {
    private ClientInit() {}

    public static void init(IEventBus modBus) {
        modBus.addListener(ClientInit::onRenderers);
        modBus.addListener(ClientInit::onClientSetup);
        MinecraftForge.EVENT_BUS.addListener(PlayerClips::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(PlayerClips::onCameraAngles);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, ClientPlayerNetworkEvent.LoggingOut.class, e -> LoadCheck.reset());
    }

    private static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(HoldfastAnimTest.TEST_WALKER.get(), ctx -> new GeoEntityRenderer<>(ctx, new WalkerModel()));
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(PlayerClips::registerLayer);
    }
}
