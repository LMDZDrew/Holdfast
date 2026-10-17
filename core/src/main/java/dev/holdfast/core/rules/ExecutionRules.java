package dev.holdfast.core.rules;

import java.util.Optional;

/**
 * Paired executions (S7-S9). The server validates the request, snaps both participants, plays the pair, accepts a
 * cancel only before the clip's commit tick, and applies the brain kill at its own kill tick if the target is still
 * in place. The executor is never invulnerable.
 */
public final class ExecutionRules {
    /** Proposed tolerances (animation.md S7). */
    public static final double DIST_TOLERANCE_PX = 4.0, FACING_TOLERANCE_DEG = 25.0;
    /** Maximum distance at which a request is considered at all, before snapping, px (1.5 blocks beyond pairing). */
    public static final double MAX_APPROACH_PX = 24.0;

    public enum Refusal {
        WEAPON_CANNOT_EXECUTE, NO_CLIP_FOR_POSTURE, TOO_FAR, NOT_FACING, HEAD_COVERED, TARGET_BUSY,
        ALREADY_EXECUTED, BLUNT_NOT_CHARGED
    }

    /** Facts the server measured for this request. */
    public record Request(WeaponClass weapon, Posture targetPosture, double distancePx, double facingErrorDeg,
                          boolean headExposed, boolean targetGrabbingSomeoneElse, boolean targetAlreadyExecuted,
                          double bluntCharge) {}

    private ExecutionRules() {}

    public static Optional<CombatTimings.Exec> clipFor(WeaponClass weapon, Posture posture) {
        for (CombatTimings.Exec e : CombatTimings.EXECS) {
            if (e.posture() == posture && e.weapons().contains(weapon)) return Optional.of(e);
        }
        return Optional.empty();
    }

    /** Empty when the request is valid. Firearms other than TaCZ "pistol" are refused (decisions.md). */
    public static Optional<Refusal> validate(Request r) {
        if (!r.weapon().canExecute) return Optional.of(Refusal.WEAPON_CANNOT_EXECUTE);
        Optional<CombatTimings.Exec> clip = clipFor(r.weapon(), r.targetPosture());
        if (clip.isEmpty()) return Optional.of(Refusal.NO_CLIP_FOR_POSTURE);
        if (r.targetAlreadyExecuted()) return Optional.of(Refusal.ALREADY_EXECUTED);
        if (r.targetGrabbingSomeoneElse()) return Optional.of(Refusal.TARGET_BUSY);
        if (r.distancePx() > clip.get().distPx() + MAX_APPROACH_PX
                || r.distancePx() < clip.get().distPx() - DIST_TOLERANCE_PX) return Optional.of(Refusal.TOO_FAR);
        if (Math.abs(r.facingErrorDeg()) > FACING_TOLERANCE_DEG) return Optional.of(Refusal.NOT_FACING);
        if (!r.headExposed()) return Optional.of(Refusal.HEAD_COVERED);
        // Standing blunt executions are the full-charge swing; on the ground a bat or hammer needs no charge.
        if (r.weapon().blunt() && r.targetPosture() == Posture.UPRIGHT && r.bluntCharge() < WalkerStrikeRules.FULL_CHARGE) {
            return Optional.of(Refusal.BLUNT_NOT_CHARGED);
        }
        return Optional.empty();
    }

    public enum Step { RUNNING, KILL, FAILED_TARGET_MOVED, CANCELLED, DONE }

    public static final class Session {
        public final CombatTimings.Exec clip;
        public final long startTick;
        private boolean cancelled, killed, failed;

        public Session(CombatTimings.Exec clip, long startTick) {
            this.clip = clip;
            this.startTick = startTick;
        }

        public long commitTick() {
            return startTick + clip.commitTick();
        }

        public long killTick() {
            return startTick + clip.killTick();
        }

        public long endTick() {
            return startTick + clip.lengthTicks();
        }

        /** S8: a cancel is accepted only if the server receives it before the commit tick. */
        public boolean cancel(long arrivalTick) {
            if (cancelled || killed || arrivalTick >= commitTick()) return false;
            cancelled = true;
            return true;
        }

        /**
         * Advance to {@code now}. Returns KILL exactly once, on the first tick at or after the kill tick, if the
         * target is still within tolerance of its snapped position and alive.
         */
        public Step tick(long now, boolean targetInPlace) {
            if (cancelled) return Step.CANCELLED;
            if (failed) return Step.FAILED_TARGET_MOVED;
            if (!killed && now >= killTick()) {
                if (!targetInPlace) {
                    failed = true;
                    return Step.FAILED_TARGET_MOVED;
                }
                killed = true;
                return Step.KILL;
            }
            return now >= endTick() ? Step.DONE : Step.RUNNING;
        }

        public boolean committed(long now) {
            return !cancelled && now >= commitTick();
        }
    }
}
