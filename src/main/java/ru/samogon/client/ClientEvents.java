package ru.samogon.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import ru.samogon.SamogonMod;
import ru.samogon.registry.ModItems;

/** Client-only hook that turns the guide item into an illustrated book. */
@EventBusSubscriber(modid = SamogonMod.MOD_ID, value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {}

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        openGuide(event.getItemStack());
        if (event.getItemStack().is(ModItems.GUIDE.get())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        openGuide(event.getItemStack());
        if (event.getItemStack().is(ModItems.GUIDE.get())) {
            event.setCanceled(true);
        }
    }

    private static void openGuide(ItemStack stack) {
        if (stack.is(ModItems.GUIDE.get()) && !(Minecraft.getInstance().screen instanceof GuideScreen)) {
            Minecraft.getInstance().setScreen(new GuideScreen());
        }
    }
}
