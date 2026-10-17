package dev.holdfast.animtest.client;

import dev.holdfast.animtest.Clips;
import dev.holdfast.animtest.HoldfastAnimTest;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.core.animation.Animation;
import software.bernie.geckolib.core.keyframe.BoneAnimation;
import software.bernie.geckolib.loading.object.BakedAnimations;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Once per world join: checks that GeckoLib and Player Animator actually loaded every exported file and clip,
 * that every bone an animation names exists in its model (GeckoLib silently skips missing bones), and that the
 * loaded lengths match the exports. Prints PASS/FAIL lines to the log and a summary in chat.
 */
final class LoadCheck {
    private static boolean done;

    private LoadCheck() {}

    static void reset() {
        done = false;
    }

    static void runOnce() {
        Minecraft mc = Minecraft.getInstance();
        if (done || mc.player == null) return;
        done = true;
        int fails = 0;
        fails += checkGeo("geo/walker.geo.json", "animations/walker.animation.json", "animation.holdfast.walker.", Clips.WALKER);
        fails += checkGeo("geo/walker_crawler.geo.json", "animations/walker_crawler.animation.json", "animation.holdfast.crawler.", Clips.CRAWLER);
        Map<String, KeyframeAnimation> loaded = PlayerAnimationRegistry.getModAnimations(HoldfastAnimTest.ASSETS);
        for (Map.Entry<String, Double> e : Clips.PLAYER.entrySet()) {
            KeyframeAnimation a = loaded.get(e.getKey());
            if (a == null) {
                fails++;
                HoldfastAnimTest.LOG.error("[holdfast-test] FAIL Player Animator did not load holdfast:{}", e.getKey());
                continue;
            }
            int expected = (int) Math.round(e.getValue() * 20.0);
            HoldfastAnimTest.LOG.info("[holdfast-test] {} player clip {}: parts {}, endTick {}, stopTick {}, infinite {} (export {} ticks)",
                    Math.abs(a.endTick - expected) <= 1 ? "PASS" : "CHECK", e.getKey(), enabledParts(a),
                    a.endTick, a.stopTick, a.isInfinite, expected);
        }
        String summary = "[holdfast-test] load check: " + (fails == 0 ? "PASS" : fails + " FAIL") + " (walker " + Clips.WALKER.size()
                + ", crawler " + Clips.CRAWLER.size() + ", player " + Clips.PLAYER.size() + " of " + loaded.size() + " loaded)";
        HoldfastAnimTest.LOG.info(summary);
        mc.player.displayClientMessage(Component.literal(summary), false);
    }

    private static int checkGeo(String geoPath, String animPath, String prefix, Map<String, Double> expected) {
        int fails = 0;
        ResourceLocation geo = new ResourceLocation(HoldfastAnimTest.ASSETS, geoPath);
        ResourceLocation anims = new ResourceLocation(HoldfastAnimTest.ASSETS, animPath);
        BakedGeoModel model = GeckoLibCache.getBakedModels().get(geo);
        BakedAnimations baked = GeckoLibCache.getBakedAnimations().get(anims);
        if (model == null) {
            HoldfastAnimTest.LOG.error("[holdfast-test] FAIL GeckoLib did not load {}", geo);
            return 1;
        }
        if (baked == null) {
            HoldfastAnimTest.LOG.error("[holdfast-test] FAIL GeckoLib did not load {}", anims);
            return 1;
        }
        Set<String> bones = new HashSet<>();
        for (GeoBone b : model.topLevelBones()) collect(b, bones);
        HoldfastAnimTest.LOG.info("[holdfast-test] PASS {} loaded with {} bones: {}", geo, bones.size(), new TreeSet<>(bones));
        for (Map.Entry<String, Double> e : expected.entrySet()) {
            Animation a = baked.getAnimation(prefix + e.getKey());
            if (a == null) {
                fails++;
                HoldfastAnimTest.LOG.error("[holdfast-test] FAIL {} has no animation {}", anims, prefix + e.getKey());
                continue;
            }
            Set<String> missing = new TreeSet<>();
            for (BoneAnimation ba : a.boneAnimations()) if (!bones.contains(ba.boneName())) missing.add(ba.boneName());
            double exportTicks = e.getValue() * 20.0;
            boolean lengthOk = Math.abs(a.length() - exportTicks) < 0.5;
            if (!missing.isEmpty()) fails++;
            HoldfastAnimTest.LOG.info("[holdfast-test] {} {}: length {} ticks (export {}), loop {}, {} bone tracks{}, {} timeline, {} sound",
                    missing.isEmpty() && lengthOk ? "PASS" : "FAIL", e.getKey(), TestFmt.f(a.length()), TestFmt.f(exportTicks),
                    a.loopType(), a.boneAnimations().length, missing.isEmpty() ? "" : " MISSING BONES " + missing,
                    a.keyFrames().customInstructions().length, a.keyFrames().sounds().length);
            if (missing.isEmpty() && !lengthOk) fails++;
        }
        return fails;
    }

    private static Set<String> enabledParts(KeyframeAnimation a) {
        Set<String> out = new TreeSet<>();
        a.getBodyParts().forEach((k, v) -> { if (v.isEnabled()) out.add(k); });
        return out;
    }

    private static void collect(GeoBone b, Set<String> out) {
        out.add(b.getName());
        for (GeoBone c : b.getChildBones()) collect(c, out);
    }

    private static final class TestFmt {
        static String f(double v) {
            return String.format(java.util.Locale.ROOT, "%.2f", v);
        }
    }
}
