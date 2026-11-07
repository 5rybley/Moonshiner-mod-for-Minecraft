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
import ru.samogon.block.ProcessBlock;
import ru.samogon.menu.ProcessMenu;
import ru.samogon.process.MachineData;
import ru.samogon.process.Simulation.Fraction;
import ru.samogon.registry.ModBlocks;

import java.util.Locale;

/**
 * A cooper's field ledger shared by all machines.
 *
 * <p>The static page, its oak frame, grain, ornaments, seal plate, brass rules,
 * slot wells, arrows and progress track are baked into a texture by
 * {@code tools/GenerateGui.java} so the interface can be edited in an image
 * program. Only the live values are painted here: the machine accent, the item
 * seal, the state, the metrics and the progress fill.</p>
 */
public final class ProcessScreen extends AbstractContainerScreen<ProcessMenu> {
    private static final int IMAGE_W = 380;
    private static final int IMAGE_H = 262;
    private static final int HEADER_H = 116;

    /** Drop shadow reaches four pixels past the panel, so the texture has a margin. */
    private static final int MARGIN = 4;
    private static final int PANEL_W = IMAGE_W + 2 * MARGIN;
    private static final int PANEL_H = IMAGE_H + 2 * MARGIN;
    private static final ResourceLocation PANEL =
            ResourceLocation.fromNamespaceAndPath("samogon", "textures/gui/machine_panel.png");
    private static final ResourceLocation PANEL_BLEND =
            ResourceLocation.fromNamespaceAndPath("samogon", "textures/gui/machine_panel_blend.png");
    private static final ResourceLocation BUTTON =
            ResourceLocation.fromNamespaceAndPath("samogon", "textures/gui/machine_button.png");

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
    private static final int BRASS = 0xFFC09246;
    private static final int BRASS_HI = 0xFFE0B970;
    private static final int BRASS_DARK = 0xFF7A582A;
    private static final int RED = 0xFF8E2E25;
    private static final int GREEN = 0xFF536640;
    private static final int PAPER_HI = 0xFFF2E4C3;

    private ParchmentButton receiverButton;
    private ParchmentButton clearButton;
    private ParchmentButton blendButton;

    public ProcessScreen(ProcessMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = IMAGE_W;
        this.imageHeight = IMAGE_H;
    }

    @Override
    protected void init() {
        super.init();
        if (menu.kind() == ProcessBlock.Kind.DISTILLER) {
            receiverButton = new ParchmentButton(leftPos + 278, topPos + 10, 88, 20,
                    Component.translatable("gui.samogon.button.receiver"),
                    button -> sendMenuButton(0),
                    Component.translatable("gui.samogon.button.receiver.tooltip"));
            addRenderableWidget(receiverButton);

            clearButton = new ParchmentButton(leftPos + 278, topPos + 33, 88, 20,
                    Component.translatable("gui.samogon.button.clear"),
                    button -> sendMenuButton(1),
                    Component.translatable("gui.samogon.button.clear.tooltip"));
            addRenderableWidget(clearButton);
        }
        if (menu.kind() == ProcessBlock.Kind.BLENDING) {
            blendButton = new ParchmentButton(leftPos + 278, topPos + 10, 88, 20,
                    Component.translatable("gui.samogon.button.blend"),
                    button -> sendMenuButton(2),
                    Component.translatable("gui.samogon.button.blend.tooltip"));
            addRenderableWidget(blendButton);
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (receiverButton != null && clearButton != null) {
            clearButton.active = menu.data(MachineData.STILL_CLEARABLE) == 1;
        }
        if (blendButton != null) {
            blendButton.active = menu.data(MachineData.STAGE) == 1 && menu.additiveCount() > 0;
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
        int accent = accentColor();

        ResourceLocation panel = menu.kind() == ProcessBlock.Kind.BLENDING ? PANEL_BLEND : PANEL;
        graphics.blit(panel, x - MARGIN, y - MARGIN, PANEL_W, PANEL_H,
                0.0F, 0.0F, PANEL_W, PANEL_H, PANEL_W, PANEL_H);

        // Live details painted over the baked page: the machine accent under its
        // seal, the item stamp and the process readouts.
        graphics.fill(x + 22, y + 36, x + 42, y + 37, accent);
        graphics.renderItem(icon(), x + 24, y + 18);
        graphics.drawString(font, title.copy().withStyle(ChatFormatting.BOLD),
                x + 52, y + 15, INK, false);
        drawState(graphics, x + 16, y + 42);
        drawProgress(graphics, x + 16, y + 55, 148, 7, accent);

        if (menu.kind() == ProcessBlock.Kind.DISTILLER) {
            drawDistillerMetrics(graphics, x, y);
        } else {
            drawBatchMetrics(graphics, x, y);
        }

        boolean blending = menu.kind() == ProcessBlock.Kind.BLENDING;
        centered(graphics, slotInputLabel(),
                x + ProcessMenu.inputX(menu.kind()) + 8, y + 146, COPPER_DARK);
        centered(graphics, Component.translatable(blending
                        ? "gui.samogon.slot.additives" : "gui.samogon.slot.additive"),
                x + (blending ? 184 : 189), y + 146, COPPER_DARK);
        centered(graphics, Component.translatable("gui.samogon.slot.output"),
                x + ProcessMenu.outputX(menu.kind()) + 8, y + 146, COPPER_DARK);
        centered(graphics, Component.translatable("gui.samogon.inventory"),
                x + 190, y + 165, SOFT);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // Labels are painted in renderBg; this keeps item tooltips above them.
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        Component tooltip = null;
        if (isOver(receiverButton, mouseX, mouseY)) {
            tooltip = receiverButton.tooltip();
        } else if (isOver(clearButton, mouseX, mouseY)) {
            tooltip = clearButton.tooltip();
        } else if (isOver(blendButton, mouseX, mouseY)) {
            tooltip = blendButton.tooltip();
        } else if (hoveredSlot != null && hoveredSlot.index <= ProcessMenu.SLOT_OUTPUT
                && !hoveredSlot.hasItem() && menu.getCarried().isEmpty()) {
            tooltip = slotHint(hoveredSlot.index);
        }
        if (tooltip != null) {
            renderParchmentTooltip(graphics, tooltip, mouseX, mouseY);
        }
    }

    private void drawState(GuiGraphics graphics, int x, int y) {
        int stage = menu.data(MachineData.STAGE);
        boolean ready = menu.data(MachineData.READY) == 1;
        boolean active = menu.data(MachineData.ACTIVE) == 1;
        String key;
        int colour;
        if (ready) {
            key = "gui.samogon.state.ready";
            colour = GREEN;
        } else if (active) {
            key = "gui.samogon.state.running";
            colour = COPPER;
        } else if (stage == 0 && menu.data(MachineData.STILL_PRESENT) == 0) {
            key = "gui.samogon.state.empty";
            colour = FAINT;
        } else if (stage >= 2 || menu.data(MachineData.STILL_PRESENT) == 1) {
            key = "gui.samogon.state.stalled";
            colour = RED;
        } else {
            key = "gui.samogon.state.waiting";
            colour = 0xFF8A6A2E;
        }
        graphics.drawString(font, "\u25cf", x, y, colour, false);
        graphics.drawString(font, Component.translatable(key), x + 10, y, INK, false);
    }

    /** Paints the live progress fill, ticks, knob and value over the baked track. */
    private void drawProgress(GuiGraphics graphics, int x, int y, int width, int height, int accent) {
        int progress = Math.max(0, Math.min(100, menu.data(MachineData.PROGRESS)));
        int filled = width * progress / 100;
        if (filled > 0) {
            graphics.fill(x, y, x + filled, y + height, accent);
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

    private void drawBatchMetrics(GuiGraphics graphics, int x, int y) {
        int volume = menu.data(MachineData.BATCH_VOLUME);
        int abv = menu.data(MachineData.BATCH_ABV);
        int quality = menu.data(MachineData.BATCH_QUALITY);
        int impurities = menu.data(MachineData.BATCH_IMPURITIES);
        int temperature = menu.data(MachineData.TEMPERATURE);
        int ambient = menu.data(MachineData.AMBIENT);
        int heat = menu.data(MachineData.HEAT);
        int coolant = menu.data(MachineData.COOLANT_MINUTES);

        metric(graphics, x + METRIC_LEFT, x + METRIC_LEFT_VALUE, y + METRIC_TOP,
                "gui.samogon.metric.volume", volume > 0 ? Integer.toString(volume) : "\u2014");
        metric(graphics, x + METRIC_RIGHT, x + METRIC_RIGHT_VALUE, y + METRIC_TOP,
                "gui.samogon.metric.temperature", f1(temperature));
        metric(graphics, x + METRIC_LEFT, x + METRIC_LEFT_VALUE, y + METRIC_TOP + METRIC_STEP,
                "gui.samogon.metric.abv", volume > 0 ? f1(abv) : "\u2014");
        metric(graphics, x + METRIC_RIGHT, x + METRIC_RIGHT_VALUE, y + METRIC_TOP + METRIC_STEP,
                "gui.samogon.metric.ambient", f1(ambient));
        metric(graphics, x + METRIC_LEFT, x + METRIC_LEFT_VALUE, y + METRIC_TOP + METRIC_STEP * 2,
                "gui.samogon.metric.quality", volume > 0 ? Integer.toString(quality) : "\u2014");
        metric(graphics, x + METRIC_RIGHT, x + METRIC_RIGHT_VALUE, y + METRIC_TOP + METRIC_STEP * 2,
                "gui.samogon.metric.heat", Integer.toString(heat));

        int extraY = y + METRIC_TOP + METRIC_STEP * 3;
        switch (menu.kind()) {
            case RECTIFIER -> metric(graphics, x + METRIC_LEFT, x + METRIC_LEFT_VALUE, extraY,
                    "gui.samogon.metric.sieve",
                    menu.data(MachineData.SIEVE) == 1
                            ? Component.translatable("message.samogon.yes")
                            : Component.translatable("message.samogon.no"));
            case BLENDING -> metric(graphics, x + METRIC_LEFT, x + METRIC_LEFT_VALUE, extraY,
                    "gui.samogon.metric.additives", menu.additiveCount());
            case DILUTION -> metric(graphics, x + METRIC_LEFT, x + METRIC_LEFT_VALUE, extraY,
                    "gui.samogon.metric.target", 40);
            case AGING -> metric(graphics, x + METRIC_LEFT, x + METRIC_LEFT_VALUE, extraY,
                    "gui.samogon.metric.cycles", menu.data(MachineData.BATCH_AGE));
            case FILTER -> metric(graphics, x + METRIC_LEFT, x + METRIC_LEFT_VALUE, extraY,
                    "gui.samogon.metric.filtered",
                    menu.data(MachineData.BATCH_FILTERED) == 1
                            ? Component.translatable("message.samogon.yes")
                            : Component.translatable("message.samogon.no"));
            case MASHING -> graphics.drawString(font,
                    Component.translatable("gui.samogon.hint.heat_below"),
                    x + METRIC_LEFT, extraY, COPPER_DARK, false);
            case FERMENTATION -> graphics.drawString(font,
                    Component.translatable("gui.samogon.hint.ferment_temp"),
                    x + METRIC_LEFT, extraY, COPPER_DARK, false);
            default -> metric(graphics, x + METRIC_LEFT, x + METRIC_LEFT_VALUE, extraY,
                    "gui.samogon.metric.coolant", Integer.toString(coolant));
        }
        metric(graphics, x + METRIC_RIGHT, x + METRIC_RIGHT_VALUE, extraY,
                "gui.samogon.metric.impurities", volume > 0 ? Integer.toString(impurities) : "\u2014");
    }

    private void drawDistillerMetrics(GuiGraphics graphics, int x, int y) {
        int stillTemp = menu.data(MachineData.STILL_TEMPERATURE);
        int stillAbv = menu.data(MachineData.STILL_ABV);
        int pressure = menu.data(MachineData.STILL_PRESSURE);
        int heat = menu.data(MachineData.HEAT);
        int coolant = menu.data(MachineData.COOLANT_MINUTES);
        Fraction receiver = menu.receiver();
        boolean present = menu.data(MachineData.STILL_PRESENT) == 1;

        metric(graphics, x + METRIC_LEFT, x + METRIC_LEFT_VALUE, y + METRIC_TOP,
                "gui.samogon.metric.pot", present ? f1(stillTemp) : "\u2014");
        metric(graphics, x + METRIC_RIGHT, x + METRIC_RIGHT_VALUE, y + METRIC_TOP,
                "gui.samogon.metric.pressure", present ? f2(pressure) : "\u2014");
        metric(graphics, x + METRIC_LEFT, x + METRIC_LEFT_VALUE, y + METRIC_TOP + METRIC_STEP,
                "gui.samogon.metric.abv", present ? f1(stillAbv) : "\u2014");
        metric(graphics, x + METRIC_RIGHT, x + METRIC_RIGHT_VALUE, y + METRIC_TOP + METRIC_STEP,
                "gui.samogon.metric.heat", Integer.toString(heat));
        metric(graphics, x + METRIC_LEFT, x + METRIC_LEFT_VALUE, y + METRIC_TOP + METRIC_STEP * 2,
                "gui.samogon.metric.coolant", Integer.toString(coolant));
        metric(graphics, x + METRIC_RIGHT, x + METRIC_RIGHT_VALUE, y + METRIC_TOP + METRIC_STEP * 2,
                "gui.samogon.metric.receiver",
                Component.translatable("fraction.samogon." + receiver.id()));

        graphics.drawString(font, Component.translatable("gui.samogon.metric.cuts",
                        menu.data(MachineData.HEADS_VOLUME),
                        menu.data(MachineData.HEARTS_VOLUME),
                        menu.data(MachineData.TAILS_VOLUME)),
                x + METRIC_LEFT, y + METRIC_TOP + METRIC_STEP * 3, COPPER_DARK, false);
    }

    private void metric(GuiGraphics graphics, int labelX, int valueRight, int y,
                        String key, Object value) {
        String[] pair = splitLabel(Component.translatable(key, value).getString());
        if (pair == null) {
            graphics.drawString(font, Component.translatable(key, value),
                    labelX, y, INK, false);
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

    private Component slotInputLabel() {
        return Component.translatable(menu.kind() == ProcessBlock.Kind.MASHING
                ? "gui.samogon.slot.raw" : "gui.samogon.slot.batch");
    }

    private Component slotHint(int slot) {
        if (slot == ProcessMenu.SLOT_INPUT) {
            return Component.translatable("gui.samogon.slot.input.tooltip");
        }
        if (slot >= ProcessMenu.SLOT_ADDITIVE && slot <= ProcessMenu.SLOT_ADDITIVE_LAST) {
            return Component.translatable(menu.kind() == ProcessBlock.Kind.BLENDING
                    ? "gui.samogon.slot.additives.tooltip" : "gui.samogon.slot.additive.tooltip");
        }
        if (slot == ProcessMenu.SLOT_OUTPUT) {
            return Component.translatable("gui.samogon.slot.output.tooltip");
        }
        return Component.empty();
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
        graphics.fill(tx, ty, tx + boxWidth, ty + boxHeight, PAPER_HI);
        graphics.renderOutline(tx, ty, boxWidth, boxHeight, BRASS_DARK);
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

    private int accentColor() {
        return switch (menu.kind()) {
            case MASHING -> 0xFFB56A32;
            case FERMENTATION -> 0xFF667B43;
            case DISTILLER -> 0xFF9C3F2F;
            case RECTIFIER -> 0xFF5E7F88;
            case FILTER -> 0xFF6E5A48;
            case BLENDING -> 0xFF8D4A68;
            case DILUTION -> 0xFF4F7694;
            case AGING -> 0xFFA47A2D;
        };
    }

    private ItemStack icon() {
        return new ItemStack(switch (menu.kind()) {
            case MASHING -> ModBlocks.MASH_TUN.get();
            case FERMENTATION -> ModBlocks.FERMENTATION_BARREL.get();
            case DISTILLER -> ModBlocks.DISTILLER.get();
            case RECTIFIER -> ModBlocks.REFLUX_COLUMN.get();
            case FILTER -> ModBlocks.CHARCOAL_FILTER.get();
            case BLENDING -> ModBlocks.BLENDING_TABLE.get();
            case DILUTION -> ModBlocks.DILUTION_BASIN.get();
            case AGING -> ModBlocks.AGING_CASK.get();
        });
    }

    private static String f1(int tenths) {
        return String.format(Locale.ROOT, "%.1f", tenths / 10.0);
    }

    private static String f2(int hundredths) {
        return String.format(Locale.ROOT, "%.2f", hundredths / 100.0);
    }

    /**
     * A flat, paper-coloured control that belongs to the page. Its three visual
     * states are baked into {@code textures/gui/machine_button.png}; only the
     * label is drawn live.
     */
    private static final class ParchmentButton extends Button {
        private final Component tooltip;

        private ParchmentButton(int x, int y, int width, int height, Component message,
                                OnPress onPress, Component tooltip) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
            this.tooltip = tooltip;
        }

        private Component tooltip() {
            return tooltip;
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            boolean hover = isHoveredOrFocused();
            int v = !active ? 40 : hover ? 20 : 0;
            graphics.blit(BUTTON, getX(), getY(), getWidth(), getHeight(),
                    0.0F, (float) v, getWidth(), getHeight(), 88, 60);

            var font = Minecraft.getInstance().font;
            int textColor = active ? INK : 0xFF6F5A42;
            int tx = getX() + (getWidth() - font.width(getMessage())) / 2;
            int ty = getY() + (getHeight() - 8) / 2;
            graphics.drawString(font, getMessage(), tx, ty, textColor, false);
        }
    }
}
