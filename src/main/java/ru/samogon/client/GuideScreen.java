package ru.samogon.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import ru.samogon.registry.ModBlocks;
import ru.samogon.registry.ModItems;

import java.util.Locale;

/**
 * An illustrated, parchment-styled handbook. It deliberately uses no container
 * widgets: the whole spread is drawn as a book, and the three-dimensional item
 * renders double as the diagrams.
 */
public final class GuideScreen extends Screen {
    private static final int BOOK_W = 460;
    private static final int BOOK_H = 265;
    private static final int TEX_W = 920;
    private static final int TEX_H = 530;
    private static final int CONTENT_X = 184;
    private static final int CONTENT_W = 254;
    private static final int TOC_X = 20;
    private static final int TOC_TOP = 64;
    private static final int TOC_STEP = 20;

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

    private int pageIndex;

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
        drawToc(g, mx, my);
        Page page = Page.values()[pageIndex];
        drawHeader(g, page, mx, my);
        switch (page) {
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
        }
        drawFooter(g, mx, my);
    }

    private static int tocStep() {
        return Page.values().length > 8 ? 17 : TOC_STEP;
    }

    private void drawToc(GuiGraphics g, int mx, int my) {
        g.drawString(this.font, Component.translatable("guide.samogon.book.title"), TOC_X, 17, COPPER_DARK, false);
        g.drawString(this.font, Component.translatable("guide.samogon.book.subtitle"), TOC_X, 29, SOFT, false);
        g.fill(TOC_X, 40, 158, 41, PANEL_BORDER);
        g.drawString(this.font, Component.translatable("guide.samogon.book.chapters"), TOC_X, 50, SOFT, false);

        int step = tocStep();
        for (int i = 0; i < Page.values().length; i++) {
            int y = TOC_TOP + i * step;
            boolean active = i == pageIndex;
            boolean hover = mx >= 15 && mx <= 160 && my >= y - 4 && my <= y + 12;
            if (active) {
                g.fill(15, y - 4, 160, y + 12, 0x30A65A28);
                g.fill(15, y - 4, 18, y + 12, COPPER);
            } else if (hover) {
                g.fill(15, y - 4, 160, y + 12, 0x18A65A28);
            }
            drawItem(g, pageIcon(Page.values()[i]), TOC_X, y - 4, 14);
            g.drawString(this.font, Component.translatable(Page.values()[i].tocKey),
                    TOC_X + 18, y, active ? COPPER_DARK : INK, false);
        }

        g.drawString(this.font, Component.translatable("guide.samogon.book.close"), TOC_X, 243, SOFT, false);
    }

    private void drawHeader(GuiGraphics g, Page page, int mx, int my) {
        g.drawString(this.font, Component.translatable(page.titleKey).withStyle(ChatFormatting.BOLD),
                CONTENT_X, 16, COPPER_DARK, false);
        drawIcon(g, 6, CONTENT_X, 28, 8);
        g.drawString(this.font, Component.translatable(page.tagKey), CONTENT_X + 11, 28, SOFT, false);

        String index = String.format(Locale.ROOT, "%02d", pageIndex + 1);
        g.drawString(this.font, index, 414, 16, SOFT, false);
        boolean closeHover = mx >= 430 && mx <= 450 && my >= 8 && my <= 28;
        g.drawString(this.font, "×", 438, 13, closeHover ? RED : COPPER, false);

        g.fill(CONTENT_X, 40, CONTENT_X + CONTENT_W, 41, PANEL_BORDER);
        g.fill(310, 39, 313, 42, 0xAA8A4A22);
    }

    private void drawFooter(GuiGraphics g, int mx, int my) {
        g.fill(CONTENT_X, 238, CONTENT_X + CONTENT_W, 239, 0x338A4A22);
        String pager = String.format(Locale.ROOT, "%02d / %02d", pageIndex + 1, Page.values().length);
        centered(g, pager, 400, 243, SOFT);

        boolean leftHover = mx >= 365 && mx <= 382 && my >= 238 && my <= 252;
        boolean rightHover = mx >= 418 && mx <= 436 && my >= 238 && my <= 252;
        int leftColor = pageIndex > 0 ? (leftHover ? COPPER : SOFT) : 0x668A7A5A;
        int rightColor = pageIndex < Page.values().length - 1 ? (rightHover ? COPPER : SOFT) : 0x668A7A5A;
        g.drawString(this.font, "<", 370, 242, leftColor, false);
        g.drawString(this.font, ">", 428, 242, rightColor, false);
    }

    private void drawOverview(GuiGraphics g) {
        int x = CONTENT_X;
        int y = 50;
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
    }

    private void drawMash(GuiGraphics g) {
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
        if (noteY < 226) note(g, 3, x, noteY, w, "guide.samogon.page.mash.note", GREEN);
    }

    private void drawRaw(GuiGraphics g) {
        int x = CONTENT_X;
        int w = CONTENT_W;
        ItemStack[] raw = {
                new ItemStack(ModItems.MALT.get()),
                new ItemStack(Items.WHEAT),
                new ItemStack(Items.SUGAR),
                new ItemStack(Items.SWEET_BERRIES),
                new ItemStack(Items.HONEY_BOTTLE)
        };
        String[] keys = {
                "guide.samogon.page.raw.malt",
                "guide.samogon.page.raw.wheat",
                "guide.samogon.page.raw.sugar",
                "guide.samogon.page.raw.berries",
                "guide.samogon.page.raw.honey"
        };
        int y = 52;
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
    }

    private void drawTrouble(GuiGraphics g) {
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
        int y = 52;
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
    }

    private void drawStill(GuiGraphics g) {
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
        if (y < 224) note(g, 5, x, y, w, "guide.samogon.page.still.note", GREEN);
    }

    private void drawClean(GuiGraphics g) {
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
    }

    private void drawDilute(GuiGraphics g) {
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
    }

    private void drawBlend(GuiGraphics g) {
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
    }

    private void drawAge(GuiGraphics g) {
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
        int from = x + Math.round(w * 0.2F);
        int to = x + Math.round(w * 0.6F);
        g.fill(x, gaugeY, x + w, gaugeY + gaugeH, 0xFFE2CDA1);
        g.fill(from, gaugeY, to, gaugeY + gaugeH, HEARTS_COLOR);
        g.fill(from - 1, gaugeY - 2, from + 1, gaugeY + gaugeH + 2, COPPER);
        g.fill(to - 1, gaugeY - 2, to + 1, gaugeY + gaugeH + 2, COPPER);
        g.drawString(this.font, "20%", from - 11, 114, COPPER_DARK, false);
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
    }

    private void drawMemo(GuiGraphics g) {
        int x = CONTENT_X;
        int w = CONTENT_W;
        int y = 50;
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
    }

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
        var lines = this.font.split(Component.translatable(key), width);
        for (int i = 0; i < lines.size(); i++) {
            g.drawString(this.font, lines.get(i), x, y + i * 9, color, false);
        }
    }

    /**
     * The handbook is meant to read like ink on paper. Vanilla's default
     * drop-shadow makes the small pixel font look smudged, so every custom
     * label is drawn with a flat glyph.
     */
    private void centered(GuiGraphics g, Component text, int centerX, int y, int color) {
        g.drawString(this.font, text, centerX - this.font.width(text) / 2, y, color, false);
    }

    private void centered(GuiGraphics g, String text, int centerX, int y, int color) {
        centered(g, Component.literal(text), centerX, y, color);
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

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int mx = localX(mouseX);
            int my = localY(mouseY);
            if (mx >= 430 && mx <= 450 && my >= 8 && my <= 28) {
                this.onClose();
                return true;
            }
            int step = tocStep();
            for (int i = 0; i < Page.values().length; i++) {
                int y = TOC_TOP + i * step;
                if (mx >= 15 && mx <= 160 && my >= y - 4 && my <= y + 12) {
                    pageIndex = i;
                    return true;
                }
            }
            if (mx >= 365 && mx <= 382 && my >= 238 && my <= 252) {
                previousPage();
                return true;
            }
            if (mx >= 418 && mx <= 436 && my >= 238 && my <= 252) {
                nextPage();
                return true;
            }
            return mx >= 0 && mx <= BOOK_W && my >= 0 && my <= BOOK_H;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 263) {
            previousPage();
            return true;
        }
        if (keyCode == 262) {
            nextPage();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void previousPage() {
        pageIndex = Math.max(0, pageIndex - 1);
    }

    private void nextPage() {
        pageIndex = Math.min(Page.values().length - 1, pageIndex + 1);
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
}
