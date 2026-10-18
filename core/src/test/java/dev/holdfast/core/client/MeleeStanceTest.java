package dev.holdfast.core.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static dev.holdfast.core.client.MeleeStance.Stance.*;

class MeleeStanceTest {
    @Test void restsUntilCombatThenReadyForTheTimeout() {
        var s = new MeleeStance();
        for (long t = 0; t < 200; t++) assertEquals(REST, s.stance("knife", t), "holding alone must never ready");
        s.combat("knife", 200);
        assertEquals(READY, s.stance("knife", 200));
        assertEquals(READY, s.stance("knife", 200 + MeleeStance.READY_TICKS - 1));
        assertEquals(REST, s.stance("knife", 200 + MeleeStance.READY_TICKS));
    }

    @Test void eachCombatEventRefreshesTheTimer() {
        var s = new MeleeStance();
        s.combat("axe", 0);
        s.combat("axe", 60);
        assertEquals(READY, s.stance("axe", 100));
        assertEquals(REST, s.stance("axe", 60 + MeleeStance.READY_TICKS));
    }

    @Test void switchingWeaponFamilyStartsAtRest() {
        var s = new MeleeStance();
        s.combat("katana", 0);
        assertEquals(REST, s.stance("hatchet", 1));
        assertEquals(REST, s.stance("katana", 2), "switching back must not resurrect the old timer");
        assertEquals(REST, s.stance(null, 3));
    }

    @Test void combatWithAnotherFamilyReadiesThatFamily() {
        var s = new MeleeStance();
        s.combat("knife", 0);
        s.combat("hatchet", 5);
        assertEquals(READY, s.stance("hatchet", 6));
    }
}
