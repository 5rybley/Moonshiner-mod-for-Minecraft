package ru.samogon.process;

/** Tiny dependency-free smoke test used by the Gradle check task. */
public final class SimulationTest {
    public static void main(String[] args) {
        Simulation.Batch wort = Simulation.mash("grain", 4);
        require(wort.abv() == 0, "fresh wort must not contain alcohol");
        require(Simulation.mash("sugar", -4).quality()
                        == Simulation.mash("sugar", 0).quality() - 4,
                "a negative mash bonus must lower quality instead of being clamped away");
        require(Simulation.mash("grain", 100).quality() == 100,
                "mash quality must stay clamped at 100");
        Simulation.Batch mash = Simulation.ferment(wort, 2.0, 19.0);
        require(mash.abv() > 8 && mash.abv() < 14, "fermentation must produce a plausible wash");

        double water = Simulation.boilingPoint(0);
        double azeotrope = Simulation.boilingPoint(96);
        require(water > azeotrope, "ethanol must lower the boiling point");
        require(Simulation.vapourAbv(10, 3) > Simulation.vapourAbv(10, 0),
                "reflux plates must enrich the vapour");

        StillRun run = new StillRun(new Simulation.Batch(1000, 12, 70, 55, 0, false, "grain", 0));
        for (int i = 0; i < 12000 && !run.finished(); i++) {
            run.tick(20, 0.9, true, true, 0, () -> 0.5);
        }
        require(run.finished(), "a heated still with coolant must finish");
        require(run.cutVolume(Simulation.Fraction.HEARTS) > 0, "the still must collect hearts");
        require(run.cutAbv(Simulation.Fraction.HEARTS) > 12, "hearts must be stronger than the wash");
        Simulation.Batch hearts = run.drain(Simulation.Fraction.HEARTS, 100);
        if (hearts == null) {
            require(run.cutVolume(Simulation.Fraction.HEARTS) < 100, "drain must report an empty cut");
        } else {
            require(hearts.abv() > 12, "drained hearts must stay concentrated");
        }

        StillRun dry = new StillRun(new Simulation.Batch(1000, 12, 70, 55, 0, false, "grain", 0));
        for (int i = 0; i < 200; i++) {
            dry.tick(20, 1.0, true, false, 0, () -> 1.0);
        }
        require(dry.cutVolume(Simulation.Fraction.HEARTS) == 0,
                "a still without coolant must not produce a cut");
        require(Math.abs(dry.liquidVolume() - 1000) < 0.001,
                "a still without coolant must not evaporate its charge");

        StillRun hot = new StillRun(new Simulation.Batch(1000, 50, 70, 40, 0, false, "grain", 0));
        for (int i = 0; i < 5000 && !hot.finished(); i++) {
            hot.tick(20, 1.2, true, true, 0, () -> 0.0);
        }
        require(hot.puked(), "lava-level heat must be able to puke the still");
        double hotCutAlcohol = hot.cutAlcohol(Simulation.Fraction.HEADS)
                + hot.cutAlcohol(Simulation.Fraction.HEARTS)
                + hot.cutAlcohol(Simulation.Fraction.TAILS);
        require(hotCutAlcohol <= hot.alcoholCollected() + 1.0E-9,
                "a puke must not duplicate alcohol");

        Simulation.Batch rectified = Simulation.rectify(new Simulation.Batch(400, 35, 60, 60, 1, false, "grain", 0));
        require(rectified.abv() > 90, "rectification must reach a high strength");
        require(rectified.volume() < 400, "rectification must respect the mass balance");
        Simulation.Batch tinyFeed = new Simulation.Batch(1, 1, 60, 30, 0, false, "grain", 0);
        Simulation.Batch tinyRectified = Simulation.rectify(tinyFeed);
        require(tinyRectified.alcohol() <= tinyFeed.alcohol() + 1.0E-9,
                "rectification must never create alcohol from a tiny feed");
        Simulation.Batch smallCut = Simulation.cutAtAbv(
                new Simulation.Batch(100, 50, 70, 40, 0, false, "grain", 0),
                Simulation.Fraction.HEARTS, 50, 0.45, 0.005);
        require(Math.abs(smallCut.volume() - 0.005) < 1.0E-12,
                "small cuts must keep their exact volume");
        require(Math.abs(smallCut.alcohol() - 0.0025) < 1.0E-12,
                "small cuts must keep their exact alcohol");
        boolean weakInfusionRejected = false;
        try {
            Simulation.infuse(new Simulation.Batch(100, 10, 50, 50, 0, false, "grain", 0), "berry");
        } catch (IllegalArgumentException exception) {
            weakInfusionRejected = true;
        }
        require(weakInfusionRejected, "infusion must reject a weak batch");
        Simulation.Batch filtered = Simulation.filter(rectified);
        require(filtered.impurities() < rectified.impurities(), "filter must lower impurities");
        Simulation.Batch diluted = Simulation.dilute(filtered, 40);
        require(Math.abs(diluted.abv() - 40) < 0.001, "dilution target must be exact");
        Simulation.Batch aged = Simulation.mature(diluted, true);
        require(aged.quality() > diluted.quality(), "oak aging must improve quality");
        require(aged.age() == diluted.age() + 1, "aging must advance the age counter");
        System.out.println("Simulation smoke test passed");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
