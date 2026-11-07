package ru.samogon.process;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import ru.samogon.process.AromaFamily.Family;
import ru.samogon.process.Simulation.Batch;
import ru.samogon.registry.ModItems;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Ingredients accepted by the five-slot blending table.
 *
 * <p>The table is deliberately data driven: adding a vanilla item here is
 * enough to make it a valid aromatic additive. The bottle's composition is
 * stored as a sorted comma-separated list, so the same set of ingredients
 * always produces the same drink name and the same quality even if the player
 * fills the five slots in a different order.</p>
 */
public final class BlendCatalog {
    public static final int MAX_ADDITIVES = 5;

    /**
     * One kitchen ingredient. Quality and impurity values are intentionally
     * small; five simultaneous additives should feel like a crafted recipe,
     * not an instant flawless spirit.
     */
    public record Additive(String id, Item item, Family family, int quality, int impurities) {
        public Item container() {
            return item == Items.HONEY_BOTTLE ? Items.GLASS_BOTTLE : null;
        }
    }

    public static final List<Additive> ADDITIVES = List.of(
            new Additive("sweet_berries", Items.SWEET_BERRIES, Family.BERRY, 5, 2),
            new Additive("glow_berries", Items.GLOW_BERRIES, Family.BERRY, 4, 3),
            new Additive("honey_bottle", Items.HONEY_BOTTLE, Family.HONEY, 6, 1),
            new Additive("botanical_bundle", ModItems.BOTANICAL_BUNDLE.get(), Family.BOTANICAL, 4, 2),
            new Additive("beetroot", Items.BEETROOT, Family.ROOT, 3, 2),
            new Additive("potato", Items.POTATO, Family.ROOT, 2, 4),
            new Additive("carrot", Items.CARROT, Family.ROOT, 2, 1),
            new Additive("wheat_seeds", Items.WHEAT_SEEDS, Family.SEED, 1, 2),
            new Additive("pumpkin_seeds", Items.PUMPKIN_SEEDS, Family.SEED, 2, 1),
            new Additive("melon_seeds", Items.MELON_SEEDS, Family.SEED, 1, 1),
            new Additive("beetroot_seeds", Items.BEETROOT_SEEDS, Family.SEED, 1, 3),
            new Additive("sunflower", Items.SUNFLOWER, Family.BOTANICAL, 3, 0),
            new Additive("poppy", Items.POPPY, Family.BOTANICAL, 2, 1),
            new Additive("dandelion", Items.DANDELION, Family.BOTANICAL, 2, 1),
            new Additive("apple", Items.APPLE, Family.FRUIT, 4, 1),
            new Additive("cocoa_beans", Items.COCOA_BEANS, Family.SPICE, 3, 2),
            new Additive("nether_wart", Items.NETHER_WART, Family.SPICE, 2, 5),
            new Additive("brown_mushroom", Items.BROWN_MUSHROOM, Family.FUNGUS, 2, 4),
            new Additive("red_mushroom", Items.RED_MUSHROOM, Family.FUNGUS, 2, 4),
            new Additive("kelp", Items.KELP, Family.MARINE, 1, 2),
            new Additive("dried_kelp", Items.DRIED_KELP, Family.MARINE, 2, 2),
            new Additive("cactus", Items.CACTUS, Family.BOTANICAL, 2, 3),
            new Additive("sugar_cane", Items.SUGAR_CANE, Family.SWEET, 1, 2),
            new Additive("chorus_fruit", Items.CHORUS_FRUIT, Family.EXOTIC, 5, 6),
            new Additive("sugar", Items.SUGAR, Family.SWEET, 1, 3),
            new Additive("wheat", Items.WHEAT, Family.GRAIN, 2, 2),
            // 0.0.2: a broader kitchen, garden and sea pantry.
            new Additive("melon_slice", Items.MELON_SLICE, Family.FRUIT, 3, 1),
            new Additive("pumpkin", Items.PUMPKIN, Family.FRUIT, 2, 2),
            new Additive("golden_apple", Items.GOLDEN_APPLE, Family.FRUIT, 8, 0),
            new Additive("crimson_fungus", Items.CRIMSON_FUNGUS, Family.FUNGUS, 1, 5),
            new Additive("warped_fungus", Items.WARPED_FUNGUS, Family.FUNGUS, 1, 5),
            new Additive("vine", Items.VINE, Family.BOTANICAL, 1, 2),
            new Additive("fern", Items.FERN, Family.BOTANICAL, 2, 1),
            new Additive("large_fern", Items.LARGE_FERN, Family.BOTANICAL, 2, 1),
            new Additive("torchflower", Items.TORCHFLOWER, Family.BOTANICAL, 4, 0),
            new Additive("pitcher_plant", Items.PITCHER_PLANT, Family.BOTANICAL, 4, 0),
            new Additive("spore_blossom", Items.SPORE_BLOSSOM, Family.BOTANICAL, 3, 1),
            new Additive("lily_of_the_valley", Items.LILY_OF_THE_VALLEY, Family.BOTANICAL, 3, 0),
            new Additive("blue_orchid", Items.BLUE_ORCHID, Family.BOTANICAL, 3, 0),
            new Additive("sea_pickle", Items.SEA_PICKLE, Family.MARINE, 2, 2),
            new Additive("seagrass", Items.SEAGRASS, Family.MARINE, 1, 2),
            new Additive("nautilus_shell", Items.NAUTILUS_SHELL, Family.MARINE, 4, 2),
            new Additive("prismarine_crystals", Items.PRISMARINE_CRYSTALS, Family.MARINE, 3, 2),
            new Additive("ink_sac", Items.INK_SAC, Family.MARINE, 1, 3),
            new Additive("glow_ink_sac", Items.GLOW_INK_SAC, Family.MARINE, 2, 3),
            new Additive("honeycomb", Items.HONEYCOMB, Family.HONEY, 4, 1),
            new Additive("blaze_powder", Items.BLAZE_POWDER, Family.SPICE, 4, 5),
            new Additive("magma_cream", Items.MAGMA_CREAM, Family.SPICE, 3, 5),
            new Additive("bamboo", Items.BAMBOO, Family.GRAIN, 1, 2),
            new Additive("cookie", Items.COOKIE, Family.SWEET, 1, 3),
            new Additive("bread", Items.BREAD, Family.GRAIN, 2, 2)
    );

    private static final Map<String, Additive> BY_ID = ADDITIVES.stream()
            .collect(Collectors.toUnmodifiableMap(Additive::id, additive -> additive));

    private BlendCatalog() {
    }

    /** Returns the additive represented by a stack, or null for a non-additive. */
    public static Additive find(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        for (Additive additive : ADDITIVES) {
            if (stack.is(additive.item())) return additive;
        }
        return null;
    }

    public static Additive byId(String id) {
        return id == null ? null : BY_ID.get(id);
    }

    /** Reads one ingredient from each non-empty slot. */
    public static List<Additive> readSlots(List<ItemStack> stacks) {
        List<Additive> result = new ArrayList<>();
        for (ItemStack stack : stacks) {
            if (stack == null || stack.isEmpty()) continue;
            Additive additive = find(stack);
            if (additive != null) result.add(additive);
        }
        return result;
    }

    /**
     * Encodes a composition in canonical order. Duplicate ingredients are
     * preserved because they make a stronger, more concentrated infusion.
     */
    public static String encode(List<Additive> additives) {
        return additives.stream()
                .filter(Objects::nonNull)
                .map(Additive::id)
                .sorted()
                .collect(Collectors.joining(","));
    }

    public static List<Additive> decode(String composition) {
        if (composition == null || composition.isBlank()) return List.of();
        List<Additive> result = new ArrayList<>();
        for (String id : composition.split(",")) {
            Additive additive = byId(id.strip());
            if (additive != null) result.add(additive);
        }
        return List.copyOf(result);
    }

    public static Batch blend(Batch source, List<Additive> additives) {
        if (source == null) throw new IllegalArgumentException("A batch is required");
        if (additives == null || additives.isEmpty() || additives.size() > MAX_ADDITIVES) {
            throw new IllegalArgumentException("Use one to five additives");
        }

        int qualitySum = 0;
        int impuritySum = 0;
        for (Additive additive : additives) {
            qualitySum += additive.quality();
            impuritySum += additive.impurities();
        }
        int complexity = additives.size() - 1;
        int quality = Simulation.clamp(source.quality() + (int) Math.round(qualitySum * 0.75) + complexity);
        int impurities = Simulation.clamp(source.impurities()
                + (int) Math.round(impuritySum * 0.65) - complexity / 2);
        double volume = source.volume() * (1.0 - 0.004 * additives.size());
        return new Batch(volume, source.abv(), quality, impurities, source.passes(),
                source.filtered(), source.origin(), source.age() + 1, encode(additives),
                source.rectified(), source.oak(), source.cut(), source.columnRuns());
    }

    /** The family that contributes the strongest aromatic character. */
    public static Family dominant(String composition) {
        return AromaFamily.dominant(composition);
    }

    /**
     * A stable, composition-derived name. The adjective follows the dominant
     * family and the noun is chosen from the full sorted composition, giving
     * hundreds of distinct names without registering hundreds of items.
     */
    public static MutableComponent displayName(String composition) {
        Family family = dominant(composition);
        int hash = composition == null ? 0 : composition.hashCode();
        int noun = Math.floorMod(hash, NAME_NOUNS.length);
        return Component.translatable("item.samogon.artisan_samogon.named",
                Component.translatable("samogon.blend.adjective." + family.id()),
                Component.translatable("samogon.blend.noun." + NAME_NOUNS[noun]));
    }

    /**
     * The same composition-derived name, but with the drink type in front:
     * "Chacha «Sunset wind»". This lets a blended bottle show both what it is
     * and which recipe produced it.
     */
    public static MutableComponent displayName(String composition, Component typeName) {
        Family family = dominant(composition);
        int hash = composition == null ? 0 : composition.hashCode();
        int noun = Math.floorMod(hash, NAME_NOUNS.length);
        return Component.translatable("item.samogon.artisan_samogon.typed",
                typeName,
                Component.translatable("samogon.blend.adjective." + family.id()),
                Component.translatable("samogon.blend.noun." + NAME_NOUNS[noun]));
    }

    /**
     * Origin-aware name. The raw material is part of the hash, so the same set
     * of additives produces a different name from grain than from berries.
     * This is the name used by bottles created from 0.0.2 onward.
     */
    public static MutableComponent displayName(String origin, String composition) {
        Family family = dominant(composition);
        int noun = Math.floorMod(nameSeed(origin, composition), NAME_NOUNS.length);
        return Component.translatable("item.samogon.artisan_samogon.named",
                Component.translatable("samogon.blend.adjective." + family.id()),
                Component.translatable("samogon.blend.noun." + NAME_NOUNS[noun]));
    }

    /** Origin-aware name with the drink type in front. */
    public static MutableComponent displayName(String origin, String composition, Component typeName) {
        Family family = dominant(composition);
        int noun = Math.floorMod(nameSeed(origin, composition), NAME_NOUNS.length);
        return Component.translatable("item.samogon.artisan_samogon.typed",
                typeName,
                Component.translatable("samogon.blend.adjective." + family.id()),
                Component.translatable("samogon.blend.noun." + NAME_NOUNS[noun]));
    }

    private static int nameSeed(String origin, String composition) {
        return ((origin == null ? "" : origin) + "|" + (composition == null ? "" : composition)).hashCode();
    }

    /** Localized, comma-separated composition for tooltips. */
    public static Component describe(String composition) {
        List<Additive> additives = decode(composition);
        if (additives.isEmpty()) return Component.translatable("message.samogon.blend.none");
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Additive additive : additives) counts.merge(additive.id(), 1, Integer::sum);
        MutableComponent result = Component.empty();
        boolean first = true;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (!first) result.append(Component.literal(", "));
            result.append(Component.translatable("additive.samogon." + entry.getKey()));
            if (entry.getValue() > 1) result.append(Component.literal(" ×" + entry.getValue()));
            first = false;
        }
        return result;
    }

    /** Tincture aura chosen by the dominant family, or "none". */
    public static String aura(String composition) {
        return AromaFamily.aura(dominant(composition));
    }

    /**
     * Aura strengths for a composition. Every additive contributes one point to
     * its family's aura; duplicates stack and different families stay separate,
     * so five berries give a single level-V aura while a botanical, a berry and
     * a honey give three level-I auras.
     */
    public static Map<String, Integer> auraLevels(String composition) {
        return AromaFamily.auraLevels(composition);
    }

    private static final String[] NAME_NOUNS = {
            "sunset", "wind", "root", "hive", "velvet", "smoke", "run", "tale",
            "merchant", "ward", "frost", "amber", "mist", "reach", "brook", "ember"
    };
}
