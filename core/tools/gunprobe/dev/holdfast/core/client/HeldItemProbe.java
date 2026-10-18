package dev.holdfast.core.client;

import dev.holdfast.core.HoldfastCore;
import dev.holdfast.core.combat.Weapons;
import dev.holdfast.core.net.PlayClipPacket;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.File;

/**
 * Isolated developer smoke run for held core items (GeckoLib-rendered iconic weapons); excluded from release jars.
 * Screenshots each item at rest, looking down at it, mid-swing, in third person and in the inventory, next to the
 * plain item of the same family for comparison. Not user acceptance.
 */
@Mod.EventBusSubscriber(modid = HoldfastCore.MODID, value = Dist.CLIENT)
public final class HeldItemProbe {
    private static final String[][] ITEMS = {{"holdfast_core:michonne_katana", "swing_katana_heavy"},
            {"holdfast_core:katana", "swing_katana_heavy"}};
    private static final int PER_ITEM = 160;
    private static int tick, waiting;
    private static boolean joining;

    private static String dir() { return System.getProperty("holdfast.itemProbe"); }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || dir() == null) return;
        var mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        if (mc.player == null || mc.level == null) {
            if (!joining && mc.screen != null && mc.getOverlay() == null) {
                joining = true;
                mc.createWorldOpenFlows().loadLevel(mc.screen, "HoldfastProbe");
            }
            if (++waiting > 2400) mc.stop();
            return;
        }
        tick++;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        mc.options.bobView().set(false);
        if (tick == 1) {
            var server = mc.getSingleplayerServer();
            server.execute(() -> {
                var src = server.createCommandSourceStack();
                for (String c : new String[]{"gamemode creative HoldfastProbe", "time set noon", "weather clear",
                        "gamerule doDaylightCycle false"})
                    server.getCommands().performPrefixedCommand(src, c);
            });
        }
        if (tick < 10) return;
        int i = (tick - 10) / PER_ITEM, t = (tick - 10) % PER_ITEM;
        if (i >= ITEMS.length) {
            HoldfastCore.LOG.info("[holdfast-itemprobe] finished isolated held-item smoke; this is not user acceptance");
            mc.stop();
            return;
        }
        String id = ITEMS[i][0], tag = id.replace(':', '_');
        if (t == 0) {
            var stack = new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation(id)));
            var other = new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation(ITEMS[(i + 1) % ITEMS.length][0])));
            var inv = mc.player.getInventory();
            inv.selected = 0;
            inv.setItem(0, stack);
            inv.setItem(1, other);
            mc.getConnection().send(new net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket(0));
            mc.getConnection().send(new net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket(36, stack));
            mc.getConnection().send(new net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket(37, other));
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.player.setXRot(0); mc.player.xRotO = 0;
            HoldfastCore.LOG.info("[holdfast-itemprobe] {} equipped, weapon class {}", id, Weapons.of(stack));
        }
        if (t == 20) shot(tag + "-01-rest");
        if (t == 22) { mc.player.setXRot(40); mc.player.xRotO = 40; }
        if (t == 32) shot(tag + "-02-look-down");
        if (t == 34) { mc.player.setXRot(0); mc.player.xRotO = 0; }
        if (t == 40) {
            ClientClips.play(new PlayClipPacket(mc.player.getId(), ITEMS[i][1], -1, true));
            dev.holdfast.core.net.CoreNet.CHANNEL.sendToServer(new dev.holdfast.core.net.SwingPacket());
        }
        if (t == 45) shot(tag + "-03-swing-a");
        if (t == 50) shot(tag + "-04-swing-b");
        if (t == 80) { mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); mc.player.setXRot(10); }
        if (t == 92) shot(tag + "-05-third-front");
        if (t == 96) mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        if (t == 108) shot(tag + "-06-third-back");
        if (t == 112) { mc.options.setCameraType(CameraType.FIRST_PERSON); mc.setScreen(new InventoryScreen(mc.player)); }
        if (t == 124) shot(tag + "-07-inventory");
        if (t == 128) mc.setScreen(null);
    }

    private static void shot(String name) {
        var mc = Minecraft.getInstance();
        HoldfastCore.LOG.info("[holdfast-itemprobe] {} clips: {}", name, ClientClips.describe(mc.player));
        new File(dir()).mkdirs();
        Screenshot.grab(new File(dir()), name + ".png", mc.getMainRenderTarget(), m ->
                HoldfastCore.LOG.info("[holdfast-itemprobe] screenshot {}: {}", name, m.getString()));
    }
}
