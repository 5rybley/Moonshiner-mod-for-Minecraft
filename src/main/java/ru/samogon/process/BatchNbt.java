package ru.samogon.process;

import net.minecraft.nbt.CompoundTag;
import ru.samogon.process.Simulation.Batch;

/** NBT helpers for a batch stored inside a machine. */
public final class BatchNbt {
    public static CompoundTag save(Batch batch) {
        CompoundTag tag = new CompoundTag();
        if (batch == null) return tag;
        tag.putDouble("Volume", batch.volume());
        tag.putDouble("Abv", batch.abv());
        tag.putInt("Quality", batch.quality());
        tag.putInt("Impurities", batch.impurities());
        tag.putInt("Passes", batch.passes());
        tag.putBoolean("Filtered", batch.filtered());
        tag.putString("Origin", batch.origin());
        tag.putInt("Age", batch.age());
        tag.putString("Composition", batch.composition());
        return tag;
    }

    public static Batch load(CompoundTag tag) {
        if (tag == null || !tag.contains("Volume")) return null;
        try {
            return new Batch(tag.getDouble("Volume"), tag.getDouble("Abv"), tag.getInt("Quality"),
                    tag.getInt("Impurities"), tag.getInt("Passes"), tag.getBoolean("Filtered"),
                    tag.getString("Origin"), tag.getInt("Age"), tag.getString("Composition"));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private BatchNbt() {
    }
}
