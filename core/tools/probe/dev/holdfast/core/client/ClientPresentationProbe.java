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
    private static float walkingCameraDelta;
    private static File evidence() { return new File(System.getProperty("holdfast.probe")); }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || System.getProperty("holdfast.probe") == null) return;
        var mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
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
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        if (tick == 1 && mc.getSingleplayerServer() != null) mc.getSingleplayerServer().execute(() -> {
            var player=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
            if(player!=null) player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        });
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
        if (tick == 205) {
            com.tacz.guns.client.input.AimKey.AIM_KEY.setDown(true);
            com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator.fromLocalPlayer(mc.player).aim(true);
        }
        if (tick == 225) {
            var operator=com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator.fromLocalPlayer(mc.player);
            float progress=com.tacz.guns.api.entity.IGunOperator.fromLivingEntity(mc.player).getSynAimingProgress();
            HoldfastCore.LOG.info("[holdfast-probe] TaCZ aim active {} progress {}",operator.isAim(),progress);
            if(!operator.isAim() || progress<.9F) throw new IllegalStateException("ADS fixture never reached aiming");
            phase="fresh-tacz-ads-third"; observed=false;
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            mc.player.setXRot(15); mc.player.xRotO=15;
        }
        if (tick == 235) shot();
        if (tick == 240) {
            com.tacz.guns.client.input.AimKey.AIM_KEY.setDown(false);
            com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator.fromLocalPlayer(mc.player).aim(false);
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
        if (Boolean.getBoolean("holdfast.bloodProbe")) {
            if (tick >= 350 && tick <= 510 && (tick-350)%40 == 0) {
                var contacts=dev.holdfast.core.rules.WeaponClass.Contact.values();
                var contact=contacts[Math.min(3,(tick-350)/40)];
                phase("blood-"+contact+"-"+tick, ItemStack.EMPTY, CameraType.FIRST_PERSON, 5);
                blood(contact, tick==510);
            }
            if (tick >= 355 && tick <= 515 && (tick-355)%40 == 0) shot();
            if (tick >= 550 && tick <= 580 && tick%3 == 0) blood(dev.holdfast.core.rules.WeaponClass.Contact.GUNSHOT,false);
        }
        if (tick == 360 && net.minecraftforge.fml.ModList.get().isLoaded("entity_model_features")) {
            if (!FreshMovement.active()) throw new IllegalStateException("Fresh stack is loaded but movement ownership did not activate");
            phase("fresh-walk-third", ItemStack.EMPTY, CameraType.THIRD_PERSON_BACK, 20);
            mc.options.keyUp.setDown(true);
        }
        if (tick == 390 && FreshMovement.active()) {
            if (!ClientClips.describe(mc.player).contains("locomotion null")) throw new IllegalStateException("Holdfast still owns ordinary gait");
            shot();
            phase("fresh-walk-first", ItemStack.EMPTY, CameraType.FIRST_PERSON, 75);
        }
        if (tick == 420 && FreshMovement.active()) {
            shot();
            HoldfastCore.LOG.info("[holdfast-probe] fresh moving camera maximum angular delta {} degrees",walkingCameraDelta);
            mc.options.keyUp.setDown(false);
            phase("fresh-sprint-third", ItemStack.EMPTY, CameraType.THIRD_PERSON_BACK, 20);
            mc.options.keyUp.setDown(true);
            mc.options.keySprint.setDown(true);
        }
        if (tick == 450 && FreshMovement.active()) {
            shot();
            mc.options.keySprint.setDown(false);
            phase("fresh-jump-third", ItemStack.EMPTY, CameraType.THIRD_PERSON_BACK, 20);
            mc.options.keyJump.setDown(true);
        }
        if (tick == 455 && FreshMovement.active()) shot();
        if (tick == 460) mc.options.keyJump.setDown(false);
        if (tick == 480 && FreshMovement.active()) {
            mc.options.keyUp.setDown(false);
            phase("fresh-katana-action", new ItemStack(CoreItems.KATANA.get()), CameraType.THIRD_PERSON_BACK, 20);
            ClientClips.play(new PlayClipPacket(mc.player.getId(), "swing_katana_heavy", -1, true));
        }
        if (tick == 488 && FreshMovement.active()) shot();
        if (tick == 520 && FreshMovement.active()) {
            phase("fresh-injury-override", new ItemStack(CoreItems.KNIFE.get()), CameraType.THIRD_PERSON_BACK, 20);
            mc.options.keyUp.setDown(true);
        }
        if (tick >= 520 && tick < 550 && FreshMovement.active()) ClientClips.movementChanged("LIMP");
        if (tick == 545 && FreshMovement.active()) {
            if(!ClientClips.describe(mc.player).contains("move_limp")) throw new IllegalStateException("Fresh masked injury override");
            shot();
        }
        if (tick == 550) ClientClips.movementChanged("NORMAL");
        if (tick == 560 && FreshMovement.active()) {
            phase("fresh-crouch-walk", ItemStack.EMPTY, CameraType.THIRD_PERSON_BACK, 20);
            mc.options.keyShift.setDown(true);
        }
        if (tick == 590 && FreshMovement.active()) shot();
        if (tick == 630) {
            mc.options.keyUp.setDown(false);
            mc.options.keySprint.setDown(false);
            mc.options.keyJump.setDown(false);
            mc.options.keyShift.setDown(false);
            HoldfastCore.LOG.info("[holdfast-probe] finished isolated render smoke; this is not user acceptance");
            mc.stop();
        }
    }
    private static void blood(dev.holdfast.core.rules.WeaponClass.Contact contact, boolean glance) {
        var mc=Minecraft.getInstance();
        var origin=mc.player.getEyePosition().add(mc.player.getLookAngle().scale(2));
        var hit=dev.holdfast.core.net.HitFeedbackPacket.of(-1,origin,mc.player.getLookAngle(),contact,
                dev.holdfast.core.rules.BodyRegion.TORSO,glance?dev.holdfast.core.rules.WalkerStrikeRules.Outcome.GLANCE:dev.holdfast.core.rules.WalkerStrikeRules.Outcome.STAGGER);
        if(contact==dev.holdfast.core.rules.WeaponClass.Contact.GUNSHOT) hit=hit.withExit(origin.add(mc.player.getLookAngle().scale(.4)));
        HitEffects.play(hit);
        try {
            var field=ExtraGoreEffects.class.getDeclaredField("OWNED"); field.setAccessible(true);
            var owned=(java.util.Collection<?>)field.get(null);
            if(owned.isEmpty() || owned.size()>1200) throw new IllegalStateException("Blood ownership budget: "+owned.size());
            HoldfastCore.LOG.info("[holdfast-probe] blood {} glance {} live owned {}",contact,glance,owned.size());
        } catch(ReflectiveOperationException e) {throw new RuntimeException(e);}
    }
    @SubscribeEvent public static void camera(net.minecraftforge.client.event.ViewportEvent.ComputeCameraAngles event) {
        var mc=Minecraft.getInstance();
        if(tick<390 || tick>=420 || mc.player==null || !FreshMovement.active()) return;
        float delta=Math.max(Math.abs(event.getPitch()-mc.player.getXRot()), Math.abs(event.getRoll()));
        if(!Float.isFinite(delta)) throw new IllegalStateException("Non-finite walking camera");
        walkingCameraDelta=Math.max(walkingCameraDelta,delta);
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
