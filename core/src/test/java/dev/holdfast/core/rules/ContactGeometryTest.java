package dev.holdfast.core.rules;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ContactGeometryTest {
    private static ContactGeometry.Point p(double x,double y,double z) { return new ContactGeometry.Point(x,y,z); }
    private static final ContactGeometry.Box HEAD=new ContactGeometry.Box(-4,26,-4,4,32.5,4);

    @Test void fastBulletCrossesAtBothSurfacesNotAtTickOrigin() {
        var hit=ContactGeometry.segment(p(0,29,-100),p(0,29,100),HEAD).orElseThrow();
        assertEquals(p(0,29,-4),hit.entry());assertEquals(p(0,29,4),hit.exit());assertTrue(hit.crossesExit());
    }
    @Test void reverseAndSideApproachesHitTheCorrespondingFace() {
        assertEquals(p(0,29,4),ContactGeometry.segment(p(0,29,100),p(0,29,-100),HEAD).orElseThrow().entry());
        assertEquals(p(-4,29,0),ContactGeometry.segment(p(-100,29,0),p(100,29,0),HEAD).orElseThrow().entry());
        assertEquals(p(4,29,0),ContactGeometry.segment(p(100,29,0),p(-100,29,0),HEAD).orElseThrow().entry());
    }
    @Test void shortParallelAndZeroSegmentsDoNotInventAnIntersection() {
        assertTrue(ContactGeometry.segment(p(0,29,-100),p(0,29,-5),HEAD).isEmpty());
        assertTrue(ContactGeometry.segment(p(5,29,-100),p(5,29,100),HEAD).isEmpty());
        assertTrue(ContactGeometry.segment(p(0,29,0),p(0,29,0),HEAD).isEmpty());
    }
    @Test void insideStartReconstructsEntryAndDoesNotInventAnUntraversedExit() {
        var hit=ContactGeometry.segment(p(0,29,0),p(0,29,1),HEAD).orElseThrow();
        assertEquals(p(0,29,-4),hit.entry());assertFalse(hit.crossesExit());
    }
    @Test void regionAndContactComeFromTheSameVolume() {
        for(var volume:ContactGeometry.volumes(Posture.UPRIGHT)) {
            var b=volume.box();double x=(b.x0()+b.x1())/2,y=(b.y0()+b.y1())/2;
            var hit=ContactGeometry.trace(Posture.UPRIGHT,p(x,y,-100),p(x,y,100));
            assertEquals(volume.region(),hit.region());assertTrue(b.surface(hit.entry(),1e-7));
        }
    }
    @Test void lyingSkullsAreAheadOfOriginAtGroundHeight() {
        for(var posture:new Posture[]{Posture.PRONE,Posture.CRAWLER}) {
            var head=ContactGeometry.volumes(posture).get(0).box();
            var hit=ContactGeometry.trace(posture,p(0,4,-100),p(0,4,100));
            assertEquals(BodyRegion.HEAD,hit.region());assertTrue(head.surface(hit.entry(),1e-7));
            assertTrue(hit.entry().z()<-6,"blood must not originate at the hips");
        }
    }
    @Test void arcMarginFallbackIsClampedToSkinRatherThanFreeSpace() {
        var hit=ContactGeometry.trace(Posture.UPRIGHT,p(4.4,29,-30),p(4.4,29,30));
        assertEquals(BodyRegion.HEAD,hit.region());assertTrue(HEAD.surface(hit.entry(),1e-7));
        assertEquals(4,hit.entry().x(),1e-7);assertFalse(hit.crossesExit());
    }
    @Test void nearestSurfaceProjectsAnInteriorPointOntoAFace() {
        assertTrue(HEAD.surface(ContactGeometry.nearestSurface(p(0,29,0),HEAD),1e-7));
    }
}
