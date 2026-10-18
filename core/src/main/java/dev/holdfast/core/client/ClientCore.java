package dev.holdfast.core.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.holdfast.core.HoldfastCore;
import dev.holdfast.core.net.ActionPacket;
import dev.holdfast.core.net.CoreNet;
import dev.holdfast.core.registry.CoreEntities;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** Client wiring. Display and input only: the key sends a request, the HUD shows the server's state. */
public final class ClientCore {
    /** One editable context key for escape, execute and cancel. Default F (Andrew 2026-10-17). */
    public static final KeyMapping ACTION = new KeyMapping("key.holdfast_core.action", net.minecraftforge.client.settings.KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F,
            "key.categories.holdfast_core");

    private ClientCore() {}

    public static void init(IEventBus modBus) {
        modBus.addListener(ClientCore::onRenderers);
        modBus.addListener(ClientCore::onKeys);
        modBus.addListener(ClientCore::onOverlays);
        modBus.addListener((FMLClientSetupEvent e) -> e.enqueueWork(ClientClips::registerLayers));
        modBus.addListener((FMLClientSetupEvent e) -> e.enqueueWork(() -> {
            if(net.minecraftforge.fml.ModList.get().isLoaded("tacz")) dev.holdfast.core.compat.tacz.TaczClientActions.register();
            if(net.minecraftforge.fml.ModList.get().isLoaded("firstperson")) dev.holdfast.core.compat.firstperson.FirstPersonBody.register();
        }));
        MinecraftForge.EVENT_BUS.addListener(ClientCore::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(ClientClips::camera);
        MinecraftForge.EVENT_BUS.addListener(ClientCore::onClickInput);
        MinecraftForge.EVENT_BUS.addListener(PoseTrace::onRenderTick);
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) -> {
            ClientState.resetTransient();
            if (net.minecraftforge.fml.ModList.get().isLoaded("extragore")) ExtraGoreEffects.clear();
        });
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingIn event) -> {
            if (net.minecraftforge.fml.ModList.get().isLoaded("extragore")) ExtraGoreEffects.initialize();
        });
    }

    private static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(CoreEntities.WALKER.get(), ctx -> new GeoEntityRenderer<>(ctx, new WalkerModel()));
    }

    private static void onKeys(RegisterKeyMappingsEvent event) {
        event.register(ACTION);
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientClips.tick();
        WalkerVoices.tick();
        if (mc.player == null) return;
        if(mc.screen!=null) { while(ACTION.consumeClick()) {} return; }
        while (ACTION.consumeClick()) {
            if(!contextAvailable()) continue;
            Entity looked = mc.hitResult instanceof EntityHitResult eh ? eh.getEntity() : null;
            CoreNet.CHANNEL.sendToServer(new ActionPacket(looked == null ? -1 : looked.getId()));
        }
    }

    /** Input routing only. The server still checks eligibility, distance, ammo, timing and every outcome. */
    public static boolean contextAvailable() {
        var mc=Minecraft.getInstance();
        if(mc.player==null || mc.screen!=null || mc.player.isSpectator()) return false;
        var weapon=dev.holdfast.core.combat.Weapons.held(mc.player);
        boolean walker=mc.hitResult instanceof EntityHitResult hit
                && hit.getEntity() instanceof dev.holdfast.core.entity.WalkerEntity w && !w.isCorpse();
        return dev.holdfast.core.rules.ContextKeyRules.owns(ClientState.promptStartClientTick>=0,
                ClientClips.executing(mc.player),walker,weapon.firearm(),weapon==dev.holdfast.core.rules.WeaponClass.PISTOL);
    }

    /**
     * Left click, before vanilla spends the attack strength: start the local swing clip now (the server's contact tick
     * comes from the same clip) and, for a click into the air, ask the server to start the swing. Clicks on a block
     * are mining, not attacks; clicks during an active swing are ignored, as the server refuses them.
     */
    private static void onClickInput(net.minecraftforge.client.event.InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (!event.isAttack() || event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND || mc.player == null
                || mc.player.isSpectator()) return;
        var type = mc.hitResult == null ? net.minecraft.world.phys.HitResult.Type.MISS : mc.hitResult.getType();
        if (type == net.minecraft.world.phys.HitResult.Type.BLOCK) return;
        if (!ClientClips.ownsAttack(mc.player)) return;
        // Keep vanilla's attack request, but remove its additional arm/body-yaw swing even on refused rapid clicks.
        event.setSwingHand(false);
        if (!ClientClips.localSwing(mc.player, mc.player.getAttackStrengthScale(0.5F))) return;
        if (type == net.minecraft.world.phys.HitResult.Type.MISS) CoreNet.CHANNEL.sendToServer(new dev.holdfast.core.net.SwingPacket());
    }

    private static void onOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "status", (gui, g, partial, w, h) -> drawHud(g, w, h, partial));
    }

    private static void drawHud(GuiGraphics g, int w, int h, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) return;
        var font = mc.font;
        long now = mc.level.getGameTime();
        // Grab escape prompt: the window and the key. Display only; the server judges the press.
        if (ClientState.promptStartClientTick >= 0 && ClientState.promptWindow > 0) {
            GrabOverlay.draw(g, w, h, partial);
        } else if (!ClientState.lastResult.isEmpty() && now < ClientState.resultUntil) {
            g.drawCenteredString(font, Component.translatable("hud.holdfast_core.grab." + ClientState.lastResult), w / 2, h / 2 + 24, 0xFFFFFFFF);
        }
        // Bite status: region, decline and the amputation window (design.md: the UI marks eligibility and time left).
        var c = ClientState.condition;
        if (c.infected()) {
            int x = 6, y = 6;
            g.drawString(font, Component.translatable("hud.holdfast_core.bitten", c.biteRegion().toLowerCase(java.util.Locale.ROOT)), x, y, 0xFFD24A3A);
            g.fill(x, y + 11, x + 80, y + 15, 0x80000000);
            g.fill(x, y + 11, x + (int) (80 * c.progress()), y + 15, 0xFF7A1A12);
            if (c.amputationSecondsLeft() > 0) {
                g.drawString(font, Component.translatable("hud.holdfast_core.amputation_window",
                        c.amputationSecondsLeft() / 60, String.format("%02d", c.amputationSecondsLeft() % 60)), x, y + 19, 0xFFE8D27A);
            }
        }
    }
}
