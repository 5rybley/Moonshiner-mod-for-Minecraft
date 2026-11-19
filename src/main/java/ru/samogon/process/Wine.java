package ru.samogon.process;

import ru.samogon.process.Simulation.Batch;

import java.util.Locale;

/**
 * Gameplay-only model for the winery: one chain of grape must, still wine and
 * sparkling wine made by the traditional method.
 *
 * <p>Wine deliberately lives outside the distillation taxonomy. The stage and
 * the colour are recorded in the batch {@code origin}, so a bottle always knows
 * what it holds:
 * <ul>
 *   <li>{@link #MUST_ORIGIN} — unfermented grape must, for example
 *       {@code grape_must:red};</li>
 *   <li>{@link #WINE_ORIGIN} — dry still wine, for example {@code wine:white};</li>
 *   <li>{@link #SPARKLING_ORIGIN} — sparkling wine after the second
 *       fermentation in the bottle and the dosage, for example
 *       {@code sparkling_wine:red:brut}.</li>
 * </ul>
 *
 * <p>The sparkling branch follows the research on champagne: tirage (a sugar
 * and yeast charge bottled with the base wine), a second fermentation in the
 * bottle, ageing on the lees, riddling, disgorging and finally the dosage
 * that fixes the sweetness. The rack performs those steps as one long run;
 * the dosage is chosen with the rack's button. The numbers are a simplified
 * game simulation, not a real winemaking recipe.</p>
 */
public final class Wine {
    /** Origin of unfermented grape must. */
    public static final String MUST_ORIGIN = "grape_must";
    /** Origin of dry still wine. */
    public static final String WINE_ORIGIN = "wine";
    /** Origin of sparkling wine. */
    public static final String SPARKLING_ORIGIN = "sparkling_wine";

    /** One pressing yields a litre of must. */
    public static final double MUST_VOLUME = 1000.0;
    /** A standard wine bottle, the unit every wine machine bottles. */
    public static final double BOTTLE_VOLUME = 750.0;

    /** Colour ids used in the origin string. */
    public static final String RED_ID = "red";
    public static final String WHITE_ID = "white";
    public static final String ROSE_ID = "rose";

    private static final String SEPARATOR = ":";

    /**
     * Colour of the finished wine. The colour decides the bottle look and the
     * base of the wine name; the numbers behind it come from {@link
     * GrapeVariety}, which is what the vineyard actually grows.
     */
    public enum Color {
        RED(RED_ID, "wine.samogon.color.red", "wine.samogon.still.red"),
        WHITE(WHITE_ID, "wine.samogon.color.white", "wine.samogon.still.white"),
        ROSE(ROSE_ID, "wine.samogon.color.rose", "wine.samogon.still.rose");

        private final String id;
        private final String colorKey;
        private final String nameKey;

        Color(String id, String colorKey, String nameKey) {
            this.id = id;
            this.colorKey = colorKey;
            this.nameKey = nameKey;
        }

        public String id() {
            return id;
        }

        /** Key of the bare colour word, used inside the sparkling-wine name. */
        public String colorKey() {
            return colorKey;
        }

        /** Must quality of the default variety of this colour. */
        public int mustQuality() {
            return GrapeVariety.defaultFor(this).mustQuality();
        }

        /** Average ambient temperature at which this colour ferments best. */
        public double idealTemperature() {
            return GrapeVariety.defaultFor(this).idealTemperature();
        }

        public double baseAbv() {
            return GrapeVariety.defaultFor(this).baseAbv();
        }

        public String nameKey() {
            return nameKey;
        }

        /** Unknown colours fall back to red, like an old malformed batch. */
        public static Color of(String id) {
            if (id == null) return RED;
            return switch (id.toLowerCase(Locale.ROOT)) {
                case WHITE_ID -> WHITE;
                case ROSE_ID -> ROSE;
                default -> RED;
            };
        }
    }

    /**
     * Sparkling-wine sweetness, following the EU dosage bands in grams of
     * sugar per litre. {@code sugarItems} is the game cost: one item for the
     * tirage charge plus the dosage charge.
     */
    public enum Dosage {
        BRUT_NATURE("brut_nature", 0, 1, 3),
        EXTRA_BRUT("extra_brut", 4, 1, 2),
        BRUT("brut", 9, 2, 2),
        EXTRA_DRY("extra_dry", 14, 2, 1),
        SEC("sec", 24, 3, 0),
        DEMI_SEC("demi_sec", 41, 3, -1),
        DOUX("doux", 60, 4, -2);

        private final String id;
        private final int gramsPerLitre;
        private final int sugarItems;
        private final int qualityBonus;

        Dosage(String id, int gramsPerLitre, int sugarItems, int qualityBonus) {
            this.id = id;
            this.gramsPerLitre = gramsPerLitre;
            this.sugarItems = sugarItems;
            this.qualityBonus = qualityBonus;
        }

        public String id() {
            return id;
        }

        /** Representative sugar content of the finished wine, g/l. */
        public int gramsPerLitre() {
            return gramsPerLitre;
        }

        /** Sugar items the rack consumes for the tirage and the dosage. */
        public int sugarItems() {
            return sugarItems;
        }

        public int qualityBonus() {
            return qualityBonus;
        }

        public String translationKey() {
            return "wine.samogon.dosage." + id;
        }

        public static Dosage of(String id) {
            if (id == null) return BRUT;
            for (Dosage dosage : values()) {
                if (dosage.id.equalsIgnoreCase(id)) return dosage;
            }
            return BRUT;
        }

        public static Dosage byIndex(int index) {
            Dosage[] all = values();
            return all[Math.floorMod(index, all.length)];
        }
    }

    /**
     * Fresh must from one press, using the default variety of the colour. The
     * colour decides the whole later chain; the variety refines the numbers.
     */
    public static Batch must(Color color) {
        Color charge = color == null ? Color.RED : color;
        return must(charge, GrapeVariety.defaultFor(charge));
    }

    /**
     * Fresh must from one press of a named grape. A dark grape pressed with the
     * skins off still yields a rosé colour, but it keeps its variety in the
     * origin so the bottle can say which grape it came from.
     */
    public static Batch must(Color color, GrapeVariety variety) {
        Color charge = color == null ? Color.RED : color;
        GrapeVariety grape = variety == null ? GrapeVariety.defaultFor(charge) : variety;
        return new Batch(MUST_VOLUME, 0, grape.mustQuality(), 0, 0, false,
                origin(MUST_ORIGIN, charge.id(), grape.id()), 0);
    }

    /**
     * Primary fermentation of must into dry wine. Each colour has its own
     * preferred cellar temperature: red ferments warm, white cool, rosé in
     * between. A long way from that temperature costs quality.
     */
    public static Batch ferment(Batch must, double averageTemperature) {
        Color color = colorOf(must.origin());
        GrapeVariety variety = varietyOf(must.origin(), color);
        double deviation = Math.abs(averageTemperature - variety.idealTemperature());
        double abv = Simulation.clamp(variety.baseAbv() - deviation * 0.12, 7.0, 15.0);
        int quality = Simulation.clamp(must.quality() + 10
                - (int) Math.round(deviation * 1.6)
                - (averageTemperature > 34 ? 10 : 0)
                - (averageTemperature < 8 ? 5 : 0));
        int impurities = Math.max(0, 18 - must.quality() / 6 + (int) Math.round(deviation * 2.0));
        return new Batch(must.volume() * 0.94, abv, quality, impurities, 0, false,
                origin(WINE_ORIGIN, color.id(), variety.id()), 0);
    }

    /** One cask cycle: the wine settles, rounds out and loses a little volume. */
    public static Batch age(Batch wine) {
        return new Batch(wine.volume() * 0.99, wine.abv(), Simulation.clamp(wine.quality() + 5),
                Math.max(0, wine.impurities() - 3), wine.passes(), wine.filtered(), wine.origin(),
                wine.age() + 1, wine.composition(), wine.rectified(), wine.oak(), wine.cut(),
                wine.columnRuns());
    }

    /**
     * Oak chips in the cask. The wine is recorded as {@link WineStyle#OAKED}
     * and picks up a little oak and a noticeable roundness.
     */
    public static Batch oak(Batch wine) {
        String styled = withStyle(wine.origin(), WineStyle.OAKED);
        return new Batch(wine.volume() * 0.99, wine.abv(),
                Simulation.clamp(wine.quality() + WineStyle.OAKED.qualityBonus()),
                Math.max(0, wine.impurities() - 4), wine.passes(), wine.filtered(), styled,
                wine.age() + 1, wine.composition(), wine.rectified(), wine.oak() + 1,
                wine.cut(), wine.columnRuns());
    }

    /**
     * Sugar in the cask. Sugar is added after fermentation, so the strength does
     * not change; the amount chooses the sweetness band and the style segment.
     */
    public static Batch sweeten(Batch wine, int sugarItems) {
        WineStyle style = WineStyle.forSugar(sugarItems);
        String styled = withStyle(wine.origin(), style);
        return new Batch(wine.volume(), wine.abv(),
                Simulation.clamp(wine.quality() + style.qualityBonus()),
                Math.max(0, wine.impurities() - 1), wine.passes(), wine.filtered(), styled,
                wine.age() + 1, wine.composition(), wine.rectified(), wine.oak(),
                wine.cut(), wine.columnRuns());
    }

    /**
     * Aromatizes a still wine at the blending table. The composition is already
     * stored by the blending table; this method only turns it into a style and
     * applies the style's quality.
     */
    public static Batch aromatize(Batch wine, WineStyle aroma) {
        if (aroma == null || aroma == WineStyle.DRY || !canAromatize(wine)) return wine;
        String styled = withStyle(wine.origin(), aroma);
        return new Batch(wine.volume() * 0.996, wine.abv(),
                Simulation.clamp(wine.quality() + aroma.qualityBonus()),
                Math.max(0, wine.impurities() - 1), wine.passes(), wine.filtered(), styled,
                wine.age(), wine.composition(), wine.rectified(), wine.oak(),
                wine.cut(), wine.columnRuns());
    }

    /**
     * Marries two still wines at the blending table. The colour follows the
     * pair, the two grape varieties are both recorded (the partner in the last
     * origin segment) and the numbers are volume-weighted: the blend is as
     * strong as the mixture, as old as the older wine and a little rounder than
     * either part. The composition stays empty so the bottle is named by the
     * two varieties rather than by an aromatic additive.
     */
    public static Batch cuvee(Batch base, Batch partner) {
        if (base == null || partner == null) {
            throw new IllegalArgumentException("Two wines are required to blend a cuvee");
        }
        if (!canFortify(base) || !canFortify(partner)) {
            throw new IllegalArgumentException("Only plain still wines can be blended into a cuvee");
        }
        Color baseColor = colorOf(base.origin());
        Color partnerColor = colorOf(partner.origin());
        Color color = cuveeColor(baseColor, partnerColor);
        GrapeVariety baseVariety = varietyOf(base.origin(), baseColor);
        GrapeVariety partnerVariety = varietyOf(partner.origin(), partnerColor);
        double volume = (base.volume() + partner.volume()) * 0.995;
        double abv = (base.volume() * base.abv() + partner.volume() * partner.abv())
                / (base.volume() + partner.volume());
        int quality = Simulation.clamp((int) Math.round(
                (base.quality() * base.volume() + partner.quality() * partner.volume())
                        / (base.volume() + partner.volume())
                        + WineStyle.CUVEE.qualityBonus()));
        int impurities = Math.max(0, (int) Math.round(
                (base.impurities() * base.volume() + partner.impurities() * partner.volume())
                        / (base.volume() + partner.volume())) - 2);
        return new Batch(volume, abv, quality, impurities, base.passes(), base.filtered(),
                origin(WINE_ORIGIN, color.id(), baseVariety.id(), WineStyle.CUVEE.key(),
                        partnerVariety.id()),
                Math.max(base.age(), partner.age()), "", base.rectified(),
                base.oak() + partner.oak(), base.cut(), base.columnRuns());
    }

    /**
     * The colour a cuvée of two wines gets. Equal colours keep their colour,
     * red and white make a rosé, and a rosé takes the colour of the stronger
     * partner: rosé with red is red, rosé with white is white.
     */
    public static Color cuveeColor(Color first, Color second) {
        Color left = first == null ? Color.RED : first;
        Color right = second == null ? Color.RED : second;
        if (left == right) return left;
        if (left == Color.ROSE) return right;
        if (right == Color.ROSE) return left;
        return Color.ROSE;
    }

    /**
     * The partner grape of a cuvée, read from the last origin segment. Plain
     * wines have no such segment, and the fifth segment of a sparkling origin
     * is a dosage rather than a grape, so both read as no partner.
     */
    public static GrapeVariety cuveePartner(String origin) {
        return GrapeVariety.parse(segment(origin, 4));
    }

    /**
     * Fortifies a still wine with a strong spirit. The whole bottle goes into
     * the blend; a full 100 ml charge of 96 % spirit lifts a 750 ml dry wine to
     * roughly 22 %, the strength of a real port. The result is capped at 22 %
     * and never raised artificially: a weak spirit simply makes a weaker
     * fortified wine. An oaked base becomes sherry, everything else port.
     */
    public static Batch fortify(Batch wine, Batch spirit) {
        if (wine == null || spirit == null) {
            throw new IllegalArgumentException("Wine and spirit are required");
        }
        if (!canFortify(wine)) {
            throw new IllegalArgumentException("Only a plain still wine can be fortified");
        }
        if (spirit.abv() < 30) {
            throw new IllegalArgumentException("A spirit of at least 30 % ABV is required");
        }
        WineStyle base = stillStyleOf(wine.origin());
        WineStyle style = base == WineStyle.OAKED || base == WineStyle.SHERRY
                ? WineStyle.SHERRY : WineStyle.PORT;
        // The table consumes the whole bottle, so all of its spirit must end up
        // in the blend. Mixing in only a quarter silently destroyed the rest.
        double spiritVolume = spirit.volume();
        double volume = wine.volume() + spiritVolume;
        double abv = (wine.volume() * wine.abv() + spiritVolume * spirit.abv()) / volume;
        int quality = Simulation.clamp(wine.quality() + style.qualityBonus() + spirit.quality() / 20);
        int impurities = Math.max(0, wine.impurities() - 3 + spirit.impurities() / 10);
        return new Batch(volume, Math.min(abv, 22.0), quality, impurities,
                wine.passes(), wine.filtered(), withStyle(wine.origin(), style),
                wine.age() + 1, wine.composition(), wine.rectified(), wine.oak(),
                wine.cut(), wine.columnRuns());
    }

    /**
     * The traditional method in one run: tirage, second fermentation in the
     * bottle, lees ageing, riddling, disgorging and dosage. The dosage charge
     * is chosen by the player and fixes the sweetness recorded in the origin.
     * The base wine decides the sparkling method, which is written before the
     * dosage so the last segment always stays the dosage.
     */
    public static Batch tirage(Batch wine, Dosage dosage) {
        Dosage sweet = dosage == null ? Dosage.BRUT : dosage;
        Color color = colorOf(wine.origin());
        GrapeVariety variety = varietyOf(wine.origin(), color);
        SparklingStyle spark = SparklingStyle.forBase(color, variety);
        double abv = Simulation.clamp(wine.abv() + 1.1, 8.0, 15.0);
        int quality = Simulation.clamp(wine.quality() + 8 + sweet.qualityBonus()
                - (wine.age() <= 0 ? 4 : 0));
        int impurities = Math.max(0, wine.impurities() - 2);
        return new Batch(wine.volume() * 0.98, abv, quality, impurities, wine.passes(),
                wine.filtered(), origin(SPARKLING_ORIGIN, color.id(), variety.id(), spark.key(),
                sweet.id()), wine.age() + 1, wine.composition(), wine.rectified(), wine.oak(),
                wine.cut(), wine.columnRuns());
    }

    /**
     * The style recorded in a still-wine origin. Old origins without the style
     * segment, and sparkling origins whose fourth segment is a method rather
     * than a style, read as plain dry wine.
     */
    public static WineStyle stillStyleOf(String origin) {
        WineStyle style = WineStyle.parse(segment(origin, 3));
        return style == null ? WineStyle.DRY : style;
    }

    public static WineStyle stillStyleOf(Batch batch) {
        return batch == null ? WineStyle.DRY : stillStyleOf(batch.origin());
    }

    /**
     * The style a bottle is named after. A still wine blended before the style
     * segment existed carries only its composition, so the style is read back
     * from the blend. This keeps the blend name on old bottles instead of
     * demoting them to a plain dry wine.
     */
    public static WineStyle displayStyle(Batch batch) {
        WineStyle style = stillStyleOf(batch);
        if (style == WineStyle.DRY && batch != null && batch.composition() != null
                && !batch.composition().isBlank()) {
            WineStyle blended = WineStyle.forComposition(batch.composition());
            if (blended != null) return blended;
        }
        return style;
    }

    /**
     * The plain still-wine styles the blending table can shape: the base wine,
     * an oaked wine, the four sweetness bands and a cuvée. Aromatized and
     * fortified wines are finished drinks and are not listed here.
     */
    private static boolean isPlainStill(WineStyle style) {
        return switch (style) {
            case DRY, OAKED, OFF_DRY, SEMI_SWEET, SWEET, DESSERT, CUVEE -> true;
            default -> false;
        };
    }

    /**
     * True when the table may aromatize the wine. Port and sherry are finished:
     * adding berries to a fortified wine would not turn it into sangria, it
     * would erase the fortification.
     */
    public static boolean canAromatize(Batch wine) {
        if (!isWine(wine)) return false;
        WineStyle style = stillStyleOf(wine);
        return style != WineStyle.PORT && style != WineStyle.SHERRY;
    }

    /**
     * True when the table may fortify the wine. Only a plain still wine is
     * fortified: an aromatized one (vermouth, mulled wine, sangria, honeyed)
     * and an already fortified one keep their identity.
     */
    public static boolean canFortify(Batch wine) {
        return isWine(wine) && isPlainStill(stillStyleOf(wine));
    }

    /**
     * The method recorded in a sparkling origin. Old origins carry the dosage
     * in the fourth segment; an unknown word there falls back to the colour of
     * the base wine.
     */
    public static SparklingStyle sparklingStyleOf(String origin) {
        SparklingStyle style = SparklingStyle.parse(segment(origin, 3));
        if (style != null) return style;
        Color color = colorOf(origin);
        return SparklingStyle.forBase(color, varietyOf(origin, color));
    }

    public static SparklingStyle sparklingStyleOf(Batch batch) {
        return batch == null
                ? SparklingStyle.CLASSIC : sparklingStyleOf(batch.origin());
    }

    /**
     * Rewrites a still-wine origin with a style segment. Legacy origins without
     * a variety are normalized to the colour's default grape so the style never
     * lands in the variety slot. Segments after the style are kept: a cuvée
     * stores its partner grape there, and oaking or sweetening the blend must
     * not forget it.
     */
    public static String withStyle(String origin, WineStyle style) {
        if (style == null) return origin;
        Color color = colorOf(origin);
        GrapeVariety variety = varietyOf(origin, color);
        StringBuilder builder = new StringBuilder(
                origin(WINE_ORIGIN, color.id(), variety.id(), style.key()));
        String[] parts = origin == null ? new String[0] : origin.split(SEPARATOR, -1);
        for (int i = 4; i < parts.length; i++) {
            if (parts[i] != null && !parts[i].isBlank()) {
                builder.append(SEPARATOR).append(parts[i]);
            }
        }
        return builder.toString();
    }

    /** Builds an origin from a stage and its parts, for example {@code wine:red}. */
    public static String origin(String stage, String... parts) {
        StringBuilder builder = new StringBuilder(stage);
        for (String part : parts) {
            if (part != null && !part.isBlank()) {
                builder.append(SEPARATOR).append(part);
            }
        }
        return builder.toString();
    }

    /** First segment of an origin; plain old origins pass through unchanged. */
    public static String stageOf(String origin) {
        if (origin == null) return "";
        int separator = origin.indexOf(SEPARATOR);
        return separator < 0 ? origin : origin.substring(0, separator);
    }

    private static String segment(String origin, int index) {
        if (origin == null) return null;
        String[] parts = origin.split(SEPARATOR, -1);
        return index >= 0 && index < parts.length ? parts[index] : null;
    }

    public static Color colorOf(String origin) {
        String part = segment(origin, 1);
        return Color.of(part);
    }

    /** Variety recorded in an origin; old origins fall back to the colour's default. */
    public static GrapeVariety varietyOf(String origin) {
        return varietyOf(origin, colorOf(origin));
    }

    public static GrapeVariety varietyOf(String origin, Color color) {
        // A legacy origin may carry the dosage in this slot (sparkling_wine:red:brut),
        // which is not a variety, so an unknown word falls back to the colour.
        GrapeVariety variety = GrapeVariety.parse(segment(origin, 2));
        return variety != null ? variety : GrapeVariety.defaultFor(color == null ? Color.RED : color);
    }

    /**
     * The dosage is the last segment of a sparkling origin, so it stays
     * readable whether the origin is {@code sparkling_wine:red:brut} (before the
     * vineyard) or {@code sparkling_wine:red:cabernet:brut}.
     */
    public static Dosage dosageOf(String origin) {
        return Dosage.of(segment(origin, countSegments(origin) - 1));
    }

    private static int countSegments(String origin) {
        if (origin == null || origin.isBlank()) return 0;
        return origin.split(SEPARATOR, -1).length;
    }

    /** Unfermented grape must. */
    public static boolean isMust(Batch batch) {
        return batch != null && batch.passes() <= 0 && MUST_ORIGIN.equals(stageOf(batch.origin()))
                && batch.abv() <= 0.5;
    }

    /** Dry still wine: fermented but never distilled or put through tirage. */
    public static boolean isWine(Batch batch) {
        return batch != null && batch.passes() <= 0 && WINE_ORIGIN.equals(stageOf(batch.origin()));
    }

    /** Sparkling wine after the second fermentation and the dosage. */
    public static boolean isSparkling(Batch batch) {
        return batch != null && batch.passes() <= 0 && SPARKLING_ORIGIN.equals(stageOf(batch.origin()));
    }

    /** True for a still wine that the bottle rack can charge. */
    public static boolean isStillWine(Batch batch) {
        return isWine(batch) && batch.abv() > 1.0;
    }

    /** True for every batch of the winery chain. */
    public static boolean isGrape(Batch batch) {
        return isMust(batch) || isWine(batch) || isSparkling(batch);
    }

    /**
     * True when an origin belongs to the winery chain, ignoring the
     * distillation state. Distilled grape batches no longer match
     * {@link #isGrape}, but their origin still starts with a winery stage, so
     * the classifier can recover the raw material (wine, sparkling wine, must).
     */
    public static boolean isGrapeOrigin(String origin) {
        String stage = stageOf(origin);
        return MUST_ORIGIN.equals(stage) || WINE_ORIGIN.equals(stage) || SPARKLING_ORIGIN.equals(stage);
    }

    private Wine() {}
}