package dev.holdfast.core.rules;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SwingRulesTest {
    @Test
    void everyMeleeClipHasGeneratedTiming() {
        for (WeaponClass w : WeaponClass.values()) {
            for (boolean heavy : new boolean[]{false, true}) {
                String clip = SwingRules.clipFor(w, heavy);
                if (w.firearm()) {
                    assertNull(clip, w + " must stay TaCZ's");
                    continue;
                }
                CombatTimings.Swing s = CombatTimings.swing(clip);
                assertTrue(s.contactTick() > 0 && s.contactTick() < s.lengthTicks(), clip + " contact inside the clip");
                assertTrue(s.commitTick() <= s.contactTick(), clip + " commits before contact");
            }
        }
        for (String clip : new String[]{"swing_bat", "swing_bat_heavy", "bat_charged_miss"}) assertNotNull(CombatTimings.swing(clip));
    }

    @Test
    void heavyAndChargedChoice() {
        assertTrue(SwingRules.heavy(1.0));
        assertFalse(SwingRules.heavy(0.9));
        assertEquals("swing_knife_heavy", SwingRules.clipFor(WeaponClass.KNIFE, true));
        assertEquals("swing_axe_overhead", SwingRules.clipFor(WeaponClass.HAMMER, true));
        assertEquals("swing_bat", SwingRules.chargedClip(0.5, true));
        assertEquals("swing_bat_heavy", SwingRules.chargedClip(1.0, true));
        assertEquals("bat_charged_miss", SwingRules.chargedClip(1.0, false));
    }

    @Test
    void latencyAllowanceIsCappedAndNeverEarly() {
        CombatTimings.Swing knife = CombatTimings.swing("swing_knife");
        assertEquals(knife.contactTick(), SwingRules.contactDelay(knife, 0));
        assertEquals(knife.contactTick() - 1, SwingRules.contactDelay(knife, 80));
        assertEquals(knife.contactTick() - 3, SwingRules.contactDelay(knife, 2000));
        CombatTimings.Swing fist = new CombatTimings.Swing("t", 0, 2, 9);
        assertEquals(1, SwingRules.contactDelay(fist, 2000));
    }

    @Test
    void oneResolutionThenRecoveryBlocksClicks() {
        CombatTimings.Swing s = CombatTimings.swing("swing_machete_heavy");
        SwingRules.Session session = new SwingRules.Session(s, 100, 0);
        assertFalse(session.contactDue(100 + s.contactTick() - 1));
        assertTrue(session.blocksNewSwing(101));
        assertTrue(session.contactDue(100 + s.contactTick()));
        assertFalse(session.contactDue(100 + s.contactTick() + 1), "resolved twice");
        assertFalse(session.cancel(), "cancelled after contact");
        assertTrue(session.blocksNewSwing(100 + s.lengthTicks() - 1));
        assertFalse(session.blocksNewSwing(100 + s.lengthTicks()));
    }

    @Test
    void cancelBeforeContactNeverResolvesAndFreesThePlayer() {
        SwingRules.Session session = new SwingRules.Session(CombatTimings.swing("swing_knife"), 0, 0);
        assertTrue(session.cancel());
        assertFalse(session.contactDue(1000));
        assertEquals(SwingRules.State.CANCELLED, session.state());
        assertFalse(session.blocksNewSwing(1));
    }
}
