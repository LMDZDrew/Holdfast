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
    /** design.md: one consistent context key for escape, execute and cancel. Default V. */
    public static final KeyMapping ACTION = new KeyMapping("key.holdfast_core.action", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V,
            "key.categories.holdfast_core");

    private ClientCore() {}

    public static void init(IEventBus modBus) {
        modBus.addListener(ClientCore::onRenderers);
        modBus.addListener(ClientCore::onKeys);
        modBus.addListener(ClientCore::onOverlays);
        modBus.addListener((FMLClientSetupEvent e) -> e.enqueueWork(ClientClips::registerLayers));
        MinecraftForge.EVENT_BUS.addListener(ClientCore::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(ClientClips::camera);
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
        if (mc.player == null) return;
        while (ACTION.consumeClick()) {
            Entity looked = mc.hitResult instanceof EntityHitResult eh ? eh.getEntity() : null;
            CoreNet.CHANNEL.sendToServer(new ActionPacket(looked == null ? -1 : looked.getId()));
        }
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
            float left = 1.0F - (now - ClientState.promptStartClientTick + partial) / ClientState.promptWindow;
            left = Math.max(0, Math.min(1, left));
            int bw = 120, bx = (w - bw) / 2, by = h / 2 + 24;
            g.fill(bx - 1, by - 1, bx + bw + 1, by + 7, 0xC0000000);
            g.fill(bx, by, bx + (int) (bw * left), by + 6, left > 0.35F ? 0xFFE8D27A : 0xFFD24A3A);
            Component label = Component.translatable(ClientState.promptGround ? "hud.holdfast_core.kick_free" : "hud.holdfast_core.shove",
                    ACTION.getTranslatedKeyMessage());
            g.drawCenteredString(font, label, w / 2, by - 12, 0xFFFFFFFF);
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
