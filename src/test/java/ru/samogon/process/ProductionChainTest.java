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
        wineChain();
        varietyChain();
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

        // A blended brew keeps its origin; the mug's name comes from the origin, so
        // the blend composition never overrides the beer style.
        Batch blendedBeer = new Batch(beer.volume() * 0.99, beer.abv(), beer.quality(),
                beer.impurities(), beer.passes(), beer.filtered(), beer.origin(), beer.age() + 1,
                "apple,apple", beer.rectified(), beer.oak(), beer.cut(), beer.columnRuns());
        require(beer.origin().equals(blendedBeer.origin()),
                "blending must keep the beer origin, found " + blendedBeer.origin());
        require(BeerStyle.of(blendedBeer.origin()) == BeerStyle.LIGHT,
                "a blended brew must keep its beer style");
        require(SpiritType.of(blendedBeer) == SpiritType.BEER,
                "a blended brew must still classify as beer");

        Batch wash = Simulation.mash("grain", 4);
        require(!Brewing.isBrew(wash), "ordinary mash must not be a brew");
        require(SpiritType.of(wash) != SpiritType.BEER, "ordinary mash must not be beer");

        // Kvass: rusks and water steep into a mild wort, which the beer barrel
        // ferments into a drink of about one percent alcohol.
        Batch kvassWort = Brewing.kvassWort();
        require(Brewing.isBrew(kvassWort), "kvass wort belongs to the brewery chain");
        require(Brewing.stageOf(kvassWort) == Brewing.STAGE_KVASS_WORT, "kvass wort stage");
        require(SpiritType.of(kvassWort) != SpiritType.KVASS,
                "unfermented kvass wort is not a drink yet");
        Batch kvass = Brewing.fermentKvass(kvassWort, 1.0, 22.0);
        require(Brewing.isFinishedKvass(kvass), "fermented kvass wort must give kvass");
        require(SpiritType.of(kvass) == SpiritType.KVASS, "kvass must classify as kvass");
        require(kvass.abv() > 0.5 && kvass.abv() < 2.5, "kvass strength must stay mild");
        System.out.printf(Locale.ROOT,
                "kvass chain: wort %.0f ml | kvass %.0f ml @ %.1f%% | quality %d%n",
                kvassWort.volume(), kvass.volume(), kvass.abv(), kvass.quality());

        System.out.printf(Locale.ROOT,
                "brewery chain: wort %.0f ml | hopped %.0f ml | beer %.0f ml @ %.1f%% | quality %d"
                        + " | styles %s %s %s%n",
                wort.volume(), hopped.volume(), beer.volume(), beer.abv(), beer.quality(),
                BeerStyle.of(beer.origin()), BeerStyle.of(wheat.origin()), BeerStyle.of(honey.origin()));
    }

    /**
     * The winery chain: press grapes for must, ferment to dry wine, round it
     * out in the cask and finish it by the traditional method. It must stay
     * outside the distillation taxonomy until it is actually distilled, and
     * then the raw material must yield brandy or cognac.
     */
    private static void wineChain() {
        // The press gives must, and the colour travels in the origin.
        Batch redMust = Wine.must(Wine.Color.RED);
        require(Wine.isMust(redMust), "press must give must");
        require(Wine.isGrape(redMust), "must belongs to the winery chain");
        require(SpiritType.of(redMust) == SpiritType.MUST, "must is an ingredient, not a drink");
        require(Wine.colorOf(redMust.origin()) == Wine.Color.RED, "must must keep its colour");

        // Red grapes pressed straight off the skins give a rose must.
        Batch roseMust = Wine.must(Wine.Color.ROSE);
        require(Wine.colorOf(roseMust.origin()) == Wine.Color.ROSE, "rose must must keep its colour");

        // Primary fermentation: red ferments warm, white cool.
        Batch red = Wine.ferment(redMust, Wine.Color.RED.idealTemperature());
        require(Wine.isWine(red), "fermented must is still wine");
        require(!Wine.isMust(red), "wine is no longer must");
        require(SpiritType.of(red) == SpiritType.WINE, "wine must classify as wine");
        require(red.abv() > 10.0 && red.abv() < 15.0, "red wine strength");
        require(red.volume() < redMust.volume(), "fermentation must lose volume");

        Batch white = Wine.ferment(Wine.must(Wine.Color.WHITE), Wine.Color.WHITE.idealTemperature());
        require(Wine.colorOf(white.origin()) == Wine.Color.WHITE, "white wine must keep its colour");

        // A stray cellar temperature costs quality.
        Batch warmWhite = Wine.ferment(Wine.must(Wine.Color.WHITE), 34.0);
        require(warmWhite.quality() < white.quality(), "a warm cellar must cost quality");

        // Cask aging rounds out the wine; it is the winery's only aging path.
        require(Simulation.canAge(red.abv()), "wine must be strong enough for the cask");
        Batch aged = Wine.age(red);
        require(Wine.isWine(aged), "aged wine stays wine");
        require(aged.age() == red.age() + 1, "aging must advance the cycle");
        require(aged.quality() > red.quality(), "the cask must improve the wine");
        require(aged.volume() < red.volume(), "the cask takes an angel's share");

        // The traditional method: tirage, second fermentation, dosage.
        require(Wine.isStillWine(aged), "aged wine can be charged into the rack");
        Batch sparkling = Wine.tirage(aged, Wine.Dosage.BRUT);
        require(Wine.isSparkling(sparkling), "tirage must give sparkling wine");
        require(!Wine.isWine(sparkling), "sparkling wine is no longer still wine");
        require(Wine.isGrape(sparkling), "sparkling wine belongs to the winery chain");
        require(SpiritType.of(sparkling) == SpiritType.SPARKLING_WINE,
                "sparkling wine must classify as sparkling wine");
        require(Wine.dosageOf(sparkling.origin()) == Wine.Dosage.BRUT, "the dosage must be recorded");
        require(Wine.colorOf(sparkling.origin()) == Wine.Color.RED, "the colour must survive the tirage");
        require(sparkling.abv() > aged.abv(), "the second fermentation adds a little alcohol");

        // The dosage changes the finished wine; a drier charge scores higher.
        Batch nature = Wine.tirage(aged, Wine.Dosage.BRUT_NATURE);
        Batch doux = Wine.tirage(aged, Wine.Dosage.DOUX);
        require(nature.quality() > doux.quality(), "the dosage must change the wine");

        // Cask treatments: oak and the four sugar bands.
        Batch oaked = Wine.oak(red);
        require(Wine.stillStyleOf(oaked) == WineStyle.OAKED, "oak chips must mark the wine oaked");
        require(oaked.origin().endsWith(":oaked"), "the oaked style must be in the origin");
        require(oaked.quality() > red.quality(), "oak must improve the wine");
        Batch offDry = Wine.sweeten(red, 1);
        Batch semiSweet = Wine.sweeten(red, 2);
        Batch sweet = Wine.sweeten(red, 5);
        Batch dessert = Wine.sweeten(red, 8);
        require(Wine.stillStyleOf(offDry) == WineStyle.OFF_DRY, "one sugar is off-dry");
        require(Wine.stillStyleOf(semiSweet) == WineStyle.SEMI_SWEET, "two sugars are semi-sweet");
        require(Wine.stillStyleOf(sweet) == WineStyle.SWEET, "five sugars are sweet");
        require(Wine.stillStyleOf(dessert) == WineStyle.DESSERT, "eight sugars are dessert");
        require(Math.abs(offDry.abv() - red.abv()) < 1.0E-9,
                "sugar added after fermentation must not change the strength");

        // The blending table aromatizes wine and fortifies it with spirit.
        Batch vermouth = Wine.aromatize(red, WineStyle.VERMOUTH);
        require(Wine.stillStyleOf(vermouth) == WineStyle.VERMOUTH,
                "botanicals must make vermouth");
        Batch mulled = Wine.aromatize(red, WineStyle.MULLED);
        require(Wine.stillStyleOf(mulled) == WineStyle.MULLED, "spices must mull the wine");

        // The blend's dominant aroma family picks the style, and the style must
        // survive into the origin. This is what names the bottle: berries make
        // sangria, not a plain dry wine. The blending table only fills the
        // composition and keeps the origin, so the batch below mirrors it.
        String berries = "sweet_berries,sweet_berries,sweet_berries,sweet_berries,sweet_berries";
        Batch berryBlend = new Batch(red.volume() * 0.98, red.abv(), red.quality(),
                red.impurities(), red.passes(), red.filtered(), red.origin(), red.age() + 1,
                berries, red.rectified(), red.oak(), red.cut(), red.columnRuns());
        WineStyle berryAroma = WineStyle.forComposition(berryBlend.composition());
        require(berryAroma == WineStyle.SANGRIA, "berries must map to sangria, found " + berryAroma);
        Batch sangria = Wine.aromatize(berryBlend, berryAroma);
        require(Wine.stillStyleOf(sangria) == WineStyle.SANGRIA,
                "the blended bottle must record the sangria style");
        require(sangria.origin().endsWith(":sangria"),
                "sangria must be in the origin, found " + sangria.origin());
        require(sangria.origin().startsWith("wine:red:cabernet"),
                "sangria must keep colour and variety, found " + sangria.origin());
        require(Wine.stillStyleOf("wine:white:chardonnay") == WineStyle.DRY,
                "an unblended wine stays dry");
        // Bottles blended before the style segment existed carry only the
        // composition. The name must still recover the style, otherwise the
        // blend looks like a plain dry wine (the bug reported in game).
        require(Wine.displayStyle(berryBlend) == WineStyle.SANGRIA,
                "a composition-only wine must be named by its blend");
        Batch legacyBlend = new Batch(750, 12, 70, 12, 0, false, "wine:white:chardonnay", 2, berries);
        require(Wine.displayStyle(legacyBlend) == WineStyle.SANGRIA,
                "a legacy blended bottle must recover its sangria style");
        require(Wine.displayStyle(red) == WineStyle.DRY,
                "a wine without a composition stays dry");
        require(WineStyle.forComposition("botanical_bundle") == WineStyle.VERMOUTH,
                "botanicals must map to vermouth");
        require(WineStyle.forComposition("cocoa_beans") == WineStyle.MULLED,
                "spices must map to mulled wine");
        require(WineStyle.forComposition("honey_bottle") == WineStyle.HONEYED,
                "honey must map to honeyed wine");
        require(WineStyle.forComposition("apple") == WineStyle.SANGRIA,
                "fruit must map to sangria");
        require(WineStyle.forComposition("sugar") == null,
                "sugar alone must not aromatize wine");

        Batch spirit = new Batch(100, 96, 70, 5, 1, false, "grain", 0);
        Batch port = Wine.fortify(red, spirit);
        require(Wine.stillStyleOf(port) == WineStyle.PORT, "fortified wine is port");
        require(Math.abs(port.volume() - (red.volume() + spirit.volume())) < 1.0E-9,
                "fortification must use the whole spirit charge instead of discarding it");
        double expectedPortAbv = (red.volume() * red.abv() + spirit.volume() * spirit.abv())
                / (red.volume() + spirit.volume());
        require(Math.abs(port.abv() - Math.min(expectedPortAbv, 22.0)) < 1.0E-9,
                "port strength must follow the mixture and only be capped at 22 %");
        require(port.abv() > red.abv() && port.abv() <= 22.0,
                "port must be stronger than the wine and capped at 22 %");
        Batch sherry = Wine.fortify(oaked, spirit);
        require(Wine.stillStyleOf(sherry) == WineStyle.SHERRY,
                "oaked wine fortified with spirit is sherry");

        // Fortification is terminal. Berries must not turn a port into sangria
        // and spirit must not erase an aromatized wine: the table refuses both.
        require(!Wine.canAromatize(port), "a port must not be aromatized");
        require(!Wine.canAromatize(sherry), "a sherry must not be aromatized");
        require(Wine.canAromatize(red) && Wine.canAromatize(oaked),
                "a plain or oaked wine can be aromatized");
        require(!Wine.canFortify(port) && !Wine.canFortify(sherry),
                "an already fortified wine must not be fortified again");
        require(!Wine.canFortify(vermouth), "an aromatized wine must not be fortified");
        require(Wine.canFortify(red) && Wine.canFortify(oaked) && Wine.canFortify(sweet),
                "plain, oaked and sweet wines can be fortified");
        require(Wine.stillStyleOf(Wine.aromatize(port, WineStyle.SANGRIA)) == WineStyle.PORT,
                "aromatizing a port must leave the port style alone");

        // A cuvée marries two still wines. The colour follows the pair, both
        // grape varieties are kept (the partner in the last origin segment)
        // and the numbers are volume-weighted.
        Batch cuvee = Wine.cuvee(red, white);
        require(Wine.isWine(cuvee), "a cuvee stays still wine");
        require(Wine.stillStyleOf(cuvee) == WineStyle.CUVEE,
                "a cuvee must record its style in the origin");
        require(Wine.colorOf(cuvee.origin()) == Wine.Color.ROSE,
                "red and white wine must give a rose cuvee");
        require(Wine.varietyOf(cuvee.origin()) == GrapeVariety.CABERNET
                        && Wine.cuveePartner(cuvee.origin()) == GrapeVariety.CHARDONNAY,
                "a cuvee must keep both grape varieties, found " + cuvee.origin());
        require(cuvee.composition().isBlank(),
                "a cuvee is named by its varieties, not by an aromatic composition");
        require(cuvee.volume() < red.volume() + white.volume()
                        && cuvee.volume() > (red.volume() + white.volume()) * 0.98,
                "a cuvee must keep both volumes, minus a splash");
        require(cuvee.quality() > Math.min(red.quality(), white.quality()),
                "a cuvee must be at least as good as its weaker half");
        require(Wine.cuveeColor(Wine.Color.ROSE, Wine.Color.RED) == Wine.Color.RED,
                "rose with red must give red");
        require(Wine.cuveeColor(Wine.Color.ROSE, Wine.Color.WHITE) == Wine.Color.WHITE,
                "rose with white must give white");
        require(Wine.cuveeColor(Wine.Color.RED, Wine.Color.RED) == Wine.Color.RED,
                "two wines of one colour keep that colour");
        require(Wine.canFortify(cuvee) && Wine.canAromatize(cuvee),
                "a cuvee can still be oaked, sweetened, aromatized or fortified");
        Batch cuveeOaked = Wine.oak(cuvee);
        require(Wine.stillStyleOf(cuveeOaked) == WineStyle.OAKED,
                "oaking must restyle the cuvee");
        require(Wine.cuveePartner(cuveeOaked.origin()) == GrapeVariety.CHARDONNAY,
                "oaking must keep the cuvee partner, found " + cuveeOaked.origin());
        require(Wine.cuveePartner(red.origin()) == null,
                "a plain wine has no cuvee partner");
        require(Wine.cuveePartner("sparkling_wine:white:riesling:classic:brut") == null,
                "the dosage of a sparkling wine is not a cuvee partner");

        // Sparkling methods follow the base wine.
        Batch chardonnaySparkling = Wine.tirage(Wine.ferment(
                Wine.must(Wine.Color.WHITE, GrapeVariety.CHARDONNAY),
                GrapeVariety.CHARDONNAY.idealTemperature()), Wine.Dosage.BRUT);
        require(Wine.sparklingStyleOf(chardonnaySparkling.origin()) == SparklingStyle.BLANC_DE_BLANCS,
                "Chardonnay must make a blanc de blancs");
        Batch roseSparkling = Wine.tirage(Wine.ferment(
                Wine.must(Wine.Color.ROSE, GrapeVariety.MERLOT),
                GrapeVariety.MERLOT.idealTemperature()), Wine.Dosage.BRUT);
        require(Wine.sparklingStyleOf(roseSparkling.origin()) == SparklingStyle.ROSE,
                "a rosé base must make rosé sparkling wine");
        require(Wine.sparklingStyleOf(sparkling.origin()) == SparklingStyle.RED,
                "a red base must make sparkling red");
        require(Wine.sparklingStyleOf("sparkling_wine:red:brut") == SparklingStyle.RED,
                "a legacy red sparkling origin falls back to the red method");

        // Distilled grape wine becomes brandy, and oak-aged twice-distilled
        // wine becomes cognac, even though the origin keeps its colour.
        Batch once = new Batch(1000, 30, 70, 8, 1, false, "wine:red", 0);
        require(SpiritType.of(once) == SpiritType.BRANDY, "pot-stilled wine must be brandy");
        Batch doubleDistilled = new Batch(900, 45, 80, 2, 2, false, "wine:red", 3, "", false, 2, true, 0);
        require(SpiritType.of(doubleDistilled) == SpiritType.COGNAC,
                "oak-aged double-distilled wine must be cognac");

        System.out.printf(Locale.ROOT,
                "wine chain: must %.0f ml | red %.0f ml @ %.1f%% q%d | aged %.0f ml q%d"
                        + " | sparkling %.0f ml @ %.1f%% (%s)%n",
                redMust.volume(), red.volume(), red.abv(), red.quality(), aged.volume(), aged.quality(),
                sparkling.volume(), sparkling.abv(), Wine.dosageOf(sparkling.origin()));
    }

    /**
     * The four grape varieties. The variety rides in the origin between the
     * colour and (for sparkling wine) the dosage, and it carries the numbers
     * the winery simulates: must quality, cellar temperature and dry strength.
     */
    private static void varietyChain() {
        // The colour ids still resolve to the default variety, so the older
        // wine chain and its tests keep working.
        require(Wine.Color.RED.idealTemperature() == GrapeVariety.CABERNET.idealTemperature(),
                "red must default to Cabernet");
        require(Wine.Color.WHITE.idealTemperature() == GrapeVariety.CHARDONNAY.idealTemperature(),
                "white must default to Chardonnay");
        require(Wine.varietyOf("grape_must:red") == GrapeVariety.CABERNET,
                "a legacy red must is Cabernet");
        require(Wine.varietyOf("grape_must:white") == GrapeVariety.CHARDONNAY,
                "a legacy white must is Chardonnay");
        require(Wine.varietyOf("wine:rose") == GrapeVariety.CABERNET,
                "a legacy rose falls back to the red default");

        // A named variety survives the press and the primary fermentation.
        Batch merlotMust = Wine.must(Wine.Color.RED, GrapeVariety.MERLOT);
        require("grape_must:red:merlot".equals(merlotMust.origin()),
                "the must origin must carry the variety, found " + merlotMust.origin());
        require(Wine.varietyOf(merlotMust.origin()) == GrapeVariety.MERLOT,
                "the pressed must must read its variety back");
        require(merlotMust.quality() == GrapeVariety.MERLOT.mustQuality(),
                "must quality comes from the variety");

        // A red grape pressed without skin contact keeps its variety in a rose.
        Batch merlotRose = Wine.must(Wine.Color.ROSE, GrapeVariety.MERLOT);
        require(Wine.colorOf(merlotRose.origin()) == Wine.Color.ROSE, "rose keeps its colour");
        require(Wine.varietyOf(merlotRose.origin()) == GrapeVariety.MERLOT,
                "rose keeps the grape that made it");

        Batch merlot = Wine.ferment(merlotMust, GrapeVariety.MERLOT.idealTemperature());
        require(Wine.varietyOf(merlot.origin()) == GrapeVariety.MERLOT,
                "fermentation must keep the variety");
        require(merlot.abv() > 12.0 && merlot.abv() < 13.0,
                "Merlot at its ideal cellar must reach about 12.5%, found " + merlot.abv());

        // Cabernet and Merlot are both red, but they ferment at different
        // temperatures, so the same cellar favours one of them.
        Batch cabernet = Wine.ferment(Wine.must(Wine.Color.RED, GrapeVariety.CABERNET),
                GrapeVariety.CABERNET.idealTemperature());
        require(cabernet.abv() > merlot.abv(),
                "Cabernet must reach a higher strength than Merlot at their own temperatures");
        Batch merlotOffIdeal = Wine.ferment(Wine.must(Wine.Color.RED, GrapeVariety.MERLOT),
                GrapeVariety.CABERNET.idealTemperature());
        require(merlotOffIdeal.abv() < merlot.abv(), "off-ideal heat must cost Merlot strength");
        require(merlotOffIdeal.quality() < merlot.quality(), "off-ideal heat must cost Merlot quality");

        // The whites follow their own numbers, coolest for Riesling.
        Batch riesling = Wine.ferment(Wine.must(Wine.Color.WHITE, GrapeVariety.RIESLING),
                GrapeVariety.RIESLING.idealTemperature());
        require(Wine.varietyOf(riesling.origin()) == GrapeVariety.RIESLING,
                "a white wine must keep its variety");
        require(riesling.abv() < 12.0, "Riesling must be a lighter wine than Chardonnay");
        Batch chardonnay = Wine.ferment(Wine.must(Wine.Color.WHITE, GrapeVariety.CHARDONNAY),
                GrapeVariety.CHARDONNAY.idealTemperature());
        require(chardonnay.abv() > riesling.abv(), "Chardonnay must be the stronger white");

        // Sparkling wine records both the variety and the dosage: the variety
        // is the middle segment, the dosage the last one.
        Batch sparkling = Wine.tirage(riesling, Wine.Dosage.BRUT);
        require("sparkling_wine:white:riesling:classic:brut".equals(sparkling.origin()),
                "the sparkling origin must carry the grape, method and dosage, found "
                        + sparkling.origin());
        require(Wine.varietyOf(sparkling.origin()) == GrapeVariety.RIESLING,
                "sparkling wine must remember its grape");
        require(Wine.dosageOf(sparkling.origin()) == Wine.Dosage.BRUT,
                "the dosage must still be the last segment");

        // An origin written before the vineyard has no variety segment; the
        // dosage moves into slot two and is not mistaken for a grape.
        require(Wine.varietyOf("sparkling_wine:red:brut") == GrapeVariety.CABERNET,
                "a legacy dosage in slot two is not a variety");
        require(Wine.dosageOf("sparkling_wine:red:brut") == Wine.Dosage.BRUT,
                "the legacy sparkling origin keeps its dosage");
        require(Wine.varietyOf("sparkling_wine:white:extra_dry") == GrapeVariety.CHARDONNAY,
                "a legacy white dosage falls back to Chardonnay");

        // Parsing is strict; the lenient lookup only exists for old data.
        require(GrapeVariety.parse("merlot") == GrapeVariety.MERLOT, "parse finds a known grape");
        require(GrapeVariety.parse("MERLOT") == GrapeVariety.MERLOT, "parse ignores case");
        require(GrapeVariety.parse("brut") == null, "parse rejects a non-grape word");
        require(GrapeVariety.of("brut") == GrapeVariety.CABERNET,
                "an unknown grape falls back to Cabernet like an old malformed origin");
        require(GrapeVariety.defaultFor(Wine.Color.WHITE) == GrapeVariety.CHARDONNAY,
                "the white default is Chardonnay");

        System.out.printf(Locale.ROOT,
                "varieties: cabernet %.1f%% q%d | merlot %.1f%% q%d | chardonnay %.1f%% | riesling %.1f%%"
                        + " | sparkling %s%n",
                cabernet.abv(), cabernet.quality(), merlot.abv(), merlot.quality(),
                chardonnay.abv(), riesling.abv(), sparkling.origin());
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
