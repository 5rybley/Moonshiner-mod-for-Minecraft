package ru.samogon.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import ru.samogon.SamogonMod;

/**
 * The level-V blackout: the whole screen fades to black, the warning is shown,
 * then it fades back in. Purely cosmetic; the server owns the timing and the
 * teleport that happens while the screen is dark.
 */
@EventBusSubscriber(modid = SamogonMod.MOD_ID, value = Dist.CLIENT)
public final class BlackoutOverlay {
    /** Almost fully opaque, so the world is gone but the fade still reads as a fade. */
    private static final int PEAK_ALPHA = 238;
    private static final int TEXT_RGB = 0xE8D9C0;
    /** The text only appears once the screen is dark enough to read it against. */
    private static final int TEXT_START_ALPHA = 60;
    /** The warning is scaled to take up this share of the screen width. */
    private static final float TEXT_SCREEN_FRACTION = 0.86F;
    /** Upper bound so a very narrow window does not produce absurd glyphs. */
    private static final float MAX_TEXT_SCALE = 9.0F;

    private static int fadeTicks;
    private static int holdTicks;
    private static int elapsed;
    private static boolean active;

    private BlackoutOverlay() {}

    /** Starts a blackout. Called from the packet handler on the client thread. */
    public static void begin(int fade, int hold) {
        fadeTicks = Math.max(1, fade);
        holdTicks = Math.max(0, hold);
        elapsed = 0;
        active = true;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (active && ++elapsed >= totalTicks()) {
            active = false;
        }
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!active) {
            return;
        }
        int alpha = currentAlpha();
        if (alpha <= 0) {
            return;
        }

        GuiGraphics graphics = event.getGuiGraphics();
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        graphics.fill(0, 0, width, height, alpha << 24);

        if (alpha > TEXT_START_ALPHA) {
            int textAlpha = Math.min(255, (alpha - TEXT_START_ALPHA) * 255 / (PEAK_ALPHA - TEXT_START_ALPHA));
            Minecraft minecraft = Minecraft.getInstance();
            Component text = Component.translatable("message.samogon.intoxication.blackout");
            int color = (textAlpha << 24) | TEXT_RGB;

            // Blow the warning up until it spans most of the screen width, so it
            // reads as a full-screen notice instead of a small caption.
            float scale = Math.min(MAX_TEXT_SCALE,
                    Math.max(1.0F, width * TEXT_SCREEN_FRACTION / Math.max(1, minecraft.font.width(text))));
            PoseStack pose = graphics.pose();
            pose.pushPose();
            pose.translate(width / 2.0F, height / 2.0F, 0.0F);
            pose.scale(scale, scale, 1.0F);
            graphics.drawCenteredString(minecraft.font, text, 0, -minecraft.font.lineHeight / 2, color);
            pose.popPose();
        }
    }

    private static int totalTicks() {
        return fadeTicks * 2 + holdTicks;
    }

    private static int currentAlpha() {
        if (elapsed < fadeTicks) {
            return PEAK_ALPHA * elapsed / fadeTicks;
        }
        int holdEnd = fadeTicks + holdTicks;
        if (elapsed < holdEnd) {
            return PEAK_ALPHA;
        }
        int fadeOut = elapsed - holdEnd;
        if (fadeOut >= fadeTicks) {
            return 0;
        }
        return PEAK_ALPHA * (fadeTicks - fadeOut) / fadeTicks;
    }
}
