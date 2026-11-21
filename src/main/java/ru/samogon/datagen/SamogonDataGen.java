package ru.samogon.datagen;

import net.minecraft.data.PackOutput;
import net.minecraftforge.common.data.ForgeAdvancementProvider;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.samogon.SamogonMod;

import java.util.List;

/**
 * Entry point for {@code runData}. Only server data is generated today: the
 * advancement tree is the part of the mod that changes most often, while the
 * hand-written recipes, loot tables and blockstates remain the source of truth
 * they have always been. Adding another provider here is one line.
 */
@Mod.EventBusSubscriber(modid = SamogonMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class SamogonDataGen {
    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        if (!event.includeServer()) return;
        PackOutput output = event.getGenerator().getPackOutput();
        event.getGenerator().addProvider(true, new ForgeAdvancementProvider(output, event.getLookupProvider(),
                event.getExistingFileHelper(), List.of(new SamogonAdvancements())));
    }

    private SamogonDataGen() {}
}
