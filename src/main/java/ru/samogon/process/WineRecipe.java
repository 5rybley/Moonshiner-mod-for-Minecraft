package ru.samogon.process;

/**
 * One entry of the winemaker's recipe book. Both the still-wine styles and the
 * sparkling-wine methods implement this interface so the handbook can list them
 * in one tab without knowing which branch of the winery produced them.
 *
 * <p>The interface is deliberately free of Minecraft types: the guide supplies
 * the bottle icon from {@link #iconColor()} and {@link #sparkling()}, while the
 * production chain and the headless tests only need the ids and the keys.</p>
 */
public interface WineRecipe {
    /** Stable word used in the origin string, for example {@code "oaked"}. */
    String key();

    /** Discovery id, prefixed so it never clashes with a spirit or beer id. */
    String id();

    /** Short title shown in the recipe list and in the bottle tooltip. */
    String titleKey();

    /** One-paragraph explanation of the technique. */
    String descriptionKey();

    /** What the player has to load into which machine. */
    String originKey();

    /** Ordered translation keys of the recipe steps. */
    String[] recipeSteps();

    /** Bottle colour used by the handbook icon. */
    Wine.Color iconColor();

    /** True for a sparkling recipe, false for a still wine. */
    boolean sparkling();
}