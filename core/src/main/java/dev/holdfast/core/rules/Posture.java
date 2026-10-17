package dev.holdfast.core.rules;

/** How a walker is lying or standing; decides its hit layout and which execution clips fit it. */
public enum Posture {
    UPRIGHT, PRONE, CRAWLER;

    public boolean onGround() {
        return this != UPRIGHT;
    }
}
