package dev.holdfast.core.client;

import dev.holdfast.core.HoldfastCore;
import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.core.util.Vec3f;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import net.minecraft.client.Minecraft;
import net.minecraftforge.event.TickEvent;
import org.joml.Quaternionf;

/**
 * Diagnostic only (Andrew 2026-10-17: weapons "spazzing out during the animations", items and views unspecified).
 * Every rendered frame it reads the local player's composed Player Animator rotation for the arms and held items and
 * logs a line when one turns more than {@link #JUMP_DEG} within a frame at an implausible rate, with the clips on the
 * Holdfast layers and the camera view. It changes nothing; the log tells which clip or transition is responsible.
 */
final class PoseTrace {
    private static final String[] PARTS = {"rightArm", "rightItem", "leftArm", "leftItem", "body", "torso"};
    private static final float JUMP_DEG = 45.0F;
    private static final float RATE_DEG_PER_TICK = 150.0F;
    private static final Quaternionf[] LAST = new Quaternionf[PARTS.length];
    private static double lastTime = -1;
    private static long quietUntil;
    private static int suppressed;

    private PoseTrace() {}

    static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.isPaused()) {
            lastTime = -1;
            return;
        }
        float partial = mc.getFrameTime();
        double now = mc.level.getGameTime() + partial;
        var stack = PlayerAnimationAccess.getPlayerAnimLayer(mc.player);
        double dt = now - lastTime;
        for (int i = 0; i < PARTS.length; i++) {
            Vec3f r = stack.get3DTransform(PARTS[i], TransformType.ROTATION, partial, Vec3f.ZERO);
            Quaternionf q = new Quaternionf().rotationZYX(r.getZ(), r.getY(), r.getX());
            Quaternionf prev = LAST[i];
            LAST[i] = q;
            if (prev == null || lastTime < 0 || dt <= 0 || dt > 2) continue;
            float deg = (float) Math.toDegrees(2 * Math.acos(Math.min(1, Math.abs(prev.dot(q)))));
            if (deg > JUMP_DEG && deg / dt > RATE_DEG_PER_TICK) report(mc, PARTS[i], deg, dt, r);
        }
        lastTime = now;
    }

    private static void report(Minecraft mc, String part, float deg, double dt, Vec3f r) {
        long tick = mc.level.getGameTime();
        if (tick < quietUntil) {
            suppressed++;
            return;
        }
        quietUntil = tick + 10;
        HoldfastCore.LOG.info("[holdfast] pose jump: {} turned {} deg in {} ticks; now ({}, {}, {}) deg; layers {}; view {}{}",
                part, String.format("%.1f", deg), String.format("%.2f", dt),
                String.format("%.1f", Math.toDegrees(r.getX())), String.format("%.1f", Math.toDegrees(r.getY())),
                String.format("%.1f", Math.toDegrees(r.getZ())), ClientClips.describe(mc.player),
                mc.options.getCameraType(), suppressed > 0 ? " (" + suppressed + " more suppressed)" : "");
        suppressed = 0;
    }
}
