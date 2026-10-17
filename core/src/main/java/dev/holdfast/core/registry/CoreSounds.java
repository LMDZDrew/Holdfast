package dev.holdfast.core.registry;

import dev.holdfast.core.HoldfastCore;
import dev.holdfast.core.rules.WeaponClass;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Sound events under the shared {@code holdfast:} namespace, matching the cue names the exported clips already use.
 * Every event has several original procedural variants (art/audio/gen_sounds.py, assets/holdfast/sounds.json).
 * Contact sounds are separate from swing and gunfire sounds and play only for server-confirmed contact.
 */
public final class CoreSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, HoldfastCore.MODID);

    public static final RegistryObject<SoundEvent> WALKER_GROAN = reg("walker.groan");
    public static final RegistryObject<SoundEvent> WALKER_BREATH = reg("walker.breath");
    public static final RegistryObject<SoundEvent> WALKER_SNARL = reg("walker.snarl");
    public static final RegistryObject<SoundEvent> WALKER_LUNGE = reg("walker.lunge");
    public static final RegistryObject<SoundEvent> WALKER_BITE = reg("walker.bite");
    public static final RegistryObject<SoundEvent> WALKER_DEATH = reg("walker.death");
    public static final RegistryObject<SoundEvent> IMPACT_BLUNT = reg("impact.blunt");
    public static final RegistryObject<SoundEvent> IMPACT_SLASH = reg("impact.slash");
    public static final RegistryObject<SoundEvent> IMPACT_STAB = reg("impact.stab");
    public static final RegistryObject<SoundEvent> IMPACT_GUNSHOT = reg("impact.gunshot");
    public static final RegistryObject<SoundEvent> SKULL_BLUNT = reg("impact.skull_blunt");
    public static final RegistryObject<SoundEvent> SKULL_BLADE = reg("impact.skull_blade");
    public static final RegistryObject<SoundEvent> SKULL_GUNSHOT = reg("impact.skull_gunshot");
    public static final RegistryObject<SoundEvent> SWING_LIGHT = reg("swing.light");
    public static final RegistryObject<SoundEvent> SWING_HEAVY = reg("swing.heavy");
    public static final RegistryObject<SoundEvent> BODY_FALL = reg("body.fall");
    public static final RegistryObject<SoundEvent> BITE_BLOCKED = reg("bite.blocked");

    private CoreSounds() {}

    private static RegistryObject<SoundEvent> reg(String name) {
        // Registered under our mod id, but the sound file entries live in assets/holdfast_core/sounds.json.
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(HoldfastCore.MODID, name)));
    }

    public static SoundEvent contact(WeaponClass.Contact contact, boolean skull) {
        return switch (contact) {
            case BLUNT -> (skull ? SKULL_BLUNT : IMPACT_BLUNT).get();
            case SLASH -> (skull ? SKULL_BLADE : IMPACT_SLASH).get();
            case STAB -> (skull ? SKULL_BLADE : IMPACT_STAB).get();
            case GUNSHOT -> (skull ? SKULL_GUNSHOT : IMPACT_GUNSHOT).get();
        };
    }
}
