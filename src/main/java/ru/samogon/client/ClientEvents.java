package ru.samogon.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.samogon.SamogonMod;
import ru.samogon.registry.ModItems;

/** Client-only hook that turns the guide item into an illustrated book. */
@Mod.EventBusSubscriber(modid = SamogonMod.MOD_ID, value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {}

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (openGuide(event.getItemStack())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (openGuide(event.getItemStack())) {
            event.setCanceled(true);
        }
    }

    /** Opens the matching handbook; returns true when the stack was a guide. */
    private static boolean openGuide(ItemStack stack) {
        if (stack.is(ModItems.GUIDE.get())) {
            if (!(Minecraft.getInstance().screen instanceof GuideScreen)) {
                Minecraft.getInstance().setScreen(new GuideScreen());
            }
            return true;
        }
        if (stack.is(ModItems.BREWERS_GUIDE.get())) {
            if (!(Minecraft.getInstance().screen instanceof BrewersGuideScreen)) {
                Minecraft.getInstance().setScreen(new BrewersGuideScreen());
            }
            return true;
        }
        if (stack.is(ModItems.WINEMAKERS_GUIDE.get())) {
            if (!(Minecraft.getInstance().screen instanceof WinemakersGuideScreen)) {
                Minecraft.getInstance().setScreen(new WinemakersGuideScreen());
            }
            return true;
        }
        return false;
    }
}
