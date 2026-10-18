package dev.holdfast.core.client;

import dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;

/** Use one model renderer at a time. FPM renders the animated body; PA's own pass hides the torso. */
class BodyAwarePlayer extends KeyframeAnimationPlayer {
    BodyAwarePlayer(KeyframeAnimation data) { super(data); }
    BodyAwarePlayer(KeyframeAnimation data, int tick) { super(data, tick); }

    @Override public FirstPersonMode getFirstPersonMode(float partial) {
        var requested = super.getFirstPersonMode(partial);
        if (requested == FirstPersonMode.THIRD_PERSON_MODEL
                && net.minecraftforge.fml.ModList.get().isLoaded("firstperson")
                && dev.holdfast.core.compat.firstperson.FirstPersonBody.active()) return FirstPersonMode.NONE;
        return requested;
    }
}
