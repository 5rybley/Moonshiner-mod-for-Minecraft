package ru.samogon.client;

import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.samogon.SamogonMod;
import ru.samogon.network.BanyaSteamPayload;

/**
 * The translucent veil of steam a client sees while standing inside a hot
 * banya. The server sends the room temperature a couple of times a second via
 * {@link BanyaSteamPayload}; the veil follows that temperature and fogs the
 * screen in over a few seconds, then clears on its own when the packets stop,
 * so leaving the room or closing the stove never leaves the screen fogged.
 */
@Mod.EventBusSubscriber(modid = SamogonMod.MOD_ID, value = Dist.CLIENT)
public final class BanyaSteamOverlay {
    /** Below this temperature the room is only damp air. */
    private static final float STEAM_START = 38.0F;
    /** At and above this temperature the veil is at its thickest. */
    private static final float STEAM_FULL = 95.0F;
    /** Peak veil opacity, 0..255. Steam must never hide the HUD. */
    private static final int MAX_ALPHA = 90;
    /** Forget the room after this long without a packet. */
    private static final long STALE_MILLIS = 1500L;
    /** How fast the veil follows the server temperature, tenths per tick. */
    private static final float SMOOTH_STEP = 1.2F;
    /**
     * Seconds the veil takes to fog the screen after the player steps into the
     * steam. The temperature itself snaps to the room at once; only the opacity
     * is eased in, so a preheated banya blurs the view gradually instead of
     * slamming a white filter over it.
     */
    private static final float FADE_IN_SECONDS = 5.0F;
    /** Seconds the veil takes to clear once the packets stop. */
    private static final float FADE_OUT_SECONDS = 1.5F;
    private static final int VEIL_RGB = 0xE7EDEF;

    private static int targetTenths;
    private static float shownTenths;
    /** 0 = perfectly clear, 1 = full opacity for the current temperature. */
    private static float fade;
    private static long lastUpdateMillis;

    private BanyaSteamOverlay() {}

    /** Called from the packet handler on the client thread. */
    public static void update(int tenths) {
        long now = Util.getMillis();
        // First packet after the player was outside (or the room was cold):
        // take the room temperature as it is instead of creeping up from the
        // last value the client remembers, and fog up from a clear screen.
        if (now - lastUpdateMillis > STALE_MILLIS) {
            shownTenths = tenths;
            fade = 0.0F;
        }
        targetTenths = tenths;
        lastUpdateMillis = now;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent.Post event) {
        boolean fresh = Util.getMillis() - lastUpdateMillis <= STALE_MILLIS;
        if (!fresh) {
            targetTenths = 0;
        }
        float delta = targetTenths - shownTenths;
        if (Math.abs(delta) <= SMOOTH_STEP) {
            shownTenths = targetTenths;
        } else {
            shownTenths += Math.signum(delta) * SMOOTH_STEP;
        }
        if (fresh) {
            fade = Math.min(1.0F, fade + 1.0F / (FADE_IN_SECONDS * 20.0F));
        } else {
            fade = Math.max(0.0F, fade - 1.0F / (FADE_OUT_SECONDS * 20.0F));
        }
        // Any open GUI hides the veil. Drop the fade while it is up so closing
        // the stove or the inventory does not slam the full fog back on: the
        // screen fogs in again over the usual five seconds.
        if (Minecraft.getInstance().screen != null) {
            fade = 0.0F;
        }
    }

    /**
     * Draws the steam veil as a GUI layer below the camera overlay, so the
     * crosshair and the rest of the HUD stay crisp above the fog.
     */
    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null
                || minecraft.screen != null || minecraft.options.hideGui) {
            return;
        }
        float temperature = shownTenths / 10.0F;
        float heat = Mth.clamp((temperature - STEAM_START) / (STEAM_FULL - STEAM_START), 0.0F, 1.0F);
        // Smoothstep, so the fog starts and ends gently instead of ramping in
        // a straight line.
        float eased = fade * fade * (3.0F - 2.0F * fade);
        int alpha = Math.round(MAX_ALPHA * heat * eased);
        if (alpha <= 0) {
            return;
        }

        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        graphics.fillGradient(0, 0, width, height,
                (alpha << 24) | VEIL_RGB, ((alpha * 2 / 3) << 24) | VEIL_RGB);

        // Slow, soft horizontal wisps drifting upwards, so the veil reads as
        // moving steam rather than a flat tint.
        long time = Util.getMillis();
        int bandHeight = Math.max(12, height / 7);
        int wispAlpha = Math.max(0, alpha / 4);
        for (int i = 0; i < 3; i++) {
            int travel = (int) ((time / 26 + i * height / 3L) % (height + bandHeight));
            int y = height + bandHeight - travel;
            graphics.fill(0, y, width, Math.min(height, y + bandHeight),
                    (wispAlpha << 24) | 0xF4F8F9);
        }
    }
}