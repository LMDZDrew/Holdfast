package dev.holdfast.core.rules;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BloodProfileTest {
    @Test void allContactsIncludingBulletsAreExaggeratedButBounded() {
        for (var family : WeaponClass.Contact.values()) {
            int hit = BloodProfile.count(family, false), kill = BloodProfile.count(family, true);
            assertTrue(hit >= 220 && kill > hit && kill <= BloodProfile.MAX_BURST);
        }
    }
}
