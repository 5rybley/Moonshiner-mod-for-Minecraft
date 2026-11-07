package ru.samogon.process;

import ru.samogon.process.Simulation.Batch;

/**
 * Dependency-free checks for the drink types. Each case is a production
 * history, not a hand-picked label: the classifier must recover the type from
 * origin, passes, fraction separation, rectification, filtration and oak
 * cycles.
 */
public final class SpiritTypeTest {
    public static void main(String[] args) {
        // Not distilled yet.
        expect(SpiritType.WASH, batch(0, 0, false, "grain", 0, "", false, false), "sweet wort");
        expect(SpiritType.BRAGA, batch(12, 0, false, "grain", 0, "", false, false), "fermented wash");

        // Fractional pot-stilled hearts.
        expect(SpiritType.MOONSHINE, batch(40, 1, false, "grain", 0, "", false, true), "grain moonshine");
        expect(SpiritType.MOONSHINE, batch(45, 1, false, "sugar", 0, "", false, true), "sugar moonshine");
        expect(SpiritType.MEAD, batch(38, 1, false, "honey", 0, "", false, true), "honey mead");
        expect(SpiritType.CHACHA, batch(40, 1, false, "berry", 0, "", false, true), "berry chacha");
        expect(SpiritType.CACHACA, batch(40, 1, false, "cane", 0, "", false, true), "cane cachaca");
        expect(SpiritType.POTATO, batch(40, 1, false, "potato", 0, "", false, true), "potato spirit");
        expect(SpiritType.BEET, batch(40, 1, false, "beetroot", 0, "", false, true), "beet spirit");
        expect(SpiritType.COCOA, batch(40, 1, false, "cocoa", 0, "", false, true), "cocoa spirit");
        expect(SpiritType.MELON, batch(40, 1, false, "melon", 0, "", false, true), "melon spirit");
        expect(SpiritType.CARROT, batch(40, 1, false, "carrot", 0, "", false, true), "carrot spirit");
        expect(SpiritType.CACTUS, batch(40, 1, false, "cactus", 0, "", false, true), "cactus spirit");

        // A whole run without fraction separation stays rough moonshine.
        expect(SpiritType.MOONSHINE, batch(40, 1, false, "berry", 0, "", false, false), "whole berry run");
        expect(SpiritType.MOONSHINE, batch(40, 1, false, "grain", 1, "", false, false), "whole oak run");

        // Oak-aged fractional spirits.
        expect(SpiritType.WHISKEY, batch(40, 1, false, "grain", 1, "", false, true), "grain whiskey");
        expect(SpiritType.SCOTCH, batch(40, 2, false, "grain", 1, "", false, true), "double-distilled scotch");
        expect(SpiritType.RUM, batch(40, 1, false, "sugar", 1, "", false, true), "sugar rum");
        expect(SpiritType.BRANDY, batch(40, 1, false, "berry", 1, "", false, true), "single-distilled brandy");
        expect(SpiritType.COGNAC, batch(40, 2, false, "berry", 1, "", false, true), "double-distilled cognac");
        expect(SpiritType.CALVADOS, batch(40, 1, false, "apple", 1, "", false, true), "apple calvados");
        expect(SpiritType.CACHACA, batch(40, 1, false, "cane", 1, "", false, true), "oak cane cachaca");
        expect(SpiritType.POTATO, batch(40, 1, false, "potato", 1, "", false, true), "oak potato spirit");
        expect(SpiritType.BEET, batch(40, 1, false, "beetroot", 1, "", false, true), "oak beet spirit");
        expect(SpiritType.MEAD, batch(40, 1, false, "honey", 1, "", false, true), "oak honey mead");
        expect(SpiritType.COCOA, batch(40, 1, false, "cocoa", 1, "", false, true), "oak cocoa spirit");
        expect(SpiritType.MELON, batch(40, 1, false, "melon", 1, "", false, true), "oak melon spirit");
        expect(SpiritType.CARROT, batch(40, 1, false, "carrot", 1, "", false, true), "oak carrot spirit");
        expect(SpiritType.CACTUS, batch(40, 1, false, "cactus", 1, "", false, true), "oak cactus spirit");

        // Rectification.
        expect(SpiritType.VODKA, batch(40, 2, true, "grain", 0, "", true, true), "filtered rectified vodka");
        expect(SpiritType.SPIRIT, batch(96, 2, false, "grain", 0, "", true, true), "strong unfiltered spirit");
        expect(SpiritType.SPIRIT, batch(55, 2, true, "grain", 0, "", true, true), "undiluted rectified spirit");
        expect(SpiritType.SPIRIT, batch(40, 2, true, "grain", 1, "", true, true), "oak-aged rectified spirit");

        // Aromatic blends keep the base type: additives only change the name.
        expect(SpiritType.CHACHA, batch(40, 1, false, "berry", 0, "sweet_berries", false, true), "berry blend stays chacha");
        expect(SpiritType.MOONSHINE, batch(40, 1, false, "grain", 0, "sweet_berries", false, true), "grain blend stays moonshine");
        expect(SpiritType.WHISKEY, batch(40, 1, false, "grain", 1, "apple", false, true), "oak grain blend stays whiskey");
        expect(SpiritType.COGNAC, batch(40, 2, false, "berry", 1, "honey_bottle", false, true), "oak berry blend stays cognac");
        expect(SpiritType.VODKA, batch(40, 2, true, "grain", 0, "botanical_bundle", true, true), "botanical blend stays vodka");
        expect(SpiritType.SPIRIT, batch(96, 2, false, "grain", 0, "botanical_bundle", true, true), "unfiltered blend stays spirit");

// Raw-material potential is exposed without any Minecraft types.
        require(SpiritType.potentialKey("grain").equals("potential.samogon.grain"),
                "grain potential key");
        require(SpiritType.potentialKey("potato").equals("potential.samogon.potato"),
                "potato potential key");
        require(SpiritType.potentialKey("beetroot").equals("potential.samogon.beetroot"),
                "beetroot potential key");
        require(SpiritType.potentialKey("cane").equals("potential.samogon.cane"),
                "cane potential key");
        require(SpiritType.potentialKey("apple").equals("potential.samogon.apple"),
                "apple potential key");
        require(SpiritType.potentialKey("cocoa").equals("potential.samogon.cocoa"),
                "cocoa potential key");
        require(SpiritType.potentialKey("melon").equals("potential.samogon.melon"),
                "melon potential key");
        require(SpiritType.potentialKey("carrot").equals("potential.samogon.carrot"),
                "carrot potential key");
        require(SpiritType.potentialKey("cactus").equals("potential.samogon.cactus"),
                "cactus potential key");
        require(SpiritType.potentialKey("unobtainium").equals("potential.samogon.sugar"),
                "unknown origins fall back to sugar potential");

        // The flags must survive every stage of the chain.
        Batch wash = Simulation.ferment(Simulation.mash("berry", 4), 2.0, 19.0);
        expect(SpiritType.BRAGA, wash, "real fermented berry wash");
        Batch hearts = Simulation.cutAtAbv(wash, Simulation.Fraction.HEARTS, 45, 0.45, 100);
        require(hearts.passes() == 1, "a pot-still cut is the first distillation");
        require(hearts.cut(), "a fractional cut must be recorded");

        Batch whole = Simulation.cutAtAbv(wash, Simulation.Fraction.HEARTS, 45, 0.45, 100, false);
        require(whole.passes() == 1 && !whole.cut(), "a whole run must clear the cut flag");
        expect(SpiritType.MOONSHINE, whole, "whole berry run from a real chain");

        Batch second = Simulation.cutAtAbv(hearts, Simulation.Fraction.HEARTS, 55, 0.45, 100);
        require(second.passes() == 2, "a second pot run is the second distillation");
        Batch cognac = Simulation.mature(Simulation.dilute(second, 40), true);
        require(cognac.oak() == 1, "oak aging must be recorded");
        require(cognac.age() == second.age() + 1, "the aging cycle must still advance");
        expect(SpiritType.COGNAC, cognac, "cognac from a real chain");

        Batch rectified = Simulation.rectify(Simulation.filter(Simulation.dilute(second, 40)));
        require(rectified.rectified(), "rectification must be recorded");
        require(rectified.columnRuns() == 1, "the first column pass must be counted");
        Batch filtered = Simulation.filter(rectified);
        require(filtered.rectified() && filtered.filtered(), "filtration must keep the rectified flag");
        Batch vodka = Simulation.dilute(filtered, 40);
        require(vodka.rectified() && vodka.filtered(), "dilution must keep the process flags");
        expect(SpiritType.VODKA, vodka, "vodka from a real chain");

        System.out.println("Spirit type classification passed");
    }

    private static Batch batch(double abv, int passes, boolean filtered, String origin, int oak,
                               String composition, boolean rectified, boolean cut) {
        return new Batch(1000, abv, 70, 30, passes, filtered, origin, 0, composition, rectified, oak, cut, 0);
    }

    private static void expect(SpiritType expected, Batch batch, String label) {
        SpiritType actual = SpiritType.of(batch);
        require(actual == expected, label + ": expected " + expected.id() + " but got " + actual.id());
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private SpiritTypeTest() {
    }
}