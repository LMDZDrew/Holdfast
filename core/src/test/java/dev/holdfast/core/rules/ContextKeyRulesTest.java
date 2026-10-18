package dev.holdfast.core.rules;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ContextKeyRulesTest {
    @Test void sharedKeyRoutesExactlyOneContext() {
        assertTrue(ContextKeyRules.owns(true,false,false,true,false)); // rifle grabbed: escape
        assertTrue(ContextKeyRules.owns(false,true,false,true,true)); // pistol execution: cancel
        assertFalse(ContextKeyRules.owns(false,false,true,true,true)); // pistol walker: TaCZ shove
        assertFalse(ContextKeyRules.owns(false,false,true,true,false)); // rifle walker: TaCZ shove
        assertFalse(ContextKeyRules.owns(false,false,false,true,true)); // no walker: TaCZ shove
        assertTrue(ContextKeyRules.owns(false,false,true,false,false)); // knife walker: request execute
    }
}
