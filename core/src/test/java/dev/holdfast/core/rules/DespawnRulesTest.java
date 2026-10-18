package dev.holdfast.core.rules;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DespawnRulesTest {
    private static final DespawnRules.Settings S = new DespawnRules.Settings(96, 1200, 12000, 24);

    @Test
    void walkersGoOnlyAfterStayingOutOfRange() {
        long far = 0;
        for (int i = 0; i < 59; i++) far = DespawnRules.farTicks(far, 200, 20, S);
        assertFalse(DespawnRules.remove(false, false, 200, far, 0, S), "removed before the time ran out");
        far = DespawnRules.farTicks(far, 200, 20, S);
        assertTrue(DespawnRules.remove(false, false, 200, far, 0, S));
        assertEquals(0, DespawnRules.farTicks(far, 40, 20, S), "a player in range resets the count");
        assertFalse(DespawnRules.remove(false, false, 40, 0, 0, S));
    }

    @Test
    void grabbingWalkersStay() {
        assertFalse(DespawnRules.remove(false, true, Double.POSITIVE_INFINITY, 1_000_000, 0, S));
    }

    @Test
    void corpsesGoAfterTheirTimeButNeverInView() {
        assertFalse(DespawnRules.remove(true, false, 50, 0, 11_999, S), "corpse removed early");
        assertTrue(DespawnRules.remove(true, false, 50, 0, 12_000, S));
        assertFalse(DespawnRules.remove(true, false, 10, 0, 1_000_000, S), "corpse removed in front of a player");
        assertTrue(DespawnRules.remove(true, false, 500, 1200, 10, S), "an unvisited corpse lingers forever");
    }
}
