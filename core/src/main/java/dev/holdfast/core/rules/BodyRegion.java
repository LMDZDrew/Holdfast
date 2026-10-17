package dev.holdfast.core.rules;

/** Regions shared by walker hit tests, player bites and wounds. */
public enum BodyRegion {
    HEAD, NECK, TORSO, ARM, LEG;

    /** design.md §11: head, neck and torso bites can never be amputated. */
    public boolean amputable() {
        return this == ARM || this == LEG;
    }
}
