package ru.samogon.process;

/**
 * How long a bottle of strong spirit burns in a furnace or another firebox.
 *
 * <p>Only spirit at or above {@link #MIN_ABV} is treated as fuel: ordinary
 * drinking-strength bottles are for the table, not for the fire. The weakest
 * usable bottle, exactly 80%, is still worth a single smelt, so the threshold
 * in the tooltip matches what the firebox accepts.</p>
 *
 * <p>The burn time is quoted for a full {@link #BOTTLE_ML production bottle}
 * and scales with the charge actually in the bottle: a still can leave a
 * partial tail behind, and a few millilitres of spirit must not be worth a
 * whole furnace load. Anything worth less than one smelt is not fuel at all.</p>
 *
 * <p>The burn curve has two segments. Up to the 96% azeotrope every extra
 * percent of alcohol gives the same steady gain; above the azeotrope the
 * molecular sieve has removed the last water, and the nearly anhydrous spirit
 * burns much hotter per percent. That keeps the two technical spirits distinct
 * without inventing a third item: neutral spirit smelts 48 items, anhydrous
 * spirit smelts 64.</p>
 *
 * <p>The class is deliberately free of Minecraft types so the numbers can be
 * checked by the dependency-free test task.</p>
 */
public final class SpiritFuel {
    /** Ticks a furnace spends on one smelt. */
    public static final int TICKS_PER_ITEM = 200;
    /** A full bottle: the charge the burn curve below is quoted for. */
    public static final double BOTTLE_ML = 100.0;
    /** Below this strength a bottle is a drink, not a fuel. */
    public static final double MIN_ABV = 80.0;
    /** The ethanol-water azeotrope: the strongest spirit a plain column reaches. */
    public static final double AZEOTROPE_ABV = 96.0;
    /** The strength left after the molecular sieve has taken the last water out. */
    public static final double ANHYDROUS_ABV = 99.5;
    /** 96% spirit smelts forty-eight items. */
    public static final int AZEOTROPE_TICKS = 48 * TICKS_PER_ITEM;
    /** 99.5% spirit smelts sixty-four items. */
    public static final int ANHYDROUS_TICKS = 64 * TICKS_PER_ITEM;

    /**
     * Burn time in ticks. Strength is rounded to the displayed tenth of a
     * percent first, so a value saved as 95.999999 still gets the full
     * azeotrope burn time and the tooltip and the firebox agree.
     */
    public static int burnTicks(double abv) {
        double strength = Math.round(abv * 10.0) / 10.0;
        if (strength < MIN_ABV) {
            return 0;
        }
        if (strength <= AZEOTROPE_ABV) {
            // The lowest usable strength is worth one smelt rather than nothing,
            // so "80% and above" in the tooltip and the firebox agree exactly.
            double share = (strength - MIN_ABV) / (AZEOTROPE_ABV - MIN_ABV);
            return (int) Math.round(TICKS_PER_ITEM + share * (AZEOTROPE_TICKS - TICKS_PER_ITEM));
        }
        if (strength >= ANHYDROUS_ABV) {
            return ANHYDROUS_TICKS;
        }
        double share = (strength - AZEOTROPE_ABV) / (ANHYDROUS_ABV - AZEOTROPE_ABV);
        return useful(Math.round(AZEOTROPE_TICKS + share * (ANHYDROUS_TICKS - AZEOTROPE_TICKS)));
    }

    /** A firebox should not accept a bottle that cannot finish a single smelt. */
    private static int useful(long ticks) {
        return ticks < TICKS_PER_ITEM ? 0 : (int) ticks;
    }

    /**
     * Burn time of a bottle of the given strength and volume. A production
     * bottle holds {@link #BOTTLE_ML} ml; a smaller charge burns in proportion.
     */
    public static int burnTicks(double abv, double volume) {
        int full = burnTicks(abv);
        if (full <= 0 || volume <= 0.0) {
            return 0;
        }
        double share = Math.min(1.0, volume / BOTTLE_ML);
        return useful(Math.round(full * share));
    }

    /** True when the given strength can be used as firebox fuel. */
    public static boolean isFuel(double abv) {
        return burnTicks(abv) > 0;
    }

    /** True when this particular bottle can be used as firebox fuel. */
    public static boolean isFuel(double abv, double volume) {
        return burnTicks(abv, volume) > 0;
    }

    /**
     * Number of whole furnace smelts the bottle is worth, for the tooltip.
     * Rounded down on purpose: the tooltip must never promise a smelt the
     * remaining ticks cannot actually finish.
     */
    public static int smelts(double abv) {
        return burnTicks(abv) / TICKS_PER_ITEM;
    }

    /** Whole smelts left in a bottle of the given strength and volume. */
    public static int smelts(double abv, double volume) {
        return burnTicks(abv, volume) / TICKS_PER_ITEM;
    }

    private SpiritFuel() {}
}