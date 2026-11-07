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
    public static final int STILL_FRACTIONAL = 28;
    public static final int RECTIFY_RUNS = 29;
    public static final int RECTIFY_RUN = 30;
    /** Tasting-table readout: 1 when a drink and a hydrometer are both loaded. */
    public static final int TASTING_VALID = 31;
    /** Ordinal of the detected {@link SpiritType}. */
    public static final int TASTING_TYPE = 32;
    /** Base intoxication level the drink would apply (1..4). */
    public static final int TASTING_INTOX = 33;
    /** Intoxication duration in seconds. */
    public static final int TASTING_DURATION = 34;
    /** Aura index plus one (0 = no aura). */
    public static final int TASTING_AURA = 35;
    /** 1 when the drink is a technical fraction with poison/blindness/weakness. */
    public static final int TASTING_TECHNICAL = 36;
    /** 1 when the drink can be drunk as it is. */
    public static final int TASTING_DRINKABLE = 37;
    /** Strength (1..5) of the dominant aura. */
    public static final int TASTING_AURA_LEVEL = 38;
    /** Number of distinct auras the drink carries (0..5). */
    public static final int TASTING_AURA_COUNT = 39;
    public static final int COUNT = 40;

    private MachineData() {}
}
