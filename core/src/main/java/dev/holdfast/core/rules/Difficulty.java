package dev.holdfast.core.rules;

/** design.md §11 presets. Times are real minutes/seconds, counted on server ticks (20 per second). */
public enum Difficulty {
    LOWER(1.6, 120, 10),
    DEFAULT(1.2, 60, 5),
    SEVERE(0.9, 30, 3);

    public final double grabWindowSeconds;
    public final int biteDeclineMinutes;
    public final int amputationWindowMinutes;

    Difficulty(double grabWindowSeconds, int biteDeclineMinutes, int amputationWindowMinutes) {
        this.grabWindowSeconds = grabWindowSeconds;
        this.biteDeclineMinutes = biteDeclineMinutes;
        this.amputationWindowMinutes = amputationWindowMinutes;
    }

    public int grabWindowTicks() {
        return (int) Math.round(grabWindowSeconds * 20);
    }

    public long biteDeclineTicks() {
        return biteDeclineMinutes * 60L * 20L;
    }

    public long amputationWindowTicks() {
        return amputationWindowMinutes * 60L * 20L;
    }
}
