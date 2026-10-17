package dev.holdfast.core.rules;

/**
 * Classifies a hit point on a walker into a body region, from the exported rig geometry (animation.md):
 * skull 26-32 px above the feet, jaw 24-26, torso 12-24 (arms beside it beyond |x| = 4), legs below 12.
 *
 * <p>Coordinates are walker-local pixels: x across the body (sign unused), y up from the feet, z along its
 * facing (negative = in front). A prone walker and a crawler lie face down along z with the head forward
 * (root pitched 90 deg, root offset 16 px and 20 px back: "height along the body" = offset - z), so their skull is
 * low and in front. The lying layout is approximate until real prone/crawler hitboxes exist (handoff open issue).
 */
public final class HitRegions {
    public static final double SKULL_BOTTOM = 26.0, SKULL_TOP = 32.5, JAW_BOTTOM = 24.0, TORSO_BOTTOM = 12.0;
    public static final double HALF_TORSO_WIDTH = 4.0;
    public static final double PRONE_OFFSET = 16.0, CRAWLER_OFFSET = 20.0;
    /** A lying body is about 8 px thick; above that is air, not a hit. */
    public static final double LYING_THICKNESS = 9.0;

    private HitRegions() {}

    public static BodyRegion classify(Posture posture, double x, double y, double z) {
        double along;
        if (posture == Posture.UPRIGHT) {
            along = y;
        } else {
            along = (posture == Posture.PRONE ? PRONE_OFFSET : CRAWLER_OFFSET) - z;
        }
        if (along >= SKULL_BOTTOM) return BodyRegion.HEAD;
        if (along >= JAW_BOTTOM) return BodyRegion.NECK;
        if (along >= TORSO_BOTTOM) {
            // Lying walkers' arms are splayed beside the head, so only upright arms are separated by width.
            return posture == Posture.UPRIGHT && Math.abs(x) > HALF_TORSO_WIDTH ? BodyRegion.ARM : BodyRegion.TORSO;
        }
        return posture == Posture.CRAWLER ? BodyRegion.TORSO : BodyRegion.LEG;
    }

    /** True if a walker-local point is on the brain itself (used for gunshots that report their own position). */
    public static boolean isSkull(Posture posture, double x, double y, double z) {
        return classify(posture, x, y, z) == BodyRegion.HEAD;
    }
}
