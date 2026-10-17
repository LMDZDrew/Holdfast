package dev.holdfast.core.rules;

import java.util.List;
import java.util.Set;

/**
 * Bite resolution (decisions.md, design.md §5): an intact bite-rated piece covering the bitten region blocks the
 * bite reliably, including the bite that breaks it; the next bite can penetrate. Ordinary clothing has no bite
 * rating. One penetrating bite infects.
 */
public final class BiteRules {
    private BiteRules() {}

    /**
     * A worn piece. {@code condition} is counted in bites it can still stop; a piece at 0 stays worn (repairable,
     * never despawns) but no longer protects.
     */
    public record Guard(String id, Set<BodyRegion> covers, int condition) {
        public boolean protects(BodyRegion region) {
            return condition > 0 && covers.contains(region);
        }
    }

    public enum Kind { BLOCKED, BLOCKED_AND_BROKE, PENETRATED }

    /** @param guardIndex index of the piece that took the bite, or -1 */
    public record Result(Kind kind, BodyRegion region, int guardIndex, int conditionAfter) {
        public boolean infects() {
            return kind == Kind.PENETRATED;
        }

        public boolean blocked() {
            return kind != Kind.PENETRATED;
        }
    }

    /** The first protecting piece takes the bite (outer layers are listed first by the caller). */
    public static Result resolve(BodyRegion region, List<Guard> worn) {
        for (int i = 0; i < worn.size(); i++) {
            Guard g = worn.get(i);
            if (g.protects(region)) {
                int after = g.condition() - 1;
                return new Result(after == 0 ? Kind.BLOCKED_AND_BROKE : Kind.BLOCKED, region, i, after);
            }
        }
        return new Result(Kind.PENETRATED, region, -1, 0);
    }

    /** Which region a walker bites for each kind of grab (S4): upright grabs go for the neck, ground grabs the leg. */
    public static BodyRegion biteRegion(GrabRules.Kind kind, boolean forearmGuarding) {
        if (kind == GrabRules.Kind.UPRIGHT) return forearmGuarding ? BodyRegion.ARM : BodyRegion.NECK;
        return BodyRegion.LEG;
    }
}
