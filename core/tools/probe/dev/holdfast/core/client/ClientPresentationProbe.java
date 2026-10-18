package dev.holdfast.core.client;

import dev.holdfast.core.HoldfastCore;
import dev.holdfast.core.net.PlayClipPacket;
import dev.holdfast.core.registry.CoreItems;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.File;

/** Isolated developer smoke run only; Gradle excludes this source from release jars. No acceptance claims. */
@Mod.EventBusSubscriber(modid=HoldfastCore.MODID, value=Dist.CLIENT)
public final class ClientPresentationProbe {
    private static int tick;
    private static String phase = "joining";
    private static boolean observed;
    private static boolean joining;
    private static int waiting;
    private static dev.holdfast.core.entity.WalkerEntity voiceWalker;
    private static int swingSounds, impactSounds;
    private static File evidence() { return new File(System.getProperty("holdfast.probe")); }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || System.getProperty("holdfast.probe") == null) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            if (++waiting % 100 == 0) HoldfastCore.LOG.info("[holdfast-probe] waiting screen {} overlay {}", mc.screen, mc.getOverlay());
            if (!joining && mc.screen != null && mc.getOverlay() == null) {
                joining=true;
                mc.createWorldOpenFlows().loadLevel(mc.screen, "HoldfastProbe");
            }
            if (waiting > 1200) mc.stop();
            return;
        }
        tick++;
        mc.options.bobView().set(false);
        if (tick == 20) phase("body-empty", ItemStack.EMPTY, CameraType.FIRST_PERSON, 85);
        if (tick == 40) shot();
        if (tick == 60) phase("body-knife-rest", new ItemStack(CoreItems.KNIFE.get()), CameraType.FIRST_PERSON, 85);
        if (tick == 80) shot();
        if (tick == 100) {
            phase("body-knife-swing", new ItemStack(CoreItems.KNIFE.get()), CameraType.FIRST_PERSON, 55);
            ClientClips.play(new PlayClipPacket(mc.player.getId(), "swing_knife_heavy", -1, true));
            dev.holdfast.core.net.CoreNet.CHANNEL.sendToServer(new dev.holdfast.core.net.SwingPacket());
        }
        if (tick == 105) shot();
        if (tick == 130) {
            phase("body-grab", ItemStack.EMPTY, CameraType.FIRST_PERSON, 55);
            ClientClips.play(new PlayClipPacket(mc.player.getId(), "grabbed_seize", -1, true));
        }
        if (tick == 133) shot();
        if (tick == 150) {
            ClientClips.play(PlayClipPacket.stop(mc.player.getId()));
            phase("body-katana-third", new ItemStack(CoreItems.KATANA.get()), CameraType.THIRD_PERSON_BACK, 20);
        }
        if (tick == 170) shot();
        if (tick == 190) {
            var item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation("tacz", "modern_kinetic_gun"));
            var gun = new ItemStack(item);
            gun.getOrCreateTag().putString("GunId", "tacz:glock_17");
            phase("body-tacz-pistol", gun, CameraType.FIRST_PERSON, 85);
        }
        if (tick == 215) shot();
        if (tick == 240) {
            dev.tr7zw.firstperson.api.FirstPersonAPI.setEnabled(false);
            phase("fallback-without-body", new ItemStack(CoreItems.KNIFE.get()), CameraType.FIRST_PERSON, 65);
        }
        if (tick == 260) shot();
        if (tick == 280) {
            dev.tr7zw.firstperson.api.FirstPersonAPI.setEnabled(true);
            phase("body-restored", ItemStack.EMPTY, CameraType.FIRST_PERSON, 85);
        }
        if (tick == 300) shot();
        if (tick == 330) {
            voiceWalker = new dev.holdfast.core.entity.WalkerEntity(dev.holdfast.core.registry.CoreEntities.WALKER.get(), mc.level);
            voiceWalker.setId(1900001);
            voiceWalker.setPos(mc.player.position().add(0,0,2));
            mc.level.putNonPlayerEntity(1900001, voiceWalker);
            WalkerVoices.play(new dev.holdfast.core.net.WalkerVocalPacket(1900001,
                    dev.holdfast.core.registry.CoreSounds.WALKER_GROAN.get().getLocation(), 1, .5F));
            HoldfastCore.LOG.info("[holdfast-probe] voice walker alive {} corpse {} silent {} id {}", voiceWalker.isAlive(), voiceWalker.isCorpse(), voiceWalker.isSilent(),voiceWalker.getId());
        }
        if (tick == 335) {
            HoldfastCore.LOG.info("[holdfast-probe] voice before corpse active {}", voiceActive());
            if (!voiceActive()) throw new IllegalStateException("Voice never started; cannot prove corpse interruption");
            voiceWalker.brainKill(null,0);
        }
        if (tick == 340) {
            if (voiceActive()) throw new IllegalStateException("Corpse voice still active");
            if (swingSounds!=1 || impactSounds!=0) throw new IllegalStateException("Miss audio duplicated or emitted impact: "+swingSounds+"/"+impactSounds);
            HoldfastCore.LOG.info("[holdfast-probe] corpse voice stopped; miss swing events {} impact events {}",swingSounds,impactSounds);
        }
        if (tick == 370) {
            HoldfastCore.LOG.info("[holdfast-probe] finished isolated render smoke; this is not user acceptance");
            mc.stop();
        }
    }

    private static void phase(String name, ItemStack held, CameraType camera, float pitch) {
        var mc = Minecraft.getInstance();
        phase=name; observed=false;
        mc.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, held);
        mc.getConnection().send(new net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket(36+mc.player.getInventory().selected, held));
        mc.player.setXRot(pitch); mc.player.xRotO=pitch;
        mc.options.setCameraType(camera);
        HoldfastCore.LOG.info("[holdfast-probe] phase {} at {}: {}", name, tick, ClientClips.describe(mc.player));
    }
    private static boolean voiceActive() {
        try {
            var field=WalkerVoices.class.getDeclaredField("VOICES"); field.setAccessible(true);
            var voices=(java.util.Map<?,?>)field.get(null);
            var voice=voices.get(1900001);
            return voice instanceof net.minecraft.client.resources.sounds.SoundInstance sound
                    && Minecraft.getInstance().getSoundManager().isActive(sound);
        } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
    }
    @SubscribeEvent public static void sound(net.minecraftforge.client.event.sound.PlaySoundEvent event) {
        if(event.getSound()==null) return;
        String path=event.getSound().getLocation().getPath();
        if(path.startsWith("swing.")) {
            swingSounds++;
            HoldfastCore.LOG.info("[holdfast-probe] swing sound {} at client tick {}", path,tick);
        }
        if(path.startsWith("impact.")) impactSounds++;
    }
    private static void shot() {
        var mc = Minecraft.getInstance();
        evidence().mkdirs();
        Screenshot.grab(evidence(), phase+".png", mc.getMainRenderTarget(), message ->
                HoldfastCore.LOG.info("[holdfast-probe] screenshot {}: {}", phase, message.getString()));
    }
    @SubscribeEvent public static void rendered(RenderPlayerEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (event.getEntity()!=mc.player || observed || System.getProperty("holdfast.probe")==null) return;
        var model=event.getRenderer().getModel();
        HoldfastCore.LOG.info("[holdfast-probe] rendered {}: body {} legs {}/{} arms {}/{}; FPM {}; {}",
                phase, model.body.visible, model.leftLeg.visible, model.rightLeg.visible, model.leftArm.visible,
                model.rightArm.visible, dev.tr7zw.firstperson.api.FirstPersonAPI.isRenderingPlayer(), ClientClips.describe(mc.player));
        observed=true;
    }
}
