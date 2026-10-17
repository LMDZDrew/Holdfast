package dev.holdfast.core.rules;

/**
 * Server-side grab state machine (S2-S4, S6; design.md §5). A grab gives a brief, readable escape window, not a
 * button-mash. One escape input inside the window (plus a latency allowance) shoves the walker off. At window end
 * the walker bites; a blocked bite keeps the grab and opens a new window; a penetrating bite releases the victim
 * bitten. A teammate's validated kill or shove releases it at any time.
 */
public final class GrabRules {
    public enum Kind { UPRIGHT, PRONE_ANKLE, CRAWLER_ANKLE }

    public enum State { WINDOW, ESCAPED, RELEASED, RELEASED_BITTEN }

    /** Proposed cap on the network latency allowance (animation.md S3): 150 ms. */
    public static final int MAX_LATENCY_TICKS = 3;
    /** Knockback applied by the server on an accepted escape, blocks (S3). */
    public static final double ESCAPE_KNOCKBACK = 0.75;

    private GrabRules() {}

    /** Half the round trip, rounded up to ticks, capped. */
    public static int latencyAllowanceTicks(int rttMillis) {
        int halfTicks = (int) Math.ceil(Math.max(0, rttMillis) / 2.0 / 50.0);
        return Math.min(MAX_LATENCY_TICKS, halfTicks);
    }

    public static final class Session {
        public final Kind kind;
        public final int windowTicks;
        private State state = State.WINDOW;
        private long windowStart;
        private int blockedBites;

        /** @param windowTicks from {@link Difficulty#grabWindowTicks()} plus any accessibility extension */
        public Session(Kind kind, int windowTicks, long startTick) {
            this.kind = kind;
            this.windowTicks = windowTicks;
            this.windowStart = startTick;
        }

        public State state() {
            return state;
        }

        public long windowStart() {
            return windowStart;
        }

        public long windowEnd() {
            return windowStart + windowTicks;
        }

        public int blockedBites() {
            return blockedBites;
        }

        /**
         * An escape input from the victim. {@code arrivalTick} is when the server received it. Accepted only during
         * the current window, extended by the latency allowance; one input per window (later ones are refused).
         */
        public boolean escape(long arrivalTick, int rttMillis) {
            if (state != State.WINDOW) return false;
            if (arrivalTick < windowStart) return false;
            if (arrivalTick > windowEnd() + latencyAllowanceTicks(rttMillis)) return false;
            state = State.ESCAPED;
            return true;
        }

        /** True on the tick the walker should bite (window over, no accepted escape). */
        public boolean biteDue(long now, int rttMillis) {
            return state == State.WINDOW && now > windowEnd() + latencyAllowanceTicks(rttMillis);
        }

        /** Apply the server's bite result. A blocked bite starts the next window at {@code now}. */
        public void bite(BiteRules.Result result, long now) {
            if (state != State.WINDOW) return;
            if (result.blocked()) {
                blockedBites++;
                windowStart = now;
            } else {
                state = State.RELEASED_BITTEN;
            }
        }

        /** A teammate's validated brain kill on the attacker, or an accepted shove (S6). */
        public void releaseByOther() {
            if (state == State.WINDOW) state = State.RELEASED;
        }

        /** Logout or shutdown mid-grab resolves as an escape: no unavoidable bite on reconnect. */
        public void abandon() {
            if (state == State.WINDOW) state = State.ESCAPED;
        }

        public boolean over() {
            return state != State.WINDOW;
        }
    }
}
