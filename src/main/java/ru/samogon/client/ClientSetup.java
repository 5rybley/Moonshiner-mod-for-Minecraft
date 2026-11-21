package ru.samogon.client;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import ru.samogon.SamogonMod;
import ru.samogon.block.PlacedVesselBlock;
import ru.samogon.registry.ModBlocks;
import ru.samogon.registry.ModEntityTypes;
import ru.samogon.registry.ModMenus;

/** Client-only registrations that must run on the mod event bus. */
@Mod.EventBusSubscriber(modid = SamogonMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {}

    /**
     * Registers the container screens. Forge makes vanilla's
     * {@code MenuScreens.register} public through an access transformer, which
     * is the 1.21.1 replacement for the removed menu-screen registration event.
     */
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(ModMenus.PROCESS.get(), ProcessScreen::new);
            MenuScreens.register(ModMenus.AGING_BARREL.get(), AgingBarrelScreen::new);
            MenuScreens.register(ModMenus.BREWERY.get(), BreweryScreen::new);
            MenuScreens.register(ModMenus.BANYA_STOVE.get(), BanyaStoveScreen::new);
        });
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlocks.SAMOGON_CRATE_ENTITY.get(), SamogonCrateRenderer::new);
        event.registerBlockEntityRenderer(ModBlocks.PLACED_DRINK_ENTITY.get(), PlacedDrinkRenderer::new);
        // The vomited soul-sand lump is drawn as a small dropped-block-sized cube.
        event.registerEntityRenderer(ModEntityTypes.PUKE.get(), PukeBlockRenderer::new);
    }

    /**
     * Tints the liquid element of a filled shot glass or cup by the drink inside
     * it: clear for spirits, gold for beer and white wine, red for red wine and
     * brown for kvass or stout.
     */
    @SubscribeEvent
    public static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tintIndex) -> tintIndex == 0
                        ? state.getValue(PlacedVesselBlock.LIQUID).color() : 0xFFFFFFFF,
                ModBlocks.SHOT_GLASS.get(), ModBlocks.CUP.get());
    }
}
