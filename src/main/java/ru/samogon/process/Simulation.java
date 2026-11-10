package ru.samogon.process;

/**
 * Gameplay-only mass balance and vapour-liquid equilibrium model.
 * The numbers are deliberately simplified and are NOT operating instructions
 * for real distillation equipment.
 */
public final class Simulation {
    /**
     * One batch of liquid moving through the production chain.
     *
     * <p>{@code cut} records whether the pot still separated heads, hearts and
     * tails. A whole run (no fraction separation) is drinkable but stays a
     * rough moonshine; a fractional run can become chacha, whiskey, rum,
     * brandy or cognac. {@code columnRuns} counts how many times the batch has
     * passed through the rectification column, so the column can raise the
     * strength gradually instead of jumping to azeotrope in one pass.</p>
     */
    public record Batch(double volume, double abv, int quality, int impurities, int passes,
                        boolean filtered, String origin, int age, String composition,
                        boolean rectified, int oak, boolean cut, int columnRuns) {
        /**
         * Compatibility constructor for the many unflavoured intermediate
         * batches in the production chain. A blank composition means "no
         * aromatic additives"; the old eight-argument calls stay source
         * compatible. Anything already distilled was, historically, separated
         * into fractions, so {@code cut} defaults to {@code passes > 0}.
         */
        public Batch(double volume, double abv, int quality, int impurities, int passes,
                     boolean filtered, String origin, int age) {
            this(volume, abv, quality, impurities, passes, filtered, origin, age,
                    "", false, 0, passes > 0, 0);
        }

        /** Compatibility constructor for composed batches without process flags. */
        public Batch(double volume, double abv, int quality, int impurities, int passes,
                     boolean filtered, String origin, int age, String composition) {
            this(volume, abv, quality, impurities, passes, filtered, origin, age,
                    composition, false, 0, passes > 0, 0);
        }

        /** Compatibility constructor for batches with rectification/oak flags. */
        public Batch(double volume, double abv, int quality, int impurities, int passes,
                     boolean filtered, String origin, int age, String composition,
                     boolean rectified, int oak) {
            this(volume, abv, quality, impurities, passes, filtered, origin, age,
                    composition, rectified, oak, passes > 0, 0);
        }

        public Batch {
            if (!Double.isFinite(volume) || !Double.isFinite(abv) || volume <= 0 || abv < 0 || abv > 100) {
                throw new IllegalArgumentException("Invalid batch volume or concentration");
            }
            quality = clamp(quality);
            impurities = clamp(impurities);
            passes = Math.max(0, passes);
            age = Math.max(0, age);
            origin = origin == null || origin.isBlank() ? "sugar" : origin;
            composition = composition == null ? "" : composition.strip();
            oak = Math.max(0, oak);
            columnRuns = Math.max(0, columnRuns);
        }

        public double alcohol() {
            return volume * abv / 100.0;
        }

        public Batch withVolume(double newVolume) {
            return new Batch(newVolume, abv, quality, impurities, passes, filtered, origin, age, composition,
                    rectified, oak, cut, columnRuns);
        }

        public Batch withComposition(String newComposition) {
            return new Batch(volume, abv, quality, impurities, passes, filtered, origin, age, newComposition,
                    rectified, oak, cut, columnRuns);
        }

        /** Marks the batch as column-rectified (neutral spirit). */
        public Batch asRectified() {
            return new Batch(volume, abv, quality, impurities, passes, filtered, origin, age, composition,
                    true, oak, cut, columnRuns);
        }

        /** Counts one more oak-aging cycle. */
        public Batch withOakCycle() {
            return new Batch(volume, abv, quality, impurities, passes, filtered, origin, age, composition,
                    rectified, oak + 1, cut, columnRuns);
        }

        /** Records whether the pot still separated the fractions of this batch. */
        public Batch withCuts(boolean newCut) {
            return new Batch(volume, abv, quality, impurities, passes, filtered, origin, age, composition,
                    rectified, oak, newCut, columnRuns);
        }

        /** Records how many times this batch has run through the column. */
        public Batch withColumnRuns(int runs) {
            return new Batch(volume, abv, quality, impurities, passes, filtered, origin, age, composition,
                    rectified, oak, cut, runs);
        }
    }

    public enum Fraction {
        HEADS("heads"), HEARTS("hearts"), TAILS("tails");

        private final String id;

        Fraction(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }
    }

    /** Sweet wort: 2,000 arbitrary units, no alcohol yet. */
    public static Batch mash(String origin, int qualityBonus) {
        return new Batch(2000, 0, clamp(52 + qualityBonus), 0, 0, false, origin, 0);
    }

    /**
     * Anaerobic fermentation. Temperature is the average ambient temperature in
     * degrees Celsius and deviation is the average distance from 20 C.
     */
    public static Batch ferment(Batch wort, double averageDeviation, double averageTemperature) {
        double baseAbv = switch (wort.origin()) {
            case "grain" -> 11.5;
            case "berry" -> 10.5;
            case "honey" -> 12.5;
            case "potato" -> 12.0;
            case "beetroot" -> 11.0;
            case "cane" -> 13.0;
            case "apple" -> 10.0;
            case "cocoa" -> 9.0;
            case "melon" -> 11.0;
            case "carrot" -> 10.5;
            case "cactus" -> 12.0;
            default -> 13.5;
        };
        double abv = clamp(baseAbv - averageDeviation * 0.20, 4.0, 16.0);
        int quality = wort.quality() + 8 - (int) Math.round(averageDeviation * 2.0)
                - (averageTemperature > 35 ? 10 : 0) - (averageTemperature < 10 ? 5 : 0);
        int impurities = 45 - wort.quality() / 4 + (int) Math.round(averageDeviation * 3.0)
                + (averageTemperature > 35 ? 15 : 0);
        return new Batch(wort.volume() * 0.95, abv, quality, impurities, 0, false, wort.origin(), 0,
                wort.composition());
    }

    /**
     * Approximate boiling point of an ethanol/water mixture. The azeotrope at
     * about 95.6 % ABV boils near 78.2 C; a dilute wash boils much higher.
     */
    public static double boilingPoint(double abv) {
        double[] x = {0, 5, 10, 20, 30, 40, 50, 60, 70, 80, 90, 95.6, 100};
        double[] y = {100, 95.5, 91.5, 87, 84.5, 82.5, 81, 80, 79.2, 78.7, 78.35, 78.2, 78.2};
        double value = clamp(abv, 0, 100);
        for (int i = 1; i < x.length; i++) {
            if (value <= x[i]) {
                double t = (value - x[i - 1]) / (x[i] - x[i - 1]);
                return y[i - 1] + (y[i] - y[i - 1]) * t;
            }
        }
        return 78.2;
    }

    /**
     * Effective vapour strength. A simple pot still enriches ethanol strongly;
     * each reflux plate moves the vapour closer to the azeotrope.
     */
    public static double vapourAbv(double liquidAbv, int plates) {
        double vapour = Math.min(95.6, 102.0 * (1.0 - Math.exp(-clamp(liquidAbv, 0, 100) / 16.0)));
        for (int i = 0; i < Math.max(0, Math.min(6, plates)); i++) {
            vapour += (95.6 - vapour) * 0.28;
        }
        return clamp(vapour, 0, 95.6);
    }

    /**
     * Volume-based cut classification. The old temperature guard compared the
     * pot temperature with 96 C, while the pot target was 104 C, so every cut
     * after heads became tails. Temperature is now only a runaway safety net.
     */
    public static Fraction classify(double alcoholCollectedFraction, double vapourAbv, double temperature) {
        if (alcoholCollectedFraction < 0.07) return Fraction.HEADS;
        if (alcoholCollectedFraction > 0.90 || temperature > 105.5) return Fraction.TAILS;
        return Fraction.HEARTS;
    }

    /**
     * Build one collected bottle from a receiver cut, using the cut's measured
     * strength. When {@code cuts} is false the still ran without separating
     * heads and tails: everything is collected in one receiver and the result
     * is a rough, low-quality moonshine.
     */
    public static Batch cutAtAbv(Batch source, Fraction fraction, double abv, double progress,
                                 double volume, boolean cuts) {
        int quality;
        int impurities;
        if (!cuts) {
            // A whole run drags some heads and tails into the keep, so it is
            // drinkable but noticeably rougher than separated hearts.
            quality = source.quality() - 6 + (int) Math.round(progress * 4);
            impurities = (int) Math.round(source.impurities() * 0.55) + 6;
        } else {
            switch (fraction) {
                case HEADS -> {
                    quality = source.quality() - 35 + (int) Math.round(progress * 20);
                    impurities = source.impurities() + 30;
                }
                case HEARTS -> {
                    quality = source.quality() + 10 + (int) Math.round(8 - Math.abs(progress - 0.45) * 30);
                    impurities = (int) Math.round(source.impurities() * 0.35) + 2;
                }
                default -> {
                    quality = source.quality() - 25;
                    impurities = source.impurities() + 20;
                }
            }
        }
        if (volume <= 0) throw new IllegalArgumentException("Cut volume must be positive");
        return new Batch(volume, clamp(abv, 0, 100), quality, impurities,
                source.passes() + 1, false, source.origin(), source.age(), source.composition(),
                source.rectified(), source.oak(), cuts, source.columnRuns());
    }

    /** Classic fractional cut: keeps the existing five-argument call sites. */
    public static Batch cutAtAbv(Batch source, Fraction fraction, double abv, double progress, double volume) {
        return cutAtAbv(source, fraction, abv, progress, volume, true);
    }

    /**
     * Create a fraction bottle when only vapour strength is known. The exact
     * measured strength path above is used by the still receiver so that
     * bottling can never create or destroy alcohol.
     */
    public static Batch cut(Batch source, Fraction fraction, double vapourAbv, double progress, double volume) {
        double abv = switch (fraction) {
            case HEADS -> Math.min(96, vapourAbv + 4);
            case HEARTS -> clamp(vapourAbv, 30, 95.6);
            case TAILS -> Math.max(8, vapourAbv * 0.55);
        };
        return cutAtAbv(source, fraction, abv, progress, volume, true);
    }

    /**
     * One pass through the rectification column. A real column is run several
     * times: strength climbs roughly 62 -> 76 -> 85 -> 90 -> 94 -> 96 %ABV
     * before a molecular sieve can pull the last water out to 99.5 %.
     */
    public static Batch rectify(Batch feed) {
        return rectify(feed, false);
    }

    public static Batch rectify(Batch feed, boolean sieve) {
        if (feed.alcohol() <= 0) throw new IllegalArgumentException("Rectification needs alcohol");
        int run = feed.columnRuns() + 1;
        double target = sieve && run >= 4 ? 99.5 : switch (Math.min(run, 6)) {
            case 1 -> 62;
            case 2 -> 76;
            case 3 -> 85;
            case 4 -> 90;
            case 5 -> 94;
            default -> 96;
        };
        // A column never drops below the strength the feed already has, but
        // without a molecular sieve it cannot pass the 96 % azeotrope, so a
        // stronger charge is diluted back down to it.
        target = Math.max(target, feed.abv());
        if (!sieve) target = Math.min(target, 96);
        target = Math.min(target, 99.5);

        // Keep most of the alcohol but lose a little in the column. Volume is
        // derived from the target strength, so alcohol is never created.
        double keptAlcohol = feed.alcohol() * 0.92;
        double volume = Math.min(feed.volume(), keptAlcohol / (target / 100.0));
        if (volume <= 0) throw new IllegalArgumentException("Rectification needs alcohol");
        double abv = Math.min(target, keptAlcohol * 100.0 / volume);
        return new Batch(volume, abv, clamp(feed.quality() + 12),
                (int) Math.round(feed.impurities() * 0.15), feed.passes() + 1, false,
                feed.origin(), feed.age(), feed.composition(), true, feed.oak(), feed.cut(), run);
    }

    /**
     * Charcoal filtration may be repeated as often as the player likes; every
     * pass costs a little volume. The quality gain follows the impurities that
     * were actually removed, so a clean batch no longer improves and repeated
     * passes cannot pump the score for free.
     */
    public static Batch filter(Batch source) {
        int removed = Math.min(12, Math.max(0, source.impurities()));
        return new Batch(source.volume() * 0.98, source.abv(), source.quality() + removed / 3,
                source.impurities() - removed, source.passes(), true, source.origin(), source.age(),
                source.composition(), source.rectified(), source.oak(), source.cut(), source.columnRuns());
    }

    public static Batch dilute(Batch source, double target) {
        if (target <= 0 || target >= source.abv() || target > 60) {
            throw new IllegalArgumentException("Invalid dilution target");
        }
        double volume = source.alcohol() * 100 / target;
        // Match the basin's tolerance: floating-point alcohol mass can land a
        // few ulps above 4000 even after the interaction check accepted it.
        if (volume > 4000.0 + 1.0E-9) throw new IllegalArgumentException("Bottle capacity exceeded");
        return new Batch(volume, target, source.quality(), source.impurities(), source.passes(),
                source.filtered(), source.origin(), source.age(), source.composition(),
                source.rectified(), source.oak(), source.cut(), source.columnRuns());
    }

    /**
     * Aging needs some alcohol to pull flavour out of the oak, but even a
     * freshly fermented wash (usually 4-16 % ABV) must be allowed to rest in
     * the barrel, so the lower bound is only a physical one.
     */
    public static final double AGING_MIN_ABV = 4.0;
    public static final double AGING_MAX_ABV = 60.0;

    /** True when a bottle is strong enough to mature in a cask. */
    public static boolean canAge(double abv) {
        return abv >= AGING_MIN_ABV && abv <= AGING_MAX_ABV;
    }

    public static Batch mature(Batch source, boolean oak) {
        if (!canAge(source.abv())) throw new IllegalArgumentException("Dilute before maturing");
        return new Batch(source.volume() * (oak ? 0.97 : 1.0), source.abv(),
                source.quality() + (oak ? 6 : 3), source.impurities() - (oak ? 4 : 0),
                source.passes(), source.filtered(), source.origin(), source.age() + 1,
                source.composition(), source.rectified(), source.oak() + (oak ? 1 : 0),
                source.cut(), source.columnRuns());
    }

    public static Batch infuse(Batch source, String kind) {
        if (source.abv() < 4 || source.abv() > 60) {
            throw new IllegalArgumentException("Infusion needs 4-60% ABV");
        }
        int bonus = switch (kind) {
            case "botanical" -> 4;
            case "berry" -> 5;
            case "honey" -> 6;
            default -> 2;
        };
        return new Batch(source.volume() * 0.99, source.abv(), source.quality() + bonus,
                source.impurities() + 2, source.passes(), source.filtered(), source.origin(), source.age() + 1,
                source.composition(), source.rectified(), source.oak(), source.cut(), source.columnRuns());
    }

    public static int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }

    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private Simulation() {
    }
}