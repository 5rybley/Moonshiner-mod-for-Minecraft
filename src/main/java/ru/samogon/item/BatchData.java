package ru.samogon.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import ru.samogon.process.Simulation.Batch;
import ru.samogon.registry.ModItems;

public final class BatchData {
    /** True when the stack carries the mod's own batch payload. */
    public static boolean has(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof BatchItem)) return false;
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().contains("samogon_volume");
    }

    public static Batch read(ItemStack stack) {
        if (!(stack.getItem() instanceof BatchItem item)) throw new IllegalArgumentException("Not a batch item");
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        try {
            if (data.contains("samogon_volume")) {
                // Old bottles never stored the cut flag; anything distilled was
                // historically separated into fractions.
                boolean cut = data.contains("samogon_cut")
                        ? data.getBoolean("samogon_cut")
                        : data.getInt("samogon_passes") > 0;
                return new Batch(data.getDouble("samogon_volume"), data.getDouble("samogon_abv"),
                        data.getInt("samogon_quality"), data.getInt("samogon_impurities"), data.getInt("samogon_passes"),
                        data.getBoolean("samogon_filtered"), data.getString("samogon_origin"), data.getInt("samogon_age"),
                        data.getString("samogon_composition"), data.getBoolean("samogon_rectified"),
                        data.getInt("samogon_oak"), cut, data.getInt("samogon_column_runs"));
            }
        } catch (IllegalArgumentException ignored) { /* Invalid external item data falls back to safe defaults. */ }
        boolean wort = stack.is(ModItems.SWEET_WORT.get());
        boolean mash = stack.is(ModItems.FERMENTED_MASH.get());
        int quality = wort ? 55 : mash ? 65 : 72;
        int impurities = wort ? 0 : mash ? 55 : 30;
        int passes = wort || mash ? 0 : 2;
        return new Batch(item.defaultVolume(), item.defaultAbv(), quality, impurities,
                passes, false, wort || mash ? "grain" : "sugar", 0);
    }

    public static ItemStack create(Item item, Batch batch) {
        ItemStack stack = new ItemStack(item);
        CompoundTag tag = new CompoundTag();
        tag.putDouble("samogon_volume", batch.volume());
        tag.putDouble("samogon_abv", batch.abv());
        tag.putInt("samogon_quality", batch.quality());
        tag.putInt("samogon_impurities", batch.impurities());
        tag.putInt("samogon_passes", batch.passes());
        tag.putBoolean("samogon_filtered", batch.filtered());
        tag.putString("samogon_origin", batch.origin());
        tag.putInt("samogon_age", batch.age());
        tag.putString("samogon_composition", batch.composition());
        tag.putBoolean("samogon_rectified", batch.rectified());
        tag.putInt("samogon_oak", batch.oak());
        tag.putBoolean("samogon_cut", batch.cut());
        tag.putInt("samogon_column_runs", batch.columnRuns());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    private BatchData() {}
}