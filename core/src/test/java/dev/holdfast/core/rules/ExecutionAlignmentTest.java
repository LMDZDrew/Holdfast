package dev.holdfast.core.rules;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** All-angle execution alignment (Andrew 2026-10-17). Pure geometry; the world checks are GameTests. */
class ExecutionAlignmentTest {
    private static double wrap(double d) {
        d %= 360;
        if (d > 180) d -= 360;
        if (d < -180) d += 360;
        return d;
    }

    /** Where the player clip strikes, in world blocks: the front pair's skull point in front of the player. */
    private static double[] strikePoint(CombatTimings.Exec clip, ExecutionRules.Alignment a) {
        double[] front = CombatTimings.EXEC_SKULL.get(clip.targetClip());
        double sx = -front[0], sz = -clip.distPx() - front[1];
        double r = Math.toRadians(a.playerYaw());
        double rightX = -Math.cos(r), rightZ = -Math.sin(r), fwdX = -Math.sin(r), fwdZ = Math.cos(r);
        return new double[]{a.playerX() + (sx * rightX - sz * fwdX) / 16, a.playerZ() + (sx * rightZ - sz * fwdZ) / 16};
    }

    private static double[] skullAtImpact(double wx, double wz, ExecutionRules.Alignment a) {
        double[] c = CombatTimings.EXEC_SKULL.get(a.targetClip());
        double[] o = ExecutionRules.toWorld(a.walkerYaw(), c[0], c[1]);
        return new double[]{wx + o[0], wz + o[1]};
    }

    @Test
    void everyApproachPutsTheSkullOnTheStrikePoint() {
        for (CombatTimings.Exec clip : CombatTimings.EXECS) {
            boolean lying = clip.posture() != Posture.UPRIGHT;
            for (int deg = -180; deg < 180; deg += 15) {
                float walkerYaw = 30;
                double r = Math.toRadians(walkerYaw + deg);              // player around the walker, 1.6 blocks out
                double fx = -Math.sin(Math.toRadians(walkerYaw)), fz = Math.cos(Math.toRadians(walkerYaw));
                double px = 1.6 * -Math.sin(r), pz = 1.6 * Math.cos(r);
                var a = ExecutionRules.align(clip, lying, 0, 0, walkerYaw, px, pz).orElseThrow();
                double[] hit = strikePoint(clip, a), skull = skullAtImpact(0, 0, a);
                assertEquals(hit[0], skull[0], 1e-6, clip.playerClip() + " " + deg);
                assertEquals(hit[1], skull[1], 1e-6, clip.playerClip() + " " + deg);
                if (lying) {
                    assertEquals(walkerYaw, a.walkerYaw(), 1e-6, "a lying walker must not turn");
                } else {
                    assertTrue(Math.abs(wrap(a.walkerYaw() - walkerYaw)) <= 55,
                            clip.playerClip() + " walker turned " + wrap(a.walkerYaw() - walkerYaw) + " from " + deg);
                }
                assertTrue(fx * fx + fz * fz > 0.99);
            }
        }
    }

    @Test
    void approachSidesAreNamedFromTheWalker() {
        // walker at the origin facing +z (yaw 0); its left is +x (Minecraft yaw 0 faces south)
        assertEquals(ExecutionRules.Approach.FRONT, ExecutionRules.approach(0, 0, 0, 0, 1.5));
        assertEquals(ExecutionRules.Approach.REAR, ExecutionRules.approach(0, 0, 0, 0, -1.5));
        assertEquals(ExecutionRules.Approach.LEFT, ExecutionRules.approach(0, 0, 0, 1.5, 0));
        assertEquals(ExecutionRules.Approach.RIGHT, ExecutionRules.approach(0, 0, 0, -1.5, 0));
        assertEquals(ExecutionRules.Approach.REAR, ExecutionRules.approach(0, 0, 0, 1.0, -1.2), "diagonal behind");
        assertEquals(ExecutionRules.Approach.FRONT, ExecutionRules.approach(0, 0, 0, 1.0, 1.2), "diagonal in front");
    }

    @Test
    void stepLimitsAndLyingBodies() {
        CombatTimings.Exec knife = ExecutionRules.clipFor(WeaponClass.KNIFE, Posture.UPRIGHT).orElseThrow();
        var far = ExecutionRules.align(knife, false, 0, 0, 0, 0, -4.0).orElseThrow();
        assertEquals(ExecutionRules.Refusal.TOO_FAR, ExecutionRules.checkAlignment(far, false, 180).orElseThrow());
        var near = ExecutionRules.align(knife, false, 0, 0, 0, 0, 0.2).orElseThrow();
        assertEquals(ExecutionRules.Refusal.TOO_CLOSE, ExecutionRules.checkAlignment(near, false, 0).orElseThrow());
        var ok = ExecutionRules.align(knife, false, 0, 0, 0, 0, 1.6).orElseThrow();
        assertTrue(ExecutionRules.checkAlignment(ok, false, 0).isEmpty());
        CombatTimings.Exec ground = ExecutionRules.clipFor(WeaponClass.KNIFE, Posture.PRONE).orElseThrow();
        var overBody = ExecutionRules.align(ground, true, 0, 0, 0, 0, -1.0).orElseThrow();
        assertEquals(ExecutionRules.Refusal.NO_ROOM, ExecutionRules.checkAlignment(overBody, true, 180).orElseThrow());
        var beside = ExecutionRules.align(ground, true, 0, 0, 0, 1.4, 0.8).orElseThrow();
        assertTrue(ExecutionRules.checkAlignment(beside, true, 60).isEmpty());
    }

    @Test
    void everyReactionVariantHasSkullData() {
        for (CombatTimings.Exec clip : CombatTimings.EXECS) {
            assertNotNull(CombatTimings.EXEC_SKULL.get(clip.targetClip()), clip.targetClip());
            if (clip.posture() == Posture.UPRIGHT) {
                for (ExecutionRules.Approach a : ExecutionRules.Approach.values()) {
                    assertNotNull(CombatTimings.EXEC_SKULL.get(clip.targetClip() + a.suffix), clip.targetClip() + a.suffix);
                }
            }
        }
    }
}
