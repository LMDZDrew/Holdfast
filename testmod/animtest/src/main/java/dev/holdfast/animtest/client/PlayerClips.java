package dev.holdfast.animtest.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.holdfast.animtest.Clips;
import dev.holdfast.animtest.HoldfastAnimTest;
import dev.holdfast.animtest.Net;
import dev.holdfast.animtest.TestWalker;
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
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Plays exported player clips through Player Animator and logs their timing. Also applies the first-person camera
 * rotation track from player_animation_extras/ (presentation only). The camera position track is NOT applied yet:
 * Forge 1.20.1 has no camera-position event, so that needs its own hook later.
 */
public final class PlayerClips {
    private static final ResourceLocation LAYER = new ResourceLocation(HoldfastAnimTest.MODID, "test_layer");
    private static final Map<Integer, Active> ACTIVE = new HashMap<>();
    private static int clientTicks;

    private PlayerClips() {}

    private static final class Active {
        final String clip;
        KeyframeAnimationPlayer anim;
        boolean frozen;
        final int startClientTick;
        final int camera;
        final List<float[]> camRot = new ArrayList<>(); // {time, pitch, yaw, roll}
        final Map<Double, String> timeline = new HashMap<>();
        final Set<Double> fired = new HashSet<>();
        boolean finishLogged;
        int lastCameraLogTick = -1000;

        Active(String clip, KeyframeAnimationPlayer anim, int startClientTick, int camera) {
            this.clip = clip;
            this.anim = anim;
            this.startClientTick = startClientTick;
            this.camera = camera;
        }
    }

    static void registerLayer() {
        // TaCZ's lower/upper/rotation layers are 93-96 in the installed 1.1.8-hotfix2 jar.
        // Run after its lower locomotion, but before its final gun rotation layer. Firearm
        // injury variants key only body translation and legs, leaving gun parts untouched.
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER, 95, player -> new ModifierLayer<>());
        HoldfastAnimTest.LOG.info("[holdfast-test] Player Animator layer {} registered", LAYER);
    }

    @SuppressWarnings("unchecked")
    private static ModifierLayer<IAnimation> layer(AbstractClientPlayer p) {
        return (ModifierLayer<IAnimation>) PlayerAnimationAccess.getPlayerAssociatedData(p).get(LAYER);
    }

    public static void onPacket(Net.Play m) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (m.walkerId() >= 0 && mc.level.getEntity(m.walkerId()) instanceof TestWalker w) {
            w.startLocally(m.body(), m.arms(), m.head(), m.nonce(), m.crawler());
        }
        Entity e = mc.level.getEntity(m.playerId());
        if (!(e instanceof AbstractClientPlayer p)) return;
        ModifierLayer<IAnimation> layer = layer(p);
        if (layer == null) {
            HoldfastAnimTest.LOG.error("[holdfast-test] FAIL no Player Animator layer on player {}", p.getGameProfile().getName());
            return;
        }
        if (m.playerClip().isEmpty()) {
            layer.setAnimation(null);
            ACTIVE.remove(p.getId());
            HoldfastAnimTest.LOG.info("[holdfast-test] player {} clip stopped", p.getGameProfile().getName());
            return;
        }
        String selected = m.playerClip();
        ResourceLocation held = ForgeRegistries.ITEMS.getKey(p.getMainHandItem().getItem());
        if (held != null && held.equals(new ResourceLocation("tacz", "modern_kinetic_gun"))
                && Clips.PLAYER.containsKey(selected + "_gun")) selected += "_gun";
        KeyframeAnimation data = PlayerAnimationRegistry.getAnimation(new ResourceLocation(HoldfastAnimTest.ASSETS, selected));
        if (data == null) {
            HoldfastAnimTest.LOG.error("[holdfast-test] FAIL player clip {}:{} is not registered by Player Animator",
                    HoldfastAnimTest.ASSETS, m.playerClip());
            if (p == mc.player) mc.player.displayClientMessage(Component.literal("Player Animator has no clip holdfast:" + m.playerClip()), false);
            return;
        }
        // NONE lets Player Animator defer the first-person render mode to TaCZ/vanilla.
        // THIRD_PERSON_MODEL here masks TaCZ's sight view even though this clip keys no arms.
        FirstPersonMode firstPerson = selected.endsWith("_gun")
                ? FirstPersonMode.NONE : FirstPersonMode.valueOf(m.firstPerson());
        KeyframeAnimationPlayer kp = new KeyframeAnimationPlayer(data)
                .setFirstPersonMode(firstPerson)
                .setFirstPersonConfiguration(new FirstPersonConfiguration(true, true, true, true));
        layer.setAnimation(kp);
        // Line the body up with the view so paired clips start from the same facing the server used.
        p.yBodyRot = p.getYRot();
        p.yBodyRotO = p.yBodyRot;
        Active a = new Active(selected, kp, clientTicks, m.camera());
        loadExtras(a);
        ACTIVE.put(p.getId(), a);
        Double len = Clips.PLAYER.get(selected);
        HoldfastAnimTest.LOG.info("[holdfast-test] player {} start clip {} at client tick {}; exported length {} s = {} ticks; "
                        + "Player Animator stopTick {} endTick {} infinite {}; first person {}; camera flags {} ({} rotation keys)",
                p.getGameProfile().getName(), selected, clientTicks, len == null ? "?" : TestWalkerFmt.f(len),
                len == null ? "?" : TestWalkerFmt.f(len * 20), data.stopTick, data.endTick, data.isInfinite,
                firstPerson, m.camera(), a.camRot.size());
    }

    private static final class FrozenPlayer extends KeyframeAnimationPlayer {
        private final float fraction;
        private final float seconds;
        FrozenPlayer(KeyframeAnimation data, double seconds) {
            super(data, (int) Math.floor(seconds * 20));
            this.seconds = (float) seconds;
            fraction = (float) (seconds * 20 - Math.floor(seconds * 20));
        }
        @Override public void tick() { }
        @Override public void setupAnim(float partial) { super.setupAnim(fraction); }
    }

    public static void freeze(Net.Freeze m) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getEntity(m.playerId()) instanceof AbstractClientPlayer p)) return;
        Active a = ACTIVE.get(p.getId());
        if (a == null) return;
        double seconds = Math.min(m.seconds(), Math.max(0, Clips.PLAYER.getOrDefault(a.clip, 0.0) - .001));
        FrozenPlayer frozen = new FrozenPlayer(a.anim.getData(), seconds);
        frozen.setFirstPersonMode(a.anim.getFirstPersonMode(0));
        frozen.setFirstPersonConfiguration(new FirstPersonConfiguration(true, true, true, true));
        a.anim = frozen;
        a.frozen = true;
        layer(p).setAnimation(frozen);
        if (mc.level.getEntity(m.walkerId()) instanceof TestWalker w) w.freezeAt(seconds * 20);
        HoldfastAnimTest.LOG.info("[holdfast-test] freeze {} player {} walker {} at {} seconds ({} ticks), player skin {}",
                a.clip, p.getId(), m.walkerId(), seconds, seconds * 20, p.getModelName());
    }

    private static void loadExtras(Active a) {
        ResourceLocation rl = new ResourceLocation(HoldfastAnimTest.ASSETS, "player_animation_extras/" + a.clip + ".extras.json");
        Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(rl);
        if (res.isEmpty()) {
            HoldfastAnimTest.LOG.warn("[holdfast-test] no extras file {}", rl);
            return;
        }
        try (Reader r = res.get().openAsReader()) {
            JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
            JsonObject anim = root.getAsJsonObject("animations").getAsJsonObject(a.clip);
            if (anim == null) return;
            if (anim.has("timeline")) {
                for (Map.Entry<String, JsonElement> t : anim.getAsJsonObject("timeline").entrySet()) {
                    a.timeline.put(Double.parseDouble(t.getKey()), t.getValue().getAsString());
                }
            }
            JsonObject bones = anim.getAsJsonObject("bones");
            if (bones != null && bones.has("camera") && bones.getAsJsonObject("camera").has("rotation")) {
                for (Map.Entry<String, JsonElement> k : bones.getAsJsonObject("camera").getAsJsonObject("rotation").entrySet()) {
                    JsonElement v = k.getValue();
                    var arr = (v.isJsonObject() ? v.getAsJsonObject().get("post") : v).getAsJsonArray();
                    a.camRot.add(new float[]{Float.parseFloat(k.getKey()), arr.get(0).getAsFloat(), arr.get(1).getAsFloat(), arr.get(2).getAsFloat()});
                }
                a.camRot.sort((x, y) -> Float.compare(x[0], y[0]));
            }
        } catch (Exception ex) {
            HoldfastAnimTest.LOG.error("[holdfast-test] FAIL reading {}: {}", rl, ex.toString());
        }
    }

    static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { ACTIVE.clear(); return; }
        if (mc.isPaused()) return;
        LoadCheck.runOnce();
        clientTicks++;
        ACTIVE.entrySet().removeIf(entry -> {
            Active a = entry.getValue();
            int t = a.anim.getTick();
            for (Map.Entry<Double, String> ev : a.timeline.entrySet()) {
                if (!a.fired.contains(ev.getKey()) && ev.getKey() * 20.0 <= t) {
                    a.fired.add(ev.getKey());
                    HoldfastAnimTest.LOG.info("[holdfast-test] player clip {} timeline '{}' scheduled {} s, reached at animation tick {} ({} s)",
                            a.clip, ev.getValue(), TestWalkerFmt.f(ev.getKey()), t, TestWalkerFmt.f(t / 20.0));
                }
            }
            if (!a.anim.isActive() && !a.finishLogged) {
                a.finishLogged = true;
                Double len = Clips.PLAYER.get(a.clip);
                HoldfastAnimTest.LOG.info("[holdfast-test] player clip {} finished: {} client ticks after start, animation tick {}; exported {} ticks",
                        a.clip, clientTicks - a.startClientTick, t, len == null ? "?" : TestWalkerFmt.f(len * 20));
                return false; // Keep the last clip for /hfa freeze after playback finishes.
            }
            return false;
        });
    }

    static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !mc.options.getCameraType().isFirstPerson()) return;
        Active a = ACTIVE.get(mc.player.getId());
        if (a == null || (a.camera & 1) == 0 || a.camRot.isEmpty() || !a.anim.isActive()) return;
        float time = a.anim instanceof FrozenPlayer frozen ? frozen.seconds : (float) ((a.anim.getTick() + event.getPartialTick()) / 20.0);
        float[] r = sample(a.camRot, time);
        float movement = a.clip.startsWith("move_")
                ? (float) Math.min(1.0, mc.player.getDeltaMovement().horizontalDistance() / 0.12) : 1.0F;
        float pitch = ((a.camera & 8) != 0 ? -r[1] : r[1]) * movement;
        float yaw = ((a.camera & 2) != 0 ? -r[2] : r[2]) * movement;
        float roll = ((a.camera & 4) != 0 ? -r[3] : r[3]) * movement;
        if (movement > 0.4F && clientTicks - a.lastCameraLogTick >= 20) {
            a.lastCameraLogTick = clientTicks;
            HoldfastAnimTest.LOG.info("[holdfast-test] first-person camera clip {} tick {} movement {} pitch {} yaw {} roll {}",
                    a.clip, a.anim.getTick(), TestWalkerFmt.f(movement), TestWalkerFmt.f(pitch),
                    TestWalkerFmt.f(yaw), TestWalkerFmt.f(roll));
        }
        event.setPitch(event.getPitch() + pitch);
        event.setYaw(event.getYaw() + yaw);
        event.setRoll(event.getRoll() + roll);
    }

    private static float[] sample(List<float[]> keys, float t) {
        if (t <= keys.get(0)[0]) return keys.get(0);
        for (int i = 1; i < keys.size(); i++) {
            float[] b = keys.get(i);
            if (t <= b[0]) {
                float[] a = keys.get(i - 1);
                float u = (t - a[0]) / Math.max(1e-6F, b[0] - a[0]);
                return new float[]{t, a[1] + (b[1] - a[1]) * u, a[2] + (b[2] - a[2]) * u, a[3] + (b[3] - a[3]) * u};
            }
        }
        return keys.get(keys.size() - 1);
    }

    /** Small formatter so client code doesn't reach into TestWalker internals. */
    static final class TestWalkerFmt {
        static String f(double v) {
            return String.format(Locale.ROOT, "%.2f", v);
        }
    }
}
