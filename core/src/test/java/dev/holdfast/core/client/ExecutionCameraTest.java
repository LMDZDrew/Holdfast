package dev.holdfast.core.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExecutionCameraTest {
    @Test void extremeAuthoredTurnsStayWithinCameraLimits() {
        for (float turn : new float[]{-360, -180, -90, 0, 90, 180, 360}) {
            float[] cue = ExecutionCamera.cue(turn, turn, .5F, 1.5F);
            assertTrue(Math.abs(cue[0]) <= 12);
            assertTrue(Math.abs(cue[1]) <= .75F);
        }
    }

    @Test void enteringAndLeavingActionDoesNotSnapToAnOffset() {
        assertArrayEquals(new float[]{0, 0}, ExecutionCamera.cue(50, 20, 0, 1.5F));
        assertArrayEquals(new float[]{0, 0}, ExecutionCamera.cue(50, 20, 1.5F, 1.5F));
        assertTrue(ExecutionCamera.cue(50, 20, .01F, 1.5F)[0] < .25F);
        assertTrue(ExecutionCamera.cue(50, 20, 1.49F, 1.5F)[0] < .25F);
    }
}
