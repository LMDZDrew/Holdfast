package dev.holdfast.core.client;

/** Transient display state only. Never changes movement or decides whether a player can jump. */
final class JumpPresentation {
    private boolean initialized, grounded, falling;
    private int airTicks, landingTicks;

    String advance(boolean onGround, double verticalMotion, boolean excluded) {
        if (excluded) {
            initialized = false;
            airTicks = landingTicks = 0;
            return null;
        }
        if (!initialized) {
            initialized = true;
            grounded = onGround;
            falling = verticalMotion <= 0.005;
        }
        if (!onGround) {
            if (grounded) falling = verticalMotion <= 0.005;
            else if (verticalMotion <= 0.005) falling = true;
            grounded = false;
            airTicks++;
            landingTicks = 0;
            return falling ? "move_fall" : "move_jump";
        }
        // Ignore a single off-ground sample (steps/network jitter); play all six authored landing ticks.
        if (!grounded && airTicks >= 2) landingTicks = 6;
        grounded = true;
        airTicks = 0;
        if (landingTicks > 0) {
            landingTicks--;
            return "move_land";
        }
        return null;
    }
}
