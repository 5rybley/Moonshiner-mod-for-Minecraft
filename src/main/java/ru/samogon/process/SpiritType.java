package ru.samogon.process;

import ru.samogon.process.Simulation.Batch;

/**
 * The kind of drink a bottle holds. The type is never chosen by the player:
 * it is derived from the production technology recorded in the batch — the raw
 * material (origin), whether the pot still separated the fractions, how many
 * times the spirit was distilled, whether it went through the rectification
 * column, charcoal filtration and oak aging.
 *
 * <p>Aromatic additives do not change the type: blending only changes the
 * bottle's name and aroma. A berry distillate stays chacha whether or not the
 * player adds berries, and a grain spirit aged on oak stays whiskey.</p>
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
    TINCTURE("tincture", true);

    private final String id;
    private final boolean drink;

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
     * <p>Fermented but undistilled liquid is braga. Once distilled, a whole
     * run without fraction separation stays moonshine no matter the raw
     * material; only a fractional run unlocks the origin-based family. Column
     * rectification and oak aging are checked first.</p>
     */
    public static SpiritType of(Batch batch) {
        if (batch == null) return WASH;

        // 0. Not distilled yet: wort or fermented wash.
        if (batch.passes() <= 0) {
            return batch.abv() > 0.5 ? BRAGA : WASH;
        }

        // 1. Column rectification: neutral spirit territory.
        if (batch.rectified()) {
            if (batch.oak() > 0) return SPIRIT;
            if (batch.filtered() && batch.abv() <= 50.0) return VODKA;
            return SPIRIT;
        }

        // 2. No fraction separation: a rough whole-run moonshine.
        if (!batch.cut()) return MOONSHINE;

        // 3. Oak aging: the aged-spirit family, chosen by the raw material.
        if (batch.oak() > 0) {
            return switch (batch.origin()) {
                case "berry" -> batch.passes() >= 2 ? COGNAC : BRANDY;
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

        // 4. Plain fractionally pot-stilled hearts.
        return switch (batch.origin()) {
            case "berry" -> CHACHA;
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
}