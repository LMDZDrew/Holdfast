package dev.holdfast.core.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JumpPresentationTest {
    @Test void risingHasNoSevenTickCutoffAndApexCannotOscillate() {
        var state = new JumpPresentation();
        assertNull(state.advance(true, 0, false));
        for (int i = 0; i < 12; i++) assertEquals("move_jump", state.advance(false, .1, false));
        assertEquals("move_fall", state.advance(false, 0, false));
        assertEquals("move_fall", state.advance(false, .01, false), "noisy rising sample must not restart takeoff");
    }

    @Test void landingPlaysSixTicksThenAllowsAnotherJump() {
        var state = new JumpPresentation();
        state.advance(true, 0, false);
        state.advance(false, .4, false);
        state.advance(false, -.1, false);
        for (int i = 0; i < 6; i++) assertEquals("move_land", state.advance(true, 0, false));
        assertNull(state.advance(true, 0, false));
        assertEquals("move_jump", state.advance(false, .4, false));
    }

    @Test void spawnAndSingleOffGroundSampleDoNotLand() {
        var state = new JumpPresentation();
        assertNull(state.advance(true, 0, false));
        assertEquals("move_fall", state.advance(false, -.02, false));
        assertNull(state.advance(true, 0, false));
    }

    @Test void flightWaterOrClimbingClearsAirborneState() {
        var state = new JumpPresentation();
        state.advance(false, -.1, false);
        state.advance(false, -.2, false);
        for (int i = 0; i < 20; i++) assertNull(state.advance(false, -.2, true));
        assertNull(state.advance(true, 0, false), "no phantom landing when leaving an excluded motion mode");
    }

    @Test void steppingOffLedgeStartsFallingAndJumpCanInterruptLanding() {
        var state = new JumpPresentation();
        state.advance(true, 0, false);
        assertEquals("move_fall", state.advance(false, -.01, false));
        state.advance(false, -.1, false);
        assertEquals("move_land", state.advance(true, 0, false));
        assertEquals("move_jump", state.advance(false, .42, false));
    }
}
