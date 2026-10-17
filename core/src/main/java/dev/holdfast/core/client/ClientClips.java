package dev.holdfast.core.client;

import dev.holdfast.core.HoldfastCore;
import dev.holdfast.core.combat.Weapons;
import com.google.gson.JsonParser;
import net.minecraftforge.client.event.ViewportEvent;
import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier;
import dev.kosmx.playerAnim.api.layered.modifier.SpeedModifier;
import dev.kosmx.playerAnim.core.util.Ease;
import dev.kosmx.playerAnim.core.util.Vec3f;
import dev.holdfast.core.net.PlayClipPacket;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonConfiguration;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;

/**
 * Chooses and plays Player Animator clips. Presentation only: the server decided every outcome these show.
 *
 * <p>Layers, low to high priority (TaCZ uses 93-96):
 * <ul>
 * <li>locomotion 95: walk / sprint / crouch walk / jump, or the injury walk; a legs-only {@code _gun} variant while a
 * TaCZ gun is held, so TaCZ keeps arms, items and ADS (testing.md, Codex arm-ownership run).</li>
 * <li>weapon hold 97: the held melee family's hold (arms, items, head only), over locomotion.</li>
 * <li>action 1100: server-started clips (grabs, executions, charge) and swings; owns the arms while it plays.</li>
 * </ul>
 * Locomotion and swings are chosen from what this client sees of each player (movement, the vanilla swing it already
 * receives); the owner's injury walk follows the server's synced condition.
 */
public final class ClientClips {
    private static final ResourceLocation ACTION = new ResourceLocation(HoldfastCore.MODID, "action");
    private static final ResourceLocation HOLD = new ResourceLocation(HoldfastCore.MODID, "weapon_hold");
    private static final ResourceLocation LOCOMOTION = new ResourceLocation(HoldfastCore.MODID, "locomotion");
    private static final ResourceLocation TACZ_GUN = new ResourceLocation("tacz", "modern_kinetic_gun");

    /** The owner's injury walk, from the server's condition sync (null = uninjured). */
    private static String injuryClip;

    private static final class Applied {
        AbstractClientPlayer owner;
        String locomotion, hold;
        int lastSwingTime = 99;      // "not swinging", so the first swing is caught
        boolean wasOnGround = true;
        double x, y, z, speed, distance;
        boolean positioned;
        int sinceSwing = 100;
        int airTicks, landingTicks;
        final SpeedModifier rate = new SpeedModifier(1);
        boolean rateInstalled;
    }

    private static final Map<UUID, Applied> APPLIED = new HashMap<>();
    private static final Map<Integer, String> ACTION_NAMES = new HashMap<>();
    private static final Map<String, List<float[]>> CAMERAS = new HashMap<>();

    private ClientClips() {}

    static void registerLayers() {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(ACTION, 1100, p -> new ModifierLayer<>());
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(HOLD, 97, p -> new ModifierLayer<>());
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LOCOMOTION, 95, p -> new ModifierLayer<>());
    }

    @SuppressWarnings("unchecked")
    private static ModifierLayer<IAnimation> layer(AbstractClientPlayer p, ResourceLocation id) {
        return (ModifierLayer<IAnimation>) PlayerAnimationAccess.getPlayerAssociatedData(p).get(id);
    }

    private static boolean holdingGun(Player p) {
        return TACZ_GUN.equals(ForgeRegistries.ITEMS.getKey(p.getMainHandItem().getItem()));
    }

    private static KeyframeAnimationPlayer player(String clip, FirstPersonMode fp) {
        KeyframeAnimation data = PlayerAnimationRegistry.getAnimation(new ResourceLocation(HoldfastCore.ASSETS, clip));
        if (data == null) {
            HoldfastCore.LOG.warn("[holdfast] no Player Animator clip holdfast:{}", clip);
            return null;
        }
        return new KeyframeAnimationPlayer(data).setFirstPersonMode(fp)
                .setFirstPersonConfiguration(new FirstPersonConfiguration(true, true, true, true));
    }

    /** A server-started clip (grab, execution, bite confirmation, charge). */
    public static void play(PlayClipPacket m) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getEntity(m.playerId()) instanceof AbstractClientPlayer p)) return;
        if (mc.level.getEntity(m.partnerId()) instanceof dev.holdfast.core.entity.WalkerEntity walker && !m.partnerClip().isEmpty())
            walker.startLocally(m.partnerClip(), m.partnerNonce());
        ModifierLayer<IAnimation> layer = layer(p, ACTION);
        if (layer == null) return;
        if (m.clip().isEmpty()) {
            ACTION_NAMES.remove(p.getId());
            blend(layer, null, 3);
            return;
        }
        KeyframeAnimationPlayer kp = player(m.clip(), m.ownsArms() ? FirstPersonMode.THIRD_PERSON_MODEL : FirstPersonMode.NONE);
        if (kp == null) return;
        layer.setAnimation(kp);
        ACTION_NAMES.put(p.getId(), m.clip());
        p.yBodyRot = p.getYRot();
        p.yBodyRotO = p.yBodyRot;
    }

    /** Other players' injury walks, by entity id (MovementPacket). */
    private static final Map<Integer, String> REMOTE_INJURY = new HashMap<>();

    public static void remoteMovement(dev.holdfast.core.net.MovementPacket m) {
        String clip = injuryClipFor(m.movement());
        if (clip == null) REMOTE_INJURY.remove(m.playerId());
        else REMOTE_INJURY.put(m.playerId(), clip);
    }

    static void movementChanged(String movement) {
        injuryClip = injuryClipFor(movement);
    }

    private static String injuryClipFor(String movement) {
        return switch (movement.toUpperCase(Locale.ROOT)) {
            case "ARM_INJURED" -> "move_arm_injured";
            case "LIMP" -> "move_limp";
            case "LIMP_SEVERE" -> "move_limp_severe";
            case "PROSTHESIS" -> "move_prosthesis";
            case "CRUTCH_MEDICAL" -> "move_crutch_medical";
            case "CRUTCH_IMPROVISED" -> "move_crutch_improvised";
            case "LEGLESS_CRAWL" -> "move_crawl_legless";
            default -> null;
        };
    }

    /** Called every client tick. */
    static void tick() {
        var mc = Minecraft.getInstance();
        if (mc.level == null) {
            APPLIED.clear();
            REMOTE_INJURY.clear();
            injuryClip = null;
            ACTION_NAMES.clear();
            CAMERAS.clear();
            return;
        }
        if (mc.isPaused()) return;
        for (Player pl : mc.level.players()) {
            if (pl instanceof AbstractClientPlayer p) update(p, p == mc.player);
        }
        APPLIED.keySet().removeIf(id -> mc.level.getPlayerByUUID(id) == null);
    }

    private static String family(Player p) {
        return switch (Weapons.held(p)) {
            case KNIFE -> "knife";
            case MACHETE -> "machete";
            case HATCHET -> "hatchet";
            case KATANA -> "katana";
            case AXE, HAMMER -> "axe";
            case BAT -> "bat";
            case NONE, STAFF -> "unarmed";
            default -> null;
        };
    }

    private static void blend(ModifierLayer<IAnimation> layer, IAnimation next, int ticks) {
        if (layer != null) layer.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(ticks, Ease.INOUTSINE), next);
    }

    private static void clear(ModifierLayer<IAnimation> layer) {
        if (layer == null) return;
        layer.setAnimation(null);
        while (layer.size() > 0) layer.removeModifier(0);
    }

    /** Keep command-driven inspection independent of the automatic layers, even with both mods installed. */
    private static boolean inspecting(AbstractClientPlayer p) {
        Object test = PlayerAnimationAccess.getPlayerAssociatedData(p).get(new ResourceLocation("holdfast_animtest", "test_layer"));
        return test instanceof IAnimation animation && animation.isActive();
    }

    /** Small gait follow-through on the authored carry, rather than pinning both arms to one idle pose. */
    private static class LookPlayer extends KeyframeAnimationPlayer {
        private final boolean additiveHead;
        LookPlayer(KeyframeAnimation data) { this(data, true); }
        LookPlayer(KeyframeAnimation data, boolean additiveHead) { super(data); this.additiveHead = additiveHead; }
        LookPlayer(KeyframeAnimation data, boolean additiveHead, int tick) { super(data, tick); this.additiveHead = additiveHead; }
        @Override public Vec3f get3DTransform(String part, TransformType type, float partial, Vec3f value) {
            Vec3f pose = super.get3DTransform(part, type, partial, value);
            return additiveHead && part.equals("head") && type == TransformType.ROTATION ? pose.add(value) : pose;
        }
    }

    private static final class CarryPlayer extends LookPlayer {
        private final Applied state;
        private final boolean two;
        CarryPlayer(KeyframeAnimation data, Applied state, boolean two) {
            super(data);
            this.state = state;
            this.two = two;
            setFirstPersonMode(FirstPersonMode.THIRD_PERSON_MODEL);
            setFirstPersonConfiguration(new FirstPersonConfiguration(true, true, true, true));
        }
        @Override public Vec3f get3DTransform(String part, TransformType type, float partial, Vec3f value) {
            Vec3f pose = super.get3DTransform(part, type, partial, value);
            if (type != TransformType.ROTATION || !(part.equals("rightArm") || part.equals("leftArm"))) return pose;
            if (two) return pose; // Both grips follow the authored shoulder motion and the shared body gait together.
            double phase = (state.distance + state.speed * partial) * Math.PI / 1.15;
            float amount = (float) Math.min(1.3, state.speed / (4.317 / 20));
            float side = part.equals("rightArm") ? 1 : -1;
            return pose.add(new Vec3f((float) Math.sin(phase - 0.25) * amount * side * 0.075F,
                    0, (float) Math.sin(phase * 2) * amount * 0.012F));
        }
    }

    private static void update(AbstractClientPlayer p, boolean self) {
        Applied a = APPLIED.computeIfAbsent(p.getUUID(), k -> new Applied());
        if (a.owner != p) {
            a = new Applied();
            a.owner = p;
            APPLIED.put(p.getUUID(), a);
        }
        if (inspecting(p)) {
            for (ResourceLocation id : new ResourceLocation[]{LOCOMOTION, HOLD, ACTION}) {
                ModifierLayer<IAnimation> l = layer(p, id);
                clear(l);
            }
            a.rateInstalled = false;
            a.locomotion = a.hold = null;
            a.positioned = false;
            return;
        }
        boolean gun = holdingGun(p);
        boolean freeArms = gun || p.isUsingItem();
        if (freeArms) {
            // A fading old melee hold must not mask a newly equipped gun's ADS or a bow/shield use pose.
            clear(layer(p, HOLD));
            a.hold = null;
            String actionName = ACTION_NAMES.get(p.getId());
            if (gun && actionName != null && (actionName.startsWith("swing_") || actionName.startsWith("bat_"))) {
                clear(layer(p, ACTION));
                ACTION_NAMES.remove(p.getId());
            }
        }
        // ---- locomotion
        double dx = p.getX() - a.x, dz = p.getZ() - a.z, dy = p.getY() - a.y;
        double speed = a.positioned ? Math.min(0.5, Math.hypot(dx, dz)) : 0;
        a.x = p.getX(); a.y = p.getY(); a.z = p.getZ(); a.positioned = true;
        a.speed += (speed - a.speed) * 0.45;
        a.distance += speed;
        boolean moving = a.speed > 0.008;
        if (p.onGround()) {
            if (!a.wasOnGround) a.landingTicks = 5;
            else if (a.landingTicks > 0) a.landingTicks--;
            a.airTicks = 0;
        } else {
            a.airTicks++;
            a.landingTicks = 0;
        }
        String loco;
        String injury = self ? injuryClip : REMOTE_INJURY.get(p.getId());
        if (p.isPassenger() || p.isFallFlying() || p.isSleeping() || p.isSwimming()) {
            loco = null; // These poses are owned by their existing renderer until explicitly authored.
        } else if (injury != null && moving && p.onGround()) {
            loco = injury;
        } else if (!p.onGround()) {
            loco = a.airTicks <= 7 && (dy > 0 || p.getDeltaMovement().y > 0) ? "move_jump" : "move_fall";
        } else if (a.landingTicks > 0) {
            loco = "move_land";
        } else if (!moving) {
            loco = p.isCrouching() ? "move_crouch" : "move_idle";
        } else if (p.isCrouching()) {
            loco = "move_crouch_walk";
        } else {
            loco = p.isSprinting() ? "move_sprint" : "move_walk";
        }
        a.wasOnGround = p.onGround();
        boolean noGunVariant = loco != null && (loco.startsWith("move_crutch") || loco.equals("move_crawl_legless"));
        if (loco != null && freeArms && !noGunVariant) loco += "_gun";
        ModifierLayer<IAnimation> locomotionLayer = layer(p, LOCOMOTION);
        if (locomotionLayer != null && !a.rateInstalled) {
            locomotionLayer.addModifierBefore(a.rate);
            a.rateInstalled = true;
        }
        double reference = loco == null ? 1 : switch (loco.replace("_gun", "")) {
            case "move_sprint" -> 5.612 / 20;
            case "move_crouch_walk" -> 1.295 / 20;
            case "move_limp" -> 3.670 / 20;
            case "move_limp_severe" -> 2.374 / 20;
            case "move_arm_injured" -> 4.101 / 20;
            case "move_prosthesis" -> 3.454 / 20;
            case "move_crutch_medical" -> 2.590 / 20;
            case "move_crutch_improvised" -> 1.943 / 20;
            case "move_crawl_legless" -> 0.863 / 20;
            default -> 4.317 / 20;
        };
        boolean cycle = loco != null && reference > 0 && (loco.contains("walk") || loco.contains("sprint") || loco.contains("limp") || loco.contains("injured") || loco.contains("prosthesis") || loco.contains("crutch") || loco.contains("crawl"));
        a.rate.speed = cycle ? (float) Math.max(0.1, Math.min(2, a.speed / reference)) : 1;
        if (!Objects.equals(loco, a.locomotion)) {
            a.locomotion = loco;
            ModifierLayer<IAnimation> layer = layer(p, LOCOMOTION);
            if (layer != null) {
                KeyframeAnimation data = loco == null ? null : PlayerAnimationRegistry.getAnimation(new ResourceLocation(HoldfastCore.ASSETS, loco));
                int phaseTick = 0;
                if (data != null && cycle && layer.getAnimation() instanceof KeyframeAnimationPlayer old && old.isInfinite()) {
                    double phase = (old.getTick() % (old.getData().endTick + 1.0)) / (old.getData().endTick + 1.0);
                    phaseTick = (int) (phase * (data.endTick + 1));
                }
                LookPlayer motion = data == null ? null : new LookPlayer(data, !freeArms, phaseTick);
                if (motion != null) motion.setFirstPersonMode(freeArms ? FirstPersonMode.NONE : FirstPersonMode.THIRD_PERSON_MODEL)
                        .setFirstPersonConfiguration(new FirstPersonConfiguration(true, true, true, true));
                blend(layer, motion, 4);
            }
        }
        // ---- weapon hold (melee families; TaCZ guns keep their own hold)
        String fam = gun ? null : family(p);
        String hold = fam == null || fam.equals("unarmed") || p.isUsingItem() || p.isSwimming() || p.isPassenger() ? null : "hold_" + fam;
        if (!Objects.equals(hold, a.hold)) {
            a.hold = hold;
            ModifierLayer<IAnimation> layer = layer(p, HOLD);
            KeyframeAnimation data = hold == null ? null : PlayerAnimationRegistry.getAnimation(new ResourceLocation(HoldfastCore.ASSETS, hold));
            blend(layer, data == null ? null : new CarryPlayer(data, a, fam.equals("axe") || fam.equals("bat") || fam.equals("katana")), 5);
        }
        // ---- swing: vanilla already tells every client when a player starts a swing
        if (fam != null && p.swinging && p.swingTime <= 1 && a.lastSwingTime > 1) {
            boolean heavy = a.sinceSwing >= p.getCurrentItemAttackStrengthDelay() * 0.95F;
            a.sinceSwing = 0;
            String clip = switch (fam) {
                case "bat" -> "swing_bat";
                case "axe" -> heavy ? "swing_axe_overhead" : "swing_axe";
                default -> "swing_" + fam + (heavy ? "_heavy" : "");
            };
            ModifierLayer<IAnimation> layer = layer(p, ACTION);
            if (layer != null && (layer.getAnimation() == null || !layer.getAnimation().isActive())) {
                KeyframeAnimationPlayer kp = player(clip, FirstPersonMode.THIRD_PERSON_MODEL);
                if (kp != null) {
                    blend(layer, kp, 2);
                    ACTION_NAMES.put(p.getId(), clip);
                }
            }
        }
        a.lastSwingTime = p.swinging ? p.swingTime : 99;
        a.sinceSwing++;
    }

    /** Same source-generated cosmetic camera cue as the harness, including the firearm-safe injury track. */
    static void camera(ViewportEvent.ComputeCameraAngles event) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || !mc.options.getCameraType().isFirstPerson() || inspecting(mc.player)) return;
        Applied state = APPLIED.get(mc.player.getUUID());
        if (state == null) return;
        ModifierLayer<IAnimation> action = layer(mc.player, ACTION), motion = layer(mc.player, LOCOMOTION);
        boolean acting = action != null && action.getAnimation() != null && action.getAnimation().isActive();
        String name = acting ? ACTION_NAMES.get(mc.player.getId()) : state.locomotion;
        IAnimation animation = acting ? action.getAnimation() : motion == null ? null : motion.getAnimation();
        if (name == null || !(animation instanceof KeyframeAnimationPlayer player) || !player.isActive()) return;
        List<float[]> frames = CAMERAS.computeIfAbsent(name, ClientClips::loadCamera);
        if (frames.isEmpty()) return;
        float t = (float) ((player.getTick() + event.getPartialTick() * (acting ? 1 : state.rate.speed)) / 20);
        float factor = acting ? 1 : (float) Math.min(1, state.speed / .12);
        float[] a = frames.get(0), b = a;
        for (float[] frame : frames) {
            b = frame;
            if (frame[0] >= t) break;
            a = frame;
        }
        float u = Math.max(0, Math.min(1, (t - a[0]) / Math.max(.0001F, b[0] - a[0])));
        event.setPitch(event.getPitch() + (a[1] + (b[1] - a[1]) * u) * factor);
        event.setYaw(event.getYaw() + (a[2] + (b[2] - a[2]) * u) * factor);
        event.setRoll(event.getRoll() + (a[3] + (b[3] - a[3]) * u) * factor);
    }

    private static List<float[]> loadCamera(String name) {
        List<float[]> frames = new ArrayList<>();
        var mc = Minecraft.getInstance();
        var resource = mc.getResourceManager().getResource(new ResourceLocation(HoldfastCore.ASSETS,
                "player_animation_extras/" + name + ".extras.json"));
        if (resource.isEmpty()) return frames;
        try (var reader = resource.get().openAsReader()) {
            var bones = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("animations").getAsJsonObject(name).getAsJsonObject("bones");
            if (bones == null || !bones.has("camera")) return frames;
            for (var entry : bones.getAsJsonObject("camera").getAsJsonObject("rotation").entrySet()) {
                var value = entry.getValue().getAsJsonObject().getAsJsonArray("post");
                frames.add(new float[]{Float.parseFloat(entry.getKey()), value.get(0).getAsFloat(), value.get(1).getAsFloat(), value.get(2).getAsFloat()});
            }
            frames.sort(java.util.Comparator.comparingDouble(frame -> frame[0]));
        } catch (Exception ex) {
            HoldfastCore.LOG.warn("[holdfast] camera track {} could not load: {}", name, ex.toString());
        }
        return frames;
    }
}
