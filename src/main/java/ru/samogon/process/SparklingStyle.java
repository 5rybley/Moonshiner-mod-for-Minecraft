package ru.samogon.process;

/**
 * The sparkling-wine methods the bottle rack can produce. The base wine decides
 * the method: a white wine makes a classic sparkling wine, Chardonnay makes a
 * blanc de blancs, a rosé makes a rosé sparkling wine and a red wine makes the
 * rare sparkling red.
 *
 * <p>The method is the fourth segment of the origin and the dosage stays last,
 * for example {@code sparkling_wine:white:chardonnay:blanc_de_blancs:brut}.
 * Old origins without the method simply fall back to the colour of the base
 * wine, so bottles from earlier saves keep their identity.</p>
 */
public enum SparklingStyle implements WineRecipe {
    /** Traditional method from a white base wine. */
    CLASSIC("classic", Wine.Color.WHITE, 3),
    /** Traditional method from Chardonnay only. */
    BLANC_DE_BLANCS("blanc_de_blancs", Wine.Color.WHITE, 3),
    /** Traditional method from a rosé base. */
    ROSE("rose", Wine.Color.ROSE, 3),
    /** Traditional method from a red base: a sparkling red. */
    RED("red", Wine.Color.RED, 3);

    private final String key;
    private final Wine.Color iconColor;
    private final int steps;

    SparklingStyle(String key, Wine.Color iconColor, int steps) {
        this.key = key;
        this.iconColor = iconColor;
        this.steps = steps;
    }

    @Override
    public String key() {
        return key;
    }

    @Override
    public String id() {
        return "sparkling_" + key;
    }

    /** Name of the finished bottle, with the variety and the dosage. */
    public String nameKey() {
        return "wine.samogon.sparkling." + key;
    }

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
        return true;
    }

    /**
     * The method implied by a base wine. A red base keeps the red colour, a
     * rosé stays rosé, and among white wines only Chardonnay earns the blanc de
     * blancs name; Riesling and other whites make the classic style.
     */
    public static SparklingStyle forBase(Wine.Color color, GrapeVariety variety) {
        Wine.Color charge = color == null ? Wine.Color.WHITE : color;
        if (charge == Wine.Color.RED) return RED;
        if (charge == Wine.Color.ROSE) return ROSE;
        if (variety == GrapeVariety.CHARDONNAY) return BLANC_DE_BLANCS;
        return CLASSIC;
    }

    /** Parses a method word from an origin; unknown words are not a method. */
    public static SparklingStyle parse(String id) {
        if (id != null) {
            String word = id.trim();
            for (SparklingStyle style : values()) {
                if (style.key.equalsIgnoreCase(word)) return style;
            }
        }
        return null;
    }
}