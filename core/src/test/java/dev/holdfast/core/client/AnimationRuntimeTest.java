package dev.holdfast.core.client;

import com.google.gson.JsonParser;
import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.AnimationStack;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.data.gson.GeckoLibSerializer;
import dev.kosmx.playerAnim.core.util.Vec3f;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class AnimationRuntimeTest {
    @Test void ordinaryClicksDoNotSwivelTheTorsoInPlayerAnimator() throws Exception {
        for(var timing:dev.holdfast.core.rules.CombatTimings.SWINGS) {
            if(!timing.clip().startsWith("swing_")) continue; // charged overswing is a separate action
            var player=new MeleeActionPlayer(clip(timing.clip()));
            for(int tick=0;tick<timing.lengthTicks();tick++) {
                for(int frame=0;frame<8;frame++) {
                    float partial=frame/8f;
                    player.setupAnim(partial);
                    Vec3f body=player.get3DTransform("body",TransformType.ROTATION,partial,Vec3f.ZERO);
                    assertEquals(0,body.getY(),.0002,timing.clip()+" body yaw at "+tick);
                    assertEquals(0,body.getZ(),.0002,timing.clip()+" body roll at "+tick);
                }
                player.tick();
            }
        }
    }

    static KeyframeAnimation clip(String name) throws Exception {
        try (var stream = AnimationRuntimeTest.class.getResourceAsStream("/assets/holdfast/player_animation/" + name + ".json")) {
            assertNotNull(stream, name);
            return GeckoLibSerializer.serialize(JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject()).get(0);
        }
    }

    static double angle(Vec3f a, Vec3f b) {
        var qa = new Quaternionf().rotationZYX(a.getZ(), a.getY(), a.getX());
        var qb = new Quaternionf().rotationZYX(b.getZ(), b.getY(), b.getX());
        return Math.toDegrees(2 * Math.acos(Math.min(1, Math.abs(qa.dot(qb)))));
    }

    private static double recoveryPeak(KeyframeAnimation data, String carry, String part, boolean fixed) throws Exception {
        var stack = new AnimationStack();
        stack.addAnimLayer(97, new KeyframeAnimationPlayer(clip(carry)));
        stack.addAnimLayer(1100, fixed ? new MeleeActionPlayer(data) : new KeyframeAnimationPlayer(data));
        Vec3f prev = null;
        double peak = 0;
        for (int tick = 0; tick <= data.stopTick; tick++) {
            for (int frame = 0; frame < 8; frame++) {
                float partial = frame / 8f;
                stack.setupAnim(partial);
                Vec3f pose = stack.get3DTransform(part, TransformType.ROTATION, partial, Vec3f.ZERO);
                if (prev != null && tick >= data.endTick + 1) peak = Math.max(peak, angle(prev, pose));
                prev = pose;
            }
            stack.tick();
        }
        return peak;
    }

    @Test void carryRecoveryUsingInstalledPlayerAnimator() throws Exception {
        for (String name : new String[]{"swing_knife", "swing_knife_heavy", "swing_axe", "swing_axe_overhead"}) {
            String carry = name.contains("knife") ? "hold_knife" : "hold_axe";
            var data = clip(name);
            var stack = new AnimationStack();
            var action = new ModifierLayer<IAnimation>();
            stack.addAnimLayer(97, new KeyframeAnimationPlayer(clip(carry)));
            stack.addAnimLayer(1100, action);
            action.setAnimation(new MeleeActionPlayer(data));
            Vec3f prev = null;
            double recoveryMax = 0;
            for (int tick = 0; tick < data.stopTick + 2; tick++) {
                for (int frame = 0; frame < 4; frame++) {
                    float partial = frame / 4f;
                    stack.setupAnim(partial);
                    Vec3f now = stack.get3DTransform("rightArm", TransformType.ROTATION, partial, Vec3f.ZERO);
                    if (prev != null && tick >= data.endTick - 1) recoveryMax = Math.max(recoveryMax, angle(prev, now));
                    prev = now;
                }
                stack.tick();
            }
            System.out.printf("%s end=%d stop=%d recovery maximum %.2f deg/frame%n", name, data.endTick, data.stopTick, recoveryMax);
            assertTrue(Double.isFinite(recoveryMax));
            assertTrue(recoveryMax < 20, name + " must not snap the arm to carry at clip end");
            // PA normally spends its final three ticks returning enabled channels to vanilla defaults. A live
            // carry has a different wrist frame: release to that lower layer before the action becomes inactive.
            var raw = new KeyframeAnimationPlayer(data);
            var fixed = new MeleeActionPlayer(data);
            var hold = new KeyframeAnimationPlayer(clip(carry));
            for (int i = 0; i < data.endTick + 1; i++) { raw.tick(); fixed.tick(); hold.tick(); }
            for (String part : new String[]{"rightArm", "rightItem", "body", "leftArm"}) {
                raw.setupAnim(0); fixed.setupAnim(0); hold.setupAnim(0);
                Vec3f base = hold.get3DTransform(part, TransformType.ROTATION, 0, Vec3f.ZERO);
                Vec3f old = raw.get3DTransform(part, TransformType.ROTATION, 0, base);
                Vec3f released = fixed.get3DTransform(part, TransformType.ROTATION, 0, base);
                System.out.printf("%s %s end-to-carry old %.2f fixed %.2f deg%n", name, part, angle(old, base), angle(released, base));
                assertTrue(angle(released, base) < .1, name + " " + part + " must release to the live lower layer");
                double oldPeak = recoveryPeak(data, carry, part, false), fixedPeak = recoveryPeak(data, carry, part, true);
                System.out.printf("%s %s implicit tail old %.2f fixed %.2f deg/frame%n", name, part, oldPeak, fixedPeak);
                assertTrue(fixedPeak < 3, name + " " + part + " implicit vanilla tail must not override the live carry");
                // Codex measured the raw-playback wrist spin here at 25.24 deg/frame (knife) with the old blade-down
                // carry. The 2026-10-17 knife ready carry (player_weapons.STANCES) ends the swing near its own wrist
                // frame, so the raw tail no longer wraps; the corrected release above is still asserted for all clips.
                assertTrue(Double.isFinite(oldPeak));
            }
        }
    }

    @Test void rotationFadeCrossesTheWrapWithoutHalfTurn() {
        Vec3f from = new Vec3f(0, (float) Math.toRadians(179), 0);
        Vec3f to = new Vec3f(0, (float) Math.toRadians(-179), 0);
        Vec3f middle = MeleeFade.mix(from, to, .5f, TransformType.ROTATION);
        assertTrue(angle(from, middle) < 1.1);
        assertTrue(angle(to, middle) < 1.1);
        Vec3f position = MeleeFade.mix(new Vec3f(2, 4, 6), new Vec3f(4, 6, 8), .5f, TransformType.POSITION);
        assertEquals(3, position.getX(), .0001);
        assertEquals(5, position.getY(), .0001);
        assertEquals(7, position.getZ(), .0001);
    }

    @Test void realModifierLayerFadesFromLowerLayerWhenPreviouslyEmpty() {
        var layer = new ModifierLayer<IAnimation>();
        Vec3f base = new Vec3f(0, (float) Math.toRadians(170), 0);
        Vec3f target = new Vec3f(0, (float) Math.toRadians(-170), 0);
        IAnimation next = new IAnimation() {
            public boolean isActive() { return true; }
            public void setupAnim(float partial) {}
            public Vec3f get3DTransform(String part, TransformType type, float partial, Vec3f value) { return target; }
        };
        layer.replaceAnimationWithFade(new MeleeFade(2), next, true);
        layer.setupAnim(0);
        assertTrue(angle(base, layer.get3DTransform("rightItem", TransformType.ROTATION, 0, base)) < .1);
        layer.tick(); layer.setupAnim(0);
        Vec3f middle = layer.get3DTransform("rightItem", TransformType.ROTATION, 0, base);
        assertTrue(angle(base, middle) > 9 && angle(base, middle) < 11);
        layer.tick(); layer.setupAnim(0);
        assertTrue(angle(target, layer.get3DTransform("rightItem", TransformType.ROTATION, 0, base)) < .1);
    }

    @Test void inputLockEndsAtServerClipLengthWithoutWaitingForImplicitTail() throws Exception {
        for (var timing : dev.holdfast.core.rules.CombatTimings.SWINGS) {
            var data = clip(timing.clip());
            assertEquals(timing.lengthTicks(), data.endTick + 1, timing.clip());
            var action = new MeleeActionPlayer(data);
            for (int tick = 0; tick < timing.lengthTicks(); tick++) {
                assertTrue(action.blocksInput(), timing.clip() + " unlocked early at " + tick);
                action.tick();
            }
            assertFalse(action.blocksInput(), timing.clip() + " must not add PA's implicit two-tick input delay");
        }
    }
}
