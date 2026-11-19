package ru.samogon.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Common configuration for Moonshiner. The file lives in {@code config/samogon-common.toml}
 * and is read at runtime, so editing it and restarting (or reloading the config)
 * is enough.
 *
 * <p>Two groups are exposed:</p>
 * <ul>
 *   <li>{@code gameplay} - the drunkenness system: whether a hangover follows and
 *       how long it lasts, and whether a level-V drinker faints;</li>
 *   <li>{@code machines} - one global speed multiplier for every timed machine
 *       (mashing, fermentation, distillation, rectification, filtration,
 *       blending, dilution, aging, infusion, pressing, sparkling and the
 *       brewery).</li>
 * </ul>
 *
 * <p>Accessors return the live value and fall back to a sane default before the
 * config has been loaded, so callers never see a broken number.</p>
 */
public final class SamogonConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue HANGOVER_ENABLED;
    public static final ModConfigSpec.DoubleValue HANGOVER_DURATION_MULTIPLIER;
    public static final ModConfigSpec.BooleanValue BLACKOUT_ENABLED;
    public static final ModConfigSpec.DoubleValue MACHINE_SPEED_MULTIPLIER;
    public static final ModConfigSpec.IntValue FUSEL_BLINDNESS_SECONDS;
    public static final ModConfigSpec.IntValue BANYA_SOAK_SECONDS;
    public static final ModConfigSpec.IntValue BANYA_AFTER_SECONDS;
    public static final ModConfigSpec.DoubleValue BANYA_STEAM_DENSITY;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("gameplay");
        HANGOVER_ENABLED = builder
                .comment("Apply a hangover once a drinking session that reached intoxication level III or higher ends.")
                .define("hangoverEnabled", true);
        HANGOVER_DURATION_MULTIPLIER = builder
                .comment("Scales the hangover duration. 0.0 disables the effect, 0.5 halves it, 2.0 doubles it.")
                .defineInRange("hangoverDurationMultiplier", 1.0D, 0.0D, 10.0D);
        BLACKOUT_ENABLED = builder
                .comment("At intoxication level V the drinker's session ends with a blackout: the screen fades,",
                        "\"you drank too much\" is shown and the drinker comes to somewhere nearby.",
                        "Set to false to keep the top level harmless beyond nausea and vomiting.")
                .define("blackoutEnabled", true);
        FUSEL_BLINDNESS_SECONDS = builder
                .comment("How long the heads/tails fog lasts, in seconds. Milk and honey cannot cure it;",
                        "only time or death removes it. 0 disables the fog (the other technical debuffs stay).")
                .defineInRange("fuselBlindnessSeconds", 300, 0, 3600);
        builder.pop();

        builder.push("banya");
        BANYA_SOAK_SECONDS = builder
                .comment("Seconds of continuous hot steam needed before stepping out grants the after-banya glow.",
                        "The timer only runs while the stones are actually hot.")
                .defineInRange("soakSeconds", 60, 5, 600);
        BANYA_AFTER_SECONDS = builder
                .comment("How long the after-banya regeneration lasts, in seconds. 0 disables the reward.")
                .defineInRange("afterBanyaSeconds", 300, 0, 3600);
        BANYA_STEAM_DENSITY = builder
                .comment("Multiplier for the steam particle count in a closed banya.",
                        "1.0 is the normal cloud, 0.5 halves it, 0 removes the particles (the heat, cure",
                        "and steam veil still work). Useful on low-end clients and busy servers.")
                .defineInRange("steamParticleDensity", 1.0D, 0.0D, 3.0D);
        builder.pop();

        builder.push("machines");
        MACHINE_SPEED_MULTIPLIER = builder
                .comment("Speed of every timed machine (mashing, fermentation, distillation, rectification,",
                        "filtration, blending, dilution, aging, infusion, pressing, sparkling and the brewery).",
                        "1.0 is the vanilla pace, 2.0 runs twice as fast, 0.5 drags at half speed.")
                .defineInRange("speedMultiplier", 1.0D, 0.1D, 20.0D);
        builder.pop();

        SPEC = builder.build();
    }

    private SamogonConfig() {}

    /** Scales a base machine duration given in ticks by the configured speed. */
    public static int machineTicks(int baseTicks) {
        double speed = MACHINE_SPEED_MULTIPLIER.get();
        if (speed <= 0.0D) {
            speed = 1.0D;
        }
        return Math.max(1, (int) Math.round(baseTicks / speed));
    }

    public static boolean hangoverEnabled() {
        return HANGOVER_ENABLED.get();
    }

    public static double hangoverDurationMultiplier() {
        return HANGOVER_DURATION_MULTIPLIER.get();
    }

    public static boolean blackoutEnabled() {
        return BLACKOUT_ENABLED.get();
    }

    /** Ticks of heads/tails fog; zero when the fog is disabled. */
    public static int fuselBlindnessTicks() {
        return FUSEL_BLINDNESS_SECONDS.get() * 20;
    }

    /** Ticks of steam needed for the after-banya reward. */
    public static int banyaSoakTicks() {
        return BANYA_SOAK_SECONDS.get() * 20;
    }

    /** Ticks the after-banya glow lasts; zero disables it. */
    public static int banyaAfterTicks() {
        return BANYA_AFTER_SECONDS.get() * 20;
    }

    public static double banyaSteamDensity() {
        return BANYA_STEAM_DENSITY.get();
    }
}
