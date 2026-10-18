package dev.holdfast.core.rules;

/** Bounded cosmetic burst sizes shared by both particle renderers. No outcome decisions. */
public final class BloodProfile {
    private BloodProfile() {}

    public static int count(WeaponClass.Contact contact, boolean kill) {
        return switch (contact) {
            case BLUNT -> kill ? 110 : 64;
            case SLASH -> kill ? 120 : 72;
            case STAB -> kill ? 85 : 48;
            case GUNSHOT -> kill ? 18 : 10;
        };
    }
}
