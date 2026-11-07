package ru.samogon.client;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import ru.samogon.SamogonMod;
import ru.samogon.effect.Intoxication;
import ru.samogon.registry.ModEffects;

/**
 * The drunk's own eyes. While {@link ModEffects#INTOXICATION} is on the player
 * the whole world slowly tips from side to side and a veil creeps in, both
 * scaling with the intoxication level. Everything is client-side and read
 * straight off the visible effects, so no packets are involved and the effect
 * can never disagree with what the server believes.
 *
 * <p>Nausea already warps the picture; this adds the sideways roll of the
 * horizon, the wash and a soft darkening towards the screen edges. The wash
 * takes its colour from the last tincture drunk: the hidden
 * {@link ModEffects#TINCTURE_MARK} carries the flavour in its amplifier, so a
 * berry drink leaves a plum veil, honey an amber one, and so on. A plain spirit
 * gets the old cold navy.
 *
 * <p>The edge darkening is built from flat vertex-coloured quads rather than a
 * blitted texture. A texture drawn this early in the GUI pass is not blended by
 * its alpha (the opaque pixels come out solid black with a hard rim), while the
 * {@code fill}/{@code fillGradient} path — the one {@link BlackoutOverlay} uses —
 * shades correctly.
 */
@EventBusSubscriber(modid = SamogonMod.MOD_ID, value = Dist.CLIENT)
public final class DrunkVision {
    /** Full-strength tip of the horizon, in degrees, at level V. */
    private static final float MAX_ROLL_DEGREES = 13.0F;
    /** Full-strength sideways drift of the camera, in degrees, at level V. */
    private static final float MAX_YAW_SWAY_DEGREES = 2.0F;
    /** How tinted the whole frame gets at full strength (0..1). */
    private static final float MAX_TINT_ALPHA = 0.20F;
    /** How dark the very edge of the frame gets at full strength (0..1). */
    private static final float MAX_EDGE_ALPHA = 0.55F;
    /** The cold navy veil of a plain, ungarnished spirit. */
    private static final int DEFAULT_TINT = 0x0A1633;
    /**
     * One veil colour per tincture flavour, in the same order as
     * {@code Intoxication.flavorIndex()}: berry, honey, botanical, oak, spice,
     * fruit, marine. Each is a deep, low-value version of the tincture's own
     * hue, so the frame picks up the drink without turning into coloured glass.
     */
    private static final int[] FLAVOUR_TINT = {
            0x2A0D1E, // berry     — dark plum
            0x2A1C05, // honey     — dark amber
            0x0B2012, // botanical — moss
            0x241405, // oak       — dark oak
            0x2A1005, // spice     — dark ember
            0x280D1A, // fruit     — dark rose
            0x061F26, // marine    — dark teal
    };
    /** How much of the veil hue the dark rim keeps; the rest stays black. */
    private static final float EDGE_HUE = 0.38F;
    /** Share of the screen width the left/right shading band reaches in. */
    private static final float BAND_WIDTH = 0.24F;
    /** Share of the screen height the top/bottom shading band reaches in. */
    private static final float BAND_HEIGHT = 0.28F;
    /**
     * GuiGraphics only offers a vertical gradient, so the side bands are made of
     * thin flat columns. Forty-ish steps is already far smoother than the eye can
     * resolve at these alphas.
     */
    private static final int SIDE_STEPS = 40;
    /** How fast the effect fades in and out, per client tick. */
    private static final float FADE_PER_TICK = 0.03F;
    /** How fast the veil slides from one tincture's colour to the next. */
    private static final float COLOUR_FADE_PER_TICK = 0.04F;

    /** Smoothed 0..1 strength, so drinking and sobering up are gradual. */
    private static float strength;
    /** Smoothed veil colour, 0..1 per channel, so switching drinks does not snap. */
    private static float tintR;
    private static float tintG;
    private static float tintB;
    private static boolean tintSeeded;

    private DrunkVision() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();

        float target = targetStrength(minecraft);
        strength = approach(strength, target, FADE_PER_TICK);

        int tint = targetTint(minecraft);
        if (!tintSeeded) {
            tintSeeded = true;
            tintR = channel(tint, 16);
            tintG = channel(tint, 8);
            tintB = channel(tint, 0);
        } else {
            tintR = approach(tintR, channel(tint, 16), COLOUR_FADE_PER_TICK);
            tintG = approach(tintG, channel(tint, 8), COLOUR_FADE_PER_TICK);
            tintB = approach(tintB, channel(tint, 0), COLOUR_FADE_PER_TICK);
        }
    }

    /** Steps {@code value} toward {@code target} by at most {@code step}. */
    private static float approach(float value, float target, float step) {
        float delta = target - value;
        return Math.abs(delta) <= step ? target : value + Math.signum(delta) * step;
    }

    /** One 0..255 colour byte of {@code rgb}, expressed as a 0..1 channel. */
    private static float channel(int rgb, int shift) {
        return ((rgb >> shift) & 0xFF) / 255.0F;
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (strength <= 0.001F) {
            return;
        }
        // Two slow sines, so the sway never settles into a predictable rhythm.
        double seconds = Util.getMillis() / 1000.0;
        double sway = Math.sin(seconds * 0.85) * 0.78 + Math.sin(seconds * 2.13 + 1.7) * 0.22;
        event.setRoll(event.getRoll() + (float) (sway * MAX_ROLL_DEGREES * strength));
        event.setYaw(event.getYaw()
                + (float) (Math.sin(seconds * 0.6 + 0.9) * MAX_YAW_SWAY_DEGREES * strength));
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (strength <= 0.001F || minecraft.screen != null || minecraft.options.hideGui) {
            return;
        }

        GuiGraphics graphics = event.getGuiGraphics();
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();

        // First the flat wash, then the darker rim on top of it.
        int tint = rgb(tintR, tintG, tintB);
        int tintAlpha = Math.round(MAX_TINT_ALPHA * strength * 255.0F);
        if (tintAlpha > 0) {
            graphics.fill(0, 0, width, height, (tintAlpha << 24) | tint);
        }

        int edgeAlpha = Math.round(MAX_EDGE_ALPHA * strength * 255.0F);
        if (edgeAlpha > 0) {
            int edge = rgb(tintR * EDGE_HUE, tintG * EDGE_HUE, tintB * EDGE_HUE);
            drawEdgeShade(graphics, width, height, edge, edgeAlpha);
        }
    }

    /** Packs three 0..1 channels into a 24-bit RGB value. */
    private static int rgb(float r, float g, float b) {
        int ri = Math.round(Mth.clamp(r, 0.0F, 1.0F) * 255.0F);
        int gi = Math.round(Mth.clamp(g, 0.0F, 1.0F) * 255.0F);
        int bi = Math.round(Mth.clamp(b, 0.0F, 1.0F) * 255.0F);
        return (ri << 16) | (gi << 8) | bi;
    }

    /**
     * Darkens the rim of the frame, fading smoothly into the middle. The top and
     * bottom use one vertical gradient each; the sides use thin columns because
     * only the vertical direction is available as a real gradient.
     */
    private static void drawEdgeShade(GuiGraphics graphics, int width, int height, int edge, int edgeAlpha) {
        int bandTop = Math.max(1, Math.round(height * BAND_HEIGHT));
        graphics.fillGradient(0, 0, width, bandTop, (edgeAlpha << 24) | edge, 0x00000000);
        graphics.fillGradient(0, height - bandTop, width, height, 0x00000000, (edgeAlpha << 24) | edge);

        int bandSide = Math.max(1, Math.round(width * BAND_WIDTH));
        int step = Math.max(1, bandSide / SIDE_STEPS);
        for (int x = 0; x < bandSide; x += step) {
            int alpha = sideAlphaAt(x, bandSide, edgeAlpha);
            int color = (alpha << 24) | edge;
            graphics.fill(x, 0, Math.min(x + step, bandSide), height, color);
            graphics.fill(width - Math.min(x + step, bandSide), 0, width - x, height, color);
        }
    }

    /** 1 at the very edge, easing to 0 at the clear middle. */
    private static int sideAlphaAt(int distance, int band, int edgeAlpha) {
        float remaining = 1.0F - distance / (float) band;
        return Math.round(edgeAlpha * remaining * remaining);
    }

    /**
     * The veil colour the smoothed value is heading for: the last tincture's
     * hue, or the cold navy default for a plain spirit.
     */
    private static int targetTint(Minecraft minecraft) {
        if (minecraft.player == null) {
            return DEFAULT_TINT;
        }
        MobEffectInstance mark = minecraft.player.getEffect(ModEffects.TINCTURE_MARK);
        if (mark == null) {
            return DEFAULT_TINT;
        }
        int flavour = mark.getAmplifier();
        return flavour >= 0 && flavour < FLAVOUR_TINT.length ? FLAVOUR_TINT[flavour] : DEFAULT_TINT;
    }

    /** The strength the smoothed value is heading for, based on the current effect. */
    private static float targetStrength(Minecraft minecraft) {
        // Keep the haze and the sway out of menus; both return the moment play does.
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null) {
            return 0.0F;
        }
        if (minecraft.player.isSpectator() || minecraft.player.isDeadOrDying()) {
            return 0.0F;
        }

        MobEffectInstance effect = minecraft.player.getEffect(ModEffects.INTOXICATION);
        if (effect == null) {
            return 0.0F;
        }
        int level = Math.min(Intoxication.MAX_LEVEL, effect.getAmplifier() + 1);
        float base = level / (float) Intoxication.MAX_LEVEL;

        // Same cures the rest of the rules know: brine clears the head, oak keeps
        // the drinker steady on their feet.
        if (minecraft.player.hasEffect(ModEffects.CLEAR_HEAD)) {
            base *= 0.35F;
        }
        if (minecraft.player.hasEffect(ModEffects.OAK_GRIT)) {
            base *= 0.80F;
        }
        return Mth.clamp(base, 0.0F, 1.0F);
    }
}