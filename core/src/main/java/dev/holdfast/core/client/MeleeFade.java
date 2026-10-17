package dev.holdfast.core.client;

import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier;
import dev.kosmx.playerAnim.core.util.Vec3f;

/** Player Animator's standard fade lerps wrapped Euler values through zero, e.g. 179 to -179 degrees.
 * Use the nearby representation during melee transitions; translation remains linear. */
final class MeleeFade extends AbstractFadeModifier {
    MeleeFade(int ticks) { super(ticks); }

    @Override protected float getAlpha(String part, TransformType type, float progress) {
        return (float) (0.5 - 0.5 * Math.cos(Math.PI * Math.max(0, Math.min(1, progress))));
    }

    static Vec3f mix(Vec3f from, Vec3f to, float alpha, TransformType type) {
        if (type != TransformType.ROTATION) return from.scale(1 - alpha).add(to.scale(alpha));
        return new Vec3f(turn(from.getX(), to.getX(), alpha), turn(from.getY(), to.getY(), alpha),
                turn(from.getZ(), to.getZ(), alpha));
    }

    private static float turn(float from, float to, float alpha) {
        float delta = (float) Math.atan2(Math.sin(to - from), Math.cos(to - from));
        return from + delta * alpha;
    }

    @Override public Vec3f get3DTransform(String part, TransformType type, float partial, Vec3f value) {
        Vec3f to = super.anim == null ? value : super.anim.get3DTransform(part, type, partial, value);
        Vec3f from = beginAnimation == null || !beginAnimation.isActive() ? value
                : beginAnimation.get3DTransform(part, type, partial, value);
        return mix(from, to, getAlpha(part, type, calculateProgress(partial)), type);
    }
}
