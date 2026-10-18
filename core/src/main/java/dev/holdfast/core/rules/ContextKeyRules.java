package dev.holdfast.core.rules;

/** Cosmetic request routing; never execution eligibility or a combat result. */
public final class ContextKeyRules {
    private ContextKeyRules() {}
    public static boolean owns(boolean grabbed,boolean executing,boolean lookingAtWalker,boolean firearm,boolean pistol) {
        return grabbed || executing || lookingAtWalker && !firearm;
    }
}
