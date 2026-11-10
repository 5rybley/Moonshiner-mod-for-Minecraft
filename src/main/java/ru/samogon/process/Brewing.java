package ru.samogon.process;

import ru.samogon.process.Simulation.Batch;

/**
 * Gameplay-only model for the brewery multiblock: one brewing chain of sweet
 * wort, hopped wort and finished beer. Brewing deliberately lives only in the
 * brewery; the mash tun and the fermentation barrel never produce these
 * batches.
 *
 * <p>The stage of a brew is recorded in the batch {@code origin}, so a bottle
 * already carries which step it belongs to and the classifier can tell beer
 * apart from every distilled drink:
 * <ul>
 *   <li>{@link #WORT_ORIGIN} — sweet, unfermented wort;</li>
 *   <li>{@link #HOPPED_ORIGIN} — wort boiled with hops;</li>
 *   <li>{@link #BEER_ORIGIN} — fermented beer.</li>
 * </ul>
 * The grain, the optional fermentation flavouring and the yeast family are
 * stored as suffixes of the stage, for example {@code beer:wheat:honey:ale},
 * so a finished mug can be classified into a {@link BeerStyle} from its own
 * data. Older plain origins such as {@code beer} and the three-part
 * {@code beer:wheat:honey} still parse as the default barley, unflavoured,
 * warm (ale) brew. The numbers are a simplified game simulation, not a real
 * brewing recipe.</p>
 */
public final class Brewing {
    /** Origin of unfermented brewer's wort. */
    public static final String WORT_ORIGIN = "beer_wort";
    /** Origin of wort that has been boiled with hops. */
    public static final String HOPPED_ORIGIN = "hopped_wort";
    /** Origin of finished beer. */
    public static final String BEER_ORIGIN = "beer";

    /** Fallback grain for old batches and creative items. */
    public static final String DEFAULT_GRAIN = "barley";
    /** Marker for a brew without a fermentation flavouring. */
    public static final String NO_ADDITIVE = "none";
    /** Warmer than this a brew is a top-fermenting ale, colder a lager. */
    public static final double LAGER_TEMPERATURE = 14.0;

    private static final String SEPARATOR = ":";

    /** One mash yields a bucket-sized charge of wort. */
    public static final double WORT_VOLUME = 1000;
    /** A modest table beer, about 5 % ABV. */
    public static final double BEER_ABV = 5.0;

    public static final int STAGE_EMPTY = 0;
    public static final int STAGE_WORT = 1;
    public static final int STAGE_HOPPED = 2;
    public static final int STAGE_BEER = 3;

    /** Sweet, unfermented wort straight from the mash. */
    public static Batch wort() {
        return wort(DEFAULT_GRAIN);
    }

    /** Sweet wort from a specific grain; unknown grains fall back to barley. */
    public static Batch wort(String grain) {
        BeerStyle.Grain charge = BeerStyle.Grain.of(grain);
        return new Batch(WORT_VOLUME, 0, charge.mashQuality(), 0, 0, false,
                origin(WORT_ORIGIN, charge.id(), NO_ADDITIVE), 0);
    }

    /** The same wort after a hop boil: still sugar, now bitter and aromatic. */
    public static Batch boil(Batch wort) {
        return new Batch(wort.volume(), 0, Simulation.clamp(wort.quality() + 6),
                wort.impurities(), 0, false,
                origin(HOPPED_ORIGIN, grainOf(wort.origin()), additiveOf(wort.origin())), 0);
    }

    /**
     * Fermentation. Temperature is the average ambient temperature in degrees
     * Celsius and deviation is the average distance from 20 C, exactly like the
     * fermentation barrel. A brew held at or below {@link #LAGER_TEMPERATURE}
     * ferments as a bottom-fermenting lager, anything warmer as a top-
     * fermenting ale.
     */
    public static Batch ferment(Batch hopped, double averageDeviation, double averageTemperature) {
        return ferment(hopped, averageDeviation, averageTemperature, NO_ADDITIVE);
    }

    /** Fermentation with an optional flavour charge; the grain comes from the hopped wort. */
    public static Batch ferment(Batch hopped, double averageDeviation, double averageTemperature,
                                String additive) {
        BeerStyle.Family family = averageTemperature <= LAGER_TEMPERATURE
                ? BeerStyle.Family.LAGER : BeerStyle.Family.ALE;
        BeerStyle style = BeerStyle.of(grainOf(hopped.origin()), additive, family.id());
        double abv = Simulation.clamp(BEER_ABV + style.abvBonus() - averageDeviation * 0.15, 2.5, 6.5);
        int quality = Simulation.clamp(hopped.quality() + 10 + style.qualityBonus()
                - (int) Math.round(averageDeviation * 2.0)
                - (averageTemperature > 32 ? 12 : 0)
                - (averageTemperature < 10 ? 6 : 0));
        int impurities = Math.max(0, hopped.impurities()
                + (int) Math.round(averageDeviation * 2.0)
                + (averageTemperature > 32 ? 15 : 0));
        return new Batch(hopped.volume() * 0.96, abv, quality, impurities, 0, false,
                origin(BEER_ORIGIN, style.grain().id(), style.additive().id(), style.family().id()), 0);
    }

    /** Builds an origin from a stage, grain and additive. */
    public static String origin(String stage, String grain, String additive) {
        return stage + SEPARATOR + grain + SEPARATOR + additive;
    }

    /** Builds an origin from a stage, grain, additive and yeast family. */
    public static String origin(String stage, String grain, String additive, String family) {
        return origin(stage, grain, additive) + SEPARATOR + family;
    }

    /** Stage part of an origin; old plain origins pass through unchanged. */
    public static String stageOf(String origin) {
        if (origin == null) return "";
        int separator = origin.indexOf(SEPARATOR);
        return separator < 0 ? origin : origin.substring(0, separator);
    }

    /** Grain part of an origin, defaulting to barley. */
    public static String grainOf(String origin) {
        String part = segment(origin, 1);
        return part != null && !part.isBlank() ? part : DEFAULT_GRAIN;
    }

    /** Additive part of an origin, defaulting to none. */
    public static String additiveOf(String origin) {
        String part = segment(origin, 2);
        return part != null && !part.isBlank() ? part : NO_ADDITIVE;
    }

    /**
     * Yeast-family part of an origin. Old three-part origins carry no family
     * and were all warm top fermentation, so they default to the ale family.
     */
    public static String familyOf(String origin) {
        String part = segment(origin, 3);
        return part != null && !part.isBlank() ? part : BeerStyle.Family.ALE.id();
    }

    private static String segment(String origin, int index) {
        if (origin == null) return null;
        String[] parts = origin.split(SEPARATOR, -1);
        return index >= 0 && index < parts.length ? parts[index] : null;
    }

    /** True for a finished beer. */
    public static boolean isBeerOrigin(String origin) {
        return BEER_ORIGIN.equals(stageOf(origin));
    }

    /**
     * True for a finished beer that has never been run through a still or the
     * rectification column. A brew that is distilled keeps the beer stage in
     * its origin but becomes a spirit and must be classified as one.
     */
    public static boolean isFinishedBeer(Batch batch) {
        return batch != null && batch.passes() <= 0 && isBeerOrigin(batch.origin());
    }

    /** True for every batch that belongs to the brewery chain. */
    public static boolean isBrew(Batch batch) {
        if (batch == null) return false;
        String stage = stageOf(batch.origin());
        return WORT_ORIGIN.equals(stage) || HOPPED_ORIGIN.equals(stage) || BEER_ORIGIN.equals(stage);
    }

    /** Brewery stage of a batch: 0 none, 1 wort, 2 hopped wort, 3 beer. */
    public static int stageOf(Batch batch) {
        if (batch == null) return STAGE_EMPTY;
        return switch (stageOf(batch.origin())) {
            case WORT_ORIGIN -> STAGE_WORT;
            case HOPPED_ORIGIN -> STAGE_HOPPED;
            case BEER_ORIGIN -> STAGE_BEER;
            default -> STAGE_EMPTY;
        };
    }

    private Brewing() {}
}