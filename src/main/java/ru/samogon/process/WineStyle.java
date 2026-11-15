package ru.samogon.process;

/**
 * The still-wine styles the winery can produce. The style is recorded as the
 * fourth segment of the batch origin, for example
 * {@code wine:red:cabernet:oaked}, so a bottle always knows how it was made.
 *
 * <p>The base wine is simply {@link #DRY}; every other style is a deliberate
 * treatment of that wine:</p>
 * <ul>
 *   <li>the cask can add oak ({@link #OAKED}) or sugar, and the amount of sugar
 *       chooses one of four sweetness bands — {@link #OFF_DRY},
 *       {@link #SEMI_SWEET}, {@link #SWEET} or {@link #DESSERT};</li>
 *   <li>the blending table can aromatize the wine with botanicals
 *       ({@link #VERMOUTH}), spices ({@link #MULLED}), fruit
 *       ({@link #SANGRIA}) or honey ({@link #HONEYED});</li>
 *   <li>two still wines can be married at the blending table into a
 *       {@link #CUVEE}, a blend of two varieties;</li>
 *   <li>a strong grape spirit fortifies it into {@link #PORT}, or into
 *       {@link #SHERRY} when the wine was oaked first.</li>
 * </ul>
 *
 * <p>The enum stays free of Minecraft types so the headless production-chain
 * test can load it on a plain classpath.</p>
 */
public enum WineStyle implements WineRecipe {
    /** Plain dry varietal wine: no cask treatment, no additives. */
    DRY("dry", Wine.Color.RED, 3, 0, 0, 0, 0),
    /** Dry wine rounded by oak chips in the aging cask. */
    OAKED("oaked", Wine.Color.RED, 3, 7, 0, 0, 0),
    /** One sugar per bottle: about 4-12 g/l of residual sugar. */
    OFF_DRY("off_dry", Wine.Color.WHITE, 3, 2, 0, 1, 1),
    /** Two or three sugars per bottle: about 12-45 g/l. */
    SEMI_SWEET("semi_sweet", Wine.Color.WHITE, 3, 3, 0, 2, 3),
    /** Four to six sugars per bottle: more than 45 g/l. */
    SWEET("sweet", Wine.Color.WHITE, 3, 4, -1, 4, 6),
    /** Seven or more sugars per bottle: a thick dessert wine. */
    DESSERT("dessert", Wine.Color.WHITE, 3, 5, -1, 7, 64),
    /** Dry wine aromatized with a botanical bundle. */
    VERMOUTH("vermouth", Wine.Color.WHITE, 3, 6, 0, 0, 0),
    /** Dry wine mulled with spices. */
    MULLED("mulled", Wine.Color.RED, 3, 5, 0, 0, 0),
    /** Dry wine blended with fruit or berries. */
    SANGRIA("sangria", Wine.Color.RED, 3, 4, 0, 0, 0),
    /** Dry wine sweetened and perfumed with honey. */
    HONEYED("honeyed", Wine.Color.WHITE, 3, 6, 0, 0, 0),
    /** Two still wines married at the blending table, a blend of two varieties. */
    CUVEE("cuvee", Wine.Color.ROSE, 3, 3, 0, 0, 0),
    /** Wine fortified with a strong grape spirit. */
    PORT("port", Wine.Color.RED, 4, 9, 4, 0, 0),
    /** Oaked wine fortified with a strong grape spirit. */
    SHERRY("sherry", Wine.Color.RED, 4, 8, 2, 0, 0);

    private final String key;
    private final Wine.Color iconColor;
    private final int steps;
    private final int qualityBonus;
    private final int abvBonus;
    private final int sugarMin;
    private final int sugarMax;

    WineStyle(String key, Wine.Color iconColor, int steps, int qualityBonus, int abvBonus,
              int sugarMin, int sugarMax) {
        this.key = key;
        this.iconColor = iconColor;
        this.steps = steps;
        this.qualityBonus = qualityBonus;
        this.abvBonus = abvBonus;
        this.sugarMin = sugarMin;
        this.sugarMax = sugarMax;
    }

    @Override
    public String key() {
        return key;
    }

    @Override
    public String id() {
        return "wine_" + key;
    }

    /** Name of the finished bottle: {@code "Дубовое вино «Каберне»"}. */
    public String nameKey() {
        return "wine.samogon.style." + key;
    }

    /** Short style name for the recipe list and the tooltip. */
    @Override
    public String titleKey() {
        return "guide.samogon.winemaker.recipe." + key + ".name";
    }

    @Override
    public String descriptionKey() {
        return "guide.samogon.winemaker.recipe." + key + ".desc";
    }

    @Override
    public String originKey() {
        return "guide.samogon.winemaker.recipe." + key + ".origin";
    }

    @Override
    public String[] recipeSteps() {
        String[] result = new String[steps];
        for (int i = 0; i < steps; i++) {
            result[i] = "guide.samogon.winemaker.recipe." + key + ".step" + (i + 1);
        }
        return result;
    }

    @Override
    public Wine.Color iconColor() {
        return iconColor;
    }

    @Override
    public boolean sparkling() {
        return false;
    }

    public int qualityBonus() {
        return qualityBonus;
    }

    /** Display-only strength hint; the actual port/sherry ABV is computed. */
    public int abvBonus() {
        return abvBonus;
    }

    public int sugarMin() {
        return sugarMin;
    }

    public int sugarMax() {
        return sugarMax;
    }

    /** True for the four styles chosen by the amount of sugar in the cask. */
    public boolean sweet() {
        return sugarMax > 0;
    }

    /**
     * Picks the sweetness band for a charge of sugar. The cask counts the sugar
     * items the player loaded: one is off-dry, two or three semi-sweet, four to
     * six sweet and a bigger charge makes a dessert wine.
     */
    public static WineStyle forSugar(int sugarItems) {
        if (sugarItems <= 0) return DRY;
        if (sugarItems == 1) return OFF_DRY;
        if (sugarItems <= 3) return SEMI_SWEET;
        if (sugarItems <= 6) return SWEET;
        return DESSERT;
    }

    /** Parses a style word from an origin; unknown words are not a style. */
    public static WineStyle parse(String id) {
        if (id != null) {
            String word = id.trim();
            for (WineStyle style : values()) {
                if (style.key.equalsIgnoreCase(word)) return style;
            }
        }
        return null;
    }

    /**
     * The style a blend composition creates, chosen by its dominant aroma
     * family: botanicals make vermouth, spices mull the wine, fruit and berries
     * make sangria and honey sweetens it. Any other family leaves the wine dry.
     *
     * <p>The mapping lives here rather than in {@code BlendCatalog} so the
     * headless production-chain test can verify it on a plain classpath,
     * without the Minecraft types the catalog needs for display names.</p>
     */
    public static WineStyle forComposition(String composition) {
        return switch (AromaFamily.dominant(composition)) {
            case BOTANICAL -> VERMOUTH;
            case SPICE -> MULLED;
            case BERRY, FRUIT, EXOTIC -> SANGRIA;
            case HONEY -> HONEYED;
            default -> null;
        };
    }
}