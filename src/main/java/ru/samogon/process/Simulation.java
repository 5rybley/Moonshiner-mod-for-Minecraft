package ru.samogon.process;

/**
 * Gameplay-only mass balance and vapour-liquid equilibrium model.
 * The numbers are deliberately simplified and are NOT operating instructions
 * for real distillation equipment.
 */
public final class Simulation {
    public record Batch(double volume, double abv, int quality, int impurities, int passes,
                        boolean filtered, String origin, int age, String composition) {
        /**
         * Compatibility constructor for the many unflavoured intermediate
         * batches in the production chain. A blank composition means "no
         * aromatic additives"; the old eight-argument calls stay source
         * compatible.
         */
        public Batch(double volume, double abv, int quality, int impurities, int passes,
                     boolean filtered, String origin, int age) {
            this(volume, abv, quality, impurities, passes, filtered, origin, age, "");
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
        }

        public double alcohol() {
            return volume * abv / 100.0;
        }

        public Batch withVolume(double newVolume) {
            return new Batch(newVolume, abv, quality, impurities, passes, filtered, origin, age, composition);
        }

        public Batch withComposition(String newComposition) {
            return new Batch(volume, abv, quality, impurities, passes, filtered, origin, age, newComposition);
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

    /** Build one collected bottle from a receiver cut, using the cut's measured strength. */
    public static Batch cutAtAbv(Batch source, Fraction fraction, double abv, double progress, double volume) {
        int quality;
        int impurities;
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
        if (volume <= 0) throw new IllegalArgumentException("Cut volume must be positive");
        return new Batch(volume, clamp(abv, 0, 100), quality, impurities,
                source.passes() + 1, false, source.origin(), source.age(), source.composition());
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
        return cutAtAbv(source, fraction, abv, progress, volume);
    }

    /** Column rectification of low wines into a high-strength neutral spirit. */
    public static Batch rectify(Batch feed) {
        if (feed.alcohol() <= 0) throw new IllegalArgumentException("Rectification needs alcohol");
        // Never round a tiny feed up to one unit: that would create alcohol.
        double volume = Math.min(feed.volume(), feed.alcohol() / 0.96 * 0.85);
        return new Batch(volume, 96, feed.quality() + 18, (int) Math.round(feed.impurities() * 0.15),
                feed.passes() + 1, false, feed.origin(), feed.age(), feed.composition());
    }

    public static Batch filter(Batch source) {
        if (source.filtered()) throw new IllegalArgumentException("Batch already filtered");
        return new Batch(source.volume() * 0.98, source.abv(), source.quality() + 4,
                source.impurities() - 12, source.passes(), true, source.origin(), source.age(),
                source.composition());
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
                source.filtered(), source.origin(), source.age(), source.composition());
    }

    public static Batch mature(Batch source, boolean oak) {
        if (source.abv() < 20 || source.abv() > 60) throw new IllegalArgumentException("Dilute before maturing");
        return new Batch(source.volume() * (oak ? 0.97 : 1.0), source.abv(),
                source.quality() + (oak ? 6 : 3), source.impurities() - (oak ? 4 : 0),
                source.passes(), source.filtered(), source.origin(), source.age() + 1,
                source.composition());
    }

    public static Batch infuse(Batch source, String kind) {
        if (source.abv() < 20 || source.abv() > 60) {
            throw new IllegalArgumentException("Infusion needs 20-60% ABV");
        }
        int bonus = switch (kind) {
            case "botanical" -> 4;
            case "berry" -> 5;
            case "honey" -> 6;
            default -> 2;
        };
        return new Batch(source.volume() * 0.99, source.abv(), source.quality() + bonus,
                source.impurities() + 2, source.passes(), source.filtered(), source.origin(), source.age() + 1,
                source.composition());
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
