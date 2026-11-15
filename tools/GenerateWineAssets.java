import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

/**
 * Reproducible voxel assets for the winery added in 0.0.4: the wine press, the
 * grape items and the wine bottles. The riddling rack is a 1x2x2 multiblock and
 * lives in {@code GenerateBottleRack}. It is deliberately a separate tool from
 * {@code GenerateAssets} and {@code GenerateHops} so the winery can be
 * regenerated without touching the other machines' models.
 *
 * <p>Run with JDK 21: {@code java tools/GenerateWineAssets.java}</p>
 */
public class GenerateWineAssets {
    static final Path ROOT = Path.of("src/main/resources/assets/samogon");

    record Box(double x, double y, double z, double X, double Y, double Z, String material) {}

    static final String[] DIRECTIONS = {"north", "east", "south", "west", "up", "down"};

    /** Every material used by the winery models; the new ones are painted below. */
    static final Map<String, Integer> MATERIALS = new LinkedHashMap<>(Map.ofEntries(
            Map.entry("dark_wood", 0x493426),
            Map.entry("oak", 0x8d6036),
            Map.entry("oak_light", 0xb78249),
            Map.entry("oak_end", 0x9d7144),
            Map.entry("iron", 0x9caeb1),
            Map.entry("iron_dark", 0x3c4c51),
            Map.entry("copper", 0xb86a3e),
            Map.entry("copper_light", 0xe69b61),
            Map.entry("copper_dark", 0x73402d),
            Map.entry("brass", 0xc9a24a),
            Map.entry("glass", 0x8cbdc5),
            Map.entry("glass_light", 0xd6eff0),
            Map.entry("cream", 0xe5d3a7),
            Map.entry("ink", 0x243640),
            Map.entry("paper", 0xd4b778),
            Map.entry("seal", 0x913b36),
            Map.entry("herb", 0x63874e),
            Map.entry("cork", 0xad8655),
            Map.entry("honey", 0xd89b31),
            Map.entry("amber", 0xa36520),
            Map.entry("white", 0xf1e6cf),
            Map.entry("black", 0x232a2c),
            Map.entry("grain", 0xceb56d),
            Map.entry("green", 0x466849),
            Map.entry("red", 0x8f3744),
            Map.entry("wine_red", 0x6e1f2f),
            Map.entry("wine_white", 0xd9c477),
            Map.entry("wine_rose", 0xc97f86),
            Map.entry("must", 0xa99a4a),
            Map.entry("grape_red", 0x6b2f52),
            Map.entry("grape_white", 0xb9c46a),
            // One palette per grape variety so the bunches read apart on the
            // vine and in the inventory; GenerateGrapeVine paints the same
            // four colours with this shading routine.
            Map.entry("grape_cabernet", 0x4a2470),
            Map.entry("grape_merlot", 0xa32e44),
            Map.entry("grape_chardonnay", 0xe0b84e),
            Map.entry("grape_riesling", 0xc6d962),
            // Per-variety vine foliage so the plants differ before they fruit.
            Map.entry("grape_leaf_cabernet", 0x4e7a4a),
            Map.entry("grape_leaf_merlot", 0x6f8f3e),
            Map.entry("grape_leaf_chardonnay", 0x93ac52),
            Map.entry("grape_leaf_riesling", 0x5e9a63)));

    public static void main(String[] args) throws Exception {
        writeTextures();
        writeBlockModel("wine_press", winePress());
        writeItemGeometry("red_grapes", grapes("grape_red", "grape_red"));
        writeItemGeometry("white_grapes", grapes("grape_white", "grape_white"));
        writeItemGeometry("grape_must", wineBottle("must", "cork", false));
        writeBottleVariants("wine", "wine_red", "wine_white", "wine_rose", false);
        writeBottleVariants("sparkling_wine", "wine_red", "wine_white", "wine_rose", true);
        writeBlockstate("wine_press");
        writeLoot("wine_press");
        writeLoot("bottle_rack");
        System.out.println("Generated winery assets: 1 machine, 4 items, textures and data.");
    }

    // === TEXTURES ===

    static void writeTextures() throws IOException {
        for (var entry : MATERIALS.entrySet()) {
            String name = entry.getKey();
            // Reuse the existing material PNGs for everything but the new winery
            // palette, so this tool never overwrites the other machines' art.
            if (!name.startsWith("wine_") && !name.equals("must")
                    && !name.startsWith("grape_")) {
                continue;
            }
            BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            Color base = new Color(entry.getValue());
            boolean liquid = name.startsWith("wine_") || name.equals("must");
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    int n = ((x * 7 + y * 13) % 11) - 5;
                    if (liquid) n += (y % 4 == 0 ? 6 : 0) - (x % 5 == 0 ? 4 : 0);
                    if (name.startsWith("grape_")) n += ((x + y) % 6 < 2 ? 9 : -4);
                    image.setRGB(x, y, shade(base, n));
                }
            }
            Path path = ROOT.resolve("textures/block/materials/" + name + ".png");
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

    /** A basket press: a hooped wooden tun with a screw and a juice spout. */
    static List<Box> winePress() {
        List<Box> m = new ArrayList<>();
        box(m, "dark_wood", 1.0, 0.0, 1.0, 15.0, 1.6, 15.0);
        box(m, "oak_light", 2.0, 1.6, 2.0, 14.0, 2.4, 14.0);
        box(m, "oak_light", 2.0, 2.4, 2.0, 14.0, 8.6, 3.1);
        box(m, "oak_light", 2.0, 2.4, 12.9, 14.0, 8.6, 14.0);
        box(m, "oak_light", 2.0, 2.4, 3.1, 3.1, 8.6, 12.9);
        box(m, "oak_light", 12.9, 2.4, 3.1, 14.0, 8.6, 12.9);
        box(m, "wine_red", 3.2, 2.4, 3.2, 12.8, 2.95, 12.8);
        box(m, "oak_light", 3.4, 7.6, 3.4, 12.6, 8.6, 12.6);
        box(m, "iron_dark", 7.4, 8.6, 7.4, 8.6, 15.0, 8.6);
        box(m, "iron", 7.15, 9.6, 7.15, 8.85, 10.15, 8.85);
        box(m, "iron", 7.15, 11.7, 7.15, 8.85, 12.25, 8.85);
        box(m, "iron_dark", 6.8, 14.0, 6.8, 9.2, 15.4, 9.2);
        box(m, "iron", 3.6, 14.4, 7.5, 12.4, 15.3, 8.5);
        box(m, "iron", 1.75, 4.6, 1.85, 14.25, 5.25, 2.05);
        box(m, "iron", 1.75, 4.6, 13.95, 14.25, 5.25, 14.15);
        box(m, "iron", 1.85, 4.6, 2.0, 2.05, 5.25, 14.0);
        box(m, "iron", 13.95, 4.6, 2.0, 14.15, 5.25, 14.0);
        box(m, "copper", 7.0, 2.0, -0.6, 9.0, 3.0, 2.2);
        box(m, "copper_dark", 7.4, 2.2, 1.4, 8.6, 2.9, 2.3);
        box(m, "cream", 5.4, 3.2, 1.4, 10.6, 5.4, 1.62);
        box(m, "ink", 5.7, 4.55, 1.34, 10.3, 4.9, 1.4);
        box(m, "ink", 6.1, 3.7, 1.34, 9.9, 4.05, 1.4);
        return m;
    }

    // === ITEM GEOMETRY ===

    /** A bunch of grapes: a shoulder of berries with a leaf and a stem. */
    static List<Box> grapes(String berry, String light) {
        List<Box> m = new ArrayList<>();
        box(m, berry, 4.5, 1.0, 4.5, 9.5, 6.0, 9.5);
        box(m, berry, 6.0, 0.4, 6.0, 10.0, 3.5, 10.0);
        box(m, berry, 3.0, 5.5, 6.5, 8.0, 9.5, 11.0);
        box(m, berry, 7.5, 5.0, 5.0, 11.5, 9.0, 9.0);
        box(m, berry, 5.5, 8.5, 6.0, 10.5, 12.5, 10.5);
        box(m, light, 6.0, 6.0, 4.4, 7.2, 7.2, 5.0);
        box(m, light, 6.6, 9.4, 5.0, 7.6, 10.6, 5.6);
        box(m, "herb", 6.4, 12.2, 6.4, 9.6, 13.1, 9.6);
        box(m, "herb", 9.0, 12.4, 7.0, 13.2, 13.2, 10.0);
        box(m, "oak", 7.6, 12.6, 7.6, 8.4, 14.6, 8.4);
        box(m, "oak", 4.0, 13.6, 7.7, 8.2, 14.3, 8.3);
        return m;
    }

    /** A wine bottle with a coloured fill, a paper label and a cap. */
    static List<Box> wineBottle(String fill, String cap, boolean sparkling) {
        List<Box> m = new ArrayList<>();
        oct(m, "glass", 8, 8, 3.1, 0.5, 1.2);
        oct(m, fill, 8, 8, 3.1, 1.2, 9.0);
        oct(m, "glass", 8, 8, 3.1, 8.9, 9.6);
        for (double x : new double[]{4.95, 10.75}) {
            box(m, "glass_light", x, 1.6, 5.6, x + 0.3, 8.6, 6.2);
        }
        oct(m, "glass", 8, 8, 1.5, 9.5, 12.4);
        oct(m, "glass_light", 8, 8, 1.7, 12.4, 13.0);
        box(m, "cream", 5.2, 4.2, 4.68, 10.8, 7.4, 4.92);
        box(m, "ink", 5.6, 6.5, 4.62, 10.4, 6.9, 4.7);
        box(m, "ink", 6.1, 5.4, 4.62, 9.9, 5.8, 4.7);
        box(m, "seal", 7.2, 4.6, 4.6, 8.8, 5.9, 4.68);
        if (sparkling) {
            oct(m, "brass", 8, 8, 1.85, 13.0, 14.6);
            for (int i = 0; i < 4; i++) {
                double a = Math.PI / 2 * i;
                double x = 8 + Math.cos(a) * 1.75;
                double z = 8 + Math.sin(a) * 1.75;
                box(m, "iron_dark", x - 0.15, 12.6, z - 0.15, x + 0.15, 13.1, z + 0.15);
            }
        } else {
            oct(m, cap, 8, 8, 1.7, 13.0, 14.4);
        }
        return m;
    }

    // === WRITING HELPERS ===

    static void writeBottleVariants(String id, String red, String white, String rose, boolean sparkling)
            throws IOException {
        // The variant models are written first, then the base red bottle carries
        // overrides that select them from the custom_model_data written by
        // BatchData. Building the base in memory avoids re-reading a file.
        writeItemGeometry(id + "_white", wineBottle(white, "cork", sparkling));
        writeItemGeometry(id + "_rose", wineBottle(rose, "cork", sparkling));
        Map<String, Object> model = buildModel(wineBottle(red, "cork", sparkling), true);
        List<Object> overrides = new ArrayList<>();
        for (int variant = 1; variant <= 2; variant++) {
            Map<String, Object> predicate = new LinkedHashMap<>();
            predicate.put("custom_model_data", variant);
            Map<String, Object> override = new LinkedHashMap<>();
            override.put("predicate", predicate);
            override.put("model", "samogon:item/" + id + (variant == 1 ? "_white" : "_rose"));
            overrides.add(override);
        }
        model.put("overrides", overrides);
        write(ROOT.resolve("models/item/" + id + ".json"), model);
    }

    static void writeBlockModel(String id, List<Box> boxes) throws IOException {
        writeModel("block/" + id, boxes, false);
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("parent", "samogon:block/" + id);
        write(ROOT.resolve("models/item/" + id + ".json"), item);
    }

    static void writeItemGeometry(String id, List<Box> boxes) throws IOException {
        writeModel("item/" + id, boxes, true);
    }

    static void writeModel(String path, List<Box> boxes, boolean item) throws IOException {
        write(ROOT.resolve("models/" + path + ".json"), buildModel(boxes, item));
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
            if (!MATERIALS.containsKey(material)) {
                throw new IllegalStateException("model uses undefined material " + material);
            }
            textures.put(material, "samogon:block/materials/" + material);
        }
        textures.put("particle", "#" + boxes.get(0).material);

        Map<String, Object> model = new LinkedHashMap<>();
        model.put("credit", "Original voxel geometry and pixel textures for Moonshiner; generated by tools/GenerateWineAssets.java");
        model.put("parent", "minecraft:block/block");
        model.put("texture_size", List.of(16, 16));
        model.put("ambientocclusion", !item);
        if (item) model.put("gui_light", "front");
        model.put("textures", textures);
        model.put("elements", elements);
        model.put("display", display(item));
        return model;
    }

    static Map<String, Object> display(boolean item) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("gui", transform(List.of(25, 225, 0), List.of(0, 0, 0), item ? 1.0 : 0.72));
        d.put("ground", transform(List.of(0, 0, 0), List.of(0, 3, 0), 0.4));
        d.put("fixed", transform(List.of(0, 180, 0), List.of(0, 0, 0), 0.65));
        d.put("thirdperson_righthand", transform(List.of(75, 45, 0), List.of(0, 2, 0), item ? 0.45 : 0.375));
        d.put("thirdperson_lefthand", transform(List.of(75, 45, 0), List.of(0, 2, 0), item ? 0.45 : 0.375));
        d.put("firstperson_righthand", transform(List.of(0, 45, 0), List.of(0, 1, 0), 0.55));
        d.put("firstperson_lefthand", transform(List.of(0, 225, 0), List.of(0, 1, 0), 0.55));
        return d;
    }

    static Object transform(List<?> rotation, List<?> translation, double scale) {
        return Map.of("rotation", rotation, "translation", translation,
                "scale", List.of(scale, scale, scale));
    }

    static void writeBlockstate(String id) throws IOException {
        Map<String, Object> variants = new LinkedHashMap<>();
        String[] facings = {"north", "east", "south", "west"};
        int[] rotations = {0, 90, 180, 270};
        for (int f = 0; f < facings.length; f++) {
            for (int heat = 0; heat <= 4; heat++) {
                for (boolean active : new boolean[]{false, true}) {
                    for (int progress = 0; progress <= 4; progress++) {
                        for (boolean ready : new boolean[]{false, true}) {
                            Map<String, Object> ref = new LinkedHashMap<>();
                            ref.put("model", "samogon:block/" + id);
                            if (rotations[f] != 0) ref.put("y", rotations[f]);
                            variants.put("facing=" + facings[f] + ",heat=" + heat + ",active=" + active
                                    + ",progress=" + progress + ",ready=" + ready, ref);
                        }
                    }
                }
            }
        }
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("variants", variants);
        write(ROOT.resolve("blockstates/" + id + ".json"), state);
    }

    static void writeLoot(String id) throws IOException {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("type", "minecraft:item");
        entry.put("name", "samogon:" + id);
        Map<String, Object> condition = new LinkedHashMap<>();
        condition.put("condition", "minecraft:survives_explosion");
        Map<String, Object> pool = new LinkedHashMap<>();
        pool.put("rolls", 1);
        pool.put("bonus_rolls", 0);
        pool.put("entries", List.of(entry));
        pool.put("conditions", List.of(condition));
        Map<String, Object> table = new LinkedHashMap<>();
        table.put("type", "minecraft:block");
        table.put("random_sequence", "samogon:blocks/" + id);
        table.put("pools", List.of(pool));
        write(Path.of("src/main/resources/data/samogon/loot_table/blocks/" + id + ".json"), table);
    }

    // === SMALL HELPERS ===

    static void box(List<Box> m, String mat, double x, double y, double z, double X, double Y, double Z) {
        m.add(new Box(Math.min(x, X), Math.min(y, Y), Math.min(z, Z),
                Math.max(x, X), Math.max(y, Y), Math.max(z, Z), mat));
    }

    /** Octagonal cross-section approximated by two overlapping cuboids. */
    static void oct(List<Box> m, String mat, double cx, double cz, double r, double y, double Y) {
        double s = r * 0.63;
        box(m, mat, cx - s, y, cz - r, cx + s, Y, cz + r);
        box(m, mat, cx - r, y, cz - s, cx + r, Y, cz + s);
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
            return "[" + String.join(",", list.stream().map(GenerateWineAssets::json).toList()) + "]";
        }
        return object.toString();
    }
}
