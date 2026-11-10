package ru.samogon.process;

import ru.samogon.process.Simulation.Batch;
import ru.samogon.process.Simulation.Fraction;

import java.util.Locale;

/**
 * Headless integration test for two complete production chains:
 * grain -> rectified spirit -> diluted -> oak-aged spirit,
 * honey -> filtered spirit -> diluted -> honey tincture -> aged tincture.
 *
 * The test deliberately uses the same Simulation/StillRun code as the block
 * entities, so mass-balance and cut-handling regressions surface without a
 * Minecraft client.
 */
public final class ProductionChainTest {
    private static final int MAX_STILL_SECONDS = 20_000;

    public static void main(String[] args) {
        grainChain();
        honeyChain();
        breweryChain();
        wholeRun();
        drainEdgeCases();
        System.out.println("Two production chains, the brewery chain and drain edge cases passed.");
    }

    /** Six column passes are the documented way to reach neutral spirit. */
    private static Batch rectifySix(Batch feed) {
        Batch result = feed;
        for (int run = 0; run < 6; run++) {
            result = Simulation.rectify(result);
        }
        return result;
    }

    private static void grainChain() {
        Batch wort = Simulation.mash("grain", 4);
        Batch wash = Simulation.ferment(wort, 0.5, 19.0);
        require(wash.abv() > 10 && wash.abv() < 13, "grain wash strength");
        require(wash.quality() > 55, "grain wash quality");

        StillResult still = potStill(wash);
        require(still.hearts().volume() > 100, "grain hearts volume");
        require(still.hearts().abv() > 20, "grain hearts must be usable low wines");
        require(still.heads().volume() > 0, "grain heads must be produced");
        require(still.tails().volume() > 0, "grain tails must be produced");
        require(still.totalCollectedAlcohol() <= wash.alcohol() * 1.01,
                "grain still cannot create alcohol");

        Batch rectified = rectifySix(still.hearts());
        require(rectified.abv() > 95.9, "rectifier must concentrate the spirit");
        Batch filtered = Simulation.filter(rectified);
        require(filtered.filtered(), "filter must mark the batch");
        require(filtered.impurities() <= rectified.impurities(), "filter must not add impurities");
        require(filtered.quality() >= rectified.quality(), "filter must not lower quality");

        Batch diluted = Simulation.dilute(filtered, 40);
        require(Math.abs(diluted.abv() - 40) < 0.001, "basin must reach 40% ABV");
        require(diluted.volume() > filtered.volume(), "dilution must add volume");
        require(Math.abs(diluted.alcohol() - filtered.alcohol()) < 0.01,
                "dilution must conserve alcohol");

        Batch aged = Simulation.mature(diluted, true);
        require(aged.age() == diluted.age() + 1, "aging must advance age");
        require(aged.quality() >= diluted.quality(), "oak must not lower quality");
        require(aged.abv() == 40, "aging must keep the strength");

        print("grain", wash, still, aged);
    }

    private static void honeyChain() {
        Batch wort = Simulation.mash("honey", 6);
        Batch wash = Simulation.ferment(wort, 1.2, 22.0);
        require(wash.abv() > 10 && wash.abv() < 14, "honey wash strength");

        StillResult still = potStill(wash);
        require(still.hearts().volume() > 100, "honey hearts volume");
        require(still.hearts().abv() > 20, "honey hearts must be usable low wines");

        Batch rectified = rectifySix(still.hearts());
        require(rectified.abv() > 95.9, "honey rectifier must concentrate the spirit");
        Batch filtered = Simulation.filter(rectified);
        Batch diluted = Simulation.dilute(filtered, 40);
        Batch infused = Simulation.infuse(diluted, "honey");
        require(infused.quality() >= diluted.quality(), "honey infusion must not lower quality");
        require(infused.age() == diluted.age() + 1, "infusion must count as a cycle");
        require(Math.abs(infused.abv() - 40) < 0.001, "infusion must keep 40% ABV");

        Batch aged = Simulation.mature(infused, true);
        require(aged.age() == infused.age() + 1, "tincture aging must advance age");
        require(aged.volume() < infused.volume(), "cask angel's share must reduce volume");

        print("honey", wash, still, aged);
    }

    /**
     * The brewery is a self-contained chain: mash to wort, boil with hops,
     * ferment to beer. It must classify as beer and stay out of the distilling
     * taxonomy, and no other machine may treat a brew as its feed.
     */
    private static void breweryChain() {
        Batch wort = Brewing.wort();
        require(Brewing.isBrew(wort), "brewery wort must be a brew");
        require(Brewing.stageOf(wort) == Brewing.STAGE_WORT, "brewery wort stage");
        require(SpiritType.of(wort) == SpiritType.WASH, "unfermented wort is not a drink");

        Batch hopped = Brewing.boil(wort);
        require(Brewing.stageOf(hopped) == Brewing.STAGE_HOPPED, "brewery hopped stage");
        require(hopped.quality() > wort.quality(), "the hop boil must improve the wort");
        require(SpiritType.of(hopped) == SpiritType.WASH, "hopped wort is not a drink yet");

        Batch beer = Brewing.ferment(hopped, 0.5, 20.0);
        require(Brewing.stageOf(beer) == Brewing.STAGE_BEER, "brewery beer stage");
        require(SpiritType.of(beer) == SpiritType.BEER, "fermented beer must classify as beer");
        require(beer.abv() > 4.0 && beer.abv() < 6.0, "beer must be about 5% ABV");
        require(beer.volume() < hopped.volume(), "fermentation must lose some volume");
        require(BeerStyle.of(beer.origin()) == BeerStyle.LIGHT, "plain malt mash must give the light style");

        // The grain and the optional fermentation additive are encoded in the
        // origin, so every style can be recovered from the finished batch.
        Batch wheat = Brewing.ferment(Brewing.boil(Brewing.wort(BeerStyle.Grain.WHEAT.id())), 0.5, 20.0);
        require(BeerStyle.of(wheat.origin()) == BeerStyle.WHEAT, "wheat grain must give the wheat style");
        require("wheat".equals(Brewing.grainOf(wheat.origin())), "the grain must survive into the beer origin");

        Batch honey = Brewing.ferment(Brewing.boil(Brewing.wort()), 0.5, 20.0,
                BeerStyle.Additive.HONEY.id());
        require(BeerStyle.of(honey.origin()) == BeerStyle.HONEY, "honey must give the honey style");
        require(SpiritType.of(honey) == SpiritType.BEER, "flavoured beer must still classify as beer");

        Batch dark = Brewing.ferment(Brewing.boil(Brewing.wort(BeerStyle.Grain.ROASTED.id())), 0.5, 20.0);
        require(BeerStyle.of(dark.origin()) == BeerStyle.DARK, "roasted malt must give the dark style");

        Batch ipa = Brewing.ferment(Brewing.boil(Brewing.wort()), 0.5, 20.0,
                BeerStyle.Additive.HOP.id());
        require(BeerStyle.of(ipa.origin()) == BeerStyle.IPA, "hops must give the IPA style");

        Batch porter = Brewing.ferment(Brewing.boil(Brewing.wort(BeerStyle.Grain.ROASTED.id())), 0.5, 20.0,
                BeerStyle.Additive.COCOA.id());
        require(BeerStyle.of(porter.origin()) == BeerStyle.PORTER,
                "cocoa and roasted malt must give the porter");

        // Cold fermentation turns the same grain and additive pairs into lagers.
        Batch pilsner = Brewing.ferment(Brewing.boil(Brewing.wort()), 0.5, 12.0);
        require(BeerStyle.of(pilsner.origin()) == BeerStyle.PILSNER,
                "cold barley wort must give the pilsner");
        Batch schwarz = Brewing.ferment(Brewing.boil(Brewing.wort(BeerStyle.Grain.ROASTED.id())), 0.5, 12.0);
        require(BeerStyle.of(schwarz.origin()) == BeerStyle.SCHWARZBIER,
                "cold roasted wort must give the schwarzbier");
        Batch bock = Brewing.ferment(Brewing.boil(Brewing.wort()), 0.5, 12.0,
                BeerStyle.Additive.HONEY.id());
        require(BeerStyle.of(bock.origin()) == BeerStyle.BOCK, "cold honey wort must give the bock");

        // A flavouring without a cold version keeps its taste as a warm ale.
        Batch appleCold = Brewing.ferment(Brewing.boil(Brewing.wort()), 0.5, 12.0,
                BeerStyle.Additive.APPLE.id());
        require(BeerStyle.of(appleCold.origin()) == BeerStyle.APPLE,
                "apple must fall back to the apple ale even when fermented cold");

        // Old three-part origins without a family parse as the warm ale family.
        require(BeerStyle.of("beer:wheat:berry") == BeerStyle.BERRY,
                "an old three-part origin must stay a berry ale");

        // Batches written by older versions carry the plain "beer" origin.
        require(BeerStyle.of("beer") == BeerStyle.LIGHT, "plain beer origin must fall back to the light style");

        Batch wash = Simulation.mash("grain", 4);
        require(!Brewing.isBrew(wash), "ordinary mash must not be a brew");
        require(SpiritType.of(wash) != SpiritType.BEER, "ordinary mash must not be beer");
        System.out.printf(Locale.ROOT,
                "brewery chain: wort %.0f ml | hopped %.0f ml | beer %.0f ml @ %.1f%% | quality %d"
                        + " | styles %s %s %s%n",
                wort.volume(), hopped.volume(), beer.volume(), beer.abv(), beer.quality(),
                BeerStyle.of(beer.origin()), BeerStyle.of(wheat.origin()), BeerStyle.of(honey.origin()));
    }

    private static StillResult potStill(Batch wash) {
        StillRun run = new StillRun(wash);
        int seconds = 0;
        while (!run.finished() && seconds < MAX_STILL_SECONDS) {
            run.tick(20, 0.9, true, true, 0, () -> 0.99);
            seconds++;
        }
        require(run.finished(), "still did not finish within " + MAX_STILL_SECONDS + " seconds");
        require(run.cutVolume(Fraction.HEARTS) > 0, "still produced no hearts");
        require(run.cutVolume(Fraction.HEADS) > 0, "still produced no heads");
        require(run.cutVolume(Fraction.TAILS) > 0, "still produced no tails");

        Batch heads = collect(run, Fraction.HEADS);
        Batch hearts = collect(run, Fraction.HEARTS);
        Batch tails = collect(run, Fraction.TAILS);
        require(run.cutVolume(Fraction.HEADS) < 0.001, "heads receiver was not emptied");
        require(run.cutVolume(Fraction.HEARTS) < 0.001, "hearts receiver was not emptied");
        require(run.cutVolume(Fraction.TAILS) < 0.001, "tails receiver was not emptied");

        double total = heads.alcohol() + hearts.alcohol() + tails.alcohol();
        require(Math.abs(total - run.alcoholCollected()) < 0.01,
                "receiver cuts must contain exactly the alcohol that left the pot");
        return new StillResult(heads, hearts, tails, total);
    }

    /** Bottles every remaining drop, exercising the fractional-leftover path. */
    private static Batch collect(StillRun run, Fraction fraction) {
        double volume = 0;
        double alcohol = 0;
        double quality = 0;
        double impurities = 0;
        int passes = 0;
        String origin = "grain";
        int age = 0;
        while (run.cutVolume(fraction) > 0.0001) {
            double available = run.cutVolume(fraction);
            int amount = (int) Math.min(StillRun.BOTTLE_VOLUME, Math.ceil(available));
            Batch portion = run.drain(fraction, amount);
            require(portion != null, "could not drain " + fraction + " cut");
            require(portion.volume() > 0, "drained an empty portion");
            volume += portion.volume();
            alcohol += portion.alcohol();
            quality += portion.quality() * portion.volume();
            impurities += portion.impurities() * portion.volume();
            passes = Math.max(passes, portion.passes());
            origin = portion.origin();
            age = portion.age();
        }
        require(volume > 0, "fraction " + fraction + " was empty");
        return new Batch(volume, alcohol / volume * 100,
                (int) Math.round(quality / volume), (int) Math.round(impurities / volume),
                passes, false, origin, age);
    }

    private static void wholeRun() {
        Batch wash = Simulation.ferment(Simulation.mash("berry", 2), 1.0, 20.0);
        StillRun run = new StillRun(wash, false);
        int seconds = 0;
        while (!run.finished() && seconds < MAX_STILL_SECONDS) {
            run.tick(20, 0.9, true, true, 0, () -> 0.99);
            seconds++;
        }
        require(run.finished(), "whole still did not finish");
        require(run.cutVolume(Fraction.HEADS) < 0.001,
                "a whole run must not fill the heads receiver");
        require(run.cutVolume(Fraction.HEARTS) > 0, "a whole run must collect product");
        Batch product = run.drain(Fraction.HEARTS, StillRun.BOTTLE_VOLUME);
        require(product != null, "whole run produced no bottle");
        require(!product.cut(), "whole-run product must not claim fraction separation");
        require(product.passes() == 1, "whole-run product is still one distillation");
    }

    private static void drainEdgeCases() {
        Batch tiny = new Batch(60, 20, 60, 30, 0, false, "grain", 0);
        StillRun run = new StillRun(tiny);
        for (int i = 0; i < 200 && !run.finished(); i++) {
            run.tick(20, 1.0, true, true, 0, () -> 0.99);
        }
        double available = run.cutVolume(Fraction.HEARTS);
        require(available > 0, "tiny still produced no hearts");
        Batch last = run.drain(Fraction.HEARTS, StillRun.BOTTLE_VOLUME);
        require(last != null, "fractional drain returned null");
        require(Math.abs(last.volume() - available) < 0.01,
                "fractional drain must not lose the leftover");
        require(run.cutVolume(Fraction.HEARTS) < 0.001,
                "fractional drain must empty the receiver");
    }

    private static void print(String label, Batch wash, StillResult still, Batch finalBatch) {
        System.out.printf(Locale.ROOT,
                "%s chain: wash %.1f ml @ %.1f%% | heads %.1f ml | hearts %.1f ml @ %.1f%% | "
                        + "tails %.1f ml | final %.1f ml @ %.1f%% | quality %d | age %d%n",
                label, wash.volume(), wash.abv(),
                still.heads().volume(), still.hearts().volume(), still.hearts().abv(),
                still.tails().volume(), finalBatch.volume(), finalBatch.abv(),
                finalBatch.quality(), finalBatch.age());
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private record StillResult(Batch heads, Batch hearts, Batch tails, double totalCollectedAlcohol) {
    }

    private ProductionChainTest() {
    }
}
