package dev.holdfast.core.rules;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BloodProfileTest {
    @Test void meleeIsSubstantialAndGunshotsModestWithFinitePerHitBudget() {
        for (var family : WeaponClass.Contact.values()) {
            int hit = BloodProfile.count(family, false), kill = BloodProfile.count(family, true);
            assertTrue(hit > 0 && kill > hit && kill <= 120);
            if (family != WeaponClass.Contact.GUNSHOT) {
                assertTrue(hit >= 48);
                assertTrue(hit > BloodProfile.count(WeaponClass.Contact.GUNSHOT, true) * 2);
            } else assertTrue(kill <= 18);
        }
    }
}
