package ru.samogon.datagen;

import net.minecraft.data.PackOutput;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.data.AdvancementProvider;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import ru.samogon.SamogonMod;

import java.util.List;

/**
 * Entry point for {@code runData}. Only server data is generated today: the
 * advancement tree is the part of the mod that changes most often, while the
 * hand-written recipes, loot tables and blockstates remain the source of truth
 * they have always been. Adding another provider here is one line.
 */
@EventBusSubscriber(modid = SamogonMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class SamogonDataGen {
    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        if (!event.includeServer()) return;
        PackOutput output = event.getGenerator().getPackOutput();
        event.addProvider(new AdvancementProvider(output, event.getLookupProvider(),
                event.getExistingFileHelper(), List.of(new SamogonAdvancements())));
    }

    private SamogonDataGen() {}
}
