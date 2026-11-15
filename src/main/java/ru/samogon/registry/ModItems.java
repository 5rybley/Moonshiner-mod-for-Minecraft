package ru.samogon.registry;

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import ru.samogon.SamogonMod;
import ru.samogon.item.BatchItem;
import ru.samogon.item.BrineItem;
import ru.samogon.item.GrapeSeedItem;
import ru.samogon.item.GuideItem;
import ru.samogon.item.HydrometerItem;
import ru.samogon.process.Brewing;
import ru.samogon.process.GrapeVariety;
import ru.samogon.process.Wine;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SamogonMod.MOD_ID);
    public static final DeferredItem<Item> YEAST = simple("yeast");
    public static final DeferredItem<Item> MALT = simple("malt");
    /** Dark-roasted malt: the grain charge of the dark beer style. */
    public static final DeferredItem<Item> ROASTED_MALT = simple("roasted_malt");
    /** Empty wooden mug: the only vessel beer is served in. */
    public static final DeferredItem<Item> WOODEN_MUG = simple("wooden_mug");
    public static final DeferredItem<Item> ACTIVATED_CHARCOAL = simple("activated_charcoal");
    public static final DeferredItem<Item> BOTANICAL_BUNDLE = simple("botanical_bundle");
    public static final DeferredItem<Item> OAK_CHIPS = simple("oak_chips");
    public static final DeferredItem<Item> MOLECULAR_SIEVE = simple("molecular_sieve");
    /** Hops: the bittering and aromatic charge of the brewery boil. */
    public static final DeferredItem<Item> HOPS = ITEMS.registerSimpleItem("hops",
            new Item.Properties().stacksTo(64));
    /** Hop seeds: plant on farmland to grow the hop bine, or find them in ferns. */
    public static final DeferredItem<Item> HOPS_SEEDS = ITEMS.registerItem("hops_seeds",
            props -> new ItemNameBlockItem(ModBlocks.HOPS.get(), props), new Item.Properties());
    public static final DeferredItem<Item> THERMOMETER = tool("thermometer");
    public static final DeferredItem<HydrometerItem> HYDROMETER = ITEMS.registerItem("hydrometer",
            HydrometerItem::new, new Item.Properties().stacksTo(1));
    public static final DeferredItem<GuideItem> GUIDE = ITEMS.registerItem("distillers_guide", GuideItem::new,
            new Item.Properties().stacksTo(1));
    /** The brewer's handbook: the same illustrated book, but for the brewery. */
    public static final DeferredItem<GuideItem> BREWERS_GUIDE = ITEMS.registerItem("brewers_guide", GuideItem::new,
            new Item.Properties().stacksTo(1));
    /** The winemaker's handbook: the same illustrated book, but for the winery. */
    public static final DeferredItem<GuideItem> WINEMAKERS_GUIDE = ITEMS.registerItem("winemakers_guide", GuideItem::new,
            new Item.Properties().stacksTo(1));
    public static final DeferredItem<BatchItem> SWEET_WORT = batch("sweet_wort", 0, false, 1000);
    public static final DeferredItem<BatchItem> FERMENTED_MASH = batch("fermented_mash", 12, true, 1000);
    public static final DeferredItem<BatchItem> HEADS_FRACTION = technical("heads_fraction", 70);
    public static final DeferredItem<BatchItem> HEARTS_FRACTION = batch("hearts_fraction", 55, true, 100);
    public static final DeferredItem<BatchItem> TAILS_FRACTION = technical("tails_fraction", 20);
    public static final DeferredItem<BatchItem> NEUTRAL_SPIRIT = technical("neutral_spirit", 96);
    public static final DeferredItem<BatchItem> ANHYDROUS_SPIRIT = technical("anhydrous_spirit", 99.5);
    public static final DeferredItem<BatchItem> FILTERED_SPIRIT = batch("filtered_spirit", 55, true, 100);
    public static final DeferredItem<BatchItem> DILUTED_SPIRIT = batch("diluted_spirit", 40, true, 100);
    public static final DeferredItem<BatchItem> BERRY_TINCTURE =
            tincture("berry_tincture", 40, BatchItem.Tincture.BERRY);
    public static final DeferredItem<BatchItem> HONEY_TINCTURE =
            tincture("honey_tincture", 40, BatchItem.Tincture.HONEY);
    public static final DeferredItem<BatchItem> BOTANICAL_TINCTURE =
            tincture("botanical_tincture", 40, BatchItem.Tincture.BOTANICAL);
    public static final DeferredItem<BatchItem> SPICED_TINCTURE =
            tincture("spiced_tincture", 40, BatchItem.Tincture.SPICE);
    public static final DeferredItem<BatchItem> FRUIT_TINCTURE =
            tincture("fruit_tincture", 40, BatchItem.Tincture.FRUIT);
    public static final DeferredItem<BatchItem> MARINE_TINCTURE =
            tincture("marine_tincture", 40, BatchItem.Tincture.MARINE);
    public static final DeferredItem<BatchItem> OAK_AGED_SPIRIT =
            tincture("oak_aged_spirit", 40, BatchItem.Tincture.OAK);
    /** A glass of brine: the morning-after cure. */
    public static final DeferredItem<BrineItem> BRINE =
            ITEMS.registerItem("brine", BrineItem::new, new Item.Properties().stacksTo(1));
    /** One bottle type whose name, composition, quality and aroma are generated by the blend. */
    public static final DeferredItem<BatchItem> SAMOGON =
            batch("artisan_samogon", 40, true, 100);

    // Brewery intermediates and the finished beer. Their stage is encoded in
    // the batch origin (Brewing.WORT_ORIGIN / HOPPED_ORIGIN / BEER_ORIGIN).
    public static final DeferredItem<BatchItem> BEER_WORT =
            batch("beer_wort", 0, false, Brewing.WORT_VOLUME);
    public static final DeferredItem<BatchItem> HOPPED_WORT =
            batch("hopped_wort", 0, false, Brewing.WORT_VOLUME);
    /** Finished beer, served in a wooden mug; drinking returns the empty mug. */
    public static final DeferredItem<BatchItem> BEER =
            ITEMS.registerItem("beer", props -> new BatchItem(props, Brewing.BEER_ABV, true, 500)
                    .withContainer(WOODEN_MUG::get), new Item.Properties().stacksTo(1));

    // Winery chain. Four grape varieties are grown in the vineyard; the press
    // turns them into must, the barrel ferments the must and the bottle rack
    // makes sparkling wine.
    /** Cabernet: warm-climate red, the must macerates into a full-bodied wine. */
    public static final DeferredItem<Item> CABERNET_GRAPES = grapes("cabernet_grapes");
    /** Merlot: softer red that ferments a little cooler. */
    public static final DeferredItem<Item> MERLOT_GRAPES = grapes("merlot_grapes");
    /** Chardonnay: cool-climate white, pressed straight into crisp must. */
    public static final DeferredItem<Item> CHARDONNAY_GRAPES = grapes("chardonnay_grapes");
    /** Riesling: the coolest white, light and high in acid. */
    public static final DeferredItem<Item> RIESLING_GRAPES = grapes("riesling_grapes");
    /** One seed per variety: plant on farmland to grow the matching vine. */
    public static final DeferredItem<Item> CABERNET_SEEDS = seeds(GrapeVariety.CABERNET);
    public static final DeferredItem<Item> MERLOT_SEEDS = seeds(GrapeVariety.MERLOT);
    public static final DeferredItem<Item> CHARDONNAY_SEEDS = seeds(GrapeVariety.CHARDONNAY);
    public static final DeferredItem<Item> RIESLING_SEEDS = seeds(GrapeVariety.RIESLING);
    /**
     * Legacy colour-only grapes from before the vineyard. They are hidden from
     * the creative tab but still charge the press as the default variety of
     * their colour, so old worlds and old guide links keep working.
     */
    public static final DeferredItem<Item> RED_GRAPES = grapes("red_grapes");
    /** Legacy white grapes, pressed as Chardonnay. */
    public static final DeferredItem<Item> WHITE_GRAPES = grapes("white_grapes");
    /** Fresh grape must, the charge of the fermentation barrel. */
    public static final DeferredItem<BatchItem> GRAPE_MUST =
            batch("grape_must", 0, false, Wine.MUST_VOLUME);
    /** Dry still wine, bottled by the press, the barrel or the aging cask. */
    public static final DeferredItem<BatchItem> WINE =
            batch("wine", Wine.Color.RED.baseAbv(), true, Wine.BOTTLE_VOLUME);
    /** Sparkling wine after the second fermentation and dosage. */
    public static final DeferredItem<BatchItem> SPARKLING_WINE =
            batch("sparkling_wine", Wine.Color.WHITE.baseAbv() + 1.0, true, Wine.BOTTLE_VOLUME);

    private static DeferredItem<Item> grapes(String id) {
        return ITEMS.registerSimpleItem(id, new Item.Properties().stacksTo(64));
    }

    private static DeferredItem<Item> seeds(GrapeVariety variety) {
        return ITEMS.registerItem(variety.id() + "_seeds",
                props -> new GrapeSeedItem(ModBlocks.GRAPE_VINE.get(), props, variety),
                new Item.Properties());
    }

    /** Grape the stack is, or null when it is not a bunch of grapes. */
    public static GrapeVariety varietyOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        if (stack.is(CABERNET_GRAPES.get()) || stack.is(RED_GRAPES.get())) return GrapeVariety.CABERNET;
        if (stack.is(MERLOT_GRAPES.get())) return GrapeVariety.MERLOT;
        if (stack.is(CHARDONNAY_GRAPES.get()) || stack.is(WHITE_GRAPES.get())) return GrapeVariety.CHARDONNAY;
        if (stack.is(RIESLING_GRAPES.get())) return GrapeVariety.RIESLING;
        return null;
    }

    public static boolean isGrapes(ItemStack stack) {
        return varietyOf(stack) != null;
    }

    /** The item a full vine of this variety drops. */
    public static Item grapeItem(GrapeVariety variety) {
        return switch (variety) {
            case CABERNET -> CABERNET_GRAPES.get();
            case MERLOT -> MERLOT_GRAPES.get();
            case CHARDONNAY -> CHARDONNAY_GRAPES.get();
            case RIESLING -> RIESLING_GRAPES.get();
        };
    }

    /** The seed that plants this variety's vine. */
    public static Item seedFor(GrapeVariety variety) {
        return switch (variety) {
            case CABERNET -> CABERNET_SEEDS.get();
            case MERLOT -> MERLOT_SEEDS.get();
            case CHARDONNAY -> CHARDONNAY_SEEDS.get();
            case RIESLING -> RIESLING_SEEDS.get();
        };
    }

    private static DeferredItem<Item> simple(String id) {
        return ITEMS.registerSimpleItem(id, new Item.Properties().stacksTo(16));
    }

    private static DeferredItem<Item> tool(String id) {
        return ITEMS.registerSimpleItem(id, new Item.Properties().stacksTo(1));
    }

    private static DeferredItem<BatchItem> batch(String id, double strength, boolean drinkable, double volume) {
        return ITEMS.registerItem(id, props -> new BatchItem(props, strength, drinkable, volume),
                new Item.Properties().stacksTo(1));
    }

    private static DeferredItem<BatchItem> tincture(String id, double strength, BatchItem.Tincture tincture) {
        return ITEMS.registerItem(id, props -> new BatchItem(props, strength, true, 100, tincture),
                new Item.Properties().stacksTo(1));
    }

    private static DeferredItem<BatchItem> technical(String id, double strength) {
        return ITEMS.registerItem(id,
                props -> new BatchItem(props, strength, false, 100, BatchItem.Tincture.NONE, true),
                new Item.Properties().stacksTo(1));
    }

    public static void addToTab(CreativeModeTab.Output output) {
        output.accept(GUIDE.get());
        output.accept(BREWERS_GUIDE.get());
        output.accept(WINEMAKERS_GUIDE.get());
        ModBlocks.BLOCK_ITEMS.getEntries().forEach(item -> output.accept(item.get()));
        // The legacy colour-only grapes stay registered for old worlds but are
        // hidden from the creative tab: the vineyard's four varieties replace
        // them, and their seeds are the way to get grapes now.
        ITEMS.getEntries().stream()
                .filter(item -> item != GUIDE && item != BREWERS_GUIDE && item != WINEMAKERS_GUIDE
                        && item != RED_GRAPES && item != WHITE_GRAPES)
                .forEach(item -> output.accept(item.get()));
    }

    private ModItems() {}
}
