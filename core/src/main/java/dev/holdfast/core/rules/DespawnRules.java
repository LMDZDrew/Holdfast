package dev.holdfast.core.rules;

/**
 * Walker and corpse despawning (Andrew 2026-10-17: remove walkers to avoid lag). Only loaded walkers are counted;
 * vanilla's own despawning stays off (walkers are persistent), so nothing vanishes on chunk unload.
 *
 * <ul>
 * <li>A living walker is removed once no player has been within {@code walkerDistance} blocks for
 * {@code walkerAfterTicks}. A player coming back in range resets the count. One that is grabbing someone stays.</li>
 * <li>A corpse is removed {@code corpseAfterTicks} after death, but only while no player is within
 * {@code corpseHiddenDistance} blocks, so it never disappears in front of anyone. A corpse nobody is near
 * (beyond {@code walkerDistance}) for {@code walkerAfterTicks} also goes.</li>
 * </ul>
 */
public final class DespawnRules {
    public record Settings(double walkerDistance, long walkerAfterTicks, long corpseAfterTicks, double corpseHiddenDistance) {}

    private DespawnRules() {}

    /** Far-away time after a check {@code step} ticks long: grows while no player is in range, else resets. */
    public static long farTicks(long previous, double nearestPlayer, long step, Settings s) {
        return nearestPlayer > s.walkerDistance() ? previous + step : 0;
    }

    public static boolean remove(boolean corpse, boolean busy, double nearestPlayer, long farTicks, long corpseAgeTicks, Settings s) {
        if (busy) return false;
        if (farTicks >= s.walkerAfterTicks()) return true;
        return corpse && corpseAgeTicks >= s.corpseAfterTicks() && nearestPlayer > s.corpseHiddenDistance();
    }
}
