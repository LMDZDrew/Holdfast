package dev.holdfast.core.client;

/** Bounded cosmetic execution cues. Does not move the camera or change the server's aim. */
final class ExecutionCamera {
    private ExecutionCamera() {}

    static float[] cue(float pitch, float roll, float elapsed, float duration) {
        float edge = Math.max(0, Math.min(1, Math.min(elapsed / .12F, (duration - elapsed) / .18F)));
        float envelope = edge * edge * (3 - 2 * edge);
        return new float[]{(float)(12 * Math.tanh(pitch / 24)) * envelope,
                (float)(.75 * Math.tanh(roll / 4)) * envelope};
    }
}
