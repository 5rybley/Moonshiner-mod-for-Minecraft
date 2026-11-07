package ru.samogon.client;

import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import ru.samogon.SamogonMod;
import ru.samogon.registry.ModBlocks;
import ru.samogon.registry.ModEntityTypes;
import ru.samogon.registry.ModMenus;

/** Client-only registrations that must run on the mod event bus. */
@EventBusSubscriber(modid = SamogonMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    /** Draws the crate item with the bottles it carries, matching the block in the world. */
    private static final IClientItemExtensions CRATE_ITEM_EXTENSIONS = new IClientItemExtensions() {
        @Override
        public BlockEntityWithoutLevelRenderer getCustomRenderer() {
            return SamogonCrateItemRenderer.get();
        }
    };

    private ClientSetup() {}

    @SubscribeEvent
    public static void registerMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.PROCESS.get(), ProcessScreen::new);
        event.register(ModMenus.AGING_BARREL.get(), AgingBarrelScreen::new);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlocks.SAMOGON_CRATE_ENTITY.get(), SamogonCrateRenderer::new);
        // The vomited soul-sand lump is drawn as a small dropped-block-sized cube.
        event.registerEntityRenderer(ModEntityTypes.PUKE.get(), PukeBlockRenderer::new);
    }

    @SubscribeEvent
    public static void registerClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(CRATE_ITEM_EXTENSIONS, ModBlocks.SAMOGON_CRATE_ITEM.get());
    }
}
