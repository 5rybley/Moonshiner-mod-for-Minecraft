package ru.samogon.process;

import java.util.Locale;

/**
 * Converts the internal 0-100 quality score into the five-star scale shown to
 * the player. Half stars are supported, so every 10 internal points is half a
 * star. The simulation keeps the integer score internally because the process
 * reactions, filtration and aging still need finer precision than five steps.
 *
 * <p>The scored bar uses only glyphs covered by Minecraft's built-in fonts:
 * U+2605 black star, U+2606 white star and U+2BEA (star with the left half
 * black) for a half star. The textual number beside the bar removes any
 * ambiguity about rounding.</p>
 */
public final class Quality {
    public static final String FULL = "\u2605";
    public static final String HALF = "\u2BEA";
    public static final String EMPTY = "\u2606";
    public static final int STARS = 5;

    private Quality() {
    }

    /** Exact fractional star value, 0.0-5.0. */
    public static double stars(int quality) {
        return Simulation.clamp(quality, 0, 100) / 20.0;
    }

    /** Star value rounded to the nearest half, 0.0-5.0. */
    public static double roundedStars(int quality) {
        return Math.round(stars(quality) * 2.0) / 2.0;
    }

    /** Five-cell star bar such as {@code "★★★★⯪☆"}. */
    public static String bar(int quality) {
        double value = roundedStars(quality);
        int full = (int) Math.floor(value);
        boolean half = value - full >= 0.5;
        int empty = STARS - full - (half ? 1 : 0);
        StringBuilder bar = new StringBuilder(STARS);
        for (int i = 0; i < full; i++) bar.append(FULL);
        if (half) bar.append(HALF);
        for (int i = 0; i < empty; i++) bar.append(EMPTY);
        return bar.toString();
    }

    /** Star value with one decimal, for example {@code "4.5"}. */
    public static String number(int quality) {
        return String.format(Locale.ROOT, "%.1f", roundedStars(quality));
    }

    /** Human-readable readout such as {@code "★★★★⯪☆ 4.5/5"}. */
    public static String label(int quality) {
        return bar(quality) + " " + number(quality) + "/" + STARS;
    }
}
