package dev.holdfast.core;

import dev.holdfast.core.rules.Difficulty;
import net.minecraftforge.common.ForgeConfigSpec;

/** Server config (world/serverconfig/holdfast_core-server.toml). Presets from design.md §11. */
public final class CoreConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.EnumValue<Difficulty> DIFFICULTY;
    public static final ForgeConfigSpec.IntValue GRAB_WINDOW_EXTRA_TICKS;
    public static final ForgeConfigSpec.BooleanValue NATURAL_WALKER_SPAWNS;
    public static final ForgeConfigSpec.IntValue WALKER_DESPAWN_DISTANCE, WALKER_DESPAWN_SECONDS, CORPSE_DESPAWN_MINUTES,
            CORPSE_HIDDEN_DISTANCE;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("survival");
        DIFFICULTY = b.comment("Preset: LOWER (1.6 s grab window, 120 min bite decline), DEFAULT (1.2 s, 60 min), SEVERE (0.9 s, 30 min)")
                .defineEnum("difficulty", Difficulty.DEFAULT);
        GRAB_WINDOW_EXTRA_TICKS = b.comment("Accessibility: extra ticks added to every grab escape window")
                .defineInRange("grabWindowExtraTicks", 0, 0, 40);
        b.pop();
        b.push("walkers");
        NATURAL_WALKER_SPAWNS = b.comment("Stage 1 test option: spawn walkers where vanilla zombies would (off by default)")
                .define("naturalSpawns", false);
        WALKER_DESPAWN_DISTANCE = b.comment("Despawn: a walker (or corpse) with no player within this many blocks...")
                .defineInRange("despawnDistance", 96, 16, 512);
        WALKER_DESPAWN_SECONDS = b.comment("...for this many seconds is removed (0 = never)")
                .defineInRange("despawnAfterSeconds", 60, 0, 3600);
        CORPSE_DESPAWN_MINUTES = b.comment("Despawn: corpses are removed this many minutes after death (0 = keep)...")
                .defineInRange("corpseDespawnMinutes", 10, 0, 1440);
        CORPSE_HIDDEN_DISTANCE = b.comment("...but only while no player is within this many blocks of them")
                .defineInRange("corpseHiddenDistance", 24, 0, 256);
        b.pop();
        SPEC = b.build();
    }

    private CoreConfig() {}

    public static Difficulty difficulty() {
        return DIFFICULTY.get();
    }

    /** Despawn settings in ticks and blocks; a 0 time disables that removal. */
    public static dev.holdfast.core.rules.DespawnRules.Settings despawn() {
        long walker = WALKER_DESPAWN_SECONDS.get() == 0 ? Long.MAX_VALUE : WALKER_DESPAWN_SECONDS.get() * 20L;
        long corpse = CORPSE_DESPAWN_MINUTES.get() == 0 ? Long.MAX_VALUE : CORPSE_DESPAWN_MINUTES.get() * 1200L;
        return new dev.holdfast.core.rules.DespawnRules.Settings(WALKER_DESPAWN_DISTANCE.get(), walker, corpse,
                CORPSE_HIDDEN_DISTANCE.get());
    }

    public static int grabWindowTicks() {
        return difficulty().grabWindowTicks() + GRAB_WINDOW_EXTRA_TICKS.get();
    }
}
