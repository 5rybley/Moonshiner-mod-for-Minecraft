import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Assets added with hangovers, brine and the three new tinctures.
 *
 * <p>Run from the project root with JDK 21:
 * {@code java tools/GenerateNewAssets.java}
 *
 * <p>This tool only writes the files it owns, so it never touches the 33 models
 * produced by {@code GenerateAssets.java}. New material textures live next to
 * the old ones under {@code textures/block/materials}, which keeps them on the
 * block atlas.</p>
 */
public final class GenerateNewAssets {
    static final Path ROOT = Path.of("src/main/resources/assets/samogon");
    static final String[] DIRECTIONS = {"down", "up", "north", "south", "west", "east"};

    /** Only the materials this tool creates; the rest already exist. */
    static final Map<String, Integer> NEW_MATERIALS = new LinkedHashMap<>();
    static final Random RANDOM = new Random(41207);

    static final int INK = 0x3a2618;
    static final int CREAM = 0xe8dfc8;
    static final int HIGHLIGHT = 0xfff8e8;
    static final int PALE = 0xead9af;
    static final int SHADE = 0xb58a55;

    record Box(double x, double y, double z, double X, double Y, double Z, String material) {}

    public static void main(String[] args) throws Exception {
        defineMaterials();
        writeMaterials();
        writeItems();
        writeEffectIcons();
        System.out.printf("Generated 4 item models, %d material textures and 5 effect icons.%n",
                NEW_MATERIALS.size());
    }

    // === MATERIALS ===

    static void defineMaterials() {
        material("spice", 0xb8481c);
        material("spice_light", 0xe0703a);
        material("spice_label", 0x7a2a12);
        material("fruit", 0xd98bb0);
        material("fruit_light", 0xf0b8cc);
        material("fruit_label", 0x9c4f6e);
        material("marine", 0x2f7fa8);
        material("marine_light", 0x7fc4dd);
        material("marine_label", 0x1d4f6b);
        material("brine", 0xd4dfae);
        material("brine_light", 0xeaf1d2);
    }

    static void material(String name, int color) {
        NEW_MATERIALS.put(name, color);
    }

    static void writeMaterials() throws IOException {
        Path dir = ROOT.resolve("textures/block/materials");
        Files.createDirectories(dir);
        for (var entry : NEW_MATERIALS.entrySet()) {
            String name = entry.getKey();
            Color base = new Color(entry.getValue());
            BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    int noise = RANDOM.nextInt(11) - 5;
                    if (name.endsWith("_label")) {
                        noise += (x * y) % 9 == 0 ? -16 : 0;
                    } else if (name.equals("brine")) {
                        // A few pale flecks make the cloudy brine read as salty.
                        noise += (x * 5 + y * 3) % 7 == 0 ? 20 : 0;
                    } else {
                        noise += (int) (Math.sin(y * 0.9 + x * 0.55) * 4);
                    }
                    image.setRGB(x, y, 0xff000000 | shade(base, noise));
                }
            }
            ImageIO.write(image, "PNG", dir.resolve(name + ".png").toFile());
        }
    }

    static int shade(Color color, int delta) {
        return new Color(clamp(color.getRed() + delta), clamp(color.getGreen() + delta),
                clamp(color.getBlue() + delta)).getRGB();
    }

    static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    // === ITEM MODELS ===

    static void writeItems() throws IOException {
        Map<String, List<Box>> items = new LinkedHashMap<>();
        items.put("spiced_tincture", bottle("spice", "spice_label"));
        items.put("fruit_tincture", bottle("fruit", "fruit_label"));
        items.put("marine_tincture", bottle("marine", "marine_label"));
        items.put("brine", tumbler());
        for (var entry : items.entrySet()) {
            writeModel("item/" + entry.getKey(), entry.getValue());
        }
    }

    /** The established tincture bottle, with a different liquid and label. */
    static List<Box> bottle(String liquid, String label) {
        List<Box> m = new ArrayList<>();
        double r = 3.15;
        double bottom = 1, shoulder = 10.4;
        oct(m, "glass", 8, 8, r, bottom, bottom + 0.7);
        oct(m, liquid, 8, 8, r - 0.35, bottom + 0.65, shoulder - 0.75);
        for (double x : new double[]{8 - r + 0.2, 8 + r - 0.7}) {
            box(m, "glass_light", x, bottom + 0.5, 8 - r * 0.63, x + 0.5, shoulder, 8 - r * 0.63 + 0.4);
            box(m, "glass", x, bottom + 0.5, 8 + r * 0.63 - 0.4, x + 0.5, shoulder, 8 + r * 0.63);
        }
        oct(m, "glass", 8, 8, r, shoulder - 0.7, shoulder + 0.2);
        oct(m, "glass", 8, 8, r * 0.75, shoulder + 0.2, shoulder + 1);
        double neck = 1.3;
        oct(m, "glass", 8, 8, neck, shoulder + 1, 13.9);
        box(m, "glass_light", 8 - neck * 0.6, shoulder + 1, 8 - neck - 0.05,
                8 - neck * 0.6 + 0.35, 13.8, 8 - neck + 0.2);
        oct(m, "cork", 8, 8, neck + 0.2, 13.9, 14.9);
        oct(m, label, 8, 8, neck + 0.25, 12.8, 13.8);
        box(m, label, 8 - r * 0.68, 4.1, 8 - r - 0.13, 8 + r * 0.68, 7.7, 8 - r + 0.08);
        box(m, "cream", 8 - r * 0.55, 4.5, 8 - r - 0.19, 8 + r * 0.55, 7.3, 8 - r - 0.12);
        box(m, "ink", 8 - r * 0.38, 6.5, 8 - r - 0.25, 8 + r * 0.38, 6.9, 8 - r - 0.18);
        box(m, label, 7.5, 5, 8 - r - 0.25, 8.5, 6.1, 8 - r - 0.18);
        return m;
    }

    /**
     * A tumbler of brine: a thick-based glass, a cloudy fill, a salted rim and a
     * pickle spear. Brine is pickle juice, so the spear is the clearest label
     * the item can wear.
     */
    static List<Box> tumbler() {
        List<Box> m = new ArrayList<>();
        double r = 3.1;
        double liquid = r - 0.4;
        oct(m, "glass", 8, 8, r, 1.4, 2.3);
        oct(m, "brine", 8, 8, liquid, 2.2, 10.4);
        for (double x : new double[]{8 - r + 0.35, 8 + r - 0.75}) {
            box(m, "glass_light", x, 2.4, 8 - r * 0.63, x + 0.4, 10.5, 8 - r * 0.63 + 0.32);
            box(m, "glass_light", x, 2.4, 8 + r * 0.63 - 0.32, x + 0.4, 10.5, 8 + r * 0.63);
        }
        oct(m, "glass", 8, 8, r, 10.4, 11.1);
        oct(m, "glass_light", 8, 8, r - 0.2, 11.1, 11.5);
        // Coarse salt crystals sitting on the rim.
        for (int i = 0; i < 9; i++) {
            double angle = Math.PI * 2 * i / 9 + 0.13;
            double x = 8 + Math.cos(angle) * (r - 0.35);
            double z = 8 + Math.sin(angle) * (r - 0.35);
            double size = 0.55 + (i % 3) * 0.12;
            box(m, i % 2 == 0 ? "white" : "brine_light", x - size / 2, 11.3, z - size / 2,
                    x + size / 2, 11.65 + (i % 2) * 0.15, z + size / 2);
        }
        // The pickle spear: a knobbly green shaft rising out of the glass.
        double px = 7.4, pz = 7.4;
        box(m, "herb", px, 6.0, pz, px + 1.2, 15.2, pz + 1.2);
        box(m, "green", px + 0.2, 6.4, pz + 0.25, px + 0.85, 15.0, pz + 0.95);
        box(m, "herb", px - 0.12, 15.1, pz + 0.12, px + 1.32, 15.8, pz + 1.08);
        box(m, "herb", px - 0.12, 8.0, pz - 0.08, px + 0.2, 8.7, pz + 0.25);
        box(m, "herb", px + 1.0, 11.0, pz + 0.95, px + 1.32, 11.7, pz + 1.28);
        // Undissolved salt on the front of the glass.
        double front = 8 - liquid - 0.06;
        box(m, "white", 6.4, 4.1, front, 6.8, 4.5, front + 0.08);
        box(m, "white", 7.3, 6.8, front, 7.62, 7.12, front + 0.08);
        box(m, "white", 6.1, 8.6, front, 6.38, 8.88, front + 0.08);
        return m;
    }

    static void oct(List<Box> m, String material, double cx, double cz, double r, double y, double Y) {
        double s = r * 0.63;
        box(m, material, cx - s, y, cz - r, cx + s, Y, cz + r);
        box(m, material, cx - r, y, cz - s, cx + r, Y, cz + s);
    }

    static void box(List<Box> m, String material,
                    double x, double y, double z, double X, double Y, double Z) {
        m.add(new Box(Math.min(x, X), Math.min(y, Y), Math.min(z, Z),
                Math.max(x, X), Math.max(y, Y), Math.max(z, Z), material));
    }

    static void writeModel(String path, List<Box> boxes) throws IOException {
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
            Path texture = ROOT.resolve("textures/block/materials/" + material + ".png");
            if (!Files.exists(texture)) {
                throw new IllegalStateException(path + " uses missing material " + material);
            }
            textures.put(material, "samogon:block/materials/" + material);
        }
        textures.put("particle", "#" + boxes.get(0).material());

        Map<String, Object> model = new LinkedHashMap<>();
        model.put("format_version", "26.3");
        model.put("credit", "Original voxel geometry and pixel textures for Moonshiner;"
                + " generated by tools/GenerateNewAssets.java");
        model.put("parent", "minecraft:block/block");
        model.put("ambientocclusion", false);
        model.put("textures", textures);
        model.put("elements", elements);
        model.put("gui_light", "front");
        model.put("display", display());

        Path file = ROOT.resolve("models/" + path + ".json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, json(model) + "\n", StandardCharsets.UTF_8);
    }

    static Map<String, Object> display() {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("gui", transform(List.of(25, -135, 0), List.of(0, 0, 0), 0.95));
        d.put("ground", transform(List.of(0, 0, 0), List.of(0, 3, 0), 0.4));
        d.put("fixed", transform(List.of(0, -180, 0), List.of(0, 0, 0), 0.65));
        d.put("thirdperson_righthand", transform(List.of(75, 45, 0), List.of(0, 2.5, 0), 0.5));
        d.put("thirdperson_lefthand", transform(List.of(75, 45, 0), List.of(0, 2.5, 0), 0.5));
        d.put("firstperson_righthand", transform(List.of(0, 45, 0), List.of(0, 1, 0), 0.55));
        d.put("firstperson_lefthand", transform(List.of(0, -135, 0), List.of(0, 1, 0), 0.55));
        return d;
    }

    static Map<String, Object> transform(List<?> rotation, List<?> translation, double scale) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("rotation", rotation);
        map.put("translation", translation);
        map.put("scale", List.of(scale, scale, scale));
        return map;
    }

    static String json(Object value) {
        StringBuilder out = new StringBuilder();
        writeJson(value, out, 0);
        return out.toString();
    }

    static void writeJson(Object value, StringBuilder out, int indent) {
        if (value instanceof Map<?, ?> map) {
            out.append('{');
            boolean first = true;
            for (var entry : map.entrySet()) {
                if (!first) out.append(',');
                first = false;
                newline(out, indent + 1);
                out.append('"').append(escape(String.valueOf(entry.getKey()))).append("\": ");
                writeJson(entry.getValue(), out, indent + 1);
            }
            newline(out, indent);
            out.append('}');
        } else if (value instanceof List<?> list) {
            out.append('[');
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) out.append(", ");
                writeJson(list.get(i), out, indent);
            }
            out.append(']');
        } else if (value instanceof String text) {
            out.append('"').append(escape(text)).append('"');
        } else if (value instanceof Double number) {
            if (number == Math.rint(number)) out.append(number.longValue());
            else out.append(number);
        } else {
            out.append(value);
        }
    }

    static void newline(StringBuilder out, int indent) {
        out.append('\n');
        for (int i = 0; i < indent; i++) out.append("  ");
    }

    static String escape(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // === EFFECT ICONS ===

    static void writeEffectIcons() throws IOException {
        Path dir = ROOT.resolve("textures/mob_effect");
        Files.createDirectories(dir);
        Map<String, BufferedImage> icons = new LinkedHashMap<>();
        icons.put("spiced_ember", icon(0xe0703a, 0xc4551f, 0x7a2a12, "SPICE"));
        icons.put("orchard_bloom", icon(0xf0b8cc, 0xd98bb0, 0x9c4f6e, "FRUIT"));
        icons.put("tidal_breath", icon(0x7fc4dd, 0x2f7fa8, 0x1d4f6b, "MARINE"));
        icons.put("hangover", icon(0xc7cf9a, 0x9aa860, 0x5f6b3a, "HANGOVER"));
        icons.put("clear_head", icon(0xe6f8f3, 0xa9e3d8, 0x4fa89a, "CLEAR_HEAD"));
        for (var entry : icons.entrySet()) {
            ImageIO.write(entry.getValue(), "PNG", dir.resolve(entry.getKey() + ".png").toFile());
        }
    }

    static BufferedImage icon(int light, int main, int dark, String kind) {
        BufferedImage img = new BufferedImage(18, 18, BufferedImage.TYPE_INT_ARGB);

        // Cork and neck.
        rect(img, 7, 0, 10, 0, 0x9a6a3a);
        rect(img, 7, 1, 10, 1, 0x6b4526);
        rect(img, 7, 2, 7, 5, INK);
        rect(img, 8, 2, 9, 5, CREAM);
        rect(img, 10, 2, 10, 5, INK);

        // Bottle body: outline, label field and the glass highlight.
        rect(img, 3, 6, 14, 6, INK);
        rect(img, 3, 7, 3, 15, INK);
        rect(img, 14, 7, 14, 15, INK);
        rect(img, 4, 7, 13, 7, CREAM);
        px(img, 4, 8, HIGHLIGHT);
        rect(img, 5, 8, 11, 8, light);
        px(img, 12, 8, HIGHLIGHT);
        px(img, 13, 8, CREAM);
        rect(img, 4, 9, 4, 13, HIGHLIGHT);
        rect(img, 5, 9, 12, 13, main);
        rect(img, 13, 9, 13, 13, CREAM);
        px(img, 4, 14, CREAM);
        rect(img, 5, 14, 12, 14, main);
        px(img, 13, 14, CREAM);
        px(img, 4, 15, CREAM);
        rect(img, 5, 15, 12, 15, light);
        px(img, 13, 15, CREAM);
        rect(img, 3, 16, 14, 16, INK);

        switch (kind) {
            case "SPICE" -> {
                // A flame: bright yellow body with a white-hot core.
                int fire = 0xffc24d, core = 0xfff3c4;
                px(img, 8, 9, fire);
                px(img, 7, 10, fire);
                px(img, 8, 10, fire);
                px(img, 9, 10, core);
                px(img, 7, 11, fire);
                px(img, 8, 11, core);
                px(img, 9, 11, core);
                px(img, 10, 11, fire);
                rect(img, 6, 12, 11, 12, fire);
                px(img, 8, 12, core);
                px(img, 9, 12, core);
                rect(img, 7, 13, 10, 13, dark);
                // Embers in the top-right corner.
                px(img, 15, 1, fire);
                px(img, 14, 2, fire);
                px(img, 15, 2, core);
                px(img, 13, 3, fire);
                px(img, 14, 3, fire);
                px(img, 15, 4, fire);
            }
            case "FRUIT" -> {
                // A red apple with a green leaf.
                int red = 0xe8506f, deep = 0xb03350, leaf = 0x63874e;
                px(img, 9, 9, 0x5e3a1c);
                px(img, 10, 9, leaf);
                rect(img, 7, 10, 10, 10, red);
                rect(img, 6, 11, 11, 12, red);
                rect(img, 7, 13, 10, 13, deep);
                px(img, 7, 11, HIGHLIGHT);
                px(img, 14, 1, leaf);
                px(img, 15, 1, leaf);
                px(img, 13, 2, red);
                px(img, 14, 2, red);
                px(img, 13, 3, red);
                px(img, 14, 3, red);
                px(img, 15, 3, deep);
                px(img, 14, 4, deep);
            }
            case "MARINE" -> {
                // A water droplet: pointed tip, round belly, glint.
                int pale = 0xd9f5ff, deep = 0xbfe9f7;
                px(img, 9, 9, pale);
                rect(img, 8, 10, 10, 10, pale);
                rect(img, 7, 11, 11, 12, pale);
                rect(img, 8, 13, 10, 13, deep);
                px(img, 7, 11, HIGHLIGHT);
                // Bubbles in the corner.
                px(img, 14, 2, pale);
                px(img, 15, 2, pale);
                px(img, 14, 3, pale);
                px(img, 15, 3, HIGHLIGHT);
                px(img, 16, 1, pale);
                px(img, 13, 4, pale);
            }
            case "HANGOVER" -> {
                // A queasy face: heavy eyes and a thick, drooping mouth.
                int face = 0xf2e6c8;
                rect(img, 6, 10, 7, 10, face);
                rect(img, 10, 10, 11, 10, face);
                rect(img, 7, 12, 10, 12, face);
                rect(img, 6, 13, 7, 13, face);
                rect(img, 10, 13, 11, 13, face);
                // A sickly yellow bolt of pain.
                int sick = 0xe8df6a;
                px(img, 15, 1, sick);
                px(img, 14, 2, sick);
                px(img, 15, 3, sick);
                px(img, 14, 4, sick);
                px(img, 13, 5, sick);
            }
            case "CLEAR_HEAD" -> {
                // A four-point sparkle: the fog lifting.
                rect(img, 8, 9, 9, 9, PALE);
                rect(img, 7, 10, 10, 10, PALE);
                rect(img, 6, 11, 11, 11, PALE);
                rect(img, 7, 12, 10, 12, PALE);
                rect(img, 8, 13, 9, 13, PALE);
                rect(img, 8, 11, 9, 11, HIGHLIGHT);
                int spark = 0xd9f5ff;
                px(img, 15, 1, spark);
                px(img, 14, 2, spark);
                px(img, 15, 2, HIGHLIGHT);
                px(img, 16, 2, spark);
                px(img, 15, 3, spark);
            }
            default -> throw new IllegalArgumentException("Unknown icon " + kind);
        }
        return img;
    }

    static void px(BufferedImage img, int x, int y, int rgb) {
        if (x >= 0 && y >= 0 && x < img.getWidth() && y < img.getHeight()) {
            img.setRGB(x, y, 0xff000000 | rgb);
        }
    }

    static void rect(BufferedImage img, int x1, int y1, int x2, int y2, int rgb) {
        for (int y = y1; y <= y2; y++) {
            for (int x = x1; x <= x2; x++) px(img, x, y, rgb);
        }
    }

    private GenerateNewAssets() {}
}