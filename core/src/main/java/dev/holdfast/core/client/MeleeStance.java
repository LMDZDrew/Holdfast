package dev.holdfast.core.client;

import java.util.Objects;

/**
 * Rest or ready carry for a player's held melee family (Andrew 2026-10-17: weapons should rest naturally out of
 * combat, be ready during combat and for a few seconds after, then rest again). Cosmetic and transient: it never
 * gates an attack, and nothing is saved or sent for it.
 *
 * <p>Combat events refresh READY for {@link #READY_TICKS} client game ticks: an accepted attack attempt (hit or
 * miss, local click or another player's server swing packet), an execution, a grab, a blunt charge. Holding a
 * weapon, mining, item use, looking at walkers and refused clicks do not. Changing to another family (or to no
 * melee family) starts over at REST.
 */
final class MeleeStance {
    /** Provisional tuning default (4 s); Andrew asked for "a few seconds", not a number. */
    static final int READY_TICKS = 80;

    enum Stance { REST, READY }

    private String family;
    private long readyUntil = Long.MIN_VALUE;

    /** A combat event at client game time {@code now}, for the family held then. */
    void combat(String family, long now) {
        select(family);
        readyUntil = now + READY_TICKS;
    }

    Stance stance(String family, long now) {
        select(family);
        return family != null && now < readyUntil ? Stance.READY : Stance.REST;
    }

    private void select(String family) {
        if (!Objects.equals(family, this.family)) {
            this.family = family;
            readyUntil = Long.MIN_VALUE;
        }
    }
}
