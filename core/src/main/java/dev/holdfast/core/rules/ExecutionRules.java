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

    /** Furthest the player is stepped back toward their slot, px; a bigger step back is TOO_CLOSE. */
    public static final double MAX_STEP_BACK_PX = 8.0;
    /** A lying walker is finished from in front of or beside its head, never from over its body. */
    public static final double MAX_GROUND_APPROACH_DEG = 120.0;

    public enum Refusal {
        WEAPON_CANNOT_EXECUTE, NO_CLIP_FOR_POSTURE, TOO_FAR, NOT_FACING, HEAD_COVERED, TARGET_BUSY,
        ALREADY_EXECUTED, BLUNT_NOT_CHARGED, TOO_CLOSE, OBSTRUCTED, NO_ROOM
    }

    /** Which side of an upright walker the player came from; each has its own walker reaction. */
    public enum Approach {
        FRONT(180, ""), LEFT(90, "_left"), RIGHT(270, "_right"), REAR(0, "_rear");

        /** Yaw of the walker in the pair frame (art/characters/source/placement.py, fk.entity), degrees. */
        public final double pairYaw;
        public final String suffix;

        Approach(double pairYaw, String suffix) {
            this.pairYaw = pairYaw;
            this.suffix = suffix;
        }
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
        if (Math.abs(r.facingErrorDeg()) > FACING_TOLERANCE_DEG) return Optional.of(Refusal.NOT_FACING);
        if (!r.headExposed()) return Optional.of(Refusal.HEAD_COVERED);
        // Standing blunt executions are the full-charge swing; on the ground a bat or hammer needs no charge.
        if (r.weapon().blunt() && r.targetPosture() == Posture.UPRIGHT && r.bluntCharge() < WalkerStrikeRules.FULL_CHARGE) {
            return Optional.of(Refusal.BLUNT_NOT_CHARGED);
        }
        return Optional.empty();
    }

    // ------------------------------------------------------------------ alignment from any approach

    /**
     * Where the pair goes. Minecraft coordinates (blocks, yaw in degrees, facing = (-sin yaw, cos yaw)). The walker
     * never moves; an upright one turns at most ~45 degrees to its nearest authored approach, a lying one not at all.
     * The player steps along their own approach line to the slot that puts the walker's skull where the player clip
     * strikes at impact. {@code stepAlongPx} is that step toward the walker (negative: a step back).
     */
    public record Alignment(Approach approach, String targetClip, double playerX, double playerZ, float playerYaw,
                            float walkerYaw, double stepPx, double stepAlongPx) {}

    /** Degrees from the walker's facing to the direction of the player: 0 in front, +90 on its left. */
    public static double approachAngle(double walkerYaw, double wx, double wz, double px, double pz) {
        double r = Math.toRadians(walkerYaw);
        double fx = -Math.sin(r), fz = Math.cos(r);           // walker facing
        double dx = px - wx, dz = pz - wz;
        return Math.toDegrees(Math.atan2(dx * fz - dz * fx, dx * fx + dz * fz));
    }

    /** The side of an upright walker the player stands on (diagonals go to the nearer side). */
    public static Approach approach(double walkerYaw, double wx, double wz, double px, double pz) {
        double ang = approachAngle(walkerYaw, wx, wz, px, pz);
        if (Math.abs(ang) <= 45) return Approach.FRONT;
        if (Math.abs(ang) >= 135) return Approach.REAR;
        return ang > 0 ? Approach.LEFT : Approach.RIGHT;
    }

    /**
     * Pair-frame offset (px; x = player's right, z = backward) of the walker origin, for a walker at pair yaw
     * {@code theta} whose reaction puts its skull at {@code skull}, so that skull meets the front pair's strike point.
     */
    static double[] originOffset(CombatTimings.Exec clip, double[] frontSkull, double[] skull, double theta) {
        double sx = -frontSkull[0], sz = -clip.distPx() - frontSkull[1];         // front pair: walker yaw 180
        double c = Math.cos(Math.toRadians(theta)), s = Math.sin(Math.toRadians(theta));
        return new double[]{sx - (c * skull[0] + s * skull[1]), sz - (-s * skull[0] + c * skull[1])};
    }

    /** A walker-local point (px; the walker faces local -z) as a Minecraft world offset in blocks. */
    static double[] toWorld(double walkerYaw, double lx, double lz) {
        double r = Math.toRadians(walkerYaw);
        double fx = -Math.sin(r), fz = Math.cos(r);         // forward (local -z)
        double ox = -fz, oz = fx;                           // local +x, matching the pair-frame rotation below
        return new double[]{(lx * ox - lz * fx) / 16.0, (lx * oz - lz * fz) / 16.0};
    }

    /**
     * Align a request. Lying walkers keep their yaw; upright ones use the nearest authored approach. Empty if the
     * generated skull table has no entry for the clip. Obstruction and room are the caller's (world) checks.
     */
    public static Optional<Alignment> align(CombatTimings.Exec clip, boolean lying, double wx, double wz, float walkerYaw,
                                            double px, double pz) {
        if (lying) return alignAs(clip, Approach.FRONT, true, wx, wz, walkerYaw, px, pz);
        // The authored approach that needs the smallest walker turn (the slot's own bearing shifts the sector edges).
        Alignment best = null;
        for (Approach ap : Approach.values()) {
            Alignment a = alignAs(clip, ap, false, wx, wz, walkerYaw, px, pz).orElse(null);
            if (a != null && (best == null || Math.abs(turn(a, walkerYaw)) < Math.abs(turn(best, walkerYaw)))) best = a;
        }
        return Optional.ofNullable(best);
    }

    /** Degrees the walker turns for an alignment, -180..180. */
    public static double turn(Alignment a, float walkerYaw) {
        double d = (a.walkerYaw() - walkerYaw) % 360;
        return d > 180 ? d - 360 : d < -180 ? d + 360 : d;
    }

    private static Optional<Alignment> alignAs(CombatTimings.Exec clip, Approach ap, boolean lying, double wx, double wz,
                                               float walkerYaw, double px, double pz) {
        String target = clip.targetClip() + (lying ? "" : ap.suffix);
        double[] front = CombatTimings.EXEC_SKULL.get(clip.targetClip()), skull = CombatTimings.EXEC_SKULL.get(target);
        if (front == null || skull == null) return Optional.empty();
        // the player approaches along the line to the walker (upright) or to its skull (lying)
        double hx = wx, hz = wz;
        if (lying) {
            double[] h = toWorld(walkerYaw, skull[0], skull[1]);
            hx += h[0];
            hz += h[1];
        }
        double psi0 = Math.toDegrees(Math.atan2(-(hx - px), hz - pz));       // yaw along the approach line
        double[] slot;                  // what must sit in front of the player, pair frame px: walker origin or skull
        if (lying) {
            slot = new double[]{-front[0], -clip.distPx() - front[1]};          // the front pair's strike point
        } else {
            slot = originOffset(clip, front, skull, ap.pairYaw);
        }
        double psi = psi0 - Math.toDegrees(Math.atan2(slot[0], -slot[1]));      // slot lands on the approach line
        float newWalkerYaw = lying ? walkerYaw : (float) (psi - ap.pairYaw);
        double r = Math.toRadians(psi);
        double rightX = -Math.cos(r), rightZ = -Math.sin(r), fwdX = -Math.sin(r), fwdZ = Math.cos(r);
        double offX = (slot[0] * rightX - slot[1] * fwdX) / 16.0, offZ = (slot[0] * rightZ - slot[1] * fwdZ) / 16.0;
        double nx = hx - offX, nz = hz - offZ;
        double r0 = Math.toRadians(psi0);
        double along = ((nx - px) * -Math.sin(r0) + (nz - pz) * Math.cos(r0)) * 16.0;
        return Optional.of(new Alignment(ap, target, nx, nz, (float) psi, newWalkerYaw, Math.hypot(nx - px, nz - pz) * 16.0, along));
    }

    /** Refusal for an alignment needing too long a step, a big step back, or an approach over a lying body. */
    public static Optional<Refusal> checkAlignment(Alignment a, boolean lying, double approachAngleDeg) {
        if (lying && Math.abs(approachAngleDeg) > MAX_GROUND_APPROACH_DEG) return Optional.of(Refusal.NO_ROOM);
        if (a.stepAlongPx() < -MAX_STEP_BACK_PX) return Optional.of(Refusal.TOO_CLOSE);
        if (a.stepPx() > MAX_APPROACH_PX) return Optional.of(Refusal.TOO_FAR);
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
