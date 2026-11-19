package ru.samogon.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import ru.samogon.process.GrapeVariety;
import ru.samogon.process.SparklingStyle;
import ru.samogon.process.Wine;
import ru.samogon.process.WineRecipe;
import ru.samogon.process.WineStyle;
import ru.samogon.registry.ModBlocks;
import ru.samogon.registry.ModItems;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The winemaker's handbook: the same parchment spread and two top tabs as the
 * other guides, but every chapter belongs to the winery — grapes, pressing,
 * fermentation, the cask and the traditional method for sparkling wine. The
 * colours tab lists the three wines with the numbers taken from
 * {@link Wine.Color}, so the book and the machines never disagree.
 */
public final class WinemakersGuideScreen extends Screen {
    private static final int BOOK_W = 460;
    private static final int BOOK_H = 265;
    private static final int TEX_W = 920;
    private static final int TEX_H = 530;

    private static final int TAB_Y = 11;
    private static final int TAB_H = 22;
    private static final int TAB_GUIDE_X = 176;
    private static final int TAB_GUIDE_W = 84;
    private static final int TAB_COLOURS_X = 262;
    private static final int TAB_COLOURS_W = 78;
    private static final int TAB_RECIPES_X = 342;
    private static final int TAB_RECIPES_W = 96;

    private static final int LIST_X = 16;
    private static final int LIST_W = 152;
    private static final int LIST_TOP = 46;
    private static final int LIST_BOTTOM = 232;
    private static final int CHAPTER_STEP = 17;
    private static final int COLOUR_STEP = 38;
    private static final int RECIPE_STEP = 44;

    private static final int CONTENT_LEFT = 180;
    private static final int CONTENT_X = 184;
    private static final int CONTENT_W = 254;
    private static final int CONTENT_BOTTOM = 232;
    private static final int BODY_TOP = 74;
    private static final int BODY_BASE = 50;
    private static final int BODY_VIEW_H = CONTENT_BOTTOM - BODY_TOP;

    private static final ResourceLocation BOOK = ResourceLocation.fromNamespaceAndPath("samogon", "textures/gui/guide/book.png");
    private static final ResourceLocation ICONS = ResourceLocation.fromNamespaceAndPath("samogon", "textures/gui/guide/icons.png");

    private static final int INK = 0xFF3C2A18;
    private static final int SOFT = 0xFF72583A;
    private static final int COPPER = 0xFF8A4A22;
    private static final int COPPER_DARK = 0xFF663116;
    private static final int RED = 0xFF8A2B22;
    private static final int GREEN = 0xFF4F6039;
    private static final int PANEL = 0x1FA65A28;
    private static final int PANEL_BORDER = 0x558A4A22;
    private static final int BAND = 0xFFE2CDA1;
    private static final int WINE_WHITE = 0xFFE8E0CC;
    private static final int WINE_ROSE = 0xFFD98C8C;
    private static final int WINE_RED = 0xFF8A2B22;

    private static final GrapeVariety[] VARIETIES = GrapeVariety.values();

    /** Every still style followed by every sparkling method, in book order. */
    private static final List<WineRecipe> RECIPES = buildRecipes();

    /** 0 = guide, 1 = wines, 2 = recipes. */
    private int tab;
    private int pageIndex;
    private int colourIndex;
    private int recipeIndex;
    private int listScroll;
    private int contentScroll;
    private int listMaxScroll;
    private int contentMaxScroll;

    public WinemakersGuideScreen() {
        super(Component.translatable("guide.samogon.winemaker.title"));
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, 0xB0100B06);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        float scale = bookScale();
        int bx = bookX();
        int by = bookY();
        int mx = localX(mouseX);
        int my = localY(mouseY);

        g.pose().pushPose();
        g.pose().translate(bx, by, 0.0F);
        g.pose().scale(scale, scale, 1.0F);
        drawBook(g, mx, my);
        g.pose().popPose();
    }

    private float bookScale() {
        return Math.min(1.0F, Math.min((this.width - 8.0F) / BOOK_W, (this.height - 8.0F) / BOOK_H));
    }

    private int bookX() {
        return Math.round((this.width - BOOK_W * bookScale()) * 0.5F);
    }

    private int bookY() {
        return Math.round((this.height - BOOK_H * bookScale()) * 0.5F);
    }

    private int localX(double mouseX) {
        return Math.round((float) ((mouseX - bookX()) / bookScale()));
    }

    private int localY(double mouseY) {
        return Math.round((float) ((mouseY - bookY()) / bookScale()));
    }

    private void drawBook(GuiGraphics g, int mx, int my) {
        g.blit(BOOK, 0, 0, BOOK_W, BOOK_H, 0.0F, 0.0F, TEX_W, TEX_H, TEX_W, TEX_H);
        drawChrome(g, mx, my);
        if (tab == 0) {
            drawGuide(g, mx, my);
        } else if (tab == 1) {
            drawColours(g, mx, my);
        } else {
            drawRecipes(g, mx, my);
        }
        drawFooter(g);
    }

    // === TOP CHROME ===

    private void drawChrome(GuiGraphics g, int mx, int my) {
        g.drawString(this.font, Component.translatable("guide.samogon.winemaker.book.title"), 18, 15, COPPER_DARK, false);
        g.drawString(this.font, Component.translatable("guide.samogon.winemaker.book.subtitle"), 18, 26, SOFT, false);

        drawTab(g, TAB_GUIDE_X, TAB_GUIDE_W, "guide.samogon.winemaker.tab.guide", tab == 0, mx, my);
        drawTab(g, TAB_COLOURS_X, TAB_COLOURS_W, "guide.samogon.winemaker.tab.colours", tab == 1, mx, my);
        drawTab(g, TAB_RECIPES_X, TAB_RECIPES_W, "guide.samogon.winemaker.tab.recipes", tab == 2, mx, my);

        boolean closeHover = mx >= 442 && mx <= 456 && my >= 8 && my <= 28;
        g.drawString(this.font, "×", 446, 13, closeHover ? RED : COPPER, false);

        g.fill(16, 35, 444, 36, PANEL_BORDER);
    }

    private void drawTab(GuiGraphics g, int x, int w, String key, boolean active, int mx, int my) {
        boolean hover = mx >= x && mx <= x + w && my >= TAB_Y && my <= TAB_Y + TAB_H;
        if (active) {
            g.fill(x, TAB_Y, x + w, TAB_Y + TAB_H, 0x33A65A28);
            g.fill(x, TAB_Y + TAB_H - 2, x + w, TAB_Y + TAB_H, COPPER);
        } else if (hover) {
            g.fill(x, TAB_Y, x + w, TAB_Y + TAB_H, 0x18A65A28);
        }
        Component label = Component.translatable(key);
        int color = active ? COPPER_DARK : SOFT;
        g.drawString(this.font, label, x + (w - this.font.width(label)) / 2, TAB_Y + 7, color, false);
    }

    private void drawFooter(GuiGraphics g) {
        g.fill(CONTENT_LEFT, 236, CONTENT_LEFT + CONTENT_W, 237, 0x338A4A22);
        int centerX = CONTENT_LEFT + CONTENT_W / 2;
        if (tab == 1) {
            Component progress = Component.translatable("guide.samogon.winemaker.colours.progress",
                    colourIndex + 1, VARIETIES.length);
            centered(g, progress, centerX, 243, SOFT);
        } else if (tab == 2) {
            Component progress = Component.translatable("guide.samogon.winemaker.recipes.progress",
                    recipeIndex + 1, RECIPES.size());
            centered(g, progress, centerX, 243, SOFT);
        } else {
            centered(g, Component.translatable("guide.samogon.winemaker.scroll"), centerX, 243, SOFT);
        }
    }

    // === GUIDE TAB ===

    private void drawGuide(GuiGraphics g, int mx, int my) {
        drawGuideList(g, mx, my);
        Page page = Page.values()[pageIndex];
        drawContentHeader(g, page.titleKey, page.tagKey,
                String.format(Locale.ROOT, "%02d / %02d", pageIndex + 1, Page.values().length));

        beginClip(g, CONTENT_LEFT, BODY_TOP, CONTENT_W + 4, BODY_VIEW_H);
        g.pose().pushPose();
        g.pose().translate(0.0F, (float) (BODY_TOP - BODY_BASE - contentScroll), 0.0F);
        int bottom = drawPageBody(g, page);
        g.pose().popPose();
        g.disableScissor();

        contentMaxScroll = Math.max(0, (bottom - BODY_BASE) - BODY_VIEW_H);
        contentScroll = clamp(contentScroll, 0, contentMaxScroll);
        drawScrollbar(g, CONTENT_LEFT + CONTENT_W + 1, BODY_TOP, CONTENT_BOTTOM, contentScroll, contentMaxScroll);
    }

    private void drawGuideList(GuiGraphics g, int mx, int my) {
        int count = Page.values().length;
        int total = count * CHAPTER_STEP;
        int view = LIST_BOTTOM - LIST_TOP;
        listMaxScroll = Math.max(0, total - view);
        listScroll = clamp(listScroll, 0, listMaxScroll);

        beginClip(g, LIST_X, LIST_TOP, LIST_W, view);
        for (int i = 0; i < count; i++) {
            int y = LIST_TOP - listScroll + i * CHAPTER_STEP;
            if (y + CHAPTER_STEP < LIST_TOP || y > LIST_BOTTOM) continue;
            boolean active = i == pageIndex;
            boolean hover = mx >= LIST_X && mx <= LIST_X + LIST_W && my >= y && my <= y + CHAPTER_STEP - 2;
            if (active) {
                g.fill(LIST_X - 1, y, LIST_X + LIST_W, y + CHAPTER_STEP - 2, 0x30A65A28);
                g.fill(LIST_X - 1, y, LIST_X + 1, y + CHAPTER_STEP - 2, COPPER);
            } else if (hover) {
                g.fill(LIST_X - 1, y, LIST_X + LIST_W, y + CHAPTER_STEP - 2, 0x18A65A28);
            }
            drawItem(g, pageIcon(Page.values()[i]), LIST_X + 2, y, 14);
            g.drawString(this.font, Component.translatable(Page.values()[i].tocKey),
                    LIST_X + 20, y + 2, active ? COPPER_DARK : INK, false);
        }
        g.disableScissor();
        drawScrollbar(g, LIST_X + LIST_W + 1, LIST_TOP, LIST_BOTTOM, listScroll, listMaxScroll);
    }

    private void drawContentHeader(GuiGraphics g, String titleKey, String tagKey, String counter) {
        g.drawString(this.font, Component.translatable(titleKey).withStyle(ChatFormatting.BOLD),
                CONTENT_X, 46, COPPER_DARK, false);
        g.drawString(this.font, counter, CONTENT_LEFT + CONTENT_W - this.font.width(counter), 47, SOFT, false);
        g.drawString(this.font, Component.translatable(tagKey), CONTENT_X, 57, SOFT, false);
        g.fill(CONTENT_LEFT, 69, CONTENT_LEFT + CONTENT_W, 70, PANEL_BORDER);
    }

    private int drawPageBody(GuiGraphics g, Page page) {
        return switch (page) {
            case OVERVIEW -> drawOverview(g);
            case GRAPES -> drawGrapes(g);
            case PRESS -> drawPress(g);
            case FERMENT -> drawFerment(g);
            case AGE -> drawAge(g);
            case SPARKLING -> drawSparkling(g);
            case DOSAGE -> drawDosage(g);
            case TROUBLE -> drawTrouble(g);
            case MEMO -> drawMemo(g);
        };
    }

    // === WINES TAB ===

    private void drawColours(GuiGraphics g, int mx, int my) {
        colourIndex = clamp(colourIndex, 0, VARIETIES.length - 1);
        drawColourList(g, mx, my);

        String counter = String.format(Locale.ROOT, "%02d / %02d", colourIndex + 1, VARIETIES.length);
        drawContentHeader(g, "guide.samogon.winemaker.varieties.title",
                "guide.samogon.winemaker.varieties.tag", counter);

        beginClip(g, CONTENT_LEFT, BODY_TOP, CONTENT_W + 4, BODY_VIEW_H);
        g.pose().pushPose();
        g.pose().translate(0.0F, (float) (BODY_TOP - BODY_BASE - contentScroll), 0.0F);
        int bottom = drawColourBody(g, VARIETIES[colourIndex]);
        g.pose().popPose();
        g.disableScissor();

        contentMaxScroll = Math.max(0, (bottom - BODY_BASE) - BODY_VIEW_H);
        contentScroll = clamp(contentScroll, 0, contentMaxScroll);
        drawScrollbar(g, CONTENT_LEFT + CONTENT_W + 1, BODY_TOP, CONTENT_BOTTOM, contentScroll, contentMaxScroll);
    }

    private void drawColourList(GuiGraphics g, int mx, int my) {
        int total = VARIETIES.length * COLOUR_STEP;
        int view = LIST_BOTTOM - LIST_TOP;
        listMaxScroll = Math.max(0, total - view);
        listScroll = clamp(listScroll, 0, listMaxScroll);

        beginClip(g, LIST_X, LIST_TOP, LIST_W, view);
        for (int i = 0; i < VARIETIES.length; i++) {
            GrapeVariety variety = VARIETIES[i];
            int y = LIST_TOP - listScroll + i * COLOUR_STEP;
            if (y + COLOUR_STEP < LIST_TOP || y > LIST_BOTTOM) continue;
            boolean active = i == colourIndex;
            boolean hover = mx >= LIST_X && mx <= LIST_X + LIST_W && my >= y && my <= y + COLOUR_STEP - 4;
            if (active) {
                g.fill(LIST_X - 1, y, LIST_X + LIST_W, y + COLOUR_STEP - 4, 0x30A65A28);
                g.fill(LIST_X - 1, y, LIST_X + 1, y + COLOUR_STEP - 4, COPPER);
            } else if (hover) {
                g.fill(LIST_X - 1, y, LIST_X + LIST_W, y + COLOUR_STEP - 4, 0x18A65A28);
            }
            drawItem(g, wineBottle(variety.color(), false), LIST_X + 1, y + 1, 18);
            Component name = Component.translatable(variety.nameKey()).withStyle(ChatFormatting.BOLD);
            var nameLines = this.font.split(name, LIST_W - 26);
            for (int line = 0; line < nameLines.size() && line < 2; line++) {
                g.drawString(this.font, nameLines.get(line), LIST_X + 24, y + 1 + line * 9,
                        active ? COPPER_DARK : INK, false);
            }
            int originY = y + 1 + Math.min(2, nameLines.size()) * 9 + 1;
            // Wrap the origin line exactly like the recipe list does: a long
            // "red grapes, cabernet..." used to run into the divider and be
            // cut mid-word, and two wrapped lines under a two-line name would
            // spill into the next entry. A name that already took both of its
            // lines leaves the origin a single ellipsised line.
            List<String> originLines = wrapText(
                    Component.translatable(colourOriginKey(variety)).getString(), LIST_W - 26);
            int originRows = nameLines.size() >= 2 ? 1 : 2;
            for (int line = 0; line < originLines.size() && line < originRows; line++) {
                String text = originLines.get(line);
                if (line == originRows - 1 && originLines.size() > originRows) {
                    String dots = "\u2026";
                    text = this.font.plainSubstrByWidth(text, LIST_W - 26 - this.font.width(dots))
                            .stripTrailing() + dots;
                }
                g.drawString(this.font, text, LIST_X + 24, originY + line * 9, SOFT, false);
            }
        }
        g.disableScissor();
        drawScrollbar(g, LIST_X + LIST_W + 1, LIST_TOP, LIST_BOTTOM, listScroll, listMaxScroll);
    }

    private int drawColourBody(GuiGraphics g, GrapeVariety variety) {
        int x = CONTENT_X;
        int w = CONTENT_W;
        int y = BODY_BASE;

        drawItem(g, wineBottle(variety.color(), false), x, y, 30);
        g.drawString(this.font, Component.translatable(variety.nameKey()).withStyle(ChatFormatting.BOLD),
                x + 38, y + 1, COPPER_DARK, false);
        String descriptionKey = "guide.samogon.winemaker.variety." + variety.id() + ".description";
        text(g, descriptionKey, x + 38, y + 12, w - 38, SOFT);
        y = Math.max(y + 30, y + 12 + textHeight(descriptionKey, w - 38)) + 6;

        Component origin = Component.translatable("guide.samogon.winemaker.colour.origin",
                Component.translatable(colourOriginKey(variety)));
        y += textComponent(g, origin, x, y, w, INK) + 2;

        y += textComponent(g, Component.translatable("guide.samogon.winemaker.colour.ideal",
                String.format(Locale.ROOT, "%.0f", variety.idealTemperature())), x, y, w, INK) + 2;
        y += textComponent(g, Component.translatable("guide.samogon.winemaker.colour.strength",
                String.format(Locale.ROOT, "%.1f", variety.baseAbv())), x, y, w, INK) + 2;
        y += textComponent(g, Component.translatable("guide.samogon.winemaker.colour.must",
                variety.mustQuality()), x, y, w, INK) + 4;

        g.drawString(this.font, Component.translatable("guide.samogon.winemaker.colour.steps")
                .withStyle(ChatFormatting.BOLD), x, y, COPPER_DARK, false);
        y += 12;
        for (int i = 1; i <= 3; i++) {
            String key = "guide.samogon.winemaker.variety." + variety.id() + ".step" + i;
            g.fill(x, y + 1, x + 2, y + 8, COPPER);
            g.drawString(this.font, String.valueOf(i), x + 5, y, COPPER_DARK, false);
            int height = textHeight(key, w - 18);
            text(g, key, x + 18, y, w - 18, INK);
            y += Math.max(10, height) + 3;
        }

        int noteY = y + 2;
        int noteH = 7 + textHeight("guide.samogon.winemaker.colour.note", w - 25);
        panel(g, x, noteY, w, noteH);
        drawIcon(g, 6, x + 5, noteY + 5, 10);
        text(g, "guide.samogon.winemaker.colour.note", x + 20, noteY + 4, w - 25, INK);
        return noteY + noteH;
    }

    /** Origin line of a grape variety, for example "red grapes, skins on". */
    private static String colourOriginKey(GrapeVariety variety) {
        return "guide.samogon.winemaker.variety." + variety.id() + ".origin";
    }

    // === RECIPES TAB ===

    private static List<WineRecipe> buildRecipes() {
        List<WineRecipe> result = new ArrayList<>();
        result.addAll(List.of(WineStyle.values()));
        result.addAll(List.of(SparklingStyle.values()));
        return List.copyOf(result);
    }

    private void drawRecipes(GuiGraphics g, int mx, int my) {
        recipeIndex = clamp(recipeIndex, 0, RECIPES.size() - 1);
        drawRecipeList(g, mx, my);

        String counter = String.format(Locale.ROOT, "%02d / %02d", recipeIndex + 1, RECIPES.size());
        drawContentHeader(g, "guide.samogon.winemaker.recipes.title",
                "guide.samogon.winemaker.recipes.tag", counter);

        beginClip(g, CONTENT_LEFT, BODY_TOP, CONTENT_W + 4, BODY_VIEW_H);
        g.pose().pushPose();
        g.pose().translate(0.0F, (float) (BODY_TOP - BODY_BASE - contentScroll), 0.0F);
        int bottom = drawRecipeBody(g, RECIPES.get(recipeIndex));
        g.pose().popPose();
        g.disableScissor();

        contentMaxScroll = Math.max(0, (bottom - BODY_BASE) - BODY_VIEW_H);
        contentScroll = clamp(contentScroll, 0, contentMaxScroll);
        drawScrollbar(g, CONTENT_LEFT + CONTENT_W + 1, BODY_TOP, CONTENT_BOTTOM,
                contentScroll, contentMaxScroll);
    }

    private void drawRecipeList(GuiGraphics g, int mx, int my) {
        int total = RECIPES.size() * RECIPE_STEP;
        int view = LIST_BOTTOM - LIST_TOP;
        listMaxScroll = Math.max(0, total - view);
        listScroll = clamp(listScroll, 0, listMaxScroll);

        beginClip(g, LIST_X, LIST_TOP, LIST_W, view);
        for (int i = 0; i < RECIPES.size(); i++) {
            WineRecipe recipe = RECIPES.get(i);
            int y = LIST_TOP - listScroll + i * RECIPE_STEP;
            if (y + RECIPE_STEP < LIST_TOP || y > LIST_BOTTOM) continue;
            boolean active = i == recipeIndex;
            boolean hover = mx >= LIST_X && mx <= LIST_X + LIST_W && my >= y && my <= y + RECIPE_STEP - 4;
            if (active) {
                g.fill(LIST_X - 1, y, LIST_X + LIST_W, y + RECIPE_STEP - 4, 0x30A65A28);
                g.fill(LIST_X - 1, y, LIST_X + 1, y + RECIPE_STEP - 4, COPPER);
            } else if (hover) {
                g.fill(LIST_X - 1, y, LIST_X + LIST_W, y + RECIPE_STEP - 4, 0x18A65A28);
            }
            drawItem(g, recipeIcon(recipe), LIST_X + 1, y + 1, 18);
            Component name = Component.translatable(recipe.titleKey()).withStyle(ChatFormatting.BOLD);
            var nameLines = this.font.split(name, LIST_W - 26);
            for (int line = 0; line < nameLines.size() && line < 2; line++) {
                g.drawString(this.font, nameLines.get(line), LIST_X + 24, y + 1 + line * 9,
                        active ? COPPER_DARK : INK, false);
            }
            int originY = y + 1 + Math.min(2, nameLines.size()) * 9 + 1;
            List<String> originLines = wrapText(
                    Component.translatable(recipe.originKey()).getString(), LIST_W - 26);
            for (int line = 0; line < originLines.size() && line < 2; line++) {
                String text = originLines.get(line);
                if (line == 1 && originLines.size() > 2) {
                    String dots = "\u2026";
                    text = this.font.plainSubstrByWidth(text, LIST_W - 26 - this.font.width(dots))
                            .stripTrailing() + dots;
                }
                g.drawString(this.font, text, LIST_X + 24, originY + line * 9, SOFT, false);
            }
        }
        g.disableScissor();
        drawScrollbar(g, LIST_X + LIST_W + 1, LIST_TOP, LIST_BOTTOM, listScroll, listMaxScroll);
    }

    /**
     * Splits the short origin line into as many lines as the list column needs,
     * so the text under a recipe name wraps instead of being cut off at the
     * scrollbar. The caller draws two lines and shortens the second with an
     * ellipsis when the recipe needs more.
     */
    private List<String> wrapText(String text, int width) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (word.isEmpty()) continue;
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (line.length() > 0 && this.font.width(candidate) > width) {
                lines.add(line.toString());
                line.setLength(0);
                line.append(word);
            } else {
                line.setLength(0);
                line.append(candidate);
            }
        }
        if (line.length() > 0) lines.add(line.toString());
        return lines;
    }

    private int drawRecipeBody(GuiGraphics g, WineRecipe recipe) {
        int x = CONTENT_X;
        int w = CONTENT_W;
        int y = BODY_BASE;

        drawItem(g, recipeIcon(recipe), x, y, 30);
        g.drawString(this.font, Component.translatable(recipe.titleKey()).withStyle(ChatFormatting.BOLD),
                x + 38, y + 1, COPPER_DARK, false);
        text(g, recipe.descriptionKey(), x + 38, y + 12, w - 38, SOFT);
        y = Math.max(y + 30, y + 12 + textHeight(recipe.descriptionKey(), w - 38)) + 6;

        Component origin = Component.translatable("guide.samogon.winemaker.recipes.origin",
                Component.translatable(recipe.originKey()));
        y += textComponent(g, origin, x, y, w, INK) + 5;

        g.drawString(this.font, Component.translatable("guide.samogon.winemaker.recipes.steps")
                .withStyle(ChatFormatting.BOLD), x, y, COPPER_DARK, false);
        y += 12;
        String[] steps = recipe.recipeSteps();
        for (int i = 0; i < steps.length; i++) {
            String key = steps[i];
            g.fill(x, y + 1, x + 2, y + 8, COPPER);
            g.drawString(this.font, String.valueOf(i + 1), x + 5, y, COPPER_DARK, false);
            int height = textHeight(key, w - 18);
            text(g, key, x + 18, y, w - 18, INK);
            y += Math.max(10, height) + 3;
        }
        return y;
    }

    /** The book shows the finished bottle, not the raw ingredients. */
    private ItemStack recipeIcon(WineRecipe recipe) {
        return wineBottle(recipe.iconColor(), recipe.sparkling());
    }

    // === PAGE BODIES ===

    private int drawOverview(GuiGraphics g) {
        int x = CONTENT_X;
        int w = CONTENT_W;
        text(g, "guide.samogon.winemaker.page.overview.lead", x, BODY_BASE, w, INK);
        int rowY = BODY_BASE + textHeight("guide.samogon.winemaker.page.overview.lead", w) + 4;

        ItemStack[] stages = {
                new ItemStack(ModBlocks.WINE_PRESS.get()),
                new ItemStack(ModBlocks.FERMENTATION_BARREL.get()),
                new ItemStack(ModBlocks.AGING_CASK.get()),
                new ItemStack(ModBlocks.BOTTLE_RACK.get())
        };
        int lastBottom = rowY;
        for (int i = 0; i < stages.length; i++) {
            String subKey = "guide.samogon.winemaker.page.overview.flow" + (i + 1) + ".sub";
            int rowH = Math.max(22, 10 + textHeight(subKey, w - 28));
            drawItem(g, stages[i], x + 1, rowY - 2, 22);
            g.drawString(this.font, Component.translatable("guide.samogon.winemaker.page.overview.flow" + (i + 1))
                    .withStyle(ChatFormatting.BOLD), x + 28, rowY, COPPER_DARK, false);
            text(g, subKey, x + 28, rowY + 10, w - 28, SOFT);
            lastBottom = rowY + rowH;
            boolean last = i == stages.length - 1;
            if (!last) drawIcon(g, 1, x + 8, lastBottom + 1, 7);
            rowY = lastBottom + (last ? 0 : 10);
        }

        int tipY = lastBottom + 4;
        int tipH = 7 + textHeight("guide.samogon.winemaker.page.overview.tip", w - 25);
        panel(g, x, tipY, w, tipH);
        drawIcon(g, 6, x + 5, tipY + 5, 10);
        text(g, "guide.samogon.winemaker.page.overview.tip", x + 20, tipY + 4, w - 25, INK);
        return tipY + tipH;
    }

    private int drawGrapes(GuiGraphics g) {
        int x = CONTENT_X;
        int w = CONTENT_W;
        ItemStack[] raw = {
                new ItemStack(ModItems.CABERNET_GRAPES.get()),
                new ItemStack(ModItems.CHARDONNAY_GRAPES.get()),
                new ItemStack(Items.SUGAR),
                new ItemStack(ModItems.YEAST.get()),
                new ItemStack(Items.GLASS_BOTTLE),
                new ItemStack(ModItems.OAK_CHIPS.get())
        };
        String[] keys = {
                "guide.samogon.winemaker.page.grapes.red",
                "guide.samogon.winemaker.page.grapes.white",
                "guide.samogon.winemaker.page.grapes.sugar",
                "guide.samogon.winemaker.page.grapes.yeast",
                "guide.samogon.winemaker.page.grapes.bottle",
                "guide.samogon.winemaker.page.grapes.oak"
        };
        int y = BODY_BASE;
        for (int i = 0; i < raw.length; i++) {
            drawItem(g, raw[i], x, y + 1, 16);
            text(g, keys[i], x + 21, y, w - 21, INK);
            y += Math.max(16, textHeight(keys[i], w - 21)) + 2;
        }

        int summaryH = 7 + textHeight("guide.samogon.winemaker.page.grapes.summary", w - 8);
        panel(g, x, y + 2, w, summaryH);
        text(g, "guide.samogon.winemaker.page.grapes.summary", x + 4, y + 5, w - 8, INK);
        y += summaryH + 5;

        int tipH = 7 + textHeight("guide.samogon.winemaker.page.grapes.tip", w - 20);
        panel(g, x, y + 2, w, tipH);
        drawIcon(g, 8, x + 5, y + 5, 10);
        text(g, "guide.samogon.winemaker.page.grapes.tip", x + 20, y + 4, w - 25, INK);
        return y + tipH + 2;
    }

    private int drawPress(GuiGraphics g) {
        int x = CONTENT_X;
        int w = CONTENT_W;
        drawItem(g, new ItemStack(ModBlocks.WINE_PRESS.get()), 186, 50, 36);
        drawIcon(g, 0, 230, 64, 14);
        drawItem(g, new ItemStack(ModItems.CABERNET_GRAPES.get()), 252, 58, 26);
        drawItem(g, new ItemStack(ModItems.CHARDONNAY_GRAPES.get()), 292, 58, 26);
        drawItem(g, new ItemStack(ModItems.GRAPE_MUST.get()), 332, 56, 28);
        centered(g, Component.translatable("guide.samogon.winemaker.page.press.label.press"), 204, 88, COPPER_DARK);
        centered(g, Component.translatable("guide.samogon.winemaker.page.press.label.grapes"), 265, 88, COPPER_DARK);
        centered(g, Component.translatable("guide.samogon.winemaker.page.press.label.must"), 346, 88, COPPER_DARK);

        int y = 104;
        y += step(g, x, y, w, 3, "guide.samogon.winemaker.page.press.step1", INK) + 5;
        y += step(g, x, y, w, 11, "guide.samogon.winemaker.page.press.step2", INK) + 5;
        y += step(g, x, y, w, 5, "guide.samogon.winemaker.page.press.step3", INK) + 5;

        int tipH = 7 + textHeight("guide.samogon.winemaker.page.press.tip", w - 25);
        panel(g, x, y, w, tipH);
        drawIcon(g, 6, x + 5, y + 5, 10);
        text(g, "guide.samogon.winemaker.page.press.tip", x + 20, y + 4, w - 25, INK);

        int noteY = y + tipH + 3;
        if (noteY < 226) {
            note(g, 9, x, noteY, w, "guide.samogon.winemaker.page.press.note", GREEN);
            return noteY + textHeight("guide.samogon.winemaker.page.press.note", w - 11);
        }
        return y + tipH;
    }

    private int drawFerment(GuiGraphics g) {
        int x = CONTENT_X;
        int w = CONTENT_W;
        drawItem(g, new ItemStack(ModBlocks.FERMENTATION_BARREL.get()), 186, 50, 36);
        drawIcon(g, 0, 230, 64, 14);
        drawItem(g, new ItemStack(ModItems.YEAST.get()), 252, 58, 26);
        drawItem(g, wineBottle(Wine.Color.RED, false), 292, 54, 30);
        centered(g, Component.translatable("guide.samogon.winemaker.page.ferment.label.barrel"), 204, 88, COPPER_DARK);
        centered(g, Component.translatable("guide.samogon.winemaker.page.ferment.label.yeast"), 265, 88, COPPER_DARK);
        centered(g, Component.translatable("guide.samogon.winemaker.page.ferment.label.wine"), 307, 88, COPPER_DARK);

        int gaugeY = 101;
        int gaugeH = 9;
        int from = x + Math.round(w * 0.06F);
        int to = x + Math.round(w * 0.94F);
        g.fill(x, gaugeY, x + w, gaugeY + gaugeH, BAND);
        g.fill(from, gaugeY, to, gaugeY + gaugeH, 0xFFE8D5AC);
        // The three cellar temperatures: white is the cool end, red the warm.
        mark(g, gaugeY, gaugeH, from, to, 16.0, WINE_WHITE, "16°");
        mark(g, gaugeY, gaugeH, from, to, 19.0, WINE_ROSE, "19°");
        mark(g, gaugeY, gaugeH, from, to, 24.0, WINE_RED, "24°");

        // The caption sits one line below the tick labels and wraps to the
        // content column so the two can never share pixels.
        String captionKey = "guide.samogon.winemaker.page.ferment.gauge.caption";
        int captionY = 122;
        int captionH = textHeight(captionKey, w);
        text(g, captionKey, x, captionY, w, SOFT);

        int y = captionY + captionH + 4;
        y += step(g, x, y, w, 4, "guide.samogon.winemaker.page.ferment.step1", INK) + 4;
        y += step(g, x, y, w, 10, "guide.samogon.winemaker.page.ferment.step2", INK) + 4;
        y += step(g, x, y, w, 5, "guide.samogon.winemaker.page.ferment.step3", INK) + 4;

        int tipH = 7 + textHeight("guide.samogon.winemaker.page.ferment.tip", w - 25);
        panel(g, x, y, w, tipH);
        drawIcon(g, 7, x + 5, y + 5, 10);
        text(g, "guide.samogon.winemaker.page.ferment.tip", x + 20, y + 4, w - 25, RED);
        return y + tipH;
    }

    /** One temperature mark on the fermentation gauge. */
    private void mark(GuiGraphics g, int gaugeY, int gaugeH, int from, int to,
                      double temperature, int color, String label) {
        int px = from + Math.round((float) ((temperature - 8.0) / (30.0 - 8.0) * (to - from)));
        g.fill(px - 1, gaugeY - 2, px + 1, gaugeY + gaugeH + 2, color);
        g.drawString(this.font, label, px - 5, 112, COPPER_DARK, false);
    }

    private int drawAge(GuiGraphics g) {
        int x = CONTENT_X;
        int w = CONTENT_W;
        drawItem(g, new ItemStack(ModBlocks.AGING_CASK.get()), 186, 50, 36);
        drawIcon(g, 0, 230, 64, 14);
        drawItem(g, wineBottle(Wine.Color.RED, false), 252, 56, 28);
        drawItem(g, new ItemStack(ModItems.OAK_CHIPS.get()), 292, 58, 26);
        drawItem(g, wineBottle(Wine.Color.RED, false), 332, 54, 30);
        centered(g, Component.translatable("guide.samogon.winemaker.page.age.label.cask"), 204, 88, COPPER_DARK);
        centered(g, Component.translatable("guide.samogon.winemaker.page.age.label.wine"), 265, 88, COPPER_DARK);
        centered(g, Component.translatable("guide.samogon.winemaker.page.age.label.chips"), 305, 88, COPPER_DARK);
        centered(g, Component.translatable("guide.samogon.winemaker.page.age.label.aged"), 346, 88, COPPER_DARK);

        int y = 104;
        y += step(g, x, y, w, 9, "guide.samogon.winemaker.page.age.step1", INK) + 4;
        y += step(g, x, y, w, 5, "guide.samogon.winemaker.page.age.step2", INK) + 4;
        y += step(g, x, y, w, 11, "guide.samogon.winemaker.page.age.step3", INK) + 4;

        int tipH = 7 + textHeight("guide.samogon.winemaker.page.age.tip", w - 25);
        panel(g, x, y, w, tipH);
        drawIcon(g, 6, x + 5, y + 5, 10);
        text(g, "guide.samogon.winemaker.page.age.tip", x + 20, y + 4, w - 25, INK);
        return y + tipH;
    }

    private int drawSparkling(GuiGraphics g) {
        int x = CONTENT_X;
        int w = CONTENT_W;
        drawItem(g, new ItemStack(ModBlocks.BOTTLE_RACK.get()), 186, 50, 36);
        drawIcon(g, 0, 230, 64, 14);
        drawItem(g, wineBottle(Wine.Color.WHITE, false), 252, 56, 28);
        drawItem(g, new ItemStack(Items.SUGAR), 292, 58, 26);
        drawItem(g, wineBottle(Wine.Color.WHITE, true), 332, 54, 30);
        centered(g, Component.translatable("guide.samogon.winemaker.page.sparkling.label.rack"), 204, 88, COPPER_DARK);
        centered(g, Component.translatable("guide.samogon.winemaker.page.sparkling.label.wine"), 265, 88, COPPER_DARK);
        centered(g, Component.translatable("guide.samogon.winemaker.page.sparkling.label.sugar"), 305, 88, COPPER_DARK);
        centered(g, Component.translatable("guide.samogon.winemaker.page.sparkling.label.sparkling"), 346, 88, COPPER_DARK);

        int y = 104;
        y += step(g, x, y, w, 4, "guide.samogon.winemaker.page.sparkling.step1", INK) + 4;
        y += step(g, x, y, w, 11, "guide.samogon.winemaker.page.sparkling.step2", INK) + 4;
        y += step(g, x, y, w, 5, "guide.samogon.winemaker.page.sparkling.step3", INK) + 4;

        int tipH = 7 + textHeight("guide.samogon.winemaker.page.sparkling.tip", w - 25);
        panel(g, x, y, w, tipH);
        drawIcon(g, 7, x + 5, y + 5, 10);
        text(g, "guide.samogon.winemaker.page.sparkling.tip", x + 20, y + 4, w - 25, RED);

        int noteY = y + tipH + 3;
        if (noteY < 226) {
            note(g, 9, x, noteY, w, "guide.samogon.winemaker.page.sparkling.note", GREEN);
            return noteY + textHeight("guide.samogon.winemaker.page.sparkling.note", w - 11);
        }
        return y + tipH;
    }

    private int drawDosage(GuiGraphics g) {
        int x = CONTENT_X;
        int w = CONTENT_W;
        int y = BODY_BASE;
        text(g, "guide.samogon.winemaker.page.dosage.lead", x, y, w, INK);
        y += textHeight("guide.samogon.winemaker.page.dosage.lead", w) + 6;

        // A plain grid: outline, one rule under the header and thin column
        // separators only. No band or line is drawn across a row, so nothing
        // ever crosses a label or a value.
        //
        // The width of every column is listed explicitly and the four widths
        // add up to the full table width. The old array held only three values
        // for four columns, so the last column silently took the 72 px that was
        // left over and looked far too wide next to the two narrow numeric
        // columns. Now the dosage name gets the slack it needs ("Экстра сухое"
        // is the longest label) and the three numeric columns stay compact.
        int headerH = 15;
        int rowH = 16;
        int[] widths = {112, 40, 42, 60};
        int[] edges = new int[widths.length];
        int acc = 0;
        for (int i = 0; i < widths.length; i++) {
            edges[i] = acc;
            acc += widths[i];
        }
        Wine.Dosage[] all = Wine.Dosage.values();
        int tableH = headerH + all.length * rowH;

        // Column separators go down first, then the single header rule, then
        // the outline on top.
        for (int i = 1; i < widths.length; i++) {
            g.fill(x + edges[i], y + 1, x + edges[i] + 1, y + tableH - 1, 0x338A4A22);
        }
        g.fill(x + 1, y + headerH - 1, x + w - 1, y + headerH, PANEL_BORDER);
        g.renderOutline(x, y, w, tableH, PANEL_BORDER);

        g.drawString(this.font, Component.translatable("guide.samogon.winemaker.page.dosage.table.dosage"),
                x + edges[0] + 5, y + 4, COPPER_DARK, false);
        g.drawString(this.font, Component.translatable("guide.samogon.winemaker.page.dosage.table.sugar"),
                x + edges[1] + 5, y + 4, COPPER_DARK, false);
        g.drawString(this.font, Component.translatable("guide.samogon.winemaker.page.dosage.table.items"),
                x + edges[2] + 5, y + 4, COPPER_DARK, false);
        g.drawString(this.font, Component.translatable("guide.samogon.winemaker.page.dosage.table.quality"),
                x + edges[3] + 5, y + 4, COPPER_DARK, false);

        for (int i = 0; i < all.length; i++) {
            Wine.Dosage dosage = all[i];
            int rowY = y + headerH + i * rowH + 4;
            g.drawString(this.font, Component.translatable(dosage.translationKey()),
                    x + edges[0] + 5, rowY, INK, false);
            g.drawString(this.font, String.valueOf(dosage.gramsPerLitre()),
                    x + edges[1] + 5, rowY, SOFT, false);
            g.drawString(this.font, String.valueOf(dosage.sugarItems()),
                    x + edges[2] + 5, rowY, SOFT, false);
            g.drawString(this.font, signed(dosage.qualityBonus()), x + edges[3] + 5, rowY,
                    dosage.qualityBonus() < 0 ? RED : dosage.qualityBonus() > 0 ? GREEN : SOFT, false);
        }

        int noteY = y + tableH + 5;
        int noteH = 7 + textHeight("guide.samogon.winemaker.page.dosage.tip", w - 25);
        panel(g, x, noteY, w, noteH);
        drawIcon(g, 6, x + 5, noteY + 5, 10);
        text(g, "guide.samogon.winemaker.page.dosage.tip", x + 20, noteY + 4, w - 25, INK);

        int footY = noteY + noteH + 3;
        note(g, 7, x, footY, w, "guide.samogon.winemaker.page.dosage.note", RED);
        return footY + textHeight("guide.samogon.winemaker.page.dosage.note", w - 11);
    }

    private static String signed(int value) {
        return value > 0 ? "+" + value : String.valueOf(value);
    }

    private int drawTrouble(GuiGraphics g) {
        int x = CONTENT_X;
        int w = CONTENT_W;
        String[] keys = {
                "guide.samogon.winemaker.page.trouble.press",
                "guide.samogon.winemaker.page.trouble.ferment",
                "guide.samogon.winemaker.page.trouble.yeast",
                "guide.samogon.winemaker.page.trouble.age",
                "guide.samogon.winemaker.page.trouble.rack",
                "guide.samogon.winemaker.page.trouble.sugar",
                "guide.samogon.winemaker.page.trouble.temperature"
        };
        int y = BODY_BASE;
        for (int i = 0; i < keys.length; i++) {
            int h = textHeight(keys[i], w - 14);
            g.fill(x, y + 1, x + 2, y + 10, COPPER);
            g.drawString(this.font, String.valueOf(i + 1), x + 5, y, COPPER_DARK, false);
            text(g, keys[i], x + 14, y, w - 14, INK);
            y += Math.max(14, h) + 2;
        }

        int tipH = 7 + textHeight("guide.samogon.winemaker.page.trouble.tip", w - 25);
        panel(g, x, y + 2, w, tipH);
        drawIcon(g, 9, x + 5, y + 5, 10);
        text(g, "guide.samogon.winemaker.page.trouble.tip", x + 20, y + 4, w - 25, GREEN);
        return y + tipH + 2;
    }

    private int drawMemo(GuiGraphics g) {
        int x = CONTENT_X;
        int w = CONTENT_W;
        int y = BODY_BASE;
        int headerH = 14;
        int rowH = 14;
        int[] cols = {96, 50, 108};
        String[] rowIds = {"press", "ferment", "age", "tirage"};
        int tableH = headerH + rowIds.length * rowH;

        g.fill(x, y, x + w, y + headerH, 0x338A4A22);
        g.renderOutline(x, y, w, tableH, PANEL_BORDER);
        g.fill(x + cols[0], y, x + cols[0] + 1, y + tableH, 0x338A4A22);
        g.fill(x + cols[0] + cols[1], y, x + cols[0] + cols[1] + 1, y + tableH, 0x338A4A22);
        g.drawString(this.font, Component.translatable("guide.samogon.winemaker.page.memo.table.stage"), x + 4, y + 3, COPPER_DARK, false);
        g.drawString(this.font, Component.translatable("guide.samogon.winemaker.page.memo.table.time"), x + cols[0] + 4, y + 3, COPPER_DARK, false);
        g.drawString(this.font, Component.translatable("guide.samogon.winemaker.page.memo.table.need"), x + cols[0] + cols[1] + 4, y + 3, COPPER_DARK, false);

        for (int i = 0; i < rowIds.length; i++) {
            int rowY = y + headerH + i * rowH;
            if ((i & 1) == 1) {
                g.fill(x + 1, rowY, x + w - 1, rowY + rowH, 0x18FFF4D8);
            }
            g.fill(x, rowY, x + w, rowY + 1, 0x228A4A22);
            g.drawString(this.font, Component.translatable("guide.samogon.winemaker.page.memo.row." + rowIds[i]),
                    x + 4, rowY + 3, INK, false);
            g.drawString(this.font, Component.translatable("guide.samogon.winemaker.page.memo.row." + rowIds[i] + ".time"),
                    x + cols[0] + 4, rowY + 3, SOFT, false);
            g.drawString(this.font, Component.translatable("guide.samogon.winemaker.page.memo.row." + rowIds[i] + ".need"),
                    x + cols[0] + cols[1] + 4, rowY + 3, SOFT, false);
        }

        int tableBottom = y + headerH + rowIds.length * rowH;
        int panelY = tableBottom + 4;
        panel(g, x, panelY, w, 27);
        drawItem(g, new ItemStack(ModItems.THERMOMETER.get()), x + 5, panelY + 6, 16);
        drawItem(g, new ItemStack(ModItems.HYDROMETER.get()), x + 23, panelY + 6, 16);
        text(g, "guide.samogon.winemaker.page.memo.tools", x + 44, panelY + 4, w - 48, INK);

        int safetyY = panelY + 31;
        drawIcon(g, 7, x + 2, safetyY + 2, 10);
        text(g, "guide.samogon.winemaker.page.memo.safety", x + 17, safetyY, w - 20, RED);
        return safetyY + textHeight("guide.samogon.winemaker.page.memo.safety", w - 20);
    }

    // === DRAW HELPERS ===

    private int step(GuiGraphics g, int x, int y, int w, int icon, String key, int color) {
        drawIcon(g, icon, x, y + 1, 9);
        text(g, key, x + 14, y, w - 14, color);
        return textHeight(key, w - 14);
    }

    private void note(GuiGraphics g, int icon, int x, int y, int w, String key, int color) {
        drawIcon(g, icon, x, y + 1, 8);
        text(g, key, x + 11, y, w - 11, color);
    }

    private void text(GuiGraphics g, String key, int x, int y, int width, int color) {
        textComponent(g, Component.translatable(key), x, y, width, color);
    }

    private int textComponent(GuiGraphics g, Component text, int x, int y, int width, int color) {
        var lines = this.font.split(text, width);
        for (int i = 0; i < lines.size(); i++) {
            g.drawString(this.font, lines.get(i), x, y + i * 9, color, false);
        }
        return lines.size() * 9;
    }

    private void centered(GuiGraphics g, Component text, int centerX, int y, int color) {
        g.drawString(this.font, text, centerX - this.font.width(text) / 2, y, color, false);
    }

    private int textHeight(String key, int width) {
        return this.font.split(Component.translatable(key), width).size() * 9;
    }

    private void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, PANEL);
        g.fill(x, y, x + 2, y + h, COPPER);
        g.fill(x, y, x + w, y + 1, PANEL_BORDER);
        g.fill(x, y + h - 1, x + w, y + h, PANEL_BORDER);
    }

    private void drawItem(GuiGraphics g, ItemStack stack, int x, int y, int size) {
        float scale = size / 16.0F;
        g.pose().pushPose();
        g.pose().translate(x, y, 100.0F);
        g.pose().scale(scale, scale, 1.0F);
        g.renderItem(stack, 0, 0);
        g.pose().popPose();
    }

    private void drawIcon(GuiGraphics g, int index, int x, int y, int size) {
        int u = (index & 3) * 16;
        int v = (index >> 2) * 16;
        g.blit(ICONS, x, y, size, size, u, v, 16, 16, 64, 48);
    }

    /** A wine bottle of the given colour, still or sparkling, with its model variant. */
    private ItemStack wineBottle(Wine.Color colour, boolean sparkling) {
        ItemStack stack = new ItemStack(sparkling ? ModItems.SPARKLING_WINE.get() : ModItems.WINE.get());
        int variant = switch (colour) {
            case WHITE -> 1;
            case ROSE -> 2;
            default -> 0;
        };
        if (variant > 0) {
            stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(variant));
        }
        return stack;
    }

    private ItemStack pageIcon(Page page) {
        return switch (page) {
            case OVERVIEW -> new ItemStack(ModItems.WINEMAKERS_GUIDE.get());
            case GRAPES -> new ItemStack(ModItems.CABERNET_GRAPES.get());
            case PRESS -> new ItemStack(ModBlocks.WINE_PRESS.get());
            case FERMENT -> new ItemStack(ModBlocks.FERMENTATION_BARREL.get());
            case AGE -> new ItemStack(ModBlocks.AGING_CASK.get());
            case SPARKLING -> new ItemStack(ModBlocks.BOTTLE_RACK.get());
            case DOSAGE -> new ItemStack(Items.SUGAR);
            case TROUBLE -> new ItemStack(ModItems.THERMOMETER.get());
            case MEMO -> new ItemStack(ModItems.WINEMAKERS_GUIDE.get());
        };
    }

    // === SCROLLING ===

    private void beginClip(GuiGraphics g, int x, int y, int w, int h) {
        float scale = bookScale();
        int x0 = bookX() + Math.round(x * scale);
        int y0 = bookY() + Math.round(y * scale);
        int x1 = bookX() + Math.round((x + w) * scale);
        int y1 = bookY() + Math.round((y + h) * scale);
        g.enableScissor(x0, y0, x1, y1);
    }

    private void drawScrollbar(GuiGraphics g, int x, int top, int bottom, int scroll, int maxScroll) {
        if (maxScroll <= 0) return;
        int trackH = bottom - top;
        int contentH = trackH + maxScroll;
        int thumbH = Math.max(14, trackH * trackH / Math.max(1, contentH));
        int thumbY = top + (trackH - thumbH) * scroll / Math.max(1, maxScroll);
        g.fill(x, top, x + 2, bottom, 0x228A4A22);
        g.fill(x, thumbY, x + 2, thumbY + thumbH, 0xAA8A4A22);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    // === INPUT ===

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int mx = localX(mouseX);
            int my = localY(mouseY);

            if (mx >= 442 && mx <= 456 && my >= 8 && my <= 28) {
                this.onClose();
                return true;
            }
            if (mx >= TAB_GUIDE_X && mx <= TAB_GUIDE_X + TAB_GUIDE_W && my >= TAB_Y && my <= TAB_Y + TAB_H) {
                switchTab(0);
                return true;
            }
            if (mx >= TAB_COLOURS_X && mx <= TAB_COLOURS_X + TAB_COLOURS_W && my >= TAB_Y && my <= TAB_Y + TAB_H) {
                switchTab(1);
                return true;
            }
            if (mx >= TAB_RECIPES_X && mx <= TAB_RECIPES_X + TAB_RECIPES_W && my >= TAB_Y && my <= TAB_Y + TAB_H) {
                switchTab(2);
                return true;
            }

            if (mx >= LIST_X && mx <= LIST_X + LIST_W && my >= LIST_TOP && my <= LIST_BOTTOM) {
                if (tab == 0) {
                    int index = (my - LIST_TOP + listScroll) / CHAPTER_STEP;
                    if (index >= 0 && index < Page.values().length) {
                        pageIndex = index;
                        contentScroll = 0;
                        contentMaxScroll = 0;
                        return true;
                    }
                } else if (tab == 1) {
                    int index = (my - LIST_TOP + listScroll) / COLOUR_STEP;
                    if (index >= 0 && index < VARIETIES.length) {
                        colourIndex = index;
                        contentScroll = 0;
                        contentMaxScroll = 0;
                        return true;
                    }
                } else {
                    int index = (my - LIST_TOP + listScroll) / RECIPE_STEP;
                    if (index >= 0 && index < RECIPES.size()) {
                        recipeIndex = index;
                        contentScroll = 0;
                        contentMaxScroll = 0;
                        return true;
                    }
                }
            }
            return mx >= 0 && mx <= BOOK_W && my >= 0 && my <= BOOK_H;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int mx = localX(mouseX);
        int my = localY(mouseY);
        int amount = (int) Math.round(scrollY * 18.0);
        if (amount == 0) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);

        if (mx >= LIST_X && mx <= LIST_X + LIST_W && my >= LIST_TOP && my <= LIST_BOTTOM) {
            listScroll = clamp(listScroll - amount, 0, listMaxScroll);
            return true;
        }
        if (mx >= CONTENT_LEFT && mx <= CONTENT_LEFT + CONTENT_W && my >= BODY_TOP && my <= CONTENT_BOTTOM) {
            contentScroll = clamp(contentScroll - amount, 0, contentMaxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void switchTab(int next) {
        if (tab == next) return;
        tab = next;
        listScroll = 0;
        listMaxScroll = 0;
        contentScroll = 0;
        contentMaxScroll = 0;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 258) {
            switchTab((tab + 1) % 3);
            return true;
        }
        if (keyCode == 263) {
            previous();
            return true;
        }
        if (keyCode == 262) {
            next();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void previous() {
        if (tab == 0) {
            pageIndex = Math.max(0, pageIndex - 1);
        } else if (tab == 1) {
            colourIndex = Math.max(0, colourIndex - 1);
        } else {
            recipeIndex = Math.max(0, recipeIndex - 1);
        }
        contentScroll = 0;
        contentMaxScroll = 0;
    }

    private void next() {
        if (tab == 0) {
            pageIndex = Math.min(Page.values().length - 1, pageIndex + 1);
        } else if (tab == 1) {
            colourIndex = Math.min(VARIETIES.length - 1, colourIndex + 1);
        } else {
            recipeIndex = Math.min(RECIPES.size() - 1, recipeIndex + 1);
        }
        contentScroll = 0;
        contentMaxScroll = 0;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private enum Page {
        OVERVIEW("guide.samogon.winemaker.toc.overview", "guide.samogon.winemaker.page.overview.title", "guide.samogon.winemaker.page.overview.tag"),
        GRAPES("guide.samogon.winemaker.toc.grapes", "guide.samogon.winemaker.page.grapes.title", "guide.samogon.winemaker.page.grapes.tag"),
        PRESS("guide.samogon.winemaker.toc.press", "guide.samogon.winemaker.page.press.title", "guide.samogon.winemaker.page.press.tag"),
        FERMENT("guide.samogon.winemaker.toc.ferment", "guide.samogon.winemaker.page.ferment.title", "guide.samogon.winemaker.page.ferment.tag"),
        AGE("guide.samogon.winemaker.toc.age", "guide.samogon.winemaker.page.age.title", "guide.samogon.winemaker.page.age.tag"),
        SPARKLING("guide.samogon.winemaker.toc.sparkling", "guide.samogon.winemaker.page.sparkling.title", "guide.samogon.winemaker.page.sparkling.tag"),
        DOSAGE("guide.samogon.winemaker.toc.dosage", "guide.samogon.winemaker.page.dosage.title", "guide.samogon.winemaker.page.dosage.tag"),
        TROUBLE("guide.samogon.winemaker.toc.trouble", "guide.samogon.winemaker.page.trouble.title", "guide.samogon.winemaker.page.trouble.tag"),
        MEMO("guide.samogon.winemaker.toc.memo", "guide.samogon.winemaker.page.memo.title", "guide.samogon.winemaker.page.memo.tag");

        final String tocKey;
        final String titleKey;
        final String tagKey;

        Page(String tocKey, String titleKey, String tagKey) {
            this.tocKey = tocKey;
            this.titleKey = titleKey;
            this.tagKey = tagKey;
        }
    }
}