package dev.holdfast.core.rules;

/** Bounded cosmetic burst sizes shared by both particle renderers. No outcome decisions. */
public final class BloodProfile {
    public static final int MAX_BURST = 420;
    private BloodProfile() {}

    public static int count(WeaponClass.Contact contact, boolean kill) {
        return switch (contact) {
            case BLUNT -> kill ? 380 : 260;
            case SLASH -> kill ? 420 : 300;
            case STAB -> kill ? 340 : 220;
            case GUNSHOT -> kill ? 360 : 240;
        };
    }
}
