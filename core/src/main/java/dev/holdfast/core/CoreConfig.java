package dev.holdfast.core;

import dev.holdfast.core.rules.Difficulty;
import net.minecraftforge.common.ForgeConfigSpec;

/** Server config (world/serverconfig/holdfast_core-server.toml). Presets from design.md §11. */
public final class CoreConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.EnumValue<Difficulty> DIFFICULTY;
    public static final ForgeConfigSpec.IntValue GRAB_WINDOW_EXTRA_TICKS;
    public static final ForgeConfigSpec.BooleanValue NATURAL_WALKER_SPAWNS;

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
        b.pop();
        SPEC = b.build();
    }

    private CoreConfig() {}

    public static Difficulty difficulty() {
        return DIFFICULTY.get();
    }

    public static int grabWindowTicks() {
        return difficulty().grabWindowTicks() + GRAB_WINDOW_EXTRA_TICKS.get();
    }
}
