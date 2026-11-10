package ru.samogon.process;

/**
 * Indices of the values synchronized from the brewery to its menu.
 *
 * <p>Container data slots are sent as shorts, so every value has to stay in
 * the positive 16-bit range: volumes are whole millilitres and temperatures
 * are scaled by ten.</p>
 */
public final class BreweryData {
    /** Selected process tab: 0 mash, 1 boil. */
    public static final int TAB = 0;
    public static final int STAGE = 1;
    public static final int PROGRESS = 2;
    public static final int ACTIVE = 3;
    public static final int READY = 4;
    public static final int TEMPERATURE = 5;
    public static final int AMBIENT = 6;
    public static final int VOLUME = 7;
    public static final int ABV = 8;
    public static final int QUALITY = 9;
    public static final int IMPURITIES = 10;
    /** Brew stage of the charge: 0 none, 1 wort, 2 hopped, 3 beer. */
    public static final int STEP = 11;
    public static final int DURATION = 12;
    public static final int COUNT = 13;

    private BreweryData() {}
}
