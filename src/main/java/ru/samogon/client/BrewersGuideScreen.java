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
import ru.samogon.process.BeerStyle;
import ru.samogon.registry.ModBlocks;
import ru.samogon.registry.ModItems;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The brewer's handbook: the same parchment spread and two top tabs as the
 * distiller's guide, but its chapters walk through the brewery multiblock —
 * grain, mash, hop boil, fermentation and bottling. The recipes tab lists the
 * beer styles: the basic ones from the start, the specialities once brewed.
 */
public final class BrewersGuideScreen extends Screen {
    private static final int BOOK_W = 460;
    private static final int BOOK_H = 265;
    private static final int TEX_W = 920;
    private static final int TEX_H = 530;

    private static final int TAB_Y = 11;
    private static final int TAB_H = 22;
    private static final int TAB_GUIDE_X = 198;
    private static final int TAB_GUIDE_W = 112;
    private static final int TAB_RECIPES_X = 314;
    private static final int TAB_RECIPES_W = 124;

    private static final int LIST_X = 16;
    private static final int LIST_W = 152;
    private static final int LIST_TOP = 46;
    private static final int LIST_BOTTOM = 232;
    private static final int CHAPTER_STEP = 17;
    private static final int RECIPE_STEP = 38;

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
    private static final int GOLD = 0xFFB1843B;
    private static final int RED = 0xFF8A2B22;
    private static final int GREEN = 0xFF4F6039;
    private static final int PAPER = 0xFFE6D2A6;
    private static final int PANEL = 0x1FA65A28;
    private static final int PANEL_BORDER = 0x558A4A22;
    private static final int HEARTS_COLOR = 0xFFC18A45;

    /** 0 = guide, 1 = recipes. */
    private int tab;
    private int pageIndex;
    private int recipeIndex;
    private int listScroll;
    private int contentScroll;
    private int listMaxScroll;
    private int contentMaxScroll;

    public BrewersGuideScreen() {
        super(Component.translatable("guide.samogon.brewer.title"));
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
        } else {
            drawRecipes(g, mx, my);
        }
        drawFooter(g);
    }

    // === TOP CHROME ===

    private void drawChrome(GuiGraphics g, int mx, int my) {
        g.drawString(this.font, Component.translatable("guide.samogon.brewer.book.title"), 18, 15, COPPER_DARK, false);
        g.drawString(this.font, Component.translatable("guide.samogon.brewer.book.subtitle"), 18, 26, SOFT, false);

        drawTab(g, TAB_GUIDE_X, TAB_GUIDE_W, "guide.samogon.brewer.tab.guide", tab == 0, mx, my);
        drawTab(g, TAB_RECIPES_X, TAB_RECIPES_W, "guide.samogon.brewer.tab.recipes", tab == 1, mx, my);

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
            List<BeerStyle> visible = visibleRecipes();
            Component progress = Component.translatable("guide.samogon.brewer.recipes.progress",
                    visible.size(), BeerStyle.values().length);
            centered(g, progress, centerX, 243, SOFT);
        } else {
            centered(g, Component.translatable("guide.samogon.brewer.scroll"), centerX, 243, SOFT);
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
            case MALT -> drawMalt(g);
            case MASH -> drawMash(g);
            case BOIL -> drawBoil(g);
            case FERMENT -> drawFerment(g);
            case BOTTLE -> drawBottle(g);
            case TROUBLE -> drawTrouble(g);
            case MEMO -> drawMemo(g);
        };
    }

    // === RECIPES TAB ===

    private void drawRecipes(GuiGraphics g, int mx, int my) {
        List<BeerStyle> visible = visibleRecipes();
        if (visible.isEmpty()) {
            recipeIndex = 0;
        } else {
            recipeIndex = clamp(recipeIndex, 0, visible.size() - 1);
        }
        drawRecipeList(g, mx, my, visible);

        String counter = visible.isEmpty() ? "00 / 00"
                : String.format(Locale.ROOT, "%02d / %02d", recipeIndex + 1, visible.size());
        drawContentHeader(g, "guide.samogon.brewer.recipes.title", "guide.samogon.brewer.recipes.tag", counter);

        beginClip(g, CONTENT_LEFT, BODY_TOP, CONTENT_W + 4, BODY_VIEW_H);
        g.pose().pushPose();
        g.pose().translate(0.0F, (float) (BODY_TOP - BODY_BASE - contentScroll), 0.0F);
        int bottom = visible.isEmpty()
                ? drawTextBody(g, "guide.samogon.brewer.recipes.empty")
                : drawRecipeBody(g, visible.get(recipeIndex));
        g.pose().popPose();
        g.disableScissor();

        contentMaxScroll = Math.max(0, (bottom - BODY_BASE) - BODY_VIEW_H);
        contentScroll = clamp(contentScroll, 0, contentMaxScroll);
        drawScrollbar(g, CONTENT_LEFT + CONTENT_W + 1, BODY_TOP, CONTENT_BOTTOM, contentScroll, contentMaxScroll);
    }

    private void drawRecipeList(GuiGraphics g, int mx, int my, List<BeerStyle> visible) {
        int total = visible.size() * RECIPE_STEP;
        int view = LIST_BOTTOM - LIST_TOP;
        listMaxScroll = Math.max(0, total - view);
        listScroll = clamp(listScroll, 0, listMaxScroll);

        beginClip(g, LIST_X, LIST_TOP, LIST_W, view);
        for (int i = 0; i < visible.size(); i++) {
            BeerStyle recipe = visible.get(i);
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
            Component name = Component.translatable(recipe.translationKey()).withStyle(ChatFormatting.BOLD);
            var nameLines = this.font.split(name, LIST_W - 26);
            for (int line = 0; line < nameLines.size() && line < 2; line++) {
                g.drawString(this.font, nameLines.get(line), LIST_X + 24, y + 1 + line * 9,
                        active ? COPPER_DARK : INK, false);
            }
            int originY = y + 1 + Math.min(2, nameLines.size()) * 9 + 1;
            g.drawString(this.font, Component.translatable(recipe.originKey()),
                    LIST_X + 24, originY, SOFT, false);
        }
        g.disableScissor();
        drawScrollbar(g, LIST_X + LIST_W + 1, LIST_TOP, LIST_BOTTOM, listScroll, listMaxScroll);
    }

    private int drawRecipeBody(GuiGraphics g, BeerStyle recipe) {
        int x = CONTENT_X;
        int w = CONTENT_W;
        int y = BODY_BASE;

        drawItem(g, recipeProductIcon(recipe), x, y, 30);
        g.drawString(this.font, Component.translatable(recipe.translationKey()).withStyle(ChatFormatting.BOLD),
                x + 38, y + 1, COPPER_DARK, false);
        text(g, recipe.descriptionKey(), x + 38, y + 12, w - 38, SOFT);
        y = Math.max(y + 30, y + 12 + textHeight(recipe.descriptionKey(), w - 38)) + 6;

        Component origin = Component.translatable("guide.samogon.brewer.recipe.origin",
                Component.translatable(recipe.originKey()));
        y += textComponent(g, origin, x, y, w, INK) + 5;

        g.drawString(this.font, Component.translatable("guide.samogon.brewer.recipe.steps").withStyle(ChatFormatting.BOLD),
                x, y, COPPER_DARK, false);
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

    private int drawTextBody(GuiGraphics g, String key) {
        int y = BODY_BASE;
        text(g, key, CONTENT_X, y, CONTENT_W, SOFT);
        return y + textHeight(key, CONTENT_W) + 2;
    }

    private static List<BeerStyle> visibleRecipes() {
        List<BeerStyle> result = new ArrayList<>();
        for (BeerStyle style : BeerStyle.values()) {
            if (style.base() || ClientRecipeBook.has(style.id())) {
                result.add(style);
            }
        }
        return result;
    }

    /**
     * The recipe list shows the finished beer in its own colour, so the styles
     * read apart at a glance instead of showing their raw ingredients.
     */
    private ItemStack recipeIcon(BeerStyle style) {
        return beerMug(style);
    }

    private ItemStack recipeProductIcon(BeerStyle style) {
        return beerMug(style);
    }

    /**
     * A wooden mug of the given style. The fill colour comes from the style's
     * item-model variant, so the drink's colour is visible in the book.
     */
    private ItemStack beerMug(BeerStyle style) {
        ItemStack stack = new ItemStack(ModItems.BEER.get());
        if (style.variant() > 0) {
            stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(style.variant()));
        }
        return stack;
    }

    // === PAGE BODIES ===

    private int drawOverview(GuiGraphics g) {
        int x = CONTENT_X;
        int y = BODY_BASE;
        int w = CONTENT_W;
        text(g, "guide.samogon.brewer.page.overview.lead", x, y, w, INK);
        // Every step lays itself out from its own wrapped height, so a longer
        // description never runs into the tip panel below it.
        int rowY = y + textHeight("guide.samogon.brewer.page.overview.lead", w) + 4;

        ItemStack[] stages = {
                new ItemStack(ModBlocks.BREWERY.get()),
                new ItemStack(ModItems.HOPS.get()),
                new ItemStack(ModBlocks.BEER_FERMENTATION_BARREL.get())
        };
        int lastBottom = rowY;
        for (int i = 0; i < stages.length; i++) {
            String subKey = "guide.samogon.brewer.page.overview.flow" + (i + 1) + ".sub";
            int rowH = Math.max(22, 10 + textHeight(subKey, w - 28));
            drawItem(g, stages[i], x + 1, rowY - 2, 22);
            g.drawString(this.font, Component.translatable("guide.samogon.brewer.page.overview.flow" + (i + 1))
                    .withStyle(ChatFormatting.BOLD), x + 28, rowY, COPPER_DARK, false);
            text(g, subKey, x + 28, rowY + 10, w - 28, SOFT);
            lastBottom = rowY + rowH;
            boolean last = i == stages.length - 1;
            if (!last) drawIcon(g, 1, x + 8, lastBottom + 1, 7);
            rowY = lastBottom + (last ? 0 : 10);
        }

        int tipY = lastBottom + 4;
        panel(g, x, tipY, w, 24);
        drawIcon(g, 6, x + 5, tipY + 6, 10);
        text(g, "guide.samogon.brewer.page.overview.tip", x + 20, tipY + 4, w - 25, INK);
        return tipY + 24;
    }

    private int drawMalt(GuiGraphics g) {
        int x = CONTENT_X;
        int w = CONTENT_W;
        ItemStack[] raw = {
                new ItemStack(ModItems.MALT.get()),
                new ItemStack(Items.WHEAT),
                new ItemStack(ModItems.ROASTED_MALT.get()),
                new ItemStack(ModItems.HOPS.get()),
                new ItemStack(ModItems.YEAST.get()),
                new ItemStack(Items.WATER_BUCKET),
                new ItemStack(Items.HONEY_BOTTLE),
                new ItemStack(Items.SWEET_BERRIES),
                new ItemStack(Items.APPLE),
                new ItemStack(Items.COCOA_BEANS)
        };
        String[] keys = {
                "guide.samogon.brewer.page.malt.malt",
                "guide.samogon.brewer.page.malt.wheat",
                "guide.samogon.brewer.page.malt.roasted",
                "guide.samogon.brewer.page.malt.hops",
                "guide.samogon.brewer.page.malt.yeast",
                "guide.samogon.brewer.page.malt.water",
                "guide.samogon.brewer.page.malt.honey",
                "guide.samogon.brewer.page.malt.berries",
                "guide.samogon.brewer.page.malt.apple",
                "guide.samogon.brewer.page.malt.cocoa"
        };
        int y = BODY_BASE;
        for (int i = 0; i < raw.length; i++) {
            drawItem(g, raw[i], x, y + 1, 16);
            text(g, keys[i], x + 21, y, w - 21, INK);
            y += Math.max(16, textHeight(keys[i], w - 21)) + 2;
        }

        int summaryH = 7 + textHeight("guide.samogon.brewer.page.malt.summary", w - 8);
        panel(g, x, y + 2, w, summaryH);
        text(g, "guide.samogon.brewer.page.malt.summary", x + 4, y + 5, w - 8, INK);
        y += summaryH + 5;

        int tipH = 7 + textHeight("guide.samogon.brewer.page.malt.tip", w - 20);
        panel(g, x, y + 2, w, tipH);
        drawIcon(g, 10, x + 5, y + 5, 10);
        text(g, "guide.samogon.brewer.page.malt.tip", x + 20, y + 4, w - 25, INK);
        return y + tipH + 2;
    }

    private int drawMash(GuiGraphics g) {
        int x = CONTENT_X;
        int w = CONTENT_W;
        drawItem(g, new ItemStack(ModBlocks.BREWERY.get()), 186, 50, 36);
        drawIcon(g, 0, 230, 64, 14);
        drawItem(g, new ItemStack(Items.WATER_BUCKET), 252, 58, 26);
        drawItem(g, new ItemStack(ModItems.MALT.get()), 292, 58, 26);
        drawItem(g, new ItemStack(ModItems.BEER_WORT.get()), 332, 56, 28);
        centered(g, Component.translatable("guide.samogon.brewer.page.mash.label.brewery"), 204, 88, COPPER_DARK);
        centered(g, Component.translatable("guide.samogon.brewer.page.mash.label.water"), 265, 88, COPPER_DARK);
        centered(g, Component.translatable("guide.samogon.brewer.page.mash.label.wort"), 346, 88, COPPER_DARK);

        int y = 104;
        y += step(g, x, y, w, 3, "guide.samogon.brewer.page.mash.step1", INK) + 5;
        y += step(g, x, y, w, 5, "guide.samogon.brewer.page.mash.step2", INK) + 5;
        y += step(g, x, y, w, 9, "guide.samogon.brewer.page.mash.step3", INK) + 5;

        int tipH = 7 + textHeight("guide.samogon.brewer.page.mash.tip", w - 25);
        panel(g, x, y, w, tipH);
        drawIcon(g, 9, x + 5, y + 6, 10);
        text(g, "guide.samogon.brewer.page.mash.tip", x + 20, y + 4, w - 25, INK);

        int noteY = y + tipH + 4;
        if (noteY < 226) {
            note(g, 11, x, noteY, w, "guide.samogon.brewer.page.mash.note", GREEN);
            return noteY + textHeight("guide.samogon.brewer.page.mash.note", w - 11);
        }
        return y + tipH;
    }

    private int drawBoil(GuiGraphics g) {
        int x = CONTENT_X;
        int w = CONTENT_W;
        drawItem(g, new ItemStack(ModBlocks.BREWERY.get()), 186, 50, 36);
        drawIcon(g, 2, 230, 62, 16);
        drawItem(g, new ItemStack(ModItems.HOPS.get()), 254, 58, 26);
        drawItem(g, new ItemStack(ModItems.HOPPED_WORT.get()), 296, 56, 28);
        drawIcon(g, 0, 336, 64, 14);
        centered(g, Component.translatable("guide.samogon.brewer.page.boil.label.kettle"), 204, 88, COPPER_DARK);
        centered(g, Component.translatable("guide.samogon.brewer.page.boil.label.hops"), 267, 88, COPPER_DARK);
        centered(g, Component.translatable("guide.samogon.brewer.page.boil.label.hopped"), 316, 88, COPPER_DARK);

        int y = 104;
        y += step(g, x, y, w, 1, "guide.samogon.brewer.page.boil.step1", INK) + 5;
        y += step(g, x, y, w, 2, "guide.samogon.brewer.page.boil.step2", INK) + 5;
        y += step(g, x, y, w, 5, "guide.samogon.brewer.page.boil.step3", INK) + 5;

        int tipH = 7 + textHeight("guide.samogon.brewer.page.boil.tip", w - 25);
        panel(g, x, y, w, tipH);
        drawIcon(g, 7, x + 5, y + 6, 10);
        text(g, "guide.samogon.brewer.page.boil.tip", x + 20, y + 4, w - 25, RED);

        int noteY = y + tipH + 4;
        if (noteY < 226) {
            note(g, 11, x, noteY, w, "guide.samogon.brewer.page.boil.note", GREEN);
            return noteY + textHeight("guide.samogon.brewer.page.boil.note", w - 11);
        }
        return y + tipH;
    }

    private int drawFerment(GuiGraphics g) {
        int x = CONTENT_X;
        int w = CONTENT_W;
        drawItem(g, new ItemStack(ModBlocks.BEER_FERMENTATION_BARREL.get()), 186, 50, 36);
        drawIcon(g, 4, 230, 62, 16);
        drawItem(g, new ItemStack(ModItems.YEAST.get()), 254, 58, 26);
        drawItem(g, new ItemStack(ModItems.BEER.get()), 296, 54, 30);
        centered(g, Component.translatable("guide.samogon.brewer.page.ferment.label.kettle"), 204, 88, COPPER_DARK);
        centered(g, Component.translatable("guide.samogon.brewer.page.ferment.label.yeast"), 267, 88, COPPER_DARK);
        centered(g, Component.translatable("guide.samogon.brewer.page.ferment.label.beer"), 314, 88, COPPER_DARK);

        int gaugeY = 100;
        int gaugeH = 10;
        int from = x + Math.round(w * 0.08F);
        int to = x + Math.round(w * 0.92F);
        g.fill(x, gaugeY, x + w, gaugeY + gaugeH, 0xFFE2CDA1);
        g.fill(from, gaugeY, to, gaugeY + gaugeH, HEARTS_COLOR);
        // The cold left end of the band is the bottom-fermentation (lager)
        // window; the warm rest is the top-fermentation (ale) window.
        int lagerTo = from + Math.round((to - from) * 0.2F);
        g.fill(from, gaugeY, lagerTo, gaugeY + gaugeH, 0xFF7C93B0);
        g.fill(lagerTo - 1, gaugeY - 2, lagerTo + 1, gaugeY + gaugeH + 2, COPPER_DARK);
        g.fill(from - 1, gaugeY - 2, from + 1, gaugeY + gaugeH + 2, COPPER);
        g.fill(to - 1, gaugeY - 2, to + 1, gaugeY + gaugeH + 2, COPPER);
        g.drawString(this.font, "8 °C", from - 6, 112, COPPER_DARK, false);
        g.drawString(this.font, "38 °C", to - 14, 112, COPPER_DARK, false);
        g.drawString(this.font, Component.translatable("guide.samogon.brewer.page.ferment.gauge.caption"), x, 122, SOFT, false);

        int y = 134;
        y += step(g, x, y, w, 1, "guide.samogon.brewer.page.ferment.step1", INK) + 4;
        y += step(g, x, y, w, 4, "guide.samogon.brewer.page.ferment.step2", INK) + 4;
        y += step(g, x, y, w, 10, "guide.samogon.brewer.page.ferment.step3", INK) + 4;

        int tipH = 7 + textHeight("guide.samogon.brewer.page.ferment.tip", w - 25);
        panel(g, x, y, w, tipH);
        drawIcon(g, 7, x + 5, y + 6, 10);
        text(g, "guide.samogon.brewer.page.ferment.tip", x + 20, y + 4, w - 25, RED);
        return y + tipH;
    }

    private int drawBottle(GuiGraphics g) {
        int x = CONTENT_X;
        int w = CONTENT_W;
        drawItem(g, new ItemStack(ModItems.WOODEN_MUG.get()), 196, 54, 30);
        drawIcon(g, 0, 232, 62, 14);
        drawItem(g, new ItemStack(ModItems.BEER.get()), 262, 50, 34);
        drawItem(g, new ItemStack(ModItems.BEER_WORT.get()), 308, 58, 26);
        drawItem(g, new ItemStack(ModItems.HOPPED_WORT.get()), 344, 58, 26);
        centered(g, Component.translatable("guide.samogon.brewer.page.bottle.label.bottle"), 211, 88, COPPER_DARK);
        centered(g, Component.translatable("guide.samogon.brewer.page.bottle.label.beer"), 279, 88, COPPER_DARK);
        centered(g, Component.translatable("guide.samogon.brewer.page.bottle.label.stages"), 326, 88, COPPER_DARK);

        int y = 104;
        y += step(g, x, y, w, 11, "guide.samogon.brewer.page.bottle.step1", INK) + 5;
        y += step(g, x, y, w, 3, "guide.samogon.brewer.page.bottle.step2", INK) + 5;
        y += step(g, x, y, w, 5, "guide.samogon.brewer.page.bottle.step3", INK) + 5;

        int tipH = 7 + textHeight("guide.samogon.brewer.page.bottle.tip", w - 25);
        panel(g, x, y, w, tipH);
        drawIcon(g, 6, x + 5, y + 6, 10);
        text(g, "guide.samogon.brewer.page.bottle.tip", x + 20, y + 4, w - 25, INK);
        return y + tipH;
    }

    private int drawTrouble(GuiGraphics g) {
        int x = CONTENT_X;
        int w = CONTENT_W;
        String[] keys = {
                "guide.samogon.brewer.page.trouble.heat",
                "guide.samogon.brewer.page.trouble.mash",
                "guide.samogon.brewer.page.trouble.boil",
                "guide.samogon.brewer.page.trouble.ferment",
                "guide.samogon.brewer.page.trouble.bottle",
                "guide.samogon.brewer.page.trouble.room"
        };
        int y = BODY_BASE;
        for (int i = 0; i < keys.length; i++) {
            int h = textHeight(keys[i], w - 14);
            g.fill(x, y + 1, x + 2, y + 10, COPPER);
            g.drawString(this.font, String.valueOf(i + 1), x + 5, y, COPPER_DARK, false);
            text(g, keys[i], x + 14, y, w - 14, INK);
            y += Math.max(14, h) + 2;
        }

        int tipH = 7 + textHeight("guide.samogon.brewer.page.trouble.tip", w - 20);
        panel(g, x, y + 2, w, tipH);
        drawIcon(g, 9, x + 5, y + 5, 10);
        text(g, "guide.samogon.brewer.page.trouble.tip", x + 20, y + 4, w - 25, GREEN);
        return y + tipH + 2;
    }

    private int drawMemo(GuiGraphics g) {
        int x = CONTENT_X;
        int w = CONTENT_W;
        int y = BODY_BASE;
        int headerH = 14;
        int rowH = 14;
        int[] cols = {96, 50, 108};
        String[] rowIds = {"mash", "boil", "ferment", "bottle"};
        int tableH = headerH + rowIds.length * rowH;

        g.fill(x, y, x + w, y + headerH, 0x338A4A22);
        g.renderOutline(x, y, w, tableH, PANEL_BORDER);
        g.fill(x + cols[0], y, x + cols[0] + 1, y + tableH, 0x338A4A22);
        g.fill(x + cols[0] + cols[1], y, x + cols[0] + cols[1] + 1, y + tableH, 0x338A4A22);
        g.drawString(this.font, Component.translatable("guide.samogon.brewer.page.memo.table.stage"), x + 4, y + 3, COPPER_DARK, false);
        g.drawString(this.font, Component.translatable("guide.samogon.brewer.page.memo.table.time"), x + cols[0] + 4, y + 3, COPPER_DARK, false);
        g.drawString(this.font, Component.translatable("guide.samogon.brewer.page.memo.table.need"), x + cols[0] + cols[1] + 4, y + 3, COPPER_DARK, false);

        for (int i = 0; i < rowIds.length; i++) {
            int rowY = y + headerH + i * rowH;
            if ((i & 1) == 1) {
                g.fill(x + 1, rowY, x + w - 1, rowY + rowH, 0x18FFF4D8);
            }
            g.fill(x, rowY, x + w, rowY + 1, 0x228A4A22);
            g.drawString(this.font, Component.translatable("guide.samogon.brewer.page.memo.row." + rowIds[i]),
                    x + 4, rowY + 3, INK, false);
            g.drawString(this.font, Component.translatable("guide.samogon.brewer.page.memo.row." + rowIds[i] + ".time"),
                    x + cols[0] + 4, rowY + 3, SOFT, false);
            g.drawString(this.font, Component.translatable("guide.samogon.brewer.page.memo.row." + rowIds[i] + ".need"),
                    x + cols[0] + cols[1] + 4, rowY + 3, SOFT, false);
        }

        int tableBottom = y + headerH + rowIds.length * rowH;
        int panelY = tableBottom + 4;
        panel(g, x, panelY, w, 27);
        drawItem(g, new ItemStack(ModItems.THERMOMETER.get()), x + 5, panelY + 6, 16);
        drawItem(g, new ItemStack(ModItems.HYDROMETER.get()), x + 23, panelY + 6, 16);
        text(g, "guide.samogon.brewer.page.memo.tools", x + 44, panelY + 4, w - 48, INK);

        int safetyY = panelY + 31;
        drawIcon(g, 7, x + 2, safetyY + 2, 10);
        text(g, "guide.samogon.brewer.page.memo.safety", x + 17, safetyY, w - 20, RED);
        return safetyY + textHeight("guide.samogon.brewer.page.memo.safety", w - 20);
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

    private ItemStack pageIcon(Page page) {
        return switch (page) {
            case OVERVIEW -> new ItemStack(ModItems.BREWERS_GUIDE.get());
            case MALT -> new ItemStack(ModItems.MALT.get());
            case MASH -> new ItemStack(ModBlocks.BREWERY.get());
            case BOIL -> new ItemStack(ModItems.HOPS.get());
            case FERMENT -> new ItemStack(ModItems.YEAST.get());
            case BOTTLE -> new ItemStack(ModItems.WOODEN_MUG.get());
            case TROUBLE -> new ItemStack(ModItems.THERMOMETER.get());
            case MEMO -> new ItemStack(ModItems.BREWERS_GUIDE.get());
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
            if (mx >= TAB_RECIPES_X && mx <= TAB_RECIPES_X + TAB_RECIPES_W && my >= TAB_Y && my <= TAB_Y + TAB_H) {
                switchTab(1);
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
                } else {
                    List<BeerStyle> visible = visibleRecipes();
                    int index = (my - LIST_TOP + listScroll) / RECIPE_STEP;
                    if (index >= 0 && index < visible.size()) {
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
            switchTab(tab == 0 ? 1 : 0);
            return true;
        }
        if (tab == 0) {
            if (keyCode == 263) {
                previousPage();
                return true;
            }
            if (keyCode == 262) {
                nextPage();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void previousPage() {
        pageIndex = Math.max(0, pageIndex - 1);
        contentScroll = 0;
        contentMaxScroll = 0;
    }

    private void nextPage() {
        pageIndex = Math.min(Page.values().length - 1, pageIndex + 1);
        contentScroll = 0;
        contentMaxScroll = 0;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private enum Page {
        OVERVIEW("guide.samogon.brewer.toc.overview", "guide.samogon.brewer.page.overview.title", "guide.samogon.brewer.page.overview.tag"),
        MALT("guide.samogon.brewer.toc.malt", "guide.samogon.brewer.page.malt.title", "guide.samogon.brewer.page.malt.tag"),
        MASH("guide.samogon.brewer.toc.mash", "guide.samogon.brewer.page.mash.title", "guide.samogon.brewer.page.mash.tag"),
        BOIL("guide.samogon.brewer.toc.boil", "guide.samogon.brewer.page.boil.title", "guide.samogon.brewer.page.boil.tag"),
        FERMENT("guide.samogon.brewer.toc.ferment", "guide.samogon.brewer.page.ferment.title", "guide.samogon.brewer.page.ferment.tag"),
        BOTTLE("guide.samogon.brewer.toc.bottle", "guide.samogon.brewer.page.bottle.title", "guide.samogon.brewer.page.bottle.tag"),
        TROUBLE("guide.samogon.brewer.toc.trouble", "guide.samogon.brewer.page.trouble.title", "guide.samogon.brewer.page.trouble.tag"),
        MEMO("guide.samogon.brewer.toc.memo", "guide.samogon.brewer.page.memo.title", "guide.samogon.brewer.page.memo.tag");

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
