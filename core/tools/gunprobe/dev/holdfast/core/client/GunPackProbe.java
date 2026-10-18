package dev.holdfast.core.client;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import dev.holdfast.core.HoldfastCore;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.File;

/**
 * Isolated developer smoke run for TaCZ gun packs only; Gradle excludes this source from release jars.
 * Equips each gun, then screenshots draw/rest, the empty reload at the Rhino's keyframe times, ADS, a charged
 * shot, third person and the inspect close-up, so the Holdfast pack can be compared with TaCZ's rhino357 in TaCZ's own renderer.
 */
@Mod.EventBusSubscriber(modid = HoldfastCore.MODID, value = Dist.CLIENT)
public final class GunPackProbe {
    private static final String[] GUNS = {"holdfast:ricks_python", "tacz:rhino357"};
    private static final int PER_GUN = 330;
    // reload_empty keyframe moments in seconds: latch/swing, eject, loader, insert, closing.
    private static final double[] RELOAD_SECONDS = {0.5, 1.05, 1.9, 2.15, 3.3};
    private static int tick, waiting;
    private static boolean joining, fired;
    private static String phase = "joining";

    private static String dir() { return System.getProperty("holdfast.gunProbe"); }

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
        if (tick == 5) {
            for (String id : GUNS) {
                var rl = new ResourceLocation(id);
                boolean common = TimelessAPI.getCommonGunIndex(rl).isPresent();
                boolean client = TimelessAPI.getClientGunIndex(rl).isPresent();
                HoldfastCore.LOG.info("[holdfast-gunprobe] {} common index {} client index {} type {}", id, common, client,
                        TimelessAPI.getCommonGunIndex(rl).map(i -> i.getType()).orElse("?"));
                if (!common || !client) throw new IllegalStateException("TaCZ did not load gun " + id);
            }
        }
        int g = (tick - 10) / PER_GUN, t = (tick - 10) % PER_GUN;
        if (tick < 10) return;
        if (g >= GUNS.length) {
            HoldfastCore.LOG.info("[holdfast-gunprobe] finished isolated gun-pack smoke; this is not user acceptance");
            mc.stop();
            return;
        }
        String gun = GUNS[g], tag = gun.replace(':', '_');
        var op = IClientPlayerGunOperator.fromLocalPlayer(mc.player);
        if (t == 0) {
            fired = false;
            give(gun);
            phase = tag + "-00-draw";
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.player.setXRot(0); mc.player.xRotO = 0;
        }
        if (t == 30) shot(tag + "-01-rest");
        if (t == 32) { op.reload(); log(tag, "reload requested"); }
        for (int i = 0; i < RELOAD_SECONDS.length; i++)
            if (t == 32 + (int) Math.round(RELOAD_SECONDS[i] * 20)) shot(tag + "-02-reload-" + RELOAD_SECONDS[i] + "s");
        if (t == 135) { shot(tag + "-03-reloaded"); com.tacz.guns.client.input.AimKey.AIM_KEY.setDown(true); }
        if (t > 135 && t < 152) op.aim(true);
        if (t == 150) {
            log(tag, "aim " + op.isAim() + " progress " + op.getClientAimingProgress(1));
            shot(tag + "-04-ads");
        }
        if (t == 152) { com.tacz.guns.client.input.AimKey.AIM_KEY.setDown(false); op.aim(false); }
        if (t >= 165 && t < 180 && !fired) {
            var result = op.shoot();
            if (result == com.tacz.guns.api.entity.ShootResult.SUCCESS) fired = true;
            else op.chargeShoot(true);
            log(tag, "shoot " + result + " charge " + op.getChargeProgress());
            if (t == 166) shot(tag + "-05-trigger");
        }
        if (t == 185) shot(tag + "-06-after-shot");
        if (t == 195) { mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); mc.player.setXRot(10); }
        if (t == 205) shot(tag + "-07-third-front");
        if (t == 215) mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        if (t == 225) shot(tag + "-08-third-back");
        if (t == 235) { mc.options.setCameraType(CameraType.FIRST_PERSON); mc.player.setXRot(0); }
        if (t == 260) op.inspect();
        if (t == 260 + 40) shot(tag + "-09-inspect-2s");
        if (t == 260 + 64) shot(tag + "-10-inspect-3.2s");
    }

    private static void give(String gunId) {
        var mc = Minecraft.getInstance();
        var gun = new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("tacz", "modern_kinetic_gun")));
        CompoundTag nbt = gun.getOrCreateTag();
        nbt.putString("GunId", gunId);
        nbt.putString("GunFireMode", "SEMI");
        nbt.putInt("GunCurrentAmmoCount", 0);
        var ammo = new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("tacz", "ammo")), 64);
        ammo.getOrCreateTag().putString("AmmoId", "tacz:357mag");
        var inv = mc.player.getInventory();
        inv.selected = 0;
        inv.setItem(0, gun);
        inv.setItem(1, ammo);
        mc.getConnection().send(new net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket(0));
        mc.getConnection().send(new net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket(36, gun));
        mc.getConnection().send(new net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket(37, ammo));
        log(gunId, "equipped");
    }

    private static void log(String gun, String what) {
        HoldfastCore.LOG.info("[holdfast-gunprobe] tick {} {} {}", tick, gun, what);
    }

    private static void shot(String name) {
        var mc = Minecraft.getInstance();
        phase = name;
        new File(dir()).mkdirs();
        Screenshot.grab(new File(dir()), name + ".png", mc.getMainRenderTarget(), m ->
                HoldfastCore.LOG.info("[holdfast-gunprobe] screenshot {}: {}", name, m.getString()));
    }
}
