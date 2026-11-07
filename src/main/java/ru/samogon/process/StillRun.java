package ru.samogon.process;

import ru.samogon.process.Simulation.Batch;
import ru.samogon.process.Simulation.Fraction;

/**
 * State of one pot-still run. It tracks liquid volume, strength, boiling
 * temperature and three separate receiver cuts. The block entity advances it
 * once per second.
 */
public final class StillRun {
    public static final int BOTTLE_VOLUME = 100;

    @FunctionalInterface
    public interface Roll {
        double next();
    }

    double liquidVolume;
    double abv;
    int sourceQuality;
    int sourceImpurities;
    String origin;
    int passes;
    double temperature = 20;
    double totalAlcohol;
    double alcoholCollected;
    double headsVolume;
    double heartsVolume;
    double tailsVolume;
    double headsAlcohol;
    double heartsAlcohol;
    double tailsAlcohol;
    double pressure;
    boolean finished;
    boolean puked;
    double lastVapourAbv;
    Fraction currentFraction = Fraction.HEADS;

    public StillRun(Batch wash) {
        if (wash.alcohol() < 1) throw new IllegalArgumentException("Wash contains no alcohol");
        liquidVolume = wash.volume();
        abv = wash.abv();
        sourceQuality = wash.quality();
        sourceImpurities = wash.impurities();
        origin = wash.origin();
        passes = wash.passes();
        totalAlcohol = wash.alcohol();
    }

    StillRun() {
    }

    /**
     * Advance by one second.
     *
     * @param ambient    ambient temperature in C
     * @param heat       heat source strength, 0..1.2
     * @param heatSource whether a heat source is present below the pot
     * @param coolant    whether the condenser still has cooling water
     * @param plates     number of reflux plates (0 for a simple pot still)
     * @param roll       random value in [0,1) used for the overheat roll
     */
    public void tick(double ambient, double heat, boolean heatSource, boolean coolant,
                     int plates, Roll roll) {
        if (finished) return;
        double boiling = Simulation.boilingPoint(abv);
        // Heat the wash just above its current boiling point. A fixed 104 C
        // target used to sit more than 10 C above the wash boiling point,
        // which made every pot-still run puke and turned all hearts into tails.
        double target = heatSource
                ? Math.min(100.0, boiling + 3.0 + 3.0 * Math.max(0, heat))
                : ambient;
        temperature += (target - temperature) * (heatSource ? 0.045 : 0.020);

        if (liquidVolume <= 0.5 || abv <= 0.2) {
            finished = true;
            return;
        }
        if (!heatSource || temperature < boiling - 1.0) {
            pressure = Math.max(0, pressure - 0.5);
            return;
        }
        // A condenser without water cannot remove vapour. Stopping the run is
        // clearer than silently puking the whole charge into the receiver.
        if (!coolant) {
            pressure = Math.max(0, pressure - 0.5);
            return;
        }

        // A very hot fire builds pressure even when the wash is boiling. Lava
        // and soul fire are deliberately risky; a campfire or ordinary fire is
        // the safe choice. This also gives the pressure gauge a real meaning.
        pressure = Math.max(0, pressure + (heat - 0.9) * 0.45);

        double vapourRate = 2.2 * heat * (0.65 + Math.max(0, temperature - boiling) * 0.12);
        double vapourVolume = Math.min(vapourRate, liquidVolume * 0.03);
        double vapourAbv = Simulation.vapourAbv(abv, plates);
        lastVapourAbv = vapourAbv;
        double progress = totalAlcohol > 0 ? alcoholCollected / totalAlcohol : 0;
        currentFraction = Simulation.classify(progress, vapourAbv, temperature);

        double availableAlcohol = liquidVolume * abv / 100;
        double alcoholOut = Math.min(vapourVolume * vapourAbv / 100, availableAlcohol);
        double waterOut = Math.min(Math.max(0, vapourVolume - alcoholOut),
                Math.max(0, liquidVolume - availableAlcohol));
        double outVolume = alcoholOut + waterOut;

        liquidVolume = Math.max(0, liquidVolume - outVolume);
        abv = liquidVolume > 0.01 ? Math.max(0, (availableAlcohol - alcoholOut) / liquidVolume * 100) : 0;
        alcoholCollected += alcoholOut;

        boolean overheat = heat >= 1.15 && temperature > boiling + 4.0 && roll.next() < 0.08;
        if (overheat || pressure > 100) {
            puked = true;
            pressure = 0;
            // Route exactly the vapour that left the pot. The old code added
            // an extra half-volume to hearts without removing it from the pot,
            // which duplicated alcohol on every puke.
            addCut(Fraction.HEARTS, outVolume, outVolume > 0 ? alcoholOut / outVolume * 100 : 0);
            temperature = Math.min(temperature, boiling + 6);
        } else {
            addCut(currentFraction, outVolume, outVolume > 0 ? alcoholOut / outVolume * 100 : 0);
            pressure = Math.max(0, pressure - 0.02);
        }
        if (liquidVolume <= 0.5 || abv <= 0.2) finished = true;
    }

    private void addCut(Fraction fraction, double volume, double strength) {
        if (volume <= 0) return;
        double alcohol = volume * strength / 100;
        switch (fraction) {
            case HEADS -> {
                headsVolume += volume;
                headsAlcohol += alcohol;
            }
            case HEARTS -> {
                heartsVolume += volume;
                heartsAlcohol += alcohol;
            }
            case TAILS -> {
                tailsVolume += volume;
                tailsAlcohol += alcohol;
            }
        }
    }

    /**
     * Drain one bottle from a receiver cut. If the remaining cut is smaller
     * than one bottle, the whole leftover is returned instead of becoming
     * permanently unreachable.
     */
    public Batch drain(Fraction fraction, int amount) {
        double volume = cutVolume(fraction);
        double alcohol = cutAlcohol(fraction);
        if (volume <= 0.0001 || alcohol <= 0 || amount <= 0) return null;
        double drained = Math.min(amount, volume);
        double abvOut = alcohol / volume * 100;
        double drainedAlcohol = alcohol * drained / volume;
        switch (fraction) {
            case HEADS -> {
                headsVolume = Math.max(0, headsVolume - drained);
                headsAlcohol = Math.max(0, headsAlcohol - drainedAlcohol);
            }
            case HEARTS -> {
                heartsVolume = Math.max(0, heartsVolume - drained);
                heartsAlcohol = Math.max(0, heartsAlcohol - drainedAlcohol);
            }
            case TAILS -> {
                tailsVolume = Math.max(0, tailsVolume - drained);
                tailsAlcohol = Math.max(0, tailsAlcohol - drainedAlcohol);
            }
        }
        double progress = totalAlcohol > 0 ? Math.min(1, alcoholCollected / totalAlcohol) : 1;
        Batch source = new Batch(100, abvOut, sourceQuality, sourceImpurities, passes, false, origin, 0);
        return Simulation.cutAtAbv(source, fraction, abvOut, progress, drained);
    }

    public double cutVolume(Fraction fraction) {
        return switch (fraction) {
            case HEADS -> headsVolume;
            case HEARTS -> heartsVolume;
            case TAILS -> tailsVolume;
        };
    }

    public double cutAlcohol(Fraction fraction) {
        return switch (fraction) {
            case HEADS -> headsAlcohol;
            case HEARTS -> heartsAlcohol;
            case TAILS -> tailsAlcohol;
        };
    }

    public double cutAbv(Fraction fraction) {
        double volume = cutVolume(fraction);
        return volume <= 0 ? 0 : cutAlcohol(fraction) / volume * 100;
    }

    public double temperature() {
        return temperature;
    }

    public double pressure() {
        return pressure;
    }

    public double liquidVolume() {
        return liquidVolume;
    }

    public double abv() {
        return abv;
    }

    public double totalAlcohol() {
        return totalAlcohol;
    }

    public double alcoholCollected() {
        return alcoholCollected;
    }

    public double progress() {
        return totalAlcohol <= 0 ? 0 : Math.min(1, alcoholCollected / totalAlcohol);
    }

    public double lastVapourAbv() {
        return lastVapourAbv;
    }

    public Fraction currentFraction() {
        return currentFraction;
    }

    public boolean finished() {
        return finished;
    }

    public boolean puked() {
        return puked;
    }

    public boolean consumePukeFlag() {
        boolean value = puked;
        puked = false;
        return value;
    }
}
