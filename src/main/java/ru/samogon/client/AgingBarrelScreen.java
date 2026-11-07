package ru.samogon.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import ru.samogon.block.AgingBarrelBlockEntity;
import ru.samogon.menu.AgingBarrelMenu;
import ru.samogon.registry.ModItems;

/**
 * The large infusion barrel interface, drawn as the same cooper's parchment as
 * the machines: a quiet sheet of handmade paper, a smoked-oak frame and a brass
 * rule. The frame, seal plate, slot wells and progress track are baked into a
 * texture by {@code tools/GenerateGui.java}; only the live counts, state, item
 * stamp and progress fill are painted here.
 */
public final class AgingBarrelScreen extends AbstractContainerScreen<AgingBarrelMenu> {
    private static final int IMAGE_W = 220;
    private static final int IMAGE_H = 214;

    /** Drop shadow reaches four pixels past the panel, so the texture has a margin. */
    private static final int MARGIN = 4;
    private static final int PANEL_W = IMAGE_W + 2 * MARGIN;
    private static final int PANEL_H = IMAGE_H + 2 * MARGIN;
    private static final ResourceLocation PANEL =
            ResourceLocation.fromNamespaceAndPath("samogon", "textures/gui/barrel_panel.png");

    private static final int INK = 0xFF352218;
    private static final int SOFT = 0xFF6B4E36;
    private static final int FAINT = 0xFF8A6A4B;
    private static final int COPPER = 0xFF9A4A26;
    private static final int COPPER_DARK = 0xFF6E351B;
    private static final int GROW = 0xFFA47A2D;
    private static final int BRASS_HI = 0xFFE0B970;
    private static final int GREEN = 0xFF536640;

    /** Right-hand column that carries the counts and the readiness rule. */
    private static final int PANEL_X = 126;
    private static final int PANEL_RIGHT = 210;

    public AgingBarrelScreen(AgingBarrelMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = IMAGE_W;
        this.imageHeight = IMAGE_H;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;

        graphics.blit(PANEL, x - MARGIN, y - MARGIN, PANEL_W, PANEL_H,
                0.0F, 0.0F, PANEL_W, PANEL_H, PANEL_W, PANEL_H);

        // Live details over the baked page: the accent under the seal, the
        // oak-chip stamp, the state and the counts.
        graphics.fill(x + 20, y + 31, x + 36, y + 32, GROW);
        graphics.renderItem(ModItems.OAK_CHIPS.get().getDefaultInstance(), x + 20, y + 15);
        graphics.drawString(font, Component.translatable("gui.samogon.aging_barrel.title")
                .withStyle(ChatFormatting.BOLD), x + 42, y + 15, INK, false);
        drawState(graphics, x + 42, y + 29);

        // Slot captions sit just under the rule, above their wells.
        graphics.drawString(font, Component.translatable("gui.samogon.cask.slot.bottles"),
                x + AgingBarrelMenu.BOTTLE_X, y + 42, COPPER_DARK, false);
        graphics.drawString(font, Component.translatable("gui.samogon.cask.slot.chips"),
                x + AgingBarrelMenu.CHIP_X, y + 42, COPPER_DARK, false);

        // The right column carries the bottle and chip counts, then the rule of
        // readiness. Each line has its own row so the text never collides with
        // the chip well above it or the progress bar below it.
        drawMetrics(graphics, x, y);
        drawProgress(graphics, x + PANEL_X, y + 110, PANEL_RIGHT - PANEL_X, 6);

        centered(graphics, Component.translatable("gui.samogon.inventory"), x + IMAGE_W / 2,
                y + 121, SOFT);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // Labels are painted in renderBg; this keeps item tooltips above them.
    }

    private void drawState(GuiGraphics graphics, int x, int y) {
        int bottles = menu.bottles();
        int chips = menu.chips();
        int ready = menu.ready();
        String key;
        int colour;
        if (ready > 0) {
            key = "gui.samogon.state.ready";
            colour = GREEN;
        } else if (bottles > 0 && chips > 0) {
            key = "gui.samogon.state.running";
            colour = COPPER;
        } else if (bottles > 0) {
            key = "gui.samogon.state.waiting";
            colour = 0xFF8A6A2E;
        } else {
            key = "gui.samogon.state.empty";
            colour = FAINT;
        }
        graphics.drawString(font, "\u25cf", x, y, colour, false);
        graphics.drawString(font, Component.translatable(key), x + 10, y, INK, false);
    }

    /** Paints the live progress fill, ticks, knob and value over the baked track. */
    private void drawProgress(GuiGraphics graphics, int x, int y, int width, int height) {
        int progress = Math.max(0, Math.min(100, menu.progress()));
        int filled = width * progress / 100;
        if (filled > 0) {
            graphics.fill(x, y, x + filled, y + height, GROW);
            graphics.fill(x, y, x + filled, y + 1, 0x55FFF4D8);
        }
        for (int tick : new int[]{25, 50, 75}) {
            int tx = x + width * tick / 100;
            graphics.fill(tx, y + 1, tx + 1, y + height - 1, 0x33725A3A);
        }
        if (progress > 0) {
            graphics.fill(x + filled - 1, y - 1, x + filled + 1, y + height + 1, BRASS_HI);
        }
        graphics.drawString(font, Component.translatable("gui.samogon.progress", progress),
                x, y - 12, SOFT, false);
    }

    private void drawMetrics(GuiGraphics graphics, int x, int y) {
        String bottles = Component.translatable("gui.samogon.cask.bottles",
                menu.bottles(), AgingBarrelBlockEntity.BOTTLE_SLOTS).getString();
        String chips = Component.translatable("gui.samogon.cask.chips", menu.chips()).getString();
        String ready = Component.translatable("gui.samogon.cask.ready", menu.ready()).getString();
        graphics.drawString(font, bottles, x + PANEL_X, y + 56, INK, false);
        graphics.drawString(font, chips, x + PANEL_X, y + 68, INK, false);
        int readyColour = menu.ready() > 0 ? GREEN : FAINT;
        graphics.drawString(font, ready, x + PANEL_X, y + 80, readyColour, false);
    }

    private void centered(GuiGraphics graphics, Component text, int centerX, int y, int color) {
        graphics.drawString(font, text, centerX - font.width(text) / 2, y, color, false);
    }
}
