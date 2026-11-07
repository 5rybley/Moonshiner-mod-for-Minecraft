package ru.samogon.process;

/**
 * Indices of the values synchronized from a processing block to its menu.
 *
 * <p>Container data slots are sent as shorts, so every value has to stay in
 * the positive 16-bit range. Volumes are therefore stored as whole millilitres
 * and temperatures/strengths are scaled by ten.</p>
 */
public final class MachineData {
    public static final int STAGE = 0;
    public static final int PROGRESS = 1;
    public static final int DURATION = 2;
    public static final int ACTIVE = 3;
    public static final int READY = 4;
    public static final int TEMPERATURE = 5;
    public static final int AMBIENT = 6;
    public static final int HEAT = 7;
    public static final int COOLANT_MINUTES = 8;
    public static final int BATCH_VOLUME = 9;
    public static final int BATCH_ABV = 10;
    public static final int BATCH_QUALITY = 11;
    public static final int BATCH_IMPURITIES = 12;
    public static final int BATCH_AGE = 13;
    public static final int BATCH_FILTERED = 14;
    public static final int STILL_PRESENT = 15;
    public static final int STILL_TEMPERATURE = 16;
    public static final int STILL_ABV = 17;
    public static final int STILL_PRESSURE = 18;
    public static final int HEADS_VOLUME = 19;
    public static final int HEARTS_VOLUME = 20;
    public static final int TAILS_VOLUME = 21;
    public static final int STILL_FINISHED = 22;
    public static final int RECEIVER = 23;
    public static final int SIEVE = 24;
    public static final int AGING_KIND = 25;
    public static final int STILL_CLEARABLE = 26;
    public static final int BLEND_ADDITIVES = 27;
    public static final int COUNT = 28;

    private MachineData() {}
}
