package ru.samogon.process;

/**
 * The beer styles the brewery can produce. A style is fixed by the grain used
 * in the mash, the optional flavouring added at fermentation and the yeast
 * family — warm top fermentation (ale) or cold bottom fermentation (lager).
 * The brewery records all three as suffixes of the batch origin, so a mug
 * always knows which beer it holds.
 *
 * <p>The first three styles are basic and always listed in the brewer's guide;
 * the remaining styles are hidden until the player has actually brewed them.
 * The old three-part origins without a family still parse as the warm ale
 * fermentation, so mugs from earlier saves keep their style.</p>
 */
public enum BeerStyle {
    /** Pale barley ale: the plain, always-available baseline. */
    LIGHT("light", Grain.BARLEY, Additive.NONE, Family.ALE, true, 0.0, 0),
    /** Soft wheat ale. */
    WHEAT("wheat", Grain.WHEAT, Additive.NONE, Family.ALE, true, 0.3, 2),
    /** Dark ale from roasted malt: the stout. */
    DARK("dark", Grain.ROASTED, Additive.NONE, Family.ALE, true, 0.5, 4),
    /** Barley ale fermented with honey. */
    HONEY("honey", Grain.BARLEY, Additive.HONEY, Family.ALE, false, 1.0, 5),
    /** Wheat ale fermented with berries. */
    BERRY("berry", Grain.WHEAT, Additive.BERRY, Family.ALE, false, 0.2, 3),
    /** Barley ale fermented with apple. */
    APPLE("apple", Grain.BARLEY, Additive.APPLE, Family.ALE, false, 0.4, 3),
    /** Dry-hopped barley ale: bitter and aromatic. */
    IPA("ipa", Grain.BARLEY, Additive.HOP, Family.ALE, false, 0.8, 5),
    /** Dark ale from roasted malt and cocoa beans: the porter. */
    PORTER("porter", Grain.ROASTED, Additive.COCOA, Family.ALE, false, 0.9, 6),
    /** Pale barley lager: the crisp, cold-fermented pilsner. */
    PILSNER("pilsner", Grain.BARLEY, Additive.NONE, Family.LAGER, false, 0.2, 4),
    /** Roasted-malt lager: a black beer that is still clean and dry. */
    SCHWARZBIER("schwarz", Grain.ROASTED, Additive.NONE, Family.LAGER, false, 0.6, 5),
    /** Strong honey lager: a warming bock. */
    BOCK("bock", Grain.BARLEY, Additive.HONEY, Family.LAGER, false, 1.4, 7);

    /** The grain charge of the mash. */
    public enum Grain {
        BARLEY("barley", 64),
        WHEAT("wheat", 62),
        ROASTED("roasted", 68);

        private final String id;
        private final int mashQuality;

        Grain(String id, int mashQuality) {
            this.id = id;
            this.mashQuality = mashQuality;
        }

        public String id() {
            return id;
        }

        /** Base quality of the sweet wort this grain produces. */
        public int mashQuality() {
            return mashQuality;
        }

        public static Grain of(String id) {
            if (id != null) {
                for (Grain grain : values()) {
                    if (grain.id.equals(id)) return grain;
                }
            }
            return BARLEY;
        }
    }

    /** The optional flavour charge added at fermentation. */
    public enum Additive {
        NONE("none"),
        HONEY("honey"),
        BERRY("berry"),
        APPLE("apple"),
        HOP("hop"),
        COCOA("cocoa");

        private final String id;

        Additive(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public static Additive of(String id) {
            if (id != null) {
                for (Additive additive : values()) {
                    if (additive.id.equals(id)) return additive;
                }
            }
            return NONE;
        }
    }

    /**
     * The yeast family. A warm brew is a top-fermenting ale, a cold one a
     * bottom-fermenting lager; the origin stores the family so a mug keeps it.
     */
    public enum Family {
        ALE("ale"),
        LAGER("lager");

        private final String id;

        Family(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public static Family of(String id) {
            if (id != null) {
                for (Family family : values()) {
                    if (family.id.equals(id)) return family;
                }
            }
            return ALE;
        }
    }

    private final String key;
    private final Grain grain;
    private final Additive additive;
    private final Family family;
    private final boolean base;
    private final double abvBonus;
    private final int qualityBonus;

    BeerStyle(String key, Grain grain, Additive additive, Family family, boolean base,
              double abvBonus, int qualityBonus) {
        this.key = key;
        this.grain = grain;
        this.additive = additive;
        this.family = family;
        this.base = base;
        this.abvBonus = abvBonus;
        this.qualityBonus = qualityBonus;
    }

    /** Short id used in translation keys. */
    public String key() {
        return key;
    }

    /** Stable discovery id; prefixed so it never clashes with a spirit type id. */
    public String id() {
        return "beer_" + key;
    }

    /**
     * Item-model variant index. Zero is the base mug model (pale beer); every
     * other style maps to its own coloured fill via a custom model override.
     */
    public int variant() {
        return ordinal();
    }

    public String translationKey() {
        return "beer.samogon." + key;
    }

    public String descriptionKey() {
        return "desc.samogon.beer." + key;
    }

    /** Translation key of the raw-material line on the recipes tab. */
    public String originKey() {
        return "guide.samogon.brewer.style.origin." + key;
    }

    public Grain grain() {
        return grain;
    }

    public Additive additive() {
        return additive;
    }

    public Family family() {
        return family;
    }

    /** True when the style is readable from the start. */
    public boolean base() {
        return base;
    }

    public double abvBonus() {
        return abvBonus;
    }

    public int qualityBonus() {
        return qualityBonus;
    }

    /** Ordered translation keys of the four brewing steps. */
    public String[] recipeSteps() {
        return new String[]{
                "guide.samogon.brewer.recipe.step.mash." + grain.id(),
                "guide.samogon.brewer.recipe.step.boil",
                "guide.samogon.brewer.recipe.step.ferment." + additive.id(),
                "guide.samogon.brewer.recipe.step.family." + family.id()
        };
    }

    /**
     * Finds the style for a grain, additive and yeast family, falling back in a
     * fixed order: an exact match wins; a flavouring without a cold version is
     * served as its warm ale so the taste is not lost; then the family's
     * unflavoured staple; then the warm staple. This keeps every grain and
     * additive the barrel accepts resolvable to exactly one style.
     */
    public static BeerStyle of(String grainId, String additiveId, String familyId) {
        Grain grain = Grain.of(grainId);
        Additive additive = Additive.of(additiveId);
        Family family = Family.of(familyId);
        BeerStyle match = find(grain, additive, family);
        if (match != null) return match;
        match = find(grain, additive, Family.ALE);
        if (match != null) return match;
        match = find(grain, Additive.NONE, family);
        if (match != null) return match;
        match = find(grain, Additive.NONE, Family.ALE);
        if (match != null) return match;
        return LIGHT;
    }

    private static BeerStyle find(Grain grain, Additive additive, Family family) {
        for (BeerStyle style : values()) {
            if (style.grain == grain && style.additive == additive && style.family == family) {
                return style;
            }
        }
        return null;
    }

    /** Finds the style encoded in a batch origin. */
    public static BeerStyle of(String origin) {
        return of(Brewing.grainOf(origin), Brewing.additiveOf(origin), Brewing.familyOf(origin));
    }
}