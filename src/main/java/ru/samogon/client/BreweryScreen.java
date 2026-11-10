package ru.samogon.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import ru.samogon.menu.BreweryMenu;
import ru.samogon.process.BreweryData;
import ru.samogon.process.Quality;
import ru.samogon.registry.ModBlocks;

import java.util.Locale;

/**
 * The brewhouse ledger. It shares the copper-and-oak page of the other machines
 * and adds two process tabs — mashing and boiling — whose selected step is
 * stored in the block entity. Fermentation happens in the separate beer barrel,
 * not here. The baked page, slot wells and progress track come from
 * {@code textures/gui/machine_panel.png}; only the live values, the item seal and
 * the tab strip are painted here.
 */
public final class BreweryScreen extends AbstractContainerScreen<BreweryMenu> {
    private static final int IMAGE_W = 380;
    private static final int IMAGE_H = 262;

    /** Drop shadow reaches four pixels past the panel, so the texture has a margin. */
    private static final int MARGIN = 4;
    private static final int PANEL_W = IMAGE_W + 2 * MARGIN;
    private static final int PANEL_H = IMAGE_H + 2 * MARGIN;
    private static final ResourceLocation PANEL =
            ResourceLocation.fromNamespaceAndPath("samogon", "textures/gui/machine_panel.png");
    private static final ResourceLocation BUTTON =
            ResourceLocation.fromNamespaceAndPath("samogon", "textures/gui/machine_button.png");

    private static final int TAB_X = 278;
    private static final int TAB_W = 88;
    private static final int TAB_H = 20;
    private static final int[] TAB_Y = {10, 33};

    private static final int METRIC_LEFT = 16;
    private static final int METRIC_RIGHT = 202;
    private static final int METRIC_LEFT_VALUE = 178;
    private static final int METRIC_RIGHT_VALUE = 362;
    private static final int METRIC_TOP = 68;
    private static final int METRIC_STEP = 12;

    private static final int INK = 0xFF352218;
    private static final int SOFT = 0xFF6B4E36;
    private static final int FAINT = 0xFF8A6A4B;
    private static final int COPPER = 0xFF9A4A26;
    private static final int COPPER_DARK = 0xFF6E351B;
    private static final int BRASS_HI = 0xFFE0B970;
    private static final int GREEN = 0xFF536640;
    private static final int WARM = 0xFF8A6A2E;
    private static final int ACCENT = 0xFFA5762F;

    private TabButton[] tabs = new TabButton[0];

    public BreweryScreen(BreweryMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = IMAGE_W;
        this.imageHeight = IMAGE_H;
    }

    @Override
    protected void init() {
        super.init();
        tabs = new TabButton[2];
        for (int i = 0; i < tabs.length; i++) {
            tabs[i] = new TabButton(i);
            addRenderableWidget(tabs[i]);
        }
        syncTabs();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        syncTabs();
    }

    private void syncTabs() {
        int tab = menu.data(BreweryData.TAB);
        for (int i = 0; i < tabs.length; i++) {
            if (tabs[i] != null) tabs[i].setSelected(i == tab);
        }
    }

    private void sendMenuButton(int id) {
        if (minecraft == null || minecraft.gameMode == null) return;
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;

        graphics.blit(PANEL, x - MARGIN, y - MARGIN, PANEL_W, PANEL_H,
                0.0F, 0.0F, PANEL_W, PANEL_H, PANEL_W, PANEL_H);

        // Live details over the baked page: the accent under the seal, the
        // vessel stamp and the process readouts.
        graphics.fill(x + 22, y + 36, x + 42, y + 37, ACCENT);
        graphics.renderItem(ModBlocks.BREWERY_ITEM.get().getDefaultInstance(), x + 24, y + 18);
        graphics.drawString(font, title.copy().withStyle(ChatFormatting.BOLD), x + 52, y + 15, INK, false);

        // The stage reminder sits on the title line, to the right of the name,
        // clear of the rule line baked into the page.
        String hintKey = switch (menu.data(BreweryData.TAB)) {
            case 1 -> "gui.samogon.brewery.hint.heat";
            default -> null;
        };
        if (hintKey != null) {
            graphics.drawString(font, Component.translatable(hintKey),
                    x + 52 + font.width(title) + 14, y + 15, COPPER_DARK, false);
        }

        drawState(graphics, x + 16, y + 42);
        drawProgress(graphics, x + 16, y + 55, 148, 7);
        drawMetrics(graphics, x, y);

        centered(graphics, Component.translatable("gui.samogon.brewery.slot.input"),
                x + BreweryMenu.INPUT_X + 8, y + 146, COPPER_DARK);
        centered(graphics, Component.translatable("gui.samogon.brewery.slot.additive"),
                x + BreweryMenu.ADDITIVE_X + 8, y + 146, COPPER_DARK);
        centered(graphics, Component.translatable("gui.samogon.slot.output"),
                x + BreweryMenu.OUTPUT_X + 8, y + 146, COPPER_DARK);
        centered(graphics, Component.translatable("gui.samogon.inventory"), x + 190, y + 165, SOFT);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // Labels are painted in renderBg; this keeps item tooltips above them.
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        for (TabButton tab : tabs) {
            if (isOver(tab, mouseX, mouseY)) {
                renderParchmentTooltip(graphics, tab.tooltip(), mouseX, mouseY);
                break;
            }
        }
    }

    private void drawState(GuiGraphics graphics, int x, int y) {
        boolean active = menu.data(BreweryData.ACTIVE) == 1;
        boolean ready = menu.data(BreweryData.READY) == 1 && !active;
        int step = menu.data(BreweryData.STEP);
        String key;
        int colour;
        if (active) {
            key = "gui.samogon.state.running";
            colour = COPPER;
        } else if (ready) {
            key = "gui.samogon.state.ready";
            colour = GREEN;
        } else if (step == 0) {
            key = "gui.samogon.state.empty";
            colour = FAINT;
        } else {
            key = "gui.samogon.state.waiting";
            colour = WARM;
        }
        graphics.drawString(font, "\u25cf", x, y, colour, false);
        graphics.drawString(font, Component.translatable(key), x + 10, y, INK, false);
    }

    /** Paints the live progress fill, ticks, knob and value over the baked track. */
    private void drawProgress(GuiGraphics graphics, int x, int y, int width, int height) {
        int progress = Math.max(0, Math.min(100, menu.data(BreweryData.PROGRESS)));
        int filled = width * progress / 100;
        if (filled > 0) {
            graphics.fill(x, y, x + filled, y + height, ACCENT);
            graphics.fill(x, y, x + filled, y + 1, 0x55FFF4D8);
        }
        for (int tick : new int[]{25, 50, 75}) {
            int tx = x + width * tick / 100;
            graphics.fill(tx, y + 1, tx + 1, y + height - 1, 0x33725A3A);
        }
        if (progress > 0) {
            graphics.fill(x + filled - 1, y - 1, x + filled + 1, y + height + 1, BRASS_HI);
        }
        drawSplitText(graphics, x + width + 9, y - 1, "gui.samogon.progress", progress);
    }

    private void drawMetrics(GuiGraphics graphics, int x, int y) {
        int volume = menu.data(BreweryData.VOLUME);
        int abv = menu.data(BreweryData.ABV);
        int quality = menu.data(BreweryData.QUALITY);
        int impurities = menu.data(BreweryData.IMPURITIES);
        int temperature = menu.data(BreweryData.TEMPERATURE);
        int ambient = menu.data(BreweryData.AMBIENT);
        int step = Math.max(0, Math.min(3, menu.data(BreweryData.STEP)));
        int duration = menu.data(BreweryData.DURATION);
        boolean filled = volume > 0;

        metric(graphics, x + METRIC_LEFT, x + METRIC_LEFT_VALUE, y + METRIC_TOP,
                "gui.samogon.metric.volume", filled ? Integer.toString(volume) : "\u2014");
        metric(graphics, x + METRIC_RIGHT, x + METRIC_RIGHT_VALUE, y + METRIC_TOP,
                "gui.samogon.metric.temperature", f1(temperature));
        metric(graphics, x + METRIC_LEFT, x + METRIC_LEFT_VALUE, y + METRIC_TOP + METRIC_STEP,
                "gui.samogon.metric.abv", filled ? f1(abv) : "\u2014");
        metric(graphics, x + METRIC_RIGHT, x + METRIC_RIGHT_VALUE, y + METRIC_TOP + METRIC_STEP,
                "gui.samogon.metric.ambient", f1(ambient));
        metric(graphics, x + METRIC_LEFT, x + METRIC_LEFT_VALUE, y + METRIC_TOP + METRIC_STEP * 2,
                "gui.samogon.metric.quality", filled ? Quality.bar(quality) : "\u2014");
        metric(graphics, x + METRIC_RIGHT, x + METRIC_RIGHT_VALUE, y + METRIC_TOP + METRIC_STEP * 2,
                "gui.samogon.brewery.metric.step",
                Component.translatable("gui.samogon.brewery.step." + step));
        metric(graphics, x + METRIC_LEFT, x + METRIC_LEFT_VALUE, y + METRIC_TOP + METRIC_STEP * 3,
                "gui.samogon.metric.impurities", filled ? Integer.toString(impurities) : "\u2014");
        metric(graphics, x + METRIC_RIGHT, x + METRIC_RIGHT_VALUE, y + METRIC_TOP + METRIC_STEP * 3,
                "gui.samogon.metric.duration", duration > 0 ? Integer.toString(duration / 20) : "\u2014");
    }

    private void metric(GuiGraphics graphics, int labelX, int valueRight, int y,
                        String key, Object... value) {
        String[] pair = splitLabel(Component.translatable(key, value).getString());
        if (pair == null) {
            graphics.drawString(font, Component.translatable(key, value), labelX, y, INK, false);
            return;
        }
        graphics.drawString(font, pair[0], labelX, y, SOFT, false);
        graphics.drawString(font, pair[1], valueRight - font.width(pair[1]), y, INK, false);
    }

    private void drawSplitText(GuiGraphics graphics, int x, int y, String key, Object value) {
        String[] pair = splitLabel(Component.translatable(key, value).getString());
        if (pair == null) {
            graphics.drawString(font, Component.translatable(key, value), x, y, INK, false);
            return;
        }
        graphics.drawString(font, pair[0], x, y, SOFT, false);
        graphics.drawString(font, pair[1], x + font.width(pair[0]) + 4, y, INK, false);
    }

    private static String[] splitLabel(String full) {
        int colon = full.indexOf(':');
        if (colon <= 0) return null;
        String label = full.substring(0, colon).strip();
        String value = full.substring(colon + 1).strip();
        if (label.isEmpty() || value.isEmpty()) return null;
        return new String[]{label, value};
    }

    private void centered(GuiGraphics graphics, Component text, int centerX, int y, int color) {
        graphics.drawString(font, text, centerX - font.width(text) / 2, y, color, false);
    }

    private void renderParchmentTooltip(GuiGraphics graphics, Component text, int mouseX, int mouseY) {
        var lines = font.split(text, 164);
        int textWidth = 0;
        for (var line : lines) textWidth = Math.max(textWidth, font.width(line));
        int boxWidth = textWidth + 12;
        int boxHeight = lines.size() * 10 + 8;
        int tx = mouseX + 10;
        int ty = mouseY - 12;
        if (tx + boxWidth > width - 4) tx = width - boxWidth - 4;
        if (ty + boxHeight > height - 4) ty = height - boxHeight - 4;
        if (tx < 4) tx = 4;
        if (ty < 4) ty = 4;

        graphics.fill(tx - 2, ty - 2, tx + boxWidth + 2, ty + boxHeight + 2, 0x50000000);
        graphics.fill(tx, ty, tx + boxWidth, ty + boxHeight, 0xFFF2E4C3);
        graphics.renderOutline(tx, ty, boxWidth, boxHeight, COPPER_DARK);
        graphics.fill(tx + 1, ty + 1, tx + boxWidth - 1, ty + 2, 0x66FFF4D8);
        for (int i = 0; i < lines.size(); i++) {
            graphics.drawString(font, lines.get(i), tx + 6, ty + 5 + i * 10, INK, false);
        }
    }

    private static boolean isOver(Button button, int mouseX, int mouseY) {
        return button != null && button.visible
                && mouseX >= button.getX() && mouseX < button.getX() + button.getWidth()
                && mouseY >= button.getY() && mouseY < button.getY() + button.getHeight();
    }

    private static String f1(int tenths) {
        return String.format(Locale.ROOT, "%.1f", tenths / 10.0);
    }

    /**
     * A paper tab that belongs to the page. The three visual states are baked
     * into {@code textures/gui/machine_button.png}; the selected tab is drawn
     * with the pressed row so the current step is always obvious.
     */
    private final class TabButton extends Button {
        private static final String[] KEYS = {
                "gui.samogon.brewery.tab.mash",
                "gui.samogon.brewery.tab.boil"
        };
        private final int tabId;
        private boolean selected;

        private TabButton(int tabId) {
            super(leftPos + TAB_X, topPos + TAB_Y[tabId], TAB_W, TAB_H,
                    Component.translatable(KEYS[tabId]),
                    button -> sendMenuButton(tabId), DEFAULT_NARRATION);
            this.tabId = tabId;
        }

        private void setSelected(boolean value) {
            this.selected = value;
        }

        private Component tooltip() {
            return Component.translatable(KEYS[tabId] + ".tooltip");
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            boolean hover = isHoveredOrFocused();
            int v = selected ? 40 : hover ? 20 : 0;
            graphics.blit(BUTTON, getX(), getY(), getWidth(), getHeight(),
                    0.0F, (float) v, getWidth(), getHeight(), 88, 60);

            var font = Minecraft.getInstance().font;
            int textColor = selected ? INK : 0xFF6F5A42;
            int tx = getX() + (getWidth() - font.width(getMessage())) / 2;
            int ty = getY() + (getHeight() - 8) / 2;
            graphics.drawString(font, getMessage(), tx, ty, textColor, false);
        }
    }
}
