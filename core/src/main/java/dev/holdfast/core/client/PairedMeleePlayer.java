package dev.holdfast.core.client;

import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.util.Vec3f;

/** Paired clips finish on their actual ready carry. Yield to that layer before PA adds a default wrist return. */
final class PairedMeleePlayer extends BodyAwarePlayer {
    PairedMeleePlayer(KeyframeAnimation data) { super(data); }

    @Override public Vec3f get3DTransform(String part, TransformType type, float partial, Vec3f value) {
        return getTick() + partial >= getData().endTick ? value : super.get3DTransform(part, type, partial, value);
    }
}
