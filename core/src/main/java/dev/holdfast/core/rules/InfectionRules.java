package dev.holdfast.core.rules;

/**
 * Walker-bite progression (design.md §11): irreversible, no miracle cure; duration by difficulty; eligible limb
 * amputation must begin inside its window; head, neck and torso bites are never eligible. Distinct from ordinary
 * wound infection, which this class does not model.
 */
public final class InfectionRules {
    private InfectionRules() {}

    public record Bite(BodyRegion region, long startTick, Difficulty difficulty) {
        public long endTick() {
            return startTick + difficulty.biteDeclineTicks();
        }

        public long amputationDeadline() {
            return startTick + difficulty.amputationWindowTicks();
        }
    }

    /** 0 at the bite, 1 at death. */
    public static double progress(Bite bite, long now) {
        double p = (double) (now - bite.startTick()) / bite.difficulty().biteDeclineTicks();
        return Math.max(0.0, Math.min(1.0, p));
    }

    public static boolean fatal(Bite bite, long now) {
        return now >= bite.endTick();
    }

    public static boolean amputationEligible(Bite bite, long now) {
        return bite.region().amputable() && now < bite.amputationDeadline();
    }

    /** Ticks left to start an eligible amputation, or 0. */
    public static long amputationTicksLeft(Bite bite, long now) {
        return amputationEligible(bite, now) ? bite.amputationDeadline() - now : 0;
    }
}
