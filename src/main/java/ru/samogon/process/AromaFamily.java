package ru.samogon.process;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure-Java aromatic classification of a blend composition.
 *
 * <p>This class is deliberately free of any Minecraft type so the drink-type
 * classifier can reuse it on a plain test classpath. {@link BlendCatalog}
 * keeps the item bindings; this class only knows the aromatic metadata
 * (family and weight) keyed by additive id.</p>
 *
 * <p>A blend can carry several auras at once. Every aromatic family belongs to
 * exactly one of the seven aura families, and each additive contributes one
 * point to its family's aura. Five berries therefore grant a single level-V
 * berry aura, while five different families grant five level-I auras.</p>
 */
public final class AromaFamily {
    public enum Family {
        BERRY("berry"),
        HONEY("honey"),
        BOTANICAL("botanical"),
        ROOT("root"),
        SEED("seed"),
        FRUIT("fruit"),
        SPICE("spice"),
        FUNGUS("fungus"),
        MARINE("marine"),
        EXOTIC("exotic"),
        SWEET("sweet"),
        GRAIN("grain");

        private final String id;

        Family(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }
    }

    /** The most auras one drink can carry: one per blending-table slot. */
    public static final int MAX_AURA_LEVEL = 5;

    /** Aura ids in the canonical order shared with the effect list and the drunk veil. */
    public static final List<String> AURAS = List.of(
            "berry", "honey", "botanical", "oak", "spice", "fruit", "marine");

    /** Aromatic weight per additive id: quality * 2 - impurities. */
    private static final Map<String, Integer> WEIGHTS = Map.ofEntries(
            Map.entry("sweet_berries", 8),
            Map.entry("glow_berries", 5),
            Map.entry("honey_bottle", 11),
            Map.entry("botanical_bundle", 6),
            Map.entry("beetroot", 4),
            Map.entry("potato", 0),
            Map.entry("carrot", 3),
            Map.entry("wheat_seeds", 0),
            Map.entry("pumpkin_seeds", 3),
            Map.entry("melon_seeds", 1),
            Map.entry("beetroot_seeds", -1),
            Map.entry("sunflower", 6),
            Map.entry("poppy", 3),
            Map.entry("dandelion", 3),
            Map.entry("apple", 7),
            Map.entry("cocoa_beans", 4),
            Map.entry("nether_wart", -1),
            Map.entry("brown_mushroom", 0),
            Map.entry("red_mushroom", 0),
            Map.entry("kelp", 0),
            Map.entry("dried_kelp", 2),
            Map.entry("cactus", 1),
            Map.entry("sugar_cane", 0),
            Map.entry("chorus_fruit", 4),
            Map.entry("sugar", -1),
            Map.entry("wheat", 2),
            // 0.0.2 additions: more kitchen, garden and sea ingredients.
            Map.entry("melon_slice", 5),
            Map.entry("pumpkin", 3),
            Map.entry("golden_apple", 12),
            Map.entry("crimson_fungus", 0),
            Map.entry("warped_fungus", 0),
            Map.entry("vine", 1),
            Map.entry("fern", 3),
            Map.entry("large_fern", 3),
            Map.entry("torchflower", 7),
            Map.entry("pitcher_plant", 7),
            Map.entry("spore_blossom", 5),
            Map.entry("lily_of_the_valley", 5),
            Map.entry("blue_orchid", 5),
            Map.entry("sea_pickle", 2),
            Map.entry("seagrass", 1),
            Map.entry("nautilus_shell", 6),
            Map.entry("prismarine_crystals", 4),
            Map.entry("ink_sac", 0),
            Map.entry("glow_ink_sac", 1),
            Map.entry("honeycomb", 7),
            Map.entry("blaze_powder", 3),
            Map.entry("magma_cream", 1),
            Map.entry("bamboo", 1),
            Map.entry("cookie", 1),
            Map.entry("bread", 2)
    );

    private static final Map<String, Family> FAMILIES = Map.ofEntries(
            Map.entry("sweet_berries", Family.BERRY),
            Map.entry("glow_berries", Family.BERRY),
            Map.entry("honey_bottle", Family.HONEY),
            Map.entry("botanical_bundle", Family.BOTANICAL),
            Map.entry("beetroot", Family.ROOT),
            Map.entry("potato", Family.ROOT),
            Map.entry("carrot", Family.ROOT),
            Map.entry("wheat_seeds", Family.SEED),
            Map.entry("pumpkin_seeds", Family.SEED),
            Map.entry("melon_seeds", Family.SEED),
            Map.entry("beetroot_seeds", Family.SEED),
            Map.entry("sunflower", Family.BOTANICAL),
            Map.entry("poppy", Family.BOTANICAL),
            Map.entry("dandelion", Family.BOTANICAL),
            Map.entry("apple", Family.FRUIT),
            Map.entry("cocoa_beans", Family.SPICE),
            Map.entry("nether_wart", Family.SPICE),
            Map.entry("brown_mushroom", Family.FUNGUS),
            Map.entry("red_mushroom", Family.FUNGUS),
            Map.entry("kelp", Family.MARINE),
            Map.entry("dried_kelp", Family.MARINE),
            Map.entry("cactus", Family.BOTANICAL),
            Map.entry("sugar_cane", Family.SWEET),
            Map.entry("chorus_fruit", Family.EXOTIC),
            Map.entry("sugar", Family.SWEET),
            Map.entry("wheat", Family.GRAIN),
            // 0.0.2 additions.
            Map.entry("melon_slice", Family.FRUIT),
            Map.entry("pumpkin", Family.FRUIT),
            Map.entry("golden_apple", Family.FRUIT),
            Map.entry("crimson_fungus", Family.FUNGUS),
            Map.entry("warped_fungus", Family.FUNGUS),
            Map.entry("vine", Family.BOTANICAL),
            Map.entry("fern", Family.BOTANICAL),
            Map.entry("large_fern", Family.BOTANICAL),
            Map.entry("torchflower", Family.BOTANICAL),
            Map.entry("pitcher_plant", Family.BOTANICAL),
            Map.entry("spore_blossom", Family.BOTANICAL),
            Map.entry("lily_of_the_valley", Family.BOTANICAL),
            Map.entry("blue_orchid", Family.BOTANICAL),
            Map.entry("sea_pickle", Family.MARINE),
            Map.entry("seagrass", Family.MARINE),
            Map.entry("nautilus_shell", Family.MARINE),
            Map.entry("prismarine_crystals", Family.MARINE),
            Map.entry("ink_sac", Family.MARINE),
            Map.entry("glow_ink_sac", Family.MARINE),
            Map.entry("honeycomb", Family.HONEY),
            Map.entry("blaze_powder", Family.SPICE),
            Map.entry("magma_cream", Family.SPICE),
            Map.entry("bamboo", Family.GRAIN),
            Map.entry("cookie", Family.SWEET),
            Map.entry("bread", Family.GRAIN)
    );

    /** The family that contributes the strongest aromatic character. */
    public static Family dominant(String composition) {
        if (composition == null || composition.isBlank()) return Family.SWEET;
        Map<Family, Integer> weights = new LinkedHashMap<>();
        for (String raw : composition.split(",")) {
            String id = raw.strip();
            if (id.isEmpty()) continue;
            Family family = FAMILIES.get(id);
            if (family == null) continue;
            weights.merge(family, WEIGHTS.getOrDefault(id, 0), Integer::sum);
        }
        if (weights.isEmpty()) return Family.SWEET;
        return weights.entrySet().stream()
                .max(Comparator.<Map.Entry<Family, Integer>>comparingInt(Map.Entry::getValue)
                        .thenComparing(entry -> entry.getKey().id()))
                .map(Map.Entry::getKey)
                .orElse(Family.SWEET);
    }

    /** True when the composition contains a specific additive id. */
    public static boolean has(String composition, String additiveId) {
        if (composition == null || composition.isBlank() || additiveId == null) return false;
        for (String raw : composition.split(",")) {
            if (raw.strip().equals(additiveId)) return true;
        }
        return false;
    }

    /** True when the composition contains any of the given additive ids. */
    public static boolean hasAnyOf(String composition, String... additiveIds) {
        if (additiveIds == null) return false;
        for (String id : additiveIds) {
            if (has(composition, id)) return true;
        }
        return false;
    }

    /** True when at least one additive of the family is present. */
    public static boolean hasFamily(String composition, Family family) {
        if (composition == null || composition.isBlank() || family == null) return false;
        for (String raw : composition.split(",")) {
            if (FAMILIES.get(raw.strip()) == family) return true;
        }
        return false;
    }

    /** Number of recognised aromatic additives in the composition. */
    public static int additiveCount(String composition) {
        if (composition == null || composition.isBlank()) return 0;
        int count = 0;
        for (String raw : composition.split(",")) {
            if (FAMILIES.containsKey(raw.strip())) count++;
        }
        return count;
    }

    /** The aura an aromatic family feeds, one of {@link #AURAS}. */
    public static String aura(Family family) {
        return switch (family) {
            case BERRY, SWEET, EXOTIC -> "berry";
            case FRUIT -> "fruit";
            case HONEY -> "honey";
            case BOTANICAL, SEED, FUNGUS -> "botanical";
            case MARINE -> "marine";
            case SPICE -> "spice";
            case ROOT, GRAIN -> "oak";
        };
    }

    /**
     * Aura strengths for a composition, ordered by first appearance in the
     * canonical sorted composition. Every additive adds one point to the aura
     * of its family, so duplicates stack: five berries give
     * {@code {berry=5}} while five different families give five entries of 1.
     */
    public static Map<String, Integer> auraLevels(String composition) {
        Map<String, Integer> levels = new LinkedHashMap<>();
        if (composition == null || composition.isBlank()) return levels;
        for (String raw : composition.split(",")) {
            String id = raw.strip();
            if (id.isEmpty()) continue;
            Family family = FAMILIES.get(id);
            if (family == null) continue;
            String aura = aura(family);
            levels.merge(aura, 1, (a, b) -> Math.min(MAX_AURA_LEVEL, a + b));
        }
        return levels;
    }

    private AromaFamily() {
    }
}