package dev.holdfast.core.rules;

/**
 * What one confirmed hit does to a walker (decisions.md): walkers die only by brain destruction; body impacts
 * can stagger but never accumulate lethal damage; a charged blunt swing can kill with one accurate head hit.
 */
public final class WalkerStrikeRules {
    /** Vanilla attack strength (cooldown) below this is a glancing tap, not a committed swing. */
    public static final double COMMITTED_SWING = 0.8;
    /** A blunt weapon needs this server-measured charge fraction to break the skull (S10). */
    public static final double FULL_CHARGE = 0.95;
    /** Below this a body hit doesn't even stagger. */
    public static final double STAGGER_SWING = 0.5;

    public enum Outcome { BRAIN_KILL, STAGGER, GLANCE }

    private WalkerStrikeRules() {}

    /**
     * @param swing  vanilla attack strength 0..1 for melee; 1 for gunshots
     * @param charge server-measured blunt charge 0..1 (0 when the weapon isn't charged)
     */
    public static Outcome resolve(WeaponClass weapon, BodyRegion region, double swing, double charge) {
        if (region == BodyRegion.HEAD) {
            if (weapon.firearm()) return Outcome.BRAIN_KILL;
            if (weapon.penetrates && swing >= COMMITTED_SWING) return Outcome.BRAIN_KILL;
            if (weapon.blunt() && weapon != WeaponClass.NONE && charge >= FULL_CHARGE && swing >= COMMITTED_SWING) {
                return Outcome.BRAIN_KILL;
            }
            return swing >= STAGGER_SWING ? Outcome.STAGGER : Outcome.GLANCE;
        }
        if (weapon.firearm()) return Outcome.STAGGER;
        return swing >= STAGGER_SWING ? Outcome.STAGGER : Outcome.GLANCE;
    }
}
