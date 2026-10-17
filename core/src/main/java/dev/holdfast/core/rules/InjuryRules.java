package dev.holdfast.core.rules;

import java.util.Collection;

/**
 * Injury-limited movement (S13; decisions.md "Injury movement speed"). The server applies the multiplier; the
 * client picks the locomotion clip from the synced state ({@link Movement#clip}). The worst applicable state wins.
 */
public final class InjuryRules {
    public enum Movement {
        NORMAL(1.0, null, true),
        ARM_INJURED(0.95, "move_arm_injured", true),
        LIMP(0.85, "move_limp", true),
        PROSTHESIS(0.80, "move_prosthesis", false),
        CRUTCH_MEDICAL(0.60, "move_crutch_medical", false),
        LIMP_SEVERE(0.55, "move_limp_severe", false),
        CRUTCH_IMPROVISED(0.45, "move_crutch_improvised", false),
        /** Played inside vanilla's crawling pose, which already moves at 0.3 of walking. */
        LEGLESS_CRAWL(0.20, "move_crawl_legless", false);

        public final double speed;
        public final String clip;
        public final boolean sprintAllowed;

        Movement(double speed, String clip, boolean sprintAllowed) {
            this.speed = speed;
            this.clip = clip;
            this.sprintAllowed = sprintAllowed;
        }

        /** Multiplier for the movement-speed attribute (MULTIPLY_TOTAL amount = value - 1). */
        public double attributeMultiplier() {
            return this == LEGLESS_CRAWL ? speed / 0.3 : speed;
        }

        /** The TaCZ-safe variant while a TaCZ gun is held: legs and body only, TaCZ keeps arms/items/ADS. */
        public String clip(boolean holdingTaczGun) {
            if (clip == null) return null;
            boolean hasGunVariant = this == ARM_INJURED || this == LIMP || this == LIMP_SEVERE || this == PROSTHESIS;
            return holdingTaczGun && hasGunVariant ? clip + "_gun" : clip;
        }
    }

    private InjuryRules() {}

    public static Movement worst(Collection<Movement> states) {
        Movement w = Movement.NORMAL;
        for (Movement m : states) if (m.speed < w.speed) w = m;
        return w;
    }
}
