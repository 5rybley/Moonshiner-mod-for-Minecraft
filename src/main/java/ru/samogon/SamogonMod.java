package ru.samogon;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import ru.samogon.registry.ModBlocks;
import ru.samogon.registry.ModEffects;
import ru.samogon.registry.ModEntityTypes;
import ru.samogon.registry.ModItems;
import ru.samogon.registry.ModMenus;
import ru.samogon.registry.ModSounds;

@Mod(SamogonMod.MOD_ID)
public final class SamogonMod {
    public static final String MOD_ID = "samogon";
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main", () ->
            CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + MOD_ID))
                    .icon(() -> new ItemStack(ModItems.YEAST.get()))
                    .displayItems((params, output) -> ModItems.addToTab(output))
                    .build());

    public SamogonMod(IEventBus modBus, ModContainer container) {
        ModBlocks.BLOCKS.register(modBus);
        ModBlocks.BLOCK_ITEMS.register(modBus);
        ModBlocks.BLOCK_ENTITIES.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModEffects.EFFECTS.register(modBus);
        ModSounds.SOUNDS.register(modBus);
        ModEntityTypes.ENTITY_TYPES.register(modBus);
        ModMenus.MENUS.register(modBus);
        TABS.register(modBus);
    }
}
