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
import ru.samogon.process.SpiritType;
import ru.samogon.registry.ModBlocks;
import ru.samogon.registry.ModItems;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * An illustrated, parchment-styled handbook. It deliberately uses no container
 * widgets: the whole spread is drawn as a book, and the three-dimensional item
 * renders double as the diagrams.
 *
 * <p>The spread is split into two top tabs. {@code GUIDE} holds the chapters,
 * listed in a scrollable side column while the chapter itself fills the rest of
 * the page. {@code RECIPES} lists the moonshine recipes the player has already
 * produced: the basic styles are always shown, the rest appear once a bottle of
 * that type has been made. Both the side column and the page body scroll with
 * the mouse wheel.</p>
 */
public final class GuideScreen extends Screen {
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
    private static final int HEADS_COLOR = 0xFF839C91;
    private static final int HEARTS_COLOR = 0xFFC18A45;
    private static final int TAILS_COLOR = 0xFF7B4C2E;

    /** 0 = guide, 1 = recipes. */
    private int tab;
    private int pageIndex;
    private int recipeIndex;
    private int listScroll;
    private int contentScroll;
    private int listMaxScroll;
    private int contentMaxScroll;

    public GuideScreen() {
        super(Component.translatable("guide.samogon.title"));
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
        g.drawString(this.font, Component.translatable("guide.samogon.book.title"), 18, 15, COPPER_DARK, false);
        g.drawString(this.font, Component.translatable("guide.samogon.book.subtitle"), 18, 26, SOFT, false);

        drawTab(g, TAB_GUIDE_X, TAB_GUIDE_W, "guide.samogon.tab.guide", tab == 0, mx, my);
        drawTab(g, TAB_RECIPES_X, TAB_RECIPES_W, "guide.samogon.tab.recipes", tab == 1, mx, my);

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
            List<Recipe> visible = visibleRecipes();
            Component progress = Component.translatable("guide.samogon.recipes.progress",
                    visible.size(), Recipe.values().length);
            centered(g, progress, centerX, 243, SOFT);
        } else {
            centered(g, Component.translatable("guide.samogon.scroll"), centerX, 243, SOFT);
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
            case MASH -> drawMash(g);
            case RAW -> drawRaw(g);
            case STILL -> drawStill(g);
            case CLEAN -> drawClean(g);
            case DILUTE -> drawDilute(g);
            case BLEND -> drawBlend(g);
            case AGE -> drawAge(g);
            case TROUBLE -> drawTrouble(g);
            case MEMO -> drawMemo(g);
        };
    }

    // === RECIPES TAB ===

    private void drawRecipes(GuiGraphics g, int mx, int my) {
        List<Recipe> visible = visibleRecipes();
        if (visible.isEmpty()) {
            recipeIndex = 0;
        } else {
            recipeIndex = clamp(recipeIndex, 0, visible.size() - 1);
        }
        drawRecipeList(g, mx, my, visible);

        String counter = visible.isEmpty() ? "00 / 00"
                : String.format(Locale.ROOT, "%02d / %02d", recipeIndex + 1, visible.size());
        drawContentHeader(g, "guide.samogon.recipes.title", "guide.samogon.recipes.tag", counter);

        beginClip(g, CONTENT_LEFT, BODY_TOP, CONTENT_W + 4, BODY_VIEW_H);
        g.pose().pushPose();
        g.pose().translate(0.0F, (float) (BODY_TOP - BODY_BASE - contentScroll), 0.0F);
        int bottom = visible.isEmpty()
                ? drawTextBody(g, "guide.samogon.recipes.empty")
                : drawRecipeBody(g, visible.get(recipeIndex));
        g.pose().popPose();
        g.disableScissor();

        contentMaxScroll = Math.max(0, (bottom - BODY_BASE) - BODY_VIEW_H);
        contentScroll = clamp(contentScroll, 0, contentMaxScroll);
        drawScrollbar(g, CONTENT_LEFT + CONTENT_W + 1, BODY_TOP, CONTENT_BOTTOM, contentScroll, contentMaxScroll);
    }

    private void drawRecipeList(GuiGraphics g, int mx, int my, List<Recipe> visible) {
        int total = visible.size() * RECIPE_STEP;
        int view = LIST_BOTTOM - LIST_TOP;
        listMaxScroll = Math.max(0, total - view);
        listScroll = clamp(listScroll, 0, listMaxScroll);

        beginClip(g, LIST_X, LIST_TOP, LIST_W, view);
        for (int i = 0; i < visible.size(); i++) {
            Recipe recipe = visible.get(i);
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
            Component name = Component.translatable(recipe.type.translationKey())
                    .withStyle(ChatFormatting.BOLD);
            int nameWidth = LIST_W - 26;
            var nameLines = this.font.split(name, nameWidth);
            for (int line = 0; line < nameLines.size() && line < 2; line++) {
                g.drawString(this.font, nameLines.get(line), LIST_X + 24, y + 1 + line * 9,
                        active ? COPPER_DARK : INK, false);
            }
            int originY = y + 1 + Math.min(2, nameLines.size()) * 9 + 1;
            g.drawString(this.font, Component.translatable(
                            "guide.samogon.recipe.origin." + recipe.origin),
                    LIST_X + 24, originY, SOFT, false);
        }
        g.disableScissor();
        drawScrollbar(g, LIST_X + LIST_W + 1, LIST_TOP, LIST_BOTTOM, listScroll, listMaxScroll);
    }

    private int drawRecipeBody(GuiGraphics g, Recipe recipe) {
        int x = CONTENT_X;
        int w = CONTENT_W;
        int y = BODY_BASE;

        drawItem(g, recipeIcon(recipe), x, y, 30);
        g.drawString(this.font, Component.translatable(recipe.type.translationKey()).withStyle(ChatFormatting.BOLD),
                x + 38, y + 1, COPPER_DARK, false);
        text(g, recipe.type.descriptionKey(), x + 38, y + 12, w - 38, SOFT);
        y = Math.max(y + 30, y + 12 + textHeight(recipe.type.descriptionKey(), w - 38)) + 6;

        Component origin = Component.translatable("guide.samogon.recipe.origin",
                Component.translatable("guide.samogon.recipe.origin." + recipe.origin));
        y += textComponent(g, origin, x, y, w, INK) + 5;

        g.drawString(this.font, Component.translatable("guide.samogon.recipe.steps").withStyle(ChatFormatting.BOLD),
                x, y, COPPER_DARK, false);
        y += 12;
        for (int i = 0; i < recipe.steps.length; i++) {
            String key = "guide.samogon.recipe.step." + recipe.steps[i];
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

    private static List<Recipe> visibleRecipes() {
        List<Recipe> result = new ArrayList<>();
        for (Recipe recipe : Recipe.values()) {
            if (recipe.base || ClientRecipeBook.has(recipe.type.id())) {
                result.add(recipe);
            }
        }
        return result;
    }

    private ItemStack recipeIcon(Recipe recipe) {
        return switch (recipe.type) {
            case BRAGA -> new ItemStack(ModItems.FERMENTED_MASH.get());
            case RUM, WHISKEY, SCOTCH, BRANDY, COGNAC, CALVADOS ->
                    new ItemStack(ModItems.OAK_AGED_SPIRIT.get());
            case VODKA -> new ItemStack(ModItems.DILUTED_SPIRIT.get());
            case SPIRIT -> new ItemStack(ModItems.NEUTRAL_SPIRIT.get());
            case GIN -> new ItemStack(ModItems.SAMOGON.get());
            case ABSINTHE -> artisanBottle(2);
            case LIQUEUR -> artisanBottle(1);
            case COCOA -> artisanBottle(1);
            case MELON -> artisanBottle(2);
            case CARROT -> artisanBottle(3);
            case CACTUS -> artisanBottle(4);
            case TINCTURE -> new ItemStack(ModItems.BERRY_TINCTURE.get());
            default -> new ItemStack(ModItems.SAMOGON.get());
        };
    }

    /**
     * An artisan bottle with a custom_model_data variant, so a recipe shows a
     * bottle in the drink's own colour instead of its raw material.
     */
    private ItemStack artisanBottle(int variant) {
        ItemStack stack = new ItemStack(ModItems.SAMOGON.get());
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(variant));
        return stack;
    }

    // === PAGE BODIES ===

    private int drawOverview(GuiGraphics g) {
        int x = CONTENT_X;
        int y = BODY_BASE;
        int w = CONTENT_W;
        text(g, "guide.samogon.page.overview.lead", x, y, w, INK);
        int flowY = y + textHeight("guide.samogon.page.overview.lead", w) + 4;

        ItemStack[] machines = {
                new ItemStack(ModBlocks.MASH_TUN.get()),
                new ItemStack(ModBlocks.FERMENTATION_BARREL.get()),
                new ItemStack(ModBlocks.DISTILLER.get()),
                new ItemStack(ModBlocks.REFLUX_COLUMN.get()),
                new ItemStack(ModBlocks.AGING_CASK.get())
        };
        for (int i = 0; i < machines.length; i++) {
            int rowY = flowY + i * 26;
            drawItem(g, machines[i], x + 1, rowY - 2, 22);
            g.drawString(this.font, Component.translatable("guide.samogon.page.overview.flow" + (i + 1))
                    .withStyle(ChatFormatting.BOLD), x + 28, rowY, COPPER_DARK, false);
            text(g, "guide.samogon.page.overview.flow" + (i + 1) + ".sub", x + 28, rowY + 10, w - 28, SOFT);
            if (i < machines.length - 1) {
                drawIcon(g, 1, x + 8, rowY + 20, 7);
            }
        }

        int tipY = flowY + machines.length * 26 - 2;
        panel(g, x, tipY, w, 24);
        drawIcon(g, 6, x + 5, tipY + 6, 10);
        text(g, "guide.samogon.page.overview.tip", x + 20, tipY + 4, w - 25, INK);
        return tipY + 24;
    }

    private int drawMash(GuiGraphics g) {
        int x = CONTENT_X;
        int w = CONTENT_W;
        drawItem(g, new ItemStack(ModBlocks.MASH_TUN.get()), 198, 50, 34);
        drawIcon(g, 0, 244, 64, 14);
        drawItem(g, new ItemStack(ModBlocks.FERMENTATION_BARREL.get()), 292, 50, 34);
        centered(g, Component.translatable("guide.samogon.page.mash.label.mash"), 215, 88, COPPER_DARK);
        centered(g, Component.translatable("guide.samogon.page.mash.label.ferment"), 309, 88, COPPER_DARK);

        int y = 104;
        y += step(g, x, y, w, 2, "guide.samogon.page.mash.step1", INK) + 5;
        y += step(g, x, y, w, 4, "guide.samogon.page.mash.step2", INK) + 5;
        y += step(g, x, y, w, 5, "guide.samogon.page.mash.step3", INK) + 5;

        int tipH = 7 + textHeight("guide.samogon.page.mash.tip", w - 25);
        panel(g, x, y, w, tipH);
        drawIcon(g, 10, x + 5, y + 6, 10);
        text(g, "guide.samogon.page.mash.tip", x + 20, y + 4, w - 25, INK);

        int noteY = y + tipH + 4;
        if (noteY < 226) {
            note(g, 3, x, noteY, w, "guide.samogon.page.mash.note", GREEN);
            return noteY + textHeight("guide.samogon.page.mash.note", w - 11);
        }
        return y + tipH;
    }

    private int drawRaw(GuiGraphics g) {
        int x = CONTENT_X;
        int w = CONTENT_W;
        ItemStack[] raw = {
                new ItemStack(ModItems.MALT.get()),
                new ItemStack(Items.WHEAT),
                new ItemStack(Items.SUGAR),
                new ItemStack(Items.SWEET_BERRIES),
                new ItemStack(Items.HONEY_BOTTLE),
                new ItemStack(Items.POTATO),
                new ItemStack(Items.BEETROOT),
                new ItemStack(Items.SUGAR_CANE),
                new ItemStack(Items.APPLE)
        };
        String[] keys = {
                "guide.samogon.page.raw.malt",
                "guide.samogon.page.raw.wheat",
                "guide.samogon.page.raw.sugar",
                "guide.samogon.page.raw.berries",
                "guide.samogon.page.raw.honey",
                "guide.samogon.page.raw.potato",
                "guide.samogon.page.raw.beetroot",
                "guide.samogon.page.raw.cane",
                "guide.samogon.page.raw.apple"
        };
        int y = BODY_BASE;
        for (int i = 0; i < raw.length; i++) {
            drawItem(g, raw[i], x, y + 1, 16);
            text(g, keys[i], x + 21, y, w - 21, INK);
            y += Math.max(16, textHeight(keys[i], w - 21)) + 2;
        }

        int summaryH = 7 + textHeight("guide.samogon.page.raw.summary", w - 8);
        panel(g, x, y + 2, w, summaryH);
        text(g, "guide.samogon.page.raw.summary", x + 4, y + 5, w - 8, INK);
        y += summaryH + 5;

        int tipH = 7 + textHeight("guide.samogon.page.raw.tip", w - 20);
        panel(g, x, y + 2, w, tipH);
        drawIcon(g, 6, x + 5, y + 5, 10);
        text(g, "guide.samogon.page.raw.tip", x + 20, y + 4, w - 25, INK);
        return y + tipH + 2;
    }

    private int drawTrouble(GuiGraphics g) {
        int x = CONTENT_X;
        int w = CONTENT_W;
        String[] keys = {
                "guide.samogon.page.trouble.heat",
                "guide.samogon.page.trouble.ferment",
                "guide.samogon.page.trouble.still",
                "guide.samogon.page.trouble.cuts",
                "guide.samogon.page.trouble.column",
                "guide.samogon.page.trouble.basin",
                "guide.samogon.page.trouble.bottle"
        };
        int y = BODY_BASE;
        for (int i = 0; i < keys.length; i++) {
            int h = textHeight(keys[i], w - 14);
            g.fill(x, y + 1, x + 2, y + 10, COPPER);
            g.drawString(this.font, String.valueOf(i + 1), x + 5, y, COPPER_DARK, false);
            text(g, keys[i], x + 14, y, w - 14, INK);
            y += Math.max(14, h) + 2;
        }

        int tipH = 7 + textHeight("guide.samogon.page.trouble.tip", w - 20);
        panel(g, x, y + 2, w, tipH);
        drawIcon(g, 8, x + 5, y + 5, 10);
        text(g, "guide.samogon.page.trouble.tip", x + 20, y + 4, w - 25, GREEN);
        return y + tipH + 2;
    }

    private int drawStill(GuiGraphics g) {
        int x = CONTENT_X;
        int w = CONTENT_W;
        drawItem(g, new ItemStack(ModBlocks.DISTILLER.get()), 188, 50, 42);
        drawIcon(g, 0, 236, 65, 14);
        drawItem(g, new ItemStack(ModItems.HEADS_FRACTION.get()), 252, 56, 30);
        drawItem(g, new ItemStack(ModItems.HEARTS_FRACTION.get()), 290, 56, 30);
        drawItem(g, new ItemStack(ModItems.TAILS_FRACTION.get()), 328, 56, 30);

        int barY = 94;
        int barH = 12;
        int first = 74;
        int second = 104;
        int third = w - first - second - 4;
        g.fill(x, barY, x + w, barY + barH, PAPER);
        g.fill(x, barY, x + first, barY + barH, HEADS_COLOR);
        g.fill(x + first + 2, barY, x + first + 2 + second, barY + barH, HEARTS_COLOR);
        g.fill(x + first + second + 4, barY, x + w, barY + barH, TAILS_COLOR);
        g.renderOutline(x, barY, w, barH, PANEL_BORDER);

        centered(g, Component.translatable("guide.samogon.page.still.label.heads"),
                x + first / 2, 109, COPPER_DARK);
        centered(g, Component.translatable("guide.samogon.page.still.label.hearts"),
                x + first + 2 + second / 2, 109, COPPER_DARK);
        centered(g, Component.translatable("guide.samogon.page.still.label.tails"),
                x + first + second + 4 + third / 2, 109, COPPER_DARK);

        int y = 130;
        y += step(g, x, y, w, 2, "guide.samogon.page.still.step1", INK) + 3;
        y += step(g, x, y, w, 10, "guide.samogon.page.still.step2", INK) + 3;
        y += step(g, x, y, w, 6, "guide.samogon.page.still.step3", INK) + 3;
        y += step(g, x, y, w, 7, "guide.samogon.page.still.step4", RED) + 4;
        if (y < 224) {
            note(g, 5, x, y, w, "guide.samogon.page.still.note", GREEN);
            return y + textHeight("guide.samogon.page.still.note", w - 11);
        }
        return y;
    }

    private int drawClean(GuiGraphics g) {
        int x = CONTENT_X;
        int w = CONTENT_W;
        drawItem(g, new ItemStack(ModBlocks.REFLUX_COLUMN.get()), 192, 50, 38);
        drawIcon(g, 0, 238, 65, 14);
        drawItem(g, new ItemStack(ModBlocks.CHARCOAL_FILTER.get()), 288, 50, 38);
        centered(g, Component.translatable("guide.samogon.page.clean.label.column"), 211, 90, COPPER_DARK);
        centered(g, Component.translatable("guide.samogon.page.clean.label.filter"), 307, 90, COPPER_DARK);

        badge(g, x, 100, 122, 27, 6, "guide.samogon.page.clean.badge1", "guide.samogon.page.clean.badge1.sub");
        badge(g, 310, 100, 128, 27, 5, "guide.samogon.page.clean.badge2", "guide.samogon.page.clean.badge2.sub");

        int y = 132;
        y += step(g, x, y, w, 2, "guide.samogon.page.clean.step1", INK) + 5;
        y += step(g, x, y, w, 6, "guide.samogon.page.clean.step2", INK) + 5;
        y += step(g, x, y, w, 3, "guide.samogon.page.clean.step3", INK) + 5;
        y += step(g, x, y, w, 5, "guide.samogon.page.clean.step4", INK) + 5;

        int tipH = 7 + textHeight("guide.samogon.page.clean.tip", w - 25);
        panel(g, x, y, w, tipH);
        drawIcon(g, 5, x + 5, y + 5, 10);
        text(g, "guide.samogon.page.clean.tip", x + 20, y + 4, w - 25, INK);
        return y + tipH;
    }

    private int drawDilute(GuiGraphics g) {
        int x = CONTENT_X;
        int w = CONTENT_W;
        drawItem(g, new ItemStack(ModBlocks.DILUTION_BASIN.get()), 188, 50, 40);
        drawIcon(g, 0, 234, 64, 14);
        drawItem(g, new ItemStack(Items.WATER_BUCKET), 258, 58, 26);
        drawItem(g, new ItemStack(ModItems.HYDROMETER.get()), 298, 56, 28);
        drawItem(g, new ItemStack(ModItems.DILUTED_SPIRIT.get()), 344, 58, 26);
        centered(g, Component.translatable("guide.samogon.page.dilute.label.basin"),
                208, 94, COPPER_DARK);
        centered(g, Component.translatable("guide.samogon.page.dilute.label.water"),
                271, 94, COPPER_DARK);
        centered(g, Component.translatable("guide.samogon.page.dilute.label.target"),
                357, 94, COPPER_DARK);

        int gaugeY = 104;
        int gaugeH = 10;
        int marker = x + Math.round(w * 0.4F);
        g.fill(x, gaugeY, x + w, gaugeY + gaugeH, 0xFFE2CDA1);
        g.fill(x, gaugeY, marker, gaugeY + gaugeH, HEARTS_COLOR);
        for (int i = 0; i <= 5; i++) {
            int tick = x + w * i / 5;
            g.fill(tick, gaugeY + gaugeH, tick + 1, gaugeY + gaugeH + 2, PANEL_BORDER);
        }
        g.fill(marker - 1, gaugeY - 2, marker + 1, gaugeY + gaugeH + 2, COPPER);
        g.drawString(this.font, "0", x, 116, SOFT, false);
        g.drawString(this.font, "40%", marker - 10, 116, COPPER_DARK, false);
        g.drawString(this.font, "100%", x + w - 18, 116, SOFT, false);
        g.drawString(this.font, Component.translatable("guide.samogon.page.dilute.gauge.caption"), x, 126, SOFT, false);

        int y = 138;
        y += step(g, x, y, w, 3, "guide.samogon.page.dilute.step1", INK) + 4;
        y += step(g, x, y, w, 6, "guide.samogon.page.dilute.step2", INK) + 4;
        y += step(g, x, y, w, 5, "guide.samogon.page.dilute.step3", INK) + 4;
        y += step(g, x, y, w, 11, "guide.samogon.page.dilute.step4", INK) + 5;

        int tipH = 7 + textHeight("guide.samogon.page.dilute.tip", w - 25);
        panel(g, x, y, w, tipH);
        drawIcon(g, 10, x + 5, y + 5, 10);
        text(g, "guide.samogon.page.dilute.tip", x + 20, y + 4, w - 25, INK);
        return y + tipH;
    }

    private int drawBlend(GuiGraphics g) {
        int x = CONTENT_X;
        int w = CONTENT_W;
        drawItem(g, new ItemStack(ModBlocks.BLENDING_TABLE.get()), 188, 50, 34);
        drawIcon(g, 0, 232, 64, 14);
        ItemStack[] ingredients = {
                new ItemStack(Items.SWEET_BERRIES),
                new ItemStack(Items.HONEY_BOTTLE),
                new ItemStack(ModItems.BOTANICAL_BUNDLE.get())
        };
        int[] xs = {262, 296, 330};
        for (int i = 0; i < ingredients.length; i++) {
            drawItem(g, ingredients[i], xs[i], 58, 24);
        }
        centered(g, Component.translatable("guide.samogon.page.blend.ingredients"),
                x + w / 2, 84, SOFT);

        badge(g, x, 98, 122, 27, 4,
                "guide.samogon.page.blend.badge1", "guide.samogon.page.blend.badge1.sub");
        badge(g, 310, 98, 128, 27, 5,
                "guide.samogon.page.blend.badge2", "guide.samogon.page.blend.badge2.sub");

        int y = 134;
        y += step(g, x, y, w, 3, "guide.samogon.page.blend.step1", INK) + 4;
        y += step(g, x, y, w, 8, "guide.samogon.page.blend.step2", INK) + 4;
        y += step(g, x, y, w, 5, "guide.samogon.page.blend.step3", INK) + 4;
        y += step(g, x, y, w, 11, "guide.samogon.page.blend.step4", INK) + 5;

        int tipH = 7 + textHeight("guide.samogon.page.blend.tip", w - 25);
        panel(g, x, y, w, tipH);
        drawIcon(g, 6, x + 5, y + 5, 10);
        text(g, "guide.samogon.page.blend.tip", x + 20, y + 4, w - 25, INK);
        return y + tipH;
    }

    private int drawAge(GuiGraphics g) {
        int x = CONTENT_X;
        int w = CONTENT_W;
        drawItem(g, new ItemStack(ModBlocks.AGING_CASK.get()), 191, 50, 38);
        drawIcon(g, 0, 240, 66, 14);
        drawItem(g, new ItemStack(ModItems.OAK_CHIPS.get()), 266, 60, 24);
        drawIcon(g, 5, 304, 66, 10);
        g.drawString(this.font, Component.translatable("guide.samogon.page.age.time"), 318, 67, COPPER_DARK, false);
        drawIcon(g, 0, 350, 66, 12);
        drawItem(g, new ItemStack(ModItems.OAK_AGED_SPIRIT.get()), 374, 58, 26);
        centered(g, Component.translatable("guide.samogon.page.age.label.cask"), 210, 90, COPPER_DARK);
        centered(g, Component.translatable("guide.samogon.page.age.label.oak"), 278, 90, COPPER_DARK);
        centered(g, Component.translatable("guide.samogon.page.age.label.ready"), 391, 90, COPPER_DARK);

        int gaugeY = 102;
        int gaugeH = 10;
        int from = x + Math.round(w * 0.04F);
        int to = x + Math.round(w * 0.6F);
        g.fill(x, gaugeY, x + w, gaugeY + gaugeH, 0xFFE2CDA1);
        g.fill(from, gaugeY, to, gaugeY + gaugeH, HEARTS_COLOR);
        g.fill(from - 1, gaugeY - 2, from + 1, gaugeY + gaugeH + 2, COPPER);
        g.fill(to - 1, gaugeY - 2, to + 1, gaugeY + gaugeH + 2, COPPER);
        g.drawString(this.font, "4%", from - 5, 114, COPPER_DARK, false);
        g.drawString(this.font, "60%", to - 11, 114, COPPER_DARK, false);
        g.drawString(this.font, Component.translatable("guide.samogon.page.age.gauge.caption"), x, 124, SOFT, false);

        int y = 136;
        y += step(g, x, y, w, 3, "guide.samogon.page.age.step1", INK) + 4;
        y += step(g, x, y, w, 8, "guide.samogon.page.age.step2", INK) + 4;
        y += step(g, x, y, w, 5, "guide.samogon.page.age.step3", INK) + 4;
        y += step(g, x, y, w, 11, "guide.samogon.page.age.step4", INK) + 5;

        int tipH = 7 + textHeight("guide.samogon.page.age.tip", w - 25);
        panel(g, x, y, w, tipH);
        drawIcon(g, 7, x + 5, y + 5, 10);
        text(g, "guide.samogon.page.age.tip", x + 20, y + 4, w - 25, RED);
        return y + tipH;
    }

    private int drawMemo(GuiGraphics g) {
        int x = CONTENT_X;
        int w = CONTENT_W;
        int y = BODY_BASE;
        int headerH = 14;
        int rowH = 14;
        int[] cols = {96, 50, 108};
        String[] rowIds = {"mash", "ferment", "still", "rectify", "filter", "dilute", "infusion", "age"};
        int tableH = headerH + rowIds.length * rowH;

        g.fill(x, y, x + w, y + headerH, 0x338A4A22);
        g.renderOutline(x, y, w, tableH, PANEL_BORDER);
        g.fill(x + cols[0], y, x + cols[0] + 1, y + tableH, 0x338A4A22);
        g.fill(x + cols[0] + cols[1], y, x + cols[0] + cols[1] + 1, y + tableH, 0x338A4A22);
        g.drawString(this.font, Component.translatable("guide.samogon.page.memo.table.stage"), x + 4, y + 3, COPPER_DARK, false);
        g.drawString(this.font, Component.translatable("guide.samogon.page.memo.table.time"), x + cols[0] + 4, y + 3, COPPER_DARK, false);
        g.drawString(this.font, Component.translatable("guide.samogon.page.memo.table.need"), x + cols[0] + cols[1] + 4, y + 3, COPPER_DARK, false);

        for (int i = 0; i < rowIds.length; i++) {
            int rowY = y + headerH + i * rowH;
            if ((i & 1) == 1) {
                g.fill(x + 1, rowY, x + w - 1, rowY + rowH, 0x18FFF4D8);
            }
            g.fill(x, rowY, x + w, rowY + 1, 0x228A4A22);
            g.drawString(this.font, Component.translatable("guide.samogon.page.memo.row." + rowIds[i]),
                    x + 4, rowY + 3, INK, false);
            g.drawString(this.font, Component.translatable("guide.samogon.page.memo.row." + rowIds[i] + ".time"),
                    x + cols[0] + 4, rowY + 3, SOFT, false);
            g.drawString(this.font, Component.translatable("guide.samogon.page.memo.row." + rowIds[i] + ".need"),
                    x + cols[0] + cols[1] + 4, rowY + 3, SOFT, false);
        }

        int tableBottom = y + headerH + rowIds.length * rowH;
        int panelY = tableBottom + 4;
        panel(g, x, panelY, w, 27);
        drawItem(g, new ItemStack(ModItems.THERMOMETER.get()), x + 5, panelY + 6, 16);
        drawItem(g, new ItemStack(ModItems.HYDROMETER.get()), x + 23, panelY + 6, 16);
        text(g, "guide.samogon.page.memo.tools", x + 44, panelY + 4, w - 48, INK);

        int safetyY = panelY + 31;
        drawIcon(g, 7, x + 2, safetyY + 2, 10);
        text(g, "guide.samogon.page.memo.safety", x + 17, safetyY, w - 20, RED);
        return safetyY + textHeight("guide.samogon.page.memo.safety", w - 20);
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

    /**
     * The handbook is meant to read like ink on paper. Vanilla's default
     * drop-shadow makes the small pixel font look smudged, so every custom
     * label is drawn with a flat glyph.
     */
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

    private void badge(GuiGraphics g, int x, int y, int w, int h, int icon,
                       String mainKey, String subKey) {
        g.fill(x, y, x + w, y + h, 0x1F8A4A22);
        g.fill(x, y, x + w, y + 1, PANEL_BORDER);
        g.fill(x, y + h - 1, x + w, y + h, PANEL_BORDER);
        drawIcon(g, icon, x + 5, y + 8, 10);
        g.drawString(this.font, Component.translatable(mainKey), x + 20, y + 3, COPPER_DARK, false);
        g.drawString(this.font, Component.translatable(subKey), x + 20, y + 14, SOFT, false);
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
            case OVERVIEW -> new ItemStack(ModItems.GUIDE.get());
            case MASH -> new ItemStack(ModBlocks.MASH_TUN.get());
            case RAW -> new ItemStack(ModItems.MALT.get());
            case STILL -> new ItemStack(ModBlocks.DISTILLER.get());
            case CLEAN -> new ItemStack(ModBlocks.REFLUX_COLUMN.get());
            case DILUTE -> new ItemStack(ModBlocks.DILUTION_BASIN.get());
            case BLEND -> new ItemStack(ModBlocks.BLENDING_TABLE.get());
            case AGE -> new ItemStack(ModBlocks.AGING_CASK.get());
            case TROUBLE -> new ItemStack(ModItems.THERMOMETER.get());
            case MEMO -> new ItemStack(ModItems.GUIDE.get());
        };
    }

    // === SCROLLING ===

    /** Clips subsequent drawing to a book-local rectangle, translated to the screen. */
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
                    List<Recipe> visible = visibleRecipes();
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
        OVERVIEW("guide.samogon.toc.overview", "guide.samogon.page.overview.title", "guide.samogon.page.overview.tag"),
        MASH("guide.samogon.toc.mash", "guide.samogon.page.mash.title", "guide.samogon.page.mash.tag"),
        RAW("guide.samogon.toc.raw", "guide.samogon.page.raw.title", "guide.samogon.page.raw.tag"),
        STILL("guide.samogon.toc.still", "guide.samogon.page.still.title", "guide.samogon.page.still.tag"),
        CLEAN("guide.samogon.toc.clean", "guide.samogon.page.clean.title", "guide.samogon.page.clean.tag"),
        DILUTE("guide.samogon.toc.dilute", "guide.samogon.page.dilute.title", "guide.samogon.page.dilute.tag"),
        BLEND("guide.samogon.toc.blend", "guide.samogon.page.blend.title", "guide.samogon.page.blend.tag"),
        AGE("guide.samogon.toc.age", "guide.samogon.page.age.title", "guide.samogon.page.age.tag"),
        TROUBLE("guide.samogon.toc.trouble", "guide.samogon.page.trouble.title", "guide.samogon.page.trouble.tag"),
        MEMO("guide.samogon.toc.memo", "guide.samogon.page.memo.title", "guide.samogon.page.memo.tag");

        final String tocKey;
        final String titleKey;
        final String tagKey;

        Page(String tocKey, String titleKey, String tagKey) {
            this.tocKey = tocKey;
            this.titleKey = titleKey;
            this.tagKey = tagKey;
        }
    }

    /**
     * A recipe is defined by the spirit type it produces and the ordered
     * production steps. Basic recipes are always readable; the rest only show
     * up once the player has actually made that type.
     */
    private enum Recipe {
        BRAGA(SpiritType.BRAGA, true, "any", "mash", "ferment"),
        MOONSHINE(SpiritType.MOONSHINE, true, "any", "mash", "ferment", "distill_whole"),
        CHACHA(SpiritType.CHACHA, true, "berry", "mash", "ferment", "distill_cuts"),
        RUM(SpiritType.RUM, true, "sugar", "mash", "ferment", "distill_cuts", "oak"),
        WHISKEY(SpiritType.WHISKEY, true, "grain", "mash", "ferment", "distill_cuts", "oak"),
        VODKA(SpiritType.VODKA, true, "grain", "distill_cuts", "rectify", "filter", "dilute"),
        SPIRIT(SpiritType.SPIRIT, true, "grain", "distill_cuts", "rectify"),
        GIN(SpiritType.GIN, false, "any", "distill_cuts", "rectify", "blend_botanical", "dilute"),
        ABSINTHE(SpiritType.ABSINTHE, false, "any", "distill_cuts", "rectify", "blend_wormwood"),
        LIQUEUR(SpiritType.LIQUEUR, false, "any", "distill_cuts", "dilute", "sweeten"),
        BRANDY(SpiritType.BRANDY, false, "berry", "mash", "ferment", "distill_cuts", "oak"),
        COGNAC(SpiritType.COGNAC, false, "berry", "mash", "ferment", "distill_cuts", "distill2", "oak"),
        SCOTCH(SpiritType.SCOTCH, false, "grain", "mash", "ferment", "distill_cuts", "distill2", "oak"),
        CALVADOS(SpiritType.CALVADOS, false, "apple", "mash", "ferment", "distill_cuts", "oak"),
        CACHACA(SpiritType.CACHACA, false, "cane", "mash", "ferment", "distill_cuts"),
        POTATO(SpiritType.POTATO, false, "potato", "mash", "ferment", "distill_cuts"),
        BEET(SpiritType.BEET, false, "beetroot", "mash", "ferment", "distill_cuts"),
        MEAD(SpiritType.MEAD, false, "honey", "mash", "ferment", "distill_cuts"),
        COCOA(SpiritType.COCOA, true, "cocoa", "mash", "ferment", "distill_cuts"),
        MELON(SpiritType.MELON, true, "melon", "mash", "ferment", "distill_cuts"),
        CARROT(SpiritType.CARROT, true, "carrot", "mash", "ferment", "distill_cuts"),
        CACTUS(SpiritType.CACTUS, true, "cactus", "mash", "ferment", "distill_cuts"),
        TINCTURE(SpiritType.TINCTURE, false, "any", "distill_cuts", "blend", "infusion");

        final SpiritType type;
        final boolean base;
        final String origin;
        final String[] steps;

        Recipe(SpiritType type, boolean base, String origin, String... steps) {
            this.type = type;
            this.base = base;
            this.origin = origin;
            this.steps = steps;
        }
    }
}