package dev.holdfast.core.rules;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RulesTest {
    // ---------------------------------------------------------------- hit regions

    @Test
    void uprightRegionsFollowTheRig() {
        assertEquals(BodyRegion.HEAD, HitRegions.classify(Posture.UPRIGHT, 0, 29, 0));
        assertEquals(BodyRegion.HEAD, HitRegions.classify(Posture.UPRIGHT, 3, 26.0, -3));
        assertEquals(BodyRegion.NECK, HitRegions.classify(Posture.UPRIGHT, 0, 25, 0));
        assertEquals(BodyRegion.TORSO, HitRegions.classify(Posture.UPRIGHT, 0, 18, 0));
        assertEquals(BodyRegion.ARM, HitRegions.classify(Posture.UPRIGHT, 6, 18, 0));
        assertEquals(BodyRegion.LEG, HitRegions.classify(Posture.UPRIGHT, 2, 6, 0));
    }

    @Test
    void lyingWalkersHaveTheirSkullForwardAndLow() {
        // prone: height along the body = 16 - z, so the skull (26..32) is at z = -16..-10
        assertEquals(BodyRegion.HEAD, HitRegions.classify(Posture.PRONE, 0, 4, -13));
        assertEquals(BodyRegion.TORSO, HitRegions.classify(Posture.PRONE, 0, 4, 0));
        assertEquals(BodyRegion.LEG, HitRegions.classify(Posture.PRONE, 0, 3, 10));
        // crawler: 20 - z; skull at z = -12..-6; no legs
        assertEquals(BodyRegion.HEAD, HitRegions.classify(Posture.CRAWLER, 0, 4, -9));
        assertEquals(BodyRegion.TORSO, HitRegions.classify(Posture.CRAWLER, 0, 4, 12));
    }

    // ---------------------------------------------------------------- strikes

    @Test
    void bodyHitsNeverKillWalkers() {
        for (WeaponClass w : WeaponClass.values()) {
            for (BodyRegion r : EnumSet.complementOf(EnumSet.of(BodyRegion.HEAD))) {
                assertNotEquals(WalkerStrikeRules.Outcome.BRAIN_KILL, WalkerStrikeRules.resolve(w, r, 1.0, 1.0), w + " " + r);
            }
        }
    }

    @Test
    void headHitsKillOnlyWithTheRightForce() {
        assertEquals(WalkerStrikeRules.Outcome.BRAIN_KILL, WalkerStrikeRules.resolve(WeaponClass.KNIFE, BodyRegion.HEAD, 1.0, 0));
        assertEquals(WalkerStrikeRules.Outcome.GLANCE, WalkerStrikeRules.resolve(WeaponClass.KNIFE, BodyRegion.HEAD, 0.3, 0));
        assertEquals(WalkerStrikeRules.Outcome.STAGGER, WalkerStrikeRules.resolve(WeaponClass.BAT, BodyRegion.HEAD, 1.0, 0.5));
        assertEquals(WalkerStrikeRules.Outcome.BRAIN_KILL, WalkerStrikeRules.resolve(WeaponClass.BAT, BodyRegion.HEAD, 1.0, 1.0));
        assertEquals(WalkerStrikeRules.Outcome.BRAIN_KILL, WalkerStrikeRules.resolve(WeaponClass.RIFLE, BodyRegion.HEAD, 1.0, 0));
        assertEquals(WalkerStrikeRules.Outcome.STAGGER, WalkerStrikeRules.resolve(WeaponClass.NONE, BodyRegion.HEAD, 1.0, 1.0));
    }

    // ---------------------------------------------------------------- bites

    private static final BiteRules.Guard COLLAR = new BiteRules.Guard("collar", Set.of(BodyRegion.NECK), 2);

    @Test
    void intactGuardBlocksIncludingTheBreakingBite() {
        BiteRules.Result first = BiteRules.resolve(BodyRegion.NECK, List.of(COLLAR));
        assertEquals(BiteRules.Kind.BLOCKED, first.kind());
        BiteRules.Guard worn = new BiteRules.Guard("collar", Set.of(BodyRegion.NECK), first.conditionAfter());
        BiteRules.Result breaking = BiteRules.resolve(BodyRegion.NECK, List.of(worn));
        assertEquals(BiteRules.Kind.BLOCKED_AND_BROKE, breaking.kind());
        assertFalse(breaking.infects());
        BiteRules.Guard broken = new BiteRules.Guard("collar", Set.of(BodyRegion.NECK), breaking.conditionAfter());
        assertTrue(BiteRules.resolve(BodyRegion.NECK, List.of(broken)).infects());
    }

    @Test
    void uncoveredRegionIsBitten() {
        assertTrue(BiteRules.resolve(BodyRegion.LEG, List.of(COLLAR)).infects());
        assertEquals(BodyRegion.LEG, BiteRules.biteRegion(GrabRules.Kind.CRAWLER_ANKLE, false));
        assertEquals(BodyRegion.NECK, BiteRules.biteRegion(GrabRules.Kind.UPRIGHT, false));
    }

    // ---------------------------------------------------------------- infection

    @Test
    void infectionFollowsDifficultyAndAmputationWindow() {
        InfectionRules.Bite arm = new InfectionRules.Bite(BodyRegion.ARM, 1000, Difficulty.DEFAULT);
        assertEquals(1000 + 60 * 60 * 20, arm.endTick());
        assertTrue(InfectionRules.amputationEligible(arm, 1000 + 5 * 60 * 20 - 1));
        assertFalse(InfectionRules.amputationEligible(arm, 1000 + 5 * 60 * 20));
        InfectionRules.Bite neck = new InfectionRules.Bite(BodyRegion.NECK, 0, Difficulty.LOWER);
        assertFalse(InfectionRules.amputationEligible(neck, 1));
        assertEquals(0.5, InfectionRules.progress(neck, 60 * 60 * 20), 1e-9);
        assertTrue(InfectionRules.fatal(new InfectionRules.Bite(BodyRegion.LEG, 0, Difficulty.SEVERE), 30 * 60 * 20));
    }

    // ---------------------------------------------------------------- grabs

    @Test
    void escapeInsideWindowWithLatencyAllowance() {
        assertEquals(0, GrabRules.latencyAllowanceTicks(0));
        assertEquals(1, GrabRules.latencyAllowanceTicks(100));
        assertEquals(2, GrabRules.latencyAllowanceTicks(200));
        assertEquals(3, GrabRules.latencyAllowanceTicks(1000));
        int window = Difficulty.DEFAULT.grabWindowTicks();               // 24 ticks
        GrabRules.Session onTime = new GrabRules.Session(GrabRules.Kind.UPRIGHT, window, 100);
        assertTrue(onTime.escape(100 + window, 0));
        assertEquals(GrabRules.State.ESCAPED, onTime.state());
        GrabRules.Session late = new GrabRules.Session(GrabRules.Kind.UPRIGHT, window, 100);
        assertFalse(late.escape(100 + window + 1, 0));
        GrabRules.Session laggy = new GrabRules.Session(GrabRules.Kind.UPRIGHT, window, 100);
        assertTrue(laggy.escape(100 + window + 2, 200));
        assertFalse(laggy.escape(100 + window + 2, 200), "one input per grab");
    }

    @Test
    void blockedBiteKeepsTheGrabAndPenetratingBiteReleases() {
        GrabRules.Session s = new GrabRules.Session(GrabRules.Kind.UPRIGHT, 24, 0);
        assertFalse(s.biteDue(24, 0));
        assertTrue(s.biteDue(25, 0));
        s.bite(BiteRules.resolve(BodyRegion.NECK, List.of(COLLAR)), 25);
        assertEquals(GrabRules.State.WINDOW, s.state());
        assertEquals(25, s.windowStart());
        s.bite(BiteRules.resolve(BodyRegion.NECK, List.of()), 50);
        assertEquals(GrabRules.State.RELEASED_BITTEN, s.state());
    }

    @Test
    void teammateReleaseAndLogoutNeverBite() {
        GrabRules.Session s = new GrabRules.Session(GrabRules.Kind.PRONE_ANKLE, 32, 0);
        s.releaseByOther();
        assertEquals(GrabRules.State.RELEASED, s.state());
        GrabRules.Session t = new GrabRules.Session(GrabRules.Kind.PRONE_ANKLE, 32, 0);
        t.abandon();
        assertFalse(t.biteDue(1000, 0));
    }

    // ---------------------------------------------------------------- executions

    private static ExecutionRules.Request request(WeaponClass w, Posture p, double dist) {
        return new ExecutionRules.Request(w, p, dist, 0, true, false, false, 1.0);
    }

    @Test
    void onlyPistolsAmongFirearmsCanExecute() {
        double d = ExecutionRules.clipFor(WeaponClass.PISTOL, Posture.UPRIGHT).orElseThrow().distPx();
        assertTrue(ExecutionRules.validate(request(WeaponClass.PISTOL, Posture.UPRIGHT, d)).isEmpty());
        assertTrue(ExecutionRules.validate(request(WeaponClass.PISTOL, Posture.PRONE,
                ExecutionRules.clipFor(WeaponClass.PISTOL, Posture.PRONE).orElseThrow().distPx())).isEmpty());
        for (WeaponClass w : List.of(WeaponClass.RIFLE, WeaponClass.SHOTGUN, WeaponClass.SMG, WeaponClass.SNIPER,
                WeaponClass.MG, WeaponClass.RPG)) {
            assertEquals(ExecutionRules.Refusal.WEAPON_CANNOT_EXECUTE, ExecutionRules.validate(request(w, Posture.UPRIGHT, d)).orElseThrow());
        }
        assertEquals(WeaponClass.PISTOL, WeaponClass.fromTaczType("pistol"));
        assertEquals(WeaponClass.SMG, WeaponClass.fromTaczType("smg"));
        assertFalse(WeaponClass.fromTaczType("something_new").canExecute);
    }

    @Test
    void everyMeleeClassHasClipsForEveryPosture() {
        for (WeaponClass w : List.of(WeaponClass.KNIFE, WeaponClass.AXE, WeaponClass.MACHETE, WeaponClass.HATCHET,
                WeaponClass.KATANA, WeaponClass.BAT, WeaponClass.HAMMER, WeaponClass.PISTOL)) {
            for (Posture p : Posture.values()) {
                assertTrue(ExecutionRules.clipFor(w, p).isPresent(), w + " " + p);
            }
        }
    }

    @Test
    void refusalsAreSpecific() {
        double d = ExecutionRules.clipFor(WeaponClass.KNIFE, Posture.UPRIGHT).orElseThrow().distPx();
        assertEquals(ExecutionRules.Refusal.TOO_FAR, ExecutionRules.validate(request(WeaponClass.KNIFE, Posture.UPRIGHT, d + 30)).orElseThrow());
        assertEquals(ExecutionRules.Refusal.NOT_FACING, ExecutionRules.validate(
                new ExecutionRules.Request(WeaponClass.KNIFE, Posture.UPRIGHT, d, 40, true, false, false, 0)).orElseThrow());
        assertEquals(ExecutionRules.Refusal.TARGET_BUSY, ExecutionRules.validate(
                new ExecutionRules.Request(WeaponClass.KNIFE, Posture.UPRIGHT, d, 0, true, true, false, 0)).orElseThrow());
        assertEquals(ExecutionRules.Refusal.BLUNT_NOT_CHARGED, ExecutionRules.validate(
                new ExecutionRules.Request(WeaponClass.BAT, Posture.UPRIGHT, 28, 0, true, false, false, 0.4)).orElseThrow());
        assertTrue(ExecutionRules.validate(new ExecutionRules.Request(WeaponClass.BAT, Posture.CRAWLER,
                ExecutionRules.clipFor(WeaponClass.BAT, Posture.CRAWLER).orElseThrow().distPx(), 0, true, false, false, 0)).isEmpty());
    }

    @Test
    void cancelOnlyBeforeCommitAndKillOnlyIfTargetStays() {
        CombatTimings.Exec knife = ExecutionRules.clipFor(WeaponClass.KNIFE, Posture.UPRIGHT).orElseThrow();
        ExecutionRules.Session early = new ExecutionRules.Session(knife, 100);
        assertTrue(early.cancel(100 + knife.commitTick() - 1));
        assertEquals(ExecutionRules.Step.CANCELLED, early.tick(100 + knife.killTick(), true));
        ExecutionRules.Session late = new ExecutionRules.Session(knife, 100);
        assertFalse(late.cancel(100 + knife.commitTick()));
        assertEquals(ExecutionRules.Step.RUNNING, late.tick(100 + knife.killTick() - 1, true));
        assertEquals(ExecutionRules.Step.KILL, late.tick(100 + knife.killTick(), true));
        assertNotEquals(ExecutionRules.Step.KILL, late.tick(100 + knife.killTick() + 1, true), "kill once");
        ExecutionRules.Session moved = new ExecutionRules.Session(knife, 0);
        assertEquals(ExecutionRules.Step.FAILED_TARGET_MOVED, moved.tick(knife.killTick(), false));
    }

    @Test
    void killTicksComeAfterCommitAndInsideTheClip() {
        for (CombatTimings.Exec e : CombatTimings.EXECS) {
            assertTrue(e.commitTick() < e.killTick(), e.playerClip());
            assertTrue(e.killTick() <= e.lengthTicks(), e.playerClip());
        }
    }

    // ---------------------------------------------------------------- injury speed

    @Test
    void injurySpeedsMatchTheDecisionRecord() {
        assertEquals(0.85, InjuryRules.Movement.LIMP.speed);
        assertEquals(0.55, InjuryRules.Movement.LIMP_SEVERE.speed);
        assertEquals(0.95, InjuryRules.Movement.ARM_INJURED.speed);
        assertEquals(0.20 / 0.3, InjuryRules.Movement.LEGLESS_CRAWL.attributeMultiplier(), 1e-9);
        assertEquals(InjuryRules.Movement.LIMP_SEVERE,
                InjuryRules.worst(List.of(InjuryRules.Movement.LIMP, InjuryRules.Movement.LIMP_SEVERE, InjuryRules.Movement.ARM_INJURED)));
        assertEquals("move_limp_gun", InjuryRules.Movement.LIMP.clip(true));
        assertEquals("move_crutch_medical", InjuryRules.Movement.CRUTCH_MEDICAL.clip(true));
        assertFalse(InjuryRules.Movement.PROSTHESIS.sprintAllowed);
    }
}
