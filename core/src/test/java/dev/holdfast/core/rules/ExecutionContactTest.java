package dev.holdfast.core.rules;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExecutionContactTest {
    @Test void everyAllowedReactionHasFiniteSourceContactAndUnitDirection() {
        for(var clip:CombatTimings.EXECS) {
            verify(clip.targetClip());
            if(clip.posture()==Posture.UPRIGHT)
                for(var side:new String[]{"_left","_right","_rear"}) verify(clip.targetClip()+side);
        }
        assertEquals(36,CombatTimings.EXEC_CONTACT.size());
    }
    private static void verify(String clip) {
        var c=CombatTimings.EXEC_CONTACT.get(clip);
        assertNotNull(c,clip);
        assertTrue(Double.isFinite(c.x()) && Double.isFinite(c.y()) && Double.isFinite(c.z()),clip);
        assertEquals(1,Math.sqrt(c.dx()*c.dx()+c.dy()*c.dy()+c.dz()*c.dz()),1e-5,clip);
        assertEquals(clip.startsWith("react_pistol"),c.hasExit(),clip);
        // A raised/tilted crawler skull can put its rear surface within 4 px along Z; include its height.
        if(clip.contains("_ground")) assertTrue(Math.sqrt(c.x()*c.x()+(c.y()-4.8)*(c.y()-4.8)+c.z()*c.z())>3,
                clip+" must not use the old hips offset");
    }
}
