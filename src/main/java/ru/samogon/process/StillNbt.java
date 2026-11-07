package ru.samogon.process;

import net.minecraft.nbt.CompoundTag;
import ru.samogon.process.Simulation.Fraction;

import java.util.Locale;

/** Minecraft NBT persistence for a pot-still run. */
public final class StillNbt {
    public static void save(StillRun run, CompoundTag tag) {
        tag.putDouble("Liquid", run.liquidVolume);
        tag.putDouble("Abv", run.abv);
        tag.putInt("Quality", run.sourceQuality);
        tag.putInt("Impurities", run.sourceImpurities);
        tag.putString("Origin", run.origin);
        tag.putInt("Passes", run.passes);
        tag.putString("Composition", run.composition);
        tag.putBoolean("Rectified", run.rectified);
        tag.putInt("Oak", run.oak);
        tag.putBoolean("Fractional", run.fractional);
        tag.putInt("ColumnRuns", run.columnRuns);
        tag.putDouble("Temp", run.temperature);
        tag.putDouble("TotalAlcohol", run.totalAlcohol);
        tag.putDouble("Collected", run.alcoholCollected);
        tag.putDouble("Heads", run.headsVolume);
        tag.putDouble("Hearts", run.heartsVolume);
        tag.putDouble("Tails", run.tailsVolume);
        tag.putDouble("HeadsAlcohol", run.headsAlcohol);
        tag.putDouble("HeartsAlcohol", run.heartsAlcohol);
        tag.putDouble("TailsAlcohol", run.tailsAlcohol);
        tag.putDouble("Pressure", run.pressure);
        tag.putBoolean("Finished", run.finished);
        tag.putBoolean("Puked", run.puked);
        tag.putDouble("Vapour", run.lastVapourAbv);
        tag.putString("Fraction", run.currentFraction.id());
    }

    public static StillRun load(CompoundTag tag) {
        StillRun run = new StillRun();
        run.liquidVolume = tag.getDouble("Liquid");
        run.abv = tag.getDouble("Abv");
        run.sourceQuality = tag.getInt("Quality");
        run.sourceImpurities = tag.getInt("Impurities");
        run.origin = tag.getString("Origin");
        run.passes = tag.getInt("Passes");
        run.composition = tag.getString("Composition");
        run.rectified = tag.getBoolean("Rectified");
        run.oak = tag.getInt("Oak");
        // Old still runs always separated fractions.
        run.fractional = tag.contains("Fractional") ? tag.getBoolean("Fractional") : true;
        run.columnRuns = tag.getInt("ColumnRuns");
        // Older saves may not contain Temp; starting from 0 makes a loaded still
        // look permanently frozen until the next slow heating tick.
        run.temperature = tag.contains("Temp") ? tag.getDouble("Temp") : 20;
        run.totalAlcohol = tag.getDouble("TotalAlcohol");
        run.alcoholCollected = tag.getDouble("Collected");
        run.headsVolume = tag.getDouble("Heads");
        run.heartsVolume = tag.getDouble("Hearts");
        run.tailsVolume = tag.getDouble("Tails");
        run.headsAlcohol = tag.getDouble("HeadsAlcohol");
        run.heartsAlcohol = tag.getDouble("HeartsAlcohol");
        run.tailsAlcohol = tag.getDouble("TailsAlcohol");
        run.pressure = tag.getDouble("Pressure");
        run.finished = tag.getBoolean("Finished");
        run.puked = tag.getBoolean("Puked");
        run.lastVapourAbv = tag.getDouble("Vapour");
        try {
            run.currentFraction = Fraction.valueOf(tag.getString("Fraction").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            run.currentFraction = Fraction.HEARTS;
        }
        return run;
    }

    private StillNbt() {
    }
}
