package dev.holdfast.core.rules;

import java.util.Locale;

/**
 * What a weapon can do to a walker's head, and whether it can execute. Melee classes come from the held item
 * (Holdfast items, or vanilla items mapped by tag); firearm classes come from the TaCZ gun index {@code type}.
 */
public enum WeaponClass {
    NONE(Contact.BLUNT, false, false),
    KNIFE(Contact.STAB, true, true),
    MACHETE(Contact.SLASH, true, true),
    HATCHET(Contact.SLASH, true, true),
    AXE(Contact.SLASH, true, true),
    KATANA(Contact.SLASH, true, true),
    BAT(Contact.BLUNT, false, true),
    HAMMER(Contact.BLUNT, false, true),
    STAFF(Contact.BLUNT, false, false),
    PISTOL(Contact.GUNSHOT, true, false),
    RIFLE(Contact.GUNSHOT, true, false),
    SHOTGUN(Contact.GUNSHOT, true, false),
    SMG(Contact.GUNSHOT, true, false),
    SNIPER(Contact.GUNSHOT, true, false),
    MG(Contact.GUNSHOT, true, false),
    RPG(Contact.GUNSHOT, true, false);

    /** Contact family: picks the flesh sound and blood pattern on the client. */
    public enum Contact { BLUNT, SLASH, STAB, GUNSHOT }

    public final Contact contact;
    /** Penetrates the skull on a committed hit without needing a full charge. */
    public final boolean penetrates;
    /** May start a paired execution; firearms are excluded (Andrew, 2026-10-18). */
    public final boolean canExecute;

    WeaponClass(Contact contact, boolean penetrates, boolean canExecute) {
        this.contact = contact;
        this.penetrates = penetrates;
        this.canExecute = canExecute;
    }

    public boolean firearm() {
        return contact == Contact.GUNSHOT;
    }

    public boolean blunt() {
        return contact == Contact.BLUNT;
    }

    /** TaCZ 1.1.8 gun index type ("pistol", "rifle", "shotgun", "smg", "sniper", "mg", "rpg"). Unknown types are
     * treated as rifles: they can kill with a head shot but can never execute. */
    public static WeaponClass fromTaczType(String type) {
        if (type == null) return RIFLE;
        return switch (type.toLowerCase(Locale.ROOT)) {
            case "pistol" -> PISTOL;
            case "shotgun" -> SHOTGUN;
            case "smg" -> SMG;
            case "sniper" -> SNIPER;
            case "mg" -> MG;
            case "rpg" -> RPG;
            default -> RIFLE;
        };
    }
}
