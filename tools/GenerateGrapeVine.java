import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

/**
 * Reproducible assets for the vineyard: the trellis block models, the berry
 * and leaf palettes, the blockstate, the crop loot table and the grape and
 * seed item geometry.
 *
 * <p>The vine is a single {@code grape_vine} crop block carrying an {@code age}
 * and a {@code variety} property. Eight ages collapse onto four visible stages,
 * exactly like the hop crop. Every variety gets its own model family so the
 * player can tell Cabernet from Merlot and Chardonnay from Riesling at a
 * glance: the berry colour, the leaf habit and the shape of the bunches all
 * differ. The red trellis, however, is exactly the same on every stage and for
 * every variety — a permanent structure about as tall as a player (just under
 * two blocks); only the vine, its leaves and its fruit grow.</p>
 *
 * <p>The berry and leaf palettes are painted here with the same shading
 * routine {@code GenerateWineAssets} uses for its {@code grape_*} materials,
 * and are mirrored in that tool's palette, so either generator produces the
 * same PNGs. Every variety also gets its own grape and seed item model in its
 * own colour, so the four bunches read apart in the inventory as well as on
 * the vine. Run with JDK 21: {@code java tools/GenerateGrapeVine.java}.</p>
 */
public class GenerateGrapeVine {
    static final Path ASSETS = Path.of("src/main/resources/assets/samogon");
    static final Path DATA = Path.of("src/main/resources/data");

    /** Height of the trellis in model pixels; constant on every stage. */
    static final double TRELLIS_TOP = 29.0;

    /** One berry palette per variety, so the four grapes are easy to tell apart. */
    static final Map<String, Integer> GRAPE_COLORS = new LinkedHashMap<>();

    /** One leaf palette per variety, so even an unripe vine reads as its own plant. */
    static final Map<String, Integer> LEAF_COLORS = new LinkedHashMap<>();

    static {
        GRAPE_COLORS.put("grape_cabernet", 0x4A2470);   // blue-violet
        GRAPE_COLORS.put("grape_merlot", 0xA32E44);     // clear claret red
        GRAPE_COLORS.put("grape_chardonnay", 0xE0B84E); // bright gold
        GRAPE_COLORS.put("grape_riesling", 0xC6D962);   // light lime green

        LEAF_COLORS.put("grape_leaf_cabernet", 0x4E7A4A);   // deep green
        LEAF_COLORS.put("grape_leaf_merlot", 0x6F8F3E);     // olive
        LEAF_COLORS.put("grape_leaf_chardonnay", 0x93AC52); // yellow-green
        LEAF_COLORS.put("grape_leaf_riesling", 0x5E9A63);   // cool green
    }

    /** The materials the trellis paints with; all live under block/materials. */
    static final Set<String> MATERIALS = new LinkedHashSet<>(List.of(
            "red", "green", "herb", "oak", "grape_red", "grape_white",
            "grape_cabernet", "grape_merlot", "grape_chardonnay", "grape_riesling",
            "grape_leaf_cabernet", "grape_leaf_merlot", "grape_leaf_chardonnay", "grape_leaf_riesling"));

    static final String[] DIRECTIONS = {"north", "east", "south", "west", "up", "down"};

    /**
     * Variety id, the berry material its grapes, bunch on the vine and seeds
     * are painted with, and the leaf material its foliage uses.
     */
    record Variety(String id, String berry, String leaf) {}

    /**
     * The last two ages share the ripe model in the blockstate, so both must
     * yield grapes: a vine that already looks ready should not drop only seeds.
     */
    static final List<String> RIPE_AGES = List.of("6", "7");

    static final Variety[] VARIETIES = {
            new Variety("cabernet", "grape_cabernet", "grape_leaf_cabernet"),
            new Variety("merlot", "grape_merlot", "grape_leaf_merlot"),
            new Variety("chardonnay", "grape_chardonnay", "grape_leaf_chardonnay"),
            new Variety("riesling", "grape_riesling", "grape_leaf_riesling"),
    };

    record Box(double x, double y, double z, double X, double Y, double Z, String material) {}

    public static void main(String[] args) throws Exception {
        writeGrapeTextures();
        for (Variety variety : VARIETIES) {
            for (int stage = 0; stage <= 3; stage++) {
                writeBlockModel("grape_vine_" + variety.id() + "_stage" + stage,
                        vine(variety, stage));
            }
        }
        writeBlockstate();
        writeLoot();
        for (Variety variety : VARIETIES) {
            writeItemGeometry(variety.id() + "_grapes", grapes(variety.berry()));
            writeItemGeometry(variety.id() + "_seeds", grapeSeeds(variety.berry()));
        }
        System.out.println("Generated vineyard assets: 16 trellis models, blockstate, loot, "
                + "4 palettes and 8 variety items.");
    }

    // === TEXTURES ===

    /**
     * Paints the per-variety berry and leaf palettes. The shading matches
     * {@code GenerateWineAssets} for {@code grape_*} materials: a fixed
     * diagonal noise with a brighter streak so the surfaces read as bumpy.
     */
    static void writeGrapeTextures() throws IOException {
        Map<String, Integer> palette = new LinkedHashMap<>(GRAPE_COLORS);
        palette.putAll(LEAF_COLORS);
        for (var entry : palette.entrySet()) {
            BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            Color base = new Color(entry.getValue());
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    int n = ((x * 7 + y * 13) % 11) - 5;
                    n += ((x + y) % 6 < 2 ? 9 : -4);
                    image.setRGB(x, y, shade(base, n));
                }
            }
            Path path = ASSETS.resolve("textures/block/materials/" + entry.getKey() + ".png");
            Files.createDirectories(path.getParent());
            ImageIO.write(image, "PNG", path.toFile());
        }
    }

    static int shade(Color color, int delta) {
        return new Color(clamp(color.getRed() + delta), clamp(color.getGreen() + delta),
                clamp(color.getBlue() + delta)).getRGB();
    }

    static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    // === BLOCK GEOMETRY ===

    /**
     * One visible growth stage. The trellis never changes: its two posts, feet,
     * top crossbar and middle rail are always at {@link #TRELLIS_TOP}. Only the
     * trunk, leaves and bunches rise with the stage, so a young vine is a full
     * trellis carrying a small sprout.
     */
    static List<Box> vine(Variety variety, int stage) {
        List<Box> m = new ArrayList<>();
        trellis(m);
        double top = TRELLIS_TOP * switch (stage) {
            case 0 -> 0.36;
            case 1 -> 0.60;
            case 2 -> 0.82;
            default -> 0.96;
        };
        String leaf = variety.leaf();
        switch (variety.id()) {
            case "cabernet" -> cabernet(m, stage, top, leaf);
            case "merlot" -> merlot(m, stage, top, leaf);
            case "chardonnay" -> chardonnay(m, stage, top, leaf);
            case "riesling" -> riesling(m, stage, top, leaf);
            default -> throw new IllegalArgumentException("unknown grape variety " + variety.id());
        }
        return m;
    }

    /** The permanent red trellis: identical for every stage and variety. */
    static void trellis(List<Box> m) {
        // Two posts, planted on chunky feet.
        box(m, "red", 2.0, 0.0, 7.3, 3.0, TRELLIS_TOP, 8.7);
        box(m, "red", 13.0, 0.0, 7.3, 14.0, TRELLIS_TOP, 8.7);
        box(m, "red", 1.6, 0.0, 6.9, 3.4, 1.2, 9.1);
        box(m, "red", 12.6, 0.0, 6.9, 14.4, 1.2, 9.1);
        // Top crossbar and the mid rail the vine is tied to.
        box(m, "red", 2.0, TRELLIS_TOP - 1.4, 7.4, 14.0, TRELLIS_TOP, 8.6);
        box(m, "red", 2.0, 14.2, 7.7, 14.0, 14.9, 8.3);
    }

    /** Cabernet: broad horizontal leaves and long, tapered violet clusters. */
    static void cabernet(List<Box> m, int stage, double top, String leaf) {
        trunk(m, 0.9, top);
        for (int i = 0; i <= stage; i++) {
            double y = top * (0.16 + 0.20 * i);
            box(m, leaf, 4.1, y, 6.7, 7.2, y + 1.7, 9.3);
            box(m, leaf, 8.8, y + 0.5, 6.7, 11.9, y + 2.2, 9.3);
            box(m, leaf, 7.1, y + 1.0, 5.8, 8.9, y + 2.0, 6.9);
        }
        if (stage >= 1) tendrils(m, top, 0.34, 0.62, 0.86);
        if (stage >= 2) {
            bunch(m, "grape_cabernet", 5.2, top * 0.68, 2.0, 4.6);
            bunch(m, "grape_cabernet", 10.8, top * 0.60, 2.0, 4.4);
        }
        if (stage >= 3) {
            bunch(m, "grape_cabernet", 4.4, top * 0.46, 1.9, 4.2);
            bunch(m, "grape_cabernet", 11.6, top * 0.50, 1.9, 4.2);
            bunch(m, "grape_cabernet", 8.0, top * 0.90, 1.6, 3.4);
        }
    }

    /** Merlot: a thicker trunk, plump rounded leaves and fat claret clusters. */
    static void merlot(List<Box> m, int stage, double top, String leaf) {
        trunk(m, 1.1, top * 0.94);
        for (int i = 0; i <= stage; i++) {
            double y = top * (0.14 + 0.19 * i);
            box(m, leaf, 3.6, y, 6.5, 7.4, y + 2.1, 9.5);
            box(m, leaf, 8.6, y + 0.6, 6.5, 12.4, y + 2.7, 9.5);
            box(m, leaf, 6.4, y + 1.5, 5.7, 9.6, y + 3.1, 7.1);
        }
        if (stage >= 1) tendrils(m, top * 0.94, 0.30, 0.58, 0.82);
        if (stage >= 2) {
            bunch(m, "grape_merlot", 5.0, top * 0.62, 2.4, 3.8);
            bunch(m, "grape_merlot", 11.0, top * 0.70, 2.4, 3.6);
        }
        if (stage >= 3) {
            bunch(m, "grape_merlot", 4.2, top * 0.44, 2.2, 3.4);
            bunch(m, "grape_merlot", 11.8, top * 0.48, 2.2, 3.4);
            bunch(m, "grape_merlot", 8.0, top * 0.84, 2.0, 3.2);
        }
    }

    /** Chardonnay: compact and upright, with many small leaves and tight golden clusters. */
    static void chardonnay(List<Box> m, int stage, double top, String leaf) {
        trunk(m, 0.75, top * 0.92);
        int nodes = stage * 2 + 1;
        for (int i = 0; i < nodes; i++) {
            double y = top * (0.20 + 0.09 * i);
            if (i % 2 == 0) {
                box(m, leaf, 5.1, y, 6.9, 7.3, y + 1.3, 9.1);
            } else {
                box(m, leaf, 8.7, y, 6.9, 10.9, y + 1.3, 9.1);
            }
            if (i % 3 == 0) {
                box(m, leaf, 7.4, y + 0.8, 6.0, 8.6, y + 1.7, 7.0);
            }
        }
        if (stage >= 1) tendrils(m, top * 0.92, 0.28, 0.50, 0.72, 0.90);
        if (stage >= 2) {
            bunch(m, "grape_chardonnay", 5.6, top * 0.74, 1.6, 2.8);
            bunch(m, "grape_chardonnay", 10.4, top * 0.68, 1.6, 2.6);
        }
        if (stage >= 3) {
            bunch(m, "grape_chardonnay", 4.8, top * 0.56, 1.5, 2.4);
            bunch(m, "grape_chardonnay", 11.2, top * 0.60, 1.5, 2.4);
            bunch(m, "grape_chardonnay", 8.0, top * 0.92, 1.4, 2.2);
        }
    }

    /** Riesling: a tall thin trunk, sparse narrow leaves and slim pale clusters. */
    static void riesling(List<Box> m, int stage, double top, String leaf) {
        trunk(m, 0.6, top);
        for (int i = 0; i <= stage; i++) {
            double y = top * (0.18 + 0.20 * i);
            if (i % 2 == 0) {
                box(m, leaf, 4.8, y, 7.0, 7.3, y + 1.2, 9.0);
            } else {
                box(m, leaf, 8.7, y, 7.0, 11.2, y + 1.2, 9.0);
            }
            box(m, leaf, 7.4, y + 0.7, 6.7, 8.6, y + 1.5, 7.8);
        }
        if (stage >= 1) tendrils(m, top, 0.40, 0.70);
        if (stage >= 2) {
            bunch(m, "grape_riesling", 5.4, top * 0.76, 1.4, 4.6);
            bunch(m, "grape_riesling", 10.6, top * 0.70, 1.4, 4.4);
        }
        if (stage >= 3) {
            bunch(m, "grape_riesling", 8.0, top * 0.90, 1.3, 3.8);
        }
    }

    /** A straight green trunk in the middle of the block. */
    static void trunk(List<Box> m, double half, double top) {
        box(m, "green", 8.0 - half, 0.0, 8.0 - half, 8.0 + half, top, 8.0 + half);
    }

    /** Short green tendrils tying the trunk to both posts at the given heights. */
    static void tendrils(List<Box> m, double top, double... fractions) {
        for (double fraction : fractions) {
            double y = top * fraction;
            if (y < 1.5 || y > TRELLIS_TOP - 2.0) continue;
            box(m, "green", 3.0, y, 7.5, 7.0, y + 0.7, 8.5);
            box(m, "green", 9.0, y, 7.5, 13.0, y + 0.7, 8.5);
        }
    }

    /**
     * A hanging cluster: a short green stalk, a shoulder of berries and a
     * tapering tip. {@code width} is the half-width of the shoulder, {@code
     * length} how far the bunch hangs.
     */
    static void bunch(List<Box> m, String berry, double cx, double top, double width, double length) {
        double cz = 8.0;
        box(m, "green", cx - 0.25, top, cz - 0.25, cx + 0.25, top + 1.2, cz + 0.25);
        // Shoulder, belly and tapering tip.
        box(m, berry, cx - width, top - length * 0.34, cz - width * 0.85,
                cx + width, top, cz + width * 0.85);
        box(m, berry, cx - width * 0.9, top - length * 0.66, cz - width * 0.9,
                cx + width * 0.9, top - length * 0.26, cz + width * 0.9);
        box(m, berry, cx - width * 0.55, top - length, cz - width * 0.55,
                cx + width * 0.55, top - length * 0.58, cz + width * 0.55);
        // A couple of side berries so the silhouette is not a plain box.
        box(m, berry, cx - width * 1.2, top - length * 0.34, cz - width * 0.45,
                cx - width * 0.75, top - length * 0.05, cz + width * 0.45);
        box(m, berry, cx + width * 0.75, top - length * 0.40, cz - width * 0.45,
                cx + width * 1.2, top - length * 0.10, cz + width * 0.45);
    }

    // === ITEM GEOMETRY ===

    /**
     * A bunch of the given variety's grapes: a shoulder of berries with a leaf
     * and a stem. The same mesh as the legacy red/white bunches, but painted
     * with one palette per variety.
     */
    static List<Box> grapes(String berry) {
        List<Box> m = new ArrayList<>();
        box(m, berry, 4.5, 1.0, 4.5, 9.5, 6.0, 9.5);
        box(m, berry, 6.0, 0.4, 6.0, 10.0, 3.5, 10.0);
        box(m, berry, 3.0, 5.5, 6.5, 8.0, 9.5, 11.0);
        box(m, berry, 7.5, 5.0, 5.0, 11.5, 9.0, 9.0);
        box(m, berry, 5.5, 8.5, 6.0, 10.5, 12.5, 10.5);
        box(m, "herb", 6.4, 12.2, 6.4, 9.6, 13.1, 9.6);
        box(m, "herb", 9.0, 12.4, 7.0, 13.2, 13.2, 10.0);
        box(m, "oak", 7.6, 12.6, 7.6, 8.4, 14.6, 8.4);
        box(m, "oak", 4.0, 13.6, 7.7, 8.2, 14.3, 8.3);
        return m;
    }

    /** A small heap of pips with a couple of husks, in the variety's colour. */
    static List<Box> grapeSeeds(String berry) {
        List<Box> m = new ArrayList<>();
        box(m, berry, 4.0, 1.6, 4.4, 6.4, 3.4, 6.8);
        box(m, berry, 7.0, 1.6, 5.0, 9.4, 3.4, 7.4);
        box(m, berry, 5.4, 1.6, 7.6, 7.8, 3.2, 10.0);
        box(m, berry, 8.4, 1.6, 7.8, 10.6, 3.0, 10.2);
        box(m, berry, 6.2, 3.0, 5.8, 8.0, 4.4, 7.6);
        box(m, "herb", 9.6, 1.2, 3.8, 12.2, 2.8, 6.4);
        box(m, "herb", 3.4, 1.2, 9.6, 6.0, 2.8, 12.2);
        return m;
    }

    // === WRITING HELPERS ===

    static void writeBlockModel(String id, List<Box> boxes) throws IOException {
        write(ASSETS.resolve("models/block/" + id + ".json"), buildModel(boxes, false));
    }

    static void writeItemGeometry(String id, List<Box> boxes) throws IOException {
        write(ASSETS.resolve("models/item/" + id + ".json"), buildModel(boxes, true));
    }

    static Map<String, Object> buildModel(List<Box> boxes, boolean item) {
        List<Object> elements = new ArrayList<>();
        Set<String> used = new LinkedHashSet<>();
        for (Box b : boxes) {
            used.add(b.material);
            Map<String, Object> faces = new LinkedHashMap<>();
            for (String direction : DIRECTIONS) {
                Map<String, Object> face = new LinkedHashMap<>();
                face.put("uv", List.of(0, 0, 16, 16));
                face.put("texture", "#" + b.material);
                faces.put(direction, face);
            }
            Map<String, Object> element = new LinkedHashMap<>();
            element.put("from", List.of(b.x, b.y, b.z));
            element.put("to", List.of(b.X, b.Y, b.Z));
            element.put("faces", faces);
            elements.add(element);
        }
        Map<String, Object> textures = new LinkedHashMap<>();
        for (String material : used) {
            if (!MATERIALS.contains(material)) {
                throw new IllegalStateException("model uses undefined material " + material);
            }
            textures.put(material, "samogon:block/materials/" + material);
        }
        textures.put("particle", "#" + boxes.get(0).material);

        Map<String, Object> model = new LinkedHashMap<>();
        model.put("credit", "Original voxel geometry and pixel textures for Moonshiner; generated by tools/GenerateGrapeVine.java");
        model.put("parent", "minecraft:block/block");
        model.put("texture_size", List.of(16, 16));
        model.put("ambientocclusion", false);
        if (item) model.put("gui_light", "front");
        model.put("textures", textures);
        model.put("elements", elements);
        if (item) model.put("display", display());
        return model;
    }

    static Map<String, Object> display() {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("gui", transform(List.of(25, 225, 0), List.of(0, 0, 0), 1.0));
        d.put("ground", transform(List.of(0, 0, 0), List.of(0, 3, 0), 0.4));
        d.put("fixed", transform(List.of(0, 180, 0), List.of(0, 0, 0), 0.65));
        d.put("thirdperson_righthand", transform(List.of(75, 45, 0), List.of(0, 2, 0), 0.45));
        d.put("thirdperson_lefthand", transform(List.of(75, 45, 0), List.of(0, 2, 0), 0.45));
        d.put("firstperson_righthand", transform(List.of(0, 45, 0), List.of(0, 1, 0), 0.55));
        d.put("firstperson_lefthand", transform(List.of(0, 225, 0), List.of(0, 1, 0), 0.55));
        return d;
    }

    static Object transform(List<?> rotation, List<?> translation, double scale) {
        return Map.of("rotation", rotation, "translation", translation,
                "scale", List.of(scale, scale, scale));
    }

    /** Variant keys follow the block's state order: age first, then variety. */
    static void writeBlockstate() throws IOException {
        Map<String, Object> variants = new LinkedHashMap<>();
        for (int age = 0; age <= 7; age++) {
            int stage = age / 2;
            for (Variety variety : VARIETIES) {
                Map<String, Object> ref = new LinkedHashMap<>();
                ref.put("model", "samogon:block/grape_vine_" + variety.id() + "_stage" + stage);
                variants.put("age=" + age + ",variety=" + variety.id(), ref);
            }
        }
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("variants", variants);
        write(ASSETS.resolve("blockstates/grape_vine.json"), state);
    }

    /**
     * Full grapes from a ripe vine of the matching variety, and seeds from any
     * age. The variety is a blockstate property, so each variety gets its own
     * pool guarded by a block_state_property condition.
     */
    static void writeLoot() throws IOException {
        List<Object> pools = new ArrayList<>();
        for (Variety variety : VARIETIES) {
            pools.add(pool(List.of(property("variety", variety.id()), ripeAge()),
                    entry("samogon:" + variety.id() + "_grapes",
                            List.of(setCount(2.0, 4.0), fortune(), explosionDecay()))));
        }
        for (Variety variety : VARIETIES) {
            pools.add(pool(List.of(property("variety", variety.id())),
                    entry("samogon:" + variety.id() + "_seeds", List.of(setCount(2.0, 2.0)))));
        }
        Map<String, Object> table = new LinkedHashMap<>();
        table.put("type", "minecraft:block");
        table.put("random_sequence", "samogon:blocks/grape_vine");
        table.put("pools", pools);
        write(DATA.resolve("samogon/loot_table/blocks/grape_vine.json"), table);
    }

    /**
     * Matches either of the ages that show the top model. The vine looks fully
     * grown from age 6 on, so gating the harvest on age 7 alone meant a player
     * who broke a ripe-looking vine got seeds but no grapes.
     */
    static Object ripeAge() {
        List<Object> terms = new ArrayList<>();
        for (String age : RIPE_AGES) terms.add(property("age", age));
        Map<String, Object> anyOf = new LinkedHashMap<>();
        anyOf.put("condition", "minecraft:any_of");
        anyOf.put("terms", terms);
        return anyOf;
    }

    static Object pool(List<?> conditions, Object entry) {
        Map<String, Object> pool = new LinkedHashMap<>();
        pool.put("bonus_rolls", 0.0);
        pool.put("rolls", 1.0);
        pool.put("conditions", conditions);
        pool.put("entries", List.of(entry));
        return pool;
    }

    static Object entry(String name, List<?> functions) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", "minecraft:item");
        item.put("name", name);
        item.put("functions", functions);
        return item;
    }

    static Object property(String name, String value) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put(name, value);
        Map<String, Object> condition = new LinkedHashMap<>();
        condition.put("condition", "minecraft:block_state_property");
        condition.put("block", "samogon:grape_vine");
        condition.put("properties", properties);
        return condition;
    }

    static Object setCount(double min, double max) {
        Map<String, Object> count = new LinkedHashMap<>();
        if (min == max) {
            // Loot functions are typed by a "function" key, not "type". Using
            // "type" here made the whole grape loot table unparseable, so the
            // vines dropped no seeds at all.
            count.put("function", "minecraft:set_count");
            count.put("count", min);
            return count;
        }
        Map<String, Object> range = new LinkedHashMap<>();
        range.put("type", "minecraft:uniform");
        range.put("min", min);
        range.put("max", max);
        count.put("function", "minecraft:set_count");
        count.put("count", range);
        return count;
    }

    static Object fortune() {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("extra", 3);
        parameters.put("probability", 0.5714286);
        Map<String, Object> bonus = new LinkedHashMap<>();
        bonus.put("function", "minecraft:apply_bonus");
        bonus.put("enchantment", "minecraft:fortune");
        bonus.put("formula", "minecraft:binomial_with_bonus_count");
        bonus.put("parameters", parameters);
        return bonus;
    }

    static Object explosionDecay() {
        return Map.of("function", "minecraft:explosion_decay");
    }

    // === SMALL HELPERS ===

    static void box(List<Box> m, String mat, double x, double y, double z, double X, double Y, double Z) {
        m.add(new Box(Math.min(x, X), Math.min(y, Y), Math.min(z, Z),
                Math.max(x, X), Math.max(y, Y), Math.max(z, Z), mat));
    }

    static void write(Path path, Object value) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, json(value) + "\n", StandardCharsets.UTF_8);
    }

    static String json(Object object) {
        if (object instanceof String s) return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        if (object instanceof Map<?, ?> map) {
            List<String> entries = new ArrayList<>();
            map.forEach((k, v) -> entries.add(json(k.toString()) + ":" + json(v)));
            return "{" + String.join(",", entries) + "}";
        }
        if (object instanceof List<?> list) {
            return "[" + String.join(",", list.stream().map(GenerateGrapeVine::json).toList()) + "]";
        }
        return object.toString();
    }
}