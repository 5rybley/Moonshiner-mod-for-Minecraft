package ru.samogon.process;

import java.util.Locale;

/**
 * The grape varieties grown in the vineyard. A variety fixes the colour of the
 * wine it makes and tunes the numbers the winery simulates: the quality the
 * must starts with, the cellar temperature the fermentation likes and the
 * strength the dry wine usually reaches.
 *
 * <p>The variety travels in the batch {@code origin} as its second segment, for
 * example {@code grape_must:red:cabernet} or
 * {@code sparkling_wine:white:riesling:brut}, so a bottle always knows which
 * grape it came from. Older origins without the segment fall back to the
 * default variety of the colour, which keeps pre-vineyard batches readable.</p>
 *
 * <p>The enum stays free of Minecraft types so the headless production-chain
 * test can load it on a plain classpath. The blockstate property and the item
 * mapping live in the block and registry layers.</p>
 */
public enum GrapeVariety {
    /** Warm-climate red: the classic full-bodied wine. */
    CABERNET("cabernet", Wine.Color.RED, 68, 24.0, 13.0),
    /** Softer red that ferments a little cooler. */
    MERLOT("merlot", Wine.Color.RED, 64, 22.0, 12.5),
    /** Cool-climate white: crisp and dry. */
    CHARDONNAY("chardonnay", Wine.Color.WHITE, 66, 16.0, 12.0),
    /** Coolest white: light and high in acid. */
    RIESLING("riesling", Wine.Color.WHITE, 62, 14.0, 11.5);

    private final String id;
    private final Wine.Color color;
    private final int mustQuality;
    private final double idealTemperature;
    private final double baseAbv;

    GrapeVariety(String id, Wine.Color color, int mustQuality, double idealTemperature, double baseAbv) {
        this.id = id;
        this.color = color;
        this.mustQuality = mustQuality;
        this.idealTemperature = idealTemperature;
        this.baseAbv = baseAbv;
    }

    public String id() {
        return id;
    }

    /** Colour of the grape, the base colour of the wine it makes. */
    public Wine.Color color() {
        return color;
    }

    public int mustQuality() {
        return mustQuality;
    }

    /** Average ambient temperature at which this variety ferments best. */
    public double idealTemperature() {
        return idealTemperature;
    }

    public double baseAbv() {
        return baseAbv;
    }

    /** True for a dark-skinned grape; only those can macerate into red wine. */
    public boolean isRed() {
        return color == Wine.Color.RED;
    }

    /** Name of the grape and of the still wine made from it, a plain word. */
    public String nameKey() {
        return "wine.samogon.variety." + id;
    }

    /** The variety with this id, or null when the word is not a variety at all. */
    public static GrapeVariety parse(String id) {
        if (id != null) {
            String word = id.trim();
            for (GrapeVariety variety : values()) {
                if (variety.id.equalsIgnoreCase(word)) return variety;
            }
        }
        return null;
    }

    /** Unknown varieties fall back to the first red, like an old malformed origin. */
    public static GrapeVariety of(String id) {
        GrapeVariety variety = parse(id);
        return variety != null ? variety : CABERNET;
    }

    /**
     * The variety used when an origin predates the vineyard and only carries a
     * colour: white is Chardonnay, everything else (red and rosé) is Cabernet.
     */
    public static GrapeVariety defaultFor(Wine.Color color) {
        return color == Wine.Color.WHITE ? CHARDONNAY : CABERNET;
    }

    @Override
    public String toString() {
        return id.toLowerCase(Locale.ROOT);
    }
}
