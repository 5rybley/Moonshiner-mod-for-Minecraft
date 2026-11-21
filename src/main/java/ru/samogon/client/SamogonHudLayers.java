package ru.samogon.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.gui.overlay.ForgeLayeredDraw;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.samogon.SamogonMod;

/**
 * Registers the mod's HUD layers with Forge's layered-draw system (vanilla's
 * {@code RenderGuiEvent} is gone in 1.21.1). The drunk veil and the banya steam
 * go under the camera overlay so the crosshair, hotbar and every other HUD
 * element stay crisp; the blackout goes on the post-sleep stack, above
 * everything, chat included.
 */
@Mod.EventBusSubscriber(modid = SamogonMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class SamogonHudLayers {
    /** A unique id per layer, also the name other mods can order against. */
    private static final ResourceLocation DRUNK_VISION =
            ResourceLocation.fromNamespaceAndPath(SamogonMod.MOD_ID, "drunk_vision");
    private static final ResourceLocation BANYA_STEAM =
            ResourceLocation.fromNamespaceAndPath(SamogonMod.MOD_ID, "banya_steam");
    private static final ResourceLocation BLACKOUT =
            ResourceLocation.fromNamespaceAndPath(SamogonMod.MOD_ID, "blackout");

    private SamogonHudLayers() {}

    @SubscribeEvent
    public static void onAddLayers(AddGuiOverlayLayersEvent event) {
        ForgeLayeredDraw draw = event.getLayeredDraw();

        // World veils, rendered below the camera overlay (so below the regular
        // HUD layers that live in the same pre-sleep stack).
        draw.addBelow(ForgeLayeredDraw.PRE_SLEEP_STACK, DRUNK_VISION,
                ForgeLayeredDraw.CAMERA_OVERLAY, DrunkVision::render);
        draw.addBelow(ForgeLayeredDraw.PRE_SLEEP_STACK, BANYA_STEAM,
                ForgeLayeredDraw.CAMERA_OVERLAY, BanyaSteamOverlay::render);

        // The full-screen blackout sits on top of the whole HUD.
        draw.add(ForgeLayeredDraw.POST_SLEEP_STACK, BLACKOUT, BlackoutOverlay::render);
    }
}
