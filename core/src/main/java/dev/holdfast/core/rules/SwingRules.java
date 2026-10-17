package dev.holdfast.core.rules;

/**
 * Free melee swings (ordinary left-click attacks, and the release of a charged blunt weapon). A click or release only
 * starts a swing; the server resolves it once, at the clip's contact tick from {@link CombatTimings#SWINGS}, against
 * whatever is then in reach (docs/core_combat.md, "Melee contact timing"). Clients play the same clip, so the visual
 * contact and the server's contact come from one generated table.
 */
public final class SwingRules {
    /** Vanilla attack strength at or above this plays (and times) the family's heavy swing. */
    public static final double HEAVY_SWING = 0.95;
    /** Extra reach margin around a walker's box for the blade arc, blocks: a ray test, not swept mesh collision. */
    public static final double ARC_MARGIN = 0.2;

    public enum State { PENDING, RESOLVED, CANCELLED }

    private SwingRules() {}

    /** The clip a weapon class swings; client and server share this mapping. Firearms have none (TaCZ owns them). */
    public static String clipFor(WeaponClass weapon, boolean heavy) {
        return switch (weapon) {
            case KNIFE -> heavy ? "swing_knife_heavy" : "swing_knife";
            case MACHETE -> heavy ? "swing_machete_heavy" : "swing_machete";
            case HATCHET -> heavy ? "swing_hatchet_heavy" : "swing_hatchet";
            case KATANA -> heavy ? "swing_katana_heavy" : "swing_katana";
            case AXE, HAMMER -> heavy ? "swing_axe_overhead" : "swing_axe";
            case BAT -> "swing_bat";               // left click; the heavy bat swing is the charged release
            case NONE, STAFF -> heavy ? "swing_unarmed_heavy" : "swing_unarmed";
            default -> null;
        };
    }

    /** A released blunt charge: full charge swings heavy, or overswings when nothing is in reach at release. */
    public static String chargedClip(double charge, boolean targetInReach) {
        if (charge < WalkerStrikeRules.FULL_CHARGE) return "swing_bat";
        return targetInReach ? "swing_bat_heavy" : "bat_charged_miss";
    }

    public static boolean heavy(double attackStrength) {
        return attackStrength >= HEAVY_SWING;
    }

    /**
     * Ticks from the server receiving the click to resolving contact. The client started its clip about half a round
     * trip earlier, so that much (capped like the grab allowance) is taken off; contact is never before the next tick.
     */
    public static int contactDelay(CombatTimings.Swing swing, int rttMillis) {
        return Math.max(1, swing.contactTick() - GrabRules.latencyAllowanceTicks(rttMillis));
    }

    /** One accepted swing. It resolves at most once, and blocks a new swing until its clip has played. */
    public static final class Session {
        public final CombatTimings.Swing swing;
        public final long startTick;
        public final long contactTick;
        private State state = State.PENDING;

        public Session(CombatTimings.Swing swing, long startTick, int rttMillis) {
            this.swing = swing;
            this.startTick = startTick;
            this.contactTick = startTick + contactDelay(swing, rttMillis);
        }

        public State state() {
            return state;
        }

        /** True exactly once: on the first tick at or after contact while still pending. */
        public boolean contactDue(long now) {
            if (state != State.PENDING || now < contactTick) return false;
            state = State.RESOLVED;
            return true;
        }

        /** Interruption (weapon switch, grab, execution, item use, death). Only a pending swing can be cancelled. */
        public boolean cancel() {
            if (state != State.PENDING) return false;
            state = State.CANCELLED;
            return true;
        }

        /**
         * Clicks during the clip are refused (no queueing); the client doesn't restart an active swing either. A
         * cancelled swing frees the player at once (they switched weapon or were grabbed).
         */
        public boolean blocksNewSwing(long now) {
            return state == State.PENDING || (state == State.RESOLVED && now < startTick + swing.lengthTicks());
        }

        public boolean finished(long now) {
            return !blocksNewSwing(now);
        }
    }
}
