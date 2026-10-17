package dev.holdfast.core.client;

import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.util.Vec3f;

/** Release the action back to the live carry/gait before PA's implicit return to vanilla defaults.
 * Does not retime the clip, server contact, or its input lock. */
final class MeleeActionPlayer extends KeyframeAnimationPlayer {
    MeleeActionPlayer(KeyframeAnimation data) { super(data); }

    boolean blocksInput() { return isActive() && getTick() < getData().endTick + 1; }

    @Override public Vec3f get3DTransform(String part, TransformType type, float partial, Vec3f value) {
        Vec3f pose = super.get3DTransform(part, type, partial, value);
        float length = getData().endTick + 1;
        float recovery = Math.max(0, Math.min(1, (getTick() + partial - (length - 3)) / 3));
        return MeleeFade.mix(pose, value, recovery * recovery * (3 - 2 * recovery), type);
    }
}
