package ru.samogon.client;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import ru.samogon.block.BanyaStoveBlockEntity;
import ru.samogon.menu.BanyaStoveMenu;
import ru.samogon.registry.ModBlocks;

import java.util.Locale;

/**
 * The banya stove as a furnace drawn on a cooper's ledger page.
 *
 * <p>The page itself - the oak frame, the stone firebox with its logs and coal
 * well, the brass thermometer housing and the lever rail - is baked into
 * {@code textures/gui/banya_panel.png} by {@code tools/GenerateBanyaGui.java},
 * exactly like {@code machine_panel.png} is baked for the other machines. Only
 * the live parts are painted here: the flame, the mercury column, the lever
 * knob, the item seal, the numbers and the state of the room. Every label sits
 * on its own row, so nothing can overlap at any GUI scale.</p>
 */
public final class BanyaStoveScreen extends AbstractContainerScreen<BanyaStoveMenu> {
    private static final int IMAGE_W = 180;
    private static final int IMAGE_H = 214;

    /** Drop shadow reaches four pixels past the panel, so the texture has a margin. */
    private static final int MARGIN = 4;
    private static final int PANEL_W = IMAGE_W + 2 * MARGIN;
    private static final int PANEL_H = IMAGE_H + 2 * MARGIN;
    private static final ResourceLocation PANEL =
            ResourceLocation.fromNamespaceAndPath("samogon", "textures/gui/banya_panel.png");

    /** Interior of the baked firebox, where the flame is allowed to paint. */
    private static final int FIRE_X = 13;
    private static final int FIRE_W = 48;
    private static final int FLAME_TOP = 37;
    private static final int FLAME_BASE = 88;

    /** The mercury column inside the baked glass tube. */
    private static final int GAUGE_X = 77;
    private static final int GAUGE_W = 8;
    private static final int GAUGE_TOP = 37;
    private static final int GAUGE_BASE = 91;

    /** The baked lever rail and the brass knob that rides it. */
    private static final int LEVER_X = 96;
    private static final int LEVER_Y = 60;
    private static final int LEVER_W = 72;
    private static final int KNOB_W = 10;
    private static final int KNOB_Y = 57;
    private static final int KNOB_H = 18;

    private static final int TEXT_X = 96;
    private static final int READOUT_RIGHT = 167;
    /** Breathing room between a readout label and its right-aligned value. */
    private static final int POWER_GAP = 5;

    private static final int INK = 0xFF352218;
    private static final int SOFT = 0xFF6B4E36;
    private static final int COPPER_DARK = 0xFF6E351B;
    private static final int BRASS = 0xFFC09246;
    private static final int BRASS_HI = 0xFFE0BE72;
    private static final int BRASS_DARK = 0xFF7A582A;
    private static final int MERCURY = 0xFFB8301F;
    private static final int MERCURY_HI = 0xFFE4644C;
    private static final int GREEN = 0xFF4C6438;
    private static final int RED = 0xFF8E2E25;
    private static final int STEAM = 0xFF2F6670;

    private boolean dragging;
    /** Lever position the player is dragging to, shown before the server answers. */
    private int pendingPower = -1;
    private int sentPower = -1;

    public BanyaStoveScreen(BanyaStoveMenu menu, Inventory playerInventory, Component title) {
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

        graphics.renderItem(ModBlocks.BANYA_STOVE.get().asItem().getDefaultInstance(),
                x + 9, y + 8);
        graphics.drawString(font, title.copy().withStyle(ChatFormatting.BOLD),
                x + 34, y + 12, INK, false);

        // The fire goes under the baked well, which is stamped back on top.
        drawFire(graphics, x, y);
        drawMercury(graphics, x, y);
        drawKnob(graphics, x, y);
        drawReadouts(graphics, x, y);

        centered(graphics, Component.translatable("gui.samogon.inventory"),
                x + IMAGE_W / 2, y + 118, SOFT);
    }

    /** Flames licking up from the baked logs, capped by the firehole. */
    private void drawFire(GuiGraphics graphics, int x, int y) {
        int burn = menu.data(BanyaStoveBlockEntity.DATA_BURN);
        int total = menu.data(BanyaStoveBlockEntity.DATA_BURN_TOTAL);
        if (burn <= 0 || total <= 0) {
            return;
        }
        float heat = Math.min(1.0F, burn / (float) total);
        int base = y + FLAME_BASE;
        int height = Math.max(4, Math.round((FLAME_BASE - FLAME_TOP) * 0.8F * heat));
        int flicker = (int) ((Util.getMillis() / 90L) % 3L);
        int top = base - height - flicker;

        graphics.enableScissor(x + FIRE_X, y + FLAME_TOP, x + FIRE_X + FIRE_W, base);
        graphics.fillGradient(x + 18, top, x + 56, base, 0x00FF9A2E, 0xD8FF7A18);
        graphics.fillGradient(x + 24, top + height / 3, x + 50, base, 0x00FFD36A, 0xE6FFC24A);
        graphics.disableScissor();

        // Put the coal well back over the flame.
        graphics.blit(PANEL, x + 26, y + 66, 20, 20,
                26 + MARGIN, 66 + MARGIN, 20, 20, PANEL_W, PANEL_H);
    }

    /** Red column climbing the baked glass tube, plus the bulb at its foot. */
    private void drawMercury(GuiGraphics graphics, int x, int y) {
        int span = GAUGE_BASE - GAUGE_TOP;
        float temperature = menu.temperatureTenths() / 10.0F;
        int filled = Mth.clamp(Math.round(temperature / 120.0F * span), 0, span);
        if (filled > 0) {
            graphics.fill(x + GAUGE_X, y + GAUGE_BASE - filled, x + GAUGE_X + GAUGE_W,
                    y + GAUGE_BASE, MERCURY);
            graphics.fill(x + 79, y + GAUGE_BASE - filled, x + 83,
                    y + GAUGE_BASE, MERCURY_HI);
        }
        // The bulb: 12 px of red inside the baked well, lit by one 4 px glint
        // down its middle, exactly under the column. The bottom row stays dark
        // so the well keeps its outline.
        graphics.fill(x + 75, y + 91, x + 87, y + 98, MERCURY);
        graphics.fill(x + 79, y + 92, x + 83, y + 95, MERCURY_HI);
    }

    /** The brass handle riding the baked rail. */
    private void drawKnob(GuiGraphics graphics, int x, int y) {
        int knobX = x + LEVER_X + Math.round((LEVER_W - KNOB_W) * shownPower() / 100.0F);
        int top = y + KNOB_Y;
        int bottom = top + KNOB_H;
        graphics.fill(knobX, top + 1, knobX + KNOB_W, bottom - 1, BRASS_DARK);
        graphics.fill(knobX + 1, top, knobX + KNOB_W - 1, bottom, BRASS_DARK);
        graphics.fill(knobX + 1, top + 1, knobX + KNOB_W - 1, bottom - 1, BRASS);
        graphics.fill(knobX + 2, top + 2, knobX + KNOB_W - 2, top + 4, BRASS_HI);
        graphics.fill(knobX + 4, top + 6, knobX + 6, bottom - 5, BRASS_DARK);
    }

    /** Temperature, target, power and the two states of the stove. */
    private void drawReadouts(GuiGraphics graphics, int x, int y) {
        float temperature = menu.temperatureTenths() / 10.0F;
        float target = menu.targetTenths() / 10.0F;

        graphics.drawString(font, String.format(Locale.ROOT, "%.0f\u00b0C", temperature),
                x + TEXT_X, y + 34, temperature >= 80.0F ? RED : INK, false);
        graphics.drawString(font, Component.translatable("gui.samogon.banya.target",
                        String.format(Locale.ROOT, "%.0f", target)),
                x + TEXT_X, y + 46, INK, false);

        Component label = Component.translatable("gui.samogon.banya.power");
        String power = shownPower() + "%";
        // The readout column is only 71 px wide, and Cyrillic labels are wide:
        // "Мощность 100%" does not fit and used to run the value over the word.
        // Reserve a fixed gap, trim the label to whatever room is left (a
        // longer translation can never collide with the number again) and pin
        // the percentage to the right edge of the column.
        int valueX = x + READOUT_RIGHT - font.width(power);
        int labelRoom = Math.max(0, valueX - (x + TEXT_X) - POWER_GAP);
        String labelText = font.plainSubstrByWidth(label.getString(), labelRoom);
        graphics.drawString(font, labelText, x + TEXT_X, y + 77, SOFT, false);
        graphics.drawString(font, power, valueX, y + 77, INK, false);

        if (menu.data(BanyaStoveBlockEntity.DATA_STEAM) == 1) {
            graphics.drawString(font, Component.translatable("gui.samogon.banya.steaming"),
                    x + TEXT_X, y + 90, STEAM, false);
        }

        Component room = menu.roomValid()
                ? Component.translatable("gui.samogon.banya.room", menu.roomVolume())
                : Component.translatable("gui.samogon.banya.no_room");
        graphics.drawString(font, room, x + 9, y + 104, menu.roomValid() ? GREEN : RED, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        // Explain only the empty slot. Once a fuel item sits there (or is carried
        // on the cursor) super.render already draws its own tooltip, and a second
        // box at the same corner would hide the item name and its burn time.
        boolean slotFree = (hoveredSlot == null || !hoveredSlot.hasItem())
                && menu.getCarried().isEmpty();
        if (slotFree && isHovering(BanyaStoveMenu.FUEL_X, BanyaStoveMenu.FUEL_Y, 16, 16, mouseX, mouseY)) {
            graphics.renderTooltip(font,
                    Component.translatable("gui.samogon.banya.fuel.tooltip"), mouseX, mouseY);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // Labels are painted in renderBg; this keeps item tooltips above them.
    }

    private void centered(GuiGraphics graphics, Component text, int centerX, int y, int color) {
        graphics.drawString(font, text, centerX - font.width(text) / 2, y, color, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && overLever(mouseX, mouseY)) {
            dragging = true;
            sentPower = -1;
            setPowerFromMouse(mouseX);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging) {
            setPowerFromMouse(mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void containerTick() {
        super.containerTick();
        // Drop the optimistic value as soon as the server confirms it.
        if (pendingPower >= 0 && menu.power() == pendingPower) {
            pendingPower = -1;
        }
    }

    /** Lever position to draw: the drag target until the server echoes it. */
    private int shownPower() {
        return pendingPower >= 0 ? pendingPower : menu.power();
    }

    private boolean overLever(double mouseX, double mouseY) {
        int lx = leftPos + LEVER_X;
        int ly = topPos + LEVER_Y;
        return mouseX >= lx - 4 && mouseX < lx + LEVER_W + 4
                && mouseY >= ly - 5 && mouseY < ly + KNOB_H;
    }

    private void setPowerFromMouse(double mouseX) {
        double travel = LEVER_W - KNOB_W;
        int power = Math.round((float) ((mouseX - (leftPos + LEVER_X) - KNOB_W / 2.0)
                / travel * 100.0F));
        power = Mth.clamp(power, 0, BanyaStoveBlockEntity.MAX_POWER);
        pendingPower = power;
        // Only tell the server when the value really moved, not on every
        // mouse-move event, or a single drag floods it with packets.
        if (power == sentPower) {
            return;
        }
        sentPower = power;
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId,
                    BanyaStoveMenu.POWER_BUTTON_BASE + power);
        }
    }
}