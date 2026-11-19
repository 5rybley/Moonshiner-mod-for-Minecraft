package ru.samogon.process;

import ru.samogon.process.Simulation.Batch;

/**
 * The kind of drink a bottle holds. The type is never chosen by the player:
 * it is derived from the production technology recorded in the batch — the raw
 * material (origin), whether the pot still separated the fractions, how many
 * times the spirit was distilled, whether it went through the rectification
 * column, charcoal filtration and oak aging.
 *
 * <p>Aromatic additives only change the type at the blending table and only
 * before oak aging: a rectified spirit macerated with a botanical bundle is
 * gin, the same spirit with wormwood (ferns) is absinthe, a sweetened and
 * flavoured blend at drinking strength is liqueur, and any other unaged
 * maceration is a tincture. Oak-aged blends keep the aged identity, so a
 * whiskey with apple stays whiskey.</p>
 */
public enum SpiritType {
    /** Unfermented sweet wort: an ingredient, not a drink. */
    WASH("wash", false),
    /**
     * Fermented wash. It can be drunk straight, but it is a rough, low-quality
     * drink; distilling it is what turns it into spirit.
     */
    BRAGA("braga", true),
    /** Pot-stilled without separating heads and tails: rough moonshine. */
    MOONSHINE("moonshine", true),
    /** Berry wash, fractionally pot-distilled, no oak. */
    CHACHA("chacha", true),
    /** Fractional grain spirit aged on oak. */
    WHISKEY("whiskey", true),
    /** Twice-distilled grain spirit aged on oak, the smoky single-malt family. */
    SCOTCH("scotch", true),
    /** Fractional sugar spirit aged on oak. */
    RUM("rum", true),
    /** Fractional berry spirit aged on oak after one distillation. */
    BRANDY("brandy", true),
    /** Fractional berry spirit aged on oak after two pot distillations. */
    COGNAC("cognac", true),
    /** Fractional apple spirit aged on oak. */
    CALVADOS("calvados", true),
    /** Fractional sugar-cane juice spirit, aged or not. */
    CACHACA("cachaca", true),
    /** Fractional potato spirit, aged or not. */
    POTATO("potato", true),
    /** Fractional beetroot spirit, aged or not. */
    BEET("beet", true),
    /** Fractional honey spirit, aged or not. */
    MEAD("mead", true),
    /** Fractional cocoa-bean spirit: dark and chocolate-scented, aged or not. */
    COCOA("cocoa", true),
    /** Fractional melon spirit: light and green-fruited, aged or not. */
    MELON("melon", true),
    /** Fractional carrot spirit: earthy-sweet, aged or not. */
    CARROT("carrot", true),
    /** Fractional cactus spirit: bright and vegetal, aged or not. */
    CACTUS("cactus", true),
    /** Column-rectified, charcoal-filtered and diluted to drinking strength. */
    VODKA("vodka", true),
    /** Rectified spirit that is not (yet) vodka. */
    SPIRIT("spirit", true),
    /** Rectified spirit redistilled with botanicals (kept for a future run). */
    GIN("gin", true),
    /** Botanical spirit of high strength from a pot still (kept for a future run). */
    ABSINTHE("absinthe", true),
    /** Sweet aromatic drink of low strength (kept for a future recipe). */
    LIQUEUR("liqueur", true),
    /** Spirit macerated with berries, herbs, spices or fruit; used by the tincture items. */
    TINCTURE("tincture", true),
    /** Top-fermented grain beer, brewed only in the brewery multiblock. */
    BEER("beer", true),
    /** Mild bread kvass, brewed in the brewery and fermented in the beer barrel. */
    KVASS("kvass", true),
    /** Unfermented grape must from the wine press: an ingredient, not a drink. */
    MUST("grape_must", false),
    /** Dry still wine from the winery: red, white or rosé. */
    WINE("wine", true),
    /** Sparkling wine made by the traditional second fermentation in the bottle. */
    SPARKLING_WINE("sparkling_wine", true);

    private final String id;
    private final boolean drink;

    /** The neutral botanical bundle used for gin. */
    private static final String GIN_BOTANICAL = "botanical_bundle";
    /** Ferns stand in for wormwood in absinthe. */
    private static final String[] ABSINTHE_HERBS = {"fern", "large_fern"};

    SpiritType(String id, boolean drink) {
        this.id = id;
        this.drink = drink;
    }

    public String id() {
        return id;
    }

    /** False only for wort, which is an ingredient rather than a drink. */
    public boolean isDrink() {
        return drink;
    }

    public String translationKey() {
        return "spirit.samogon." + id;
    }

    /** Translation key of the one-line description shown under the type. */
    public String descriptionKey() {
        return "desc.samogon." + id;
    }

    /**
     * Translation key describing what can eventually be made from a raw
     * material. Kept free of Minecraft types so the classifier can be tested
     * on a plain classpath.
     */
    public static String potentialKey(String origin) {
        return switch (origin == null ? "" : origin) {
            case "grain", "sugar", "berry", "honey",
                 "potato", "beetroot", "cane", "apple",
                 "cocoa", "melon", "carrot", "cactus" -> "potential.samogon." + origin;
            default -> "potential.samogon.sugar";
        };
    }

    /**
     * Classifies a batch by its production technology. First match wins.
     *
     * <p>Fermented but undistilled liquid is braga. An unaged aromatic blend
     * is classified by its additives: gin, absinthe, liqueur or tincture.
     * Otherwise a whole run without fraction separation stays moonshine no
     * matter the raw material; only a fractional run unlocks the origin-based
     * family. Column rectification and oak aging are checked first.</p>
     */
    public static SpiritType of(Batch batch) {
        if (batch == null) return WASH;

        // The winery chain is classified before the distillation taxonomy: a
        // must is an ingredient, a still wine is a drink and a sparkling wine
        // keeps the sweetness of its dosage. Distilled grape batches no longer
        // match these checks and fall through to brandy or cognac below.
        if (Wine.isMust(batch)) return MUST;
        if (Wine.isSparkling(batch)) return SPARKLING_WINE;
        if (Wine.isWine(batch)) return WINE;

        // Beer lives outside the distillation taxonomy: the brewery origin
        // identifies it directly, before the "not distilled yet" wash check.
        // The origin may carry a style suffix such as "beer:wheat:honey:ale".
        // A brew that has already been through a still or the column is a
        // spirit though, so it falls through and is classified by its grain.
        if (Brewing.isFinishedBeer(batch)) return BEER;
        // Kvass follows the same rule: only undistilled kvass keeps its type.
        if (Brewing.isFinishedKvass(batch)) return KVASS;
        String origin;
        if (Brewing.isBeerOrigin(batch.origin())) {
            // Every beer grain distils into the same grain-spirit family: the
            // mash tun records "grain" for malt, wheat and sugar alike, so a
            // distilled barley, wheat or roasted-malt beer must classify as
            // whiskey (or scotch after a second pass), not fall through to
            // brandy or moonshine.
            origin = "grain";
        } else if (Brewing.isKvassOrigin(batch.origin())) {
            // Distilled kvass counts as a grain spirit, not a beer.
            origin = "grain";
        } else {
            origin = batch.origin();
        }
        // A grape origin carries the colour and, for sparkling, the dosage
        // after the stage ("wine:red:brut"). Strip them so the family switch
        // below sees the plain raw material even after distillation.
        if (Wine.isGrapeOrigin(origin)) origin = Wine.stageOf(origin);

        // 0. Not distilled yet: wort or fermented wash.
        if (batch.passes() <= 0) {
            return batch.abv() > 0.5 ? BRAGA : WASH;
        }

        // 1. Aromatic maceration before oak aging. The cask defines the aged
        //    family, so only unaged blends are classified by their additives.
        if (batch.oak() <= 0 && !batch.composition().isBlank()) {
            return macerated(batch);
        }

        // 2. Column rectification: neutral spirit territory.
        if (batch.rectified()) {
            if (batch.oak() > 0) return SPIRIT;
            if (batch.filtered() && batch.abv() <= 50.0) return VODKA;
            return SPIRIT;
        }

        // 3. No fraction separation: a rough whole-run moonshine.
        if (!batch.cut()) return MOONSHINE;

        // 4. Oak aging: the aged-spirit family, chosen by the raw material.
        if (batch.oak() > 0) {
            return switch (origin) {
                case "berry" -> batch.passes() >= 2 ? COGNAC : BRANDY;
                case "wine", "sparkling_wine", "grape_must" -> batch.passes() >= 2 ? COGNAC : BRANDY;
                case "apple" -> CALVADOS;
                case "cane" -> CACHACA;
                case "grain" -> batch.passes() >= 2 ? SCOTCH : WHISKEY;
                case "sugar" -> RUM;
                case "potato" -> POTATO;
                case "beetroot" -> BEET;
                case "honey" -> MEAD;
                case "cocoa" -> COCOA;
                case "melon" -> MELON;
                case "carrot" -> CARROT;
                case "cactus" -> CACTUS;
                default -> BRANDY;
            };
        }

        // 5. Plain fractionally pot-stilled hearts.
        return switch (origin) {
            case "berry" -> CHACHA;
            case "wine", "sparkling_wine", "grape_must" -> BRANDY;
            case "cane" -> CACHACA;
            case "potato" -> POTATO;
            case "beetroot" -> BEET;
            case "honey" -> MEAD;
            case "cocoa" -> COCOA;
            case "melon" -> MELON;
            case "carrot" -> CARROT;
            case "cactus" -> CACTUS;
            default -> MOONSHINE;
        };
    }

    /**
     * Classifies an unaged aromatic blend. A rectified botanical spirit is gin;
     * adding wormwood (ferns) turns it into absinthe. A sweetened, flavoured
     * blend at drinking strength is a liqueur. Everything else is a tincture.
     *
     * <p>Gin and absinthe require column rectification because the player
     * macerates the botanicals in neutral spirit after the column. A pot-still
     * botanical maceration stays a tincture.</p>
     */
    private static SpiritType macerated(Batch batch) {
        String composition = batch.composition();
        if (batch.rectified() && AromaFamily.has(composition, GIN_BOTANICAL)) {
            return AromaFamily.hasAnyOf(composition, ABSINTHE_HERBS) ? ABSINTHE : GIN;
        }
        boolean sweetened = AromaFamily.hasFamily(composition, AromaFamily.Family.SWEET)
                || AromaFamily.hasFamily(composition, AromaFamily.Family.HONEY);
        boolean flavoured = AromaFamily.hasFamily(composition, AromaFamily.Family.BERRY)
                || AromaFamily.hasFamily(composition, AromaFamily.Family.FRUIT)
                || AromaFamily.hasFamily(composition, AromaFamily.Family.HONEY)
                || AromaFamily.hasFamily(composition, AromaFamily.Family.BOTANICAL)
                || AromaFamily.hasFamily(composition, AromaFamily.Family.SPICE);
        if (sweetened && flavoured && AromaFamily.additiveCount(composition) >= 2
                && batch.abv() <= 40.0) {
            return LIQUEUR;
        }
        return TINCTURE;
    }
}