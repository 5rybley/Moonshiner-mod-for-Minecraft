import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Generates the block models, the item model and the blockstate for the 1x2x2
 * bottle rack multiblock.
 *
 * <p>The shelf is authored once as a 16x32x32 drawing - one block wide, two
 * blocks tall and two blocks deep - and then cut into its four 16-cube cells,
 * index {@code dy * 2 + dz}, exactly matching {@code BottleRackBlock.PART}. The
 * footprint is not square, so the drawing is rotated in 90-degree steps for the
 * four facings: {@code FACING} runs along the rack's two-block depth, and the
 * same four cell models are turned by the matching blockstate y rotation, keyed
 * on facing and part.</p>
 *
 * <p>Run with JDK 21: {@code java tools/GenerateBottleRack.java}</p>
 */
public final class GenerateBottleRack {
    private record Box(double x, double y, double z, double X, double Y, double Z, String material) {}

    private static final Path ASSETS = Path.of("src/main/resources/assets/samogon");
    private static final String[] DIRECTIONS = {"down", "up", "north", "south", "west", "east"};
    private static final List<Box> FULL = new ArrayList<>();
    /** Bottle fills cycled across the rack so the shelf reads as a mixed cellar. */
    private static final String[] FILLS = {"wine_red", "wine_white", "wine_rose"};
    /** Depth facings, and the clockwise y rotation that points the drawing's +z at each. */
    private static final String[] FACINGS = {"south", "west", "north", "east"};
    private static final int[] ROTATION = {0, 90, 180, 270};

    public static void main(String[] args) throws Exception {
        build();
        for (int part = 0; part < 4; part++) {
            writeModel(ASSETS.resolve("models/block/bottle_rack_" + part + ".json"),
                    clip(FULL, part), false, "dark_wood");
        }
        writeModel(ASSETS.resolve("models/item/bottle_rack.json"), item(), true, "dark_wood");
        writeBlockstate();
        System.out.println("Generated bottle rack assets: " + FULL.size()
                + " source boxes, 4 block models, 1 item model, 1 multipart blockstate.");
    }

    // === GEOMETRY ===

    private static void box(String material, double x, double y, double z,
                            double X, double Y, double Z) {
        FULL.add(new Box(Math.min(x, X), Math.min(y, Y), Math.min(z, Z),
                Math.max(x, X), Math.max(y, Y), Math.max(z, Z), material));
    }

    private static void build() {
        // Four corner posts, one full height each, tying the four cells together
        // into a single shelf as tall as a standing player.
        for (double x : new double[]{0.0, 14.6}) {
            for (double z : new double[]{0.0, 30.6}) {
                box("dark_wood", x, 0.0, z, x + 1.4, 32.0, z + 1.4);
            }
        }
        // Shelf boards: a deck at the very bottom, two middle shelves and a top.
        for (double y : new double[]{0.0, 10.4, 20.8, 31.0}) {
            box("oak_light", 1.4, y, 1.4, 14.6, y + 1.0, 30.6);
        }
        // Eighteen bottles: two across the width, three deep, on three shelves.
        int index = 0;
        for (double y : new double[]{1.0, 11.4, 21.8}) {
            for (double x : new double[]{3.0, 10.8}) {
                for (double z : new double[]{3.4, 14.9, 26.4}) {
                    bottle(x, y, z, FILLS[index++ % FILLS.length]);
                }
            }
        }
    }

    /** A standing bottle used on the rack: base, coloured body, neck and cork. */
    private static void bottle(double x, double y, double z, String fill) {
        box("glass", x, y, z, x + 2.2, y + 0.5, z + 2.2);
        box(fill, x, y + 0.5, z, x + 2.2, y + 4.6, z + 2.2);
        box("glass", x + 0.6, y + 4.6, z + 0.6, x + 1.6, y + 5.7, z + 1.6);
        box("cork", x + 0.7, y + 5.6, z + 0.7, x + 1.5, y + 6.2, z + 1.5);
    }

    /** Cuts the drawing down to one of the four 16-cube cells, index dy * 2 + dz. */
    private static List<Box> clip(List<Box> boxes, int part) {
        double y0 = ((part >> 1) & 1) * 16.0;
        double z0 = (part & 1) * 16.0;
        double y1 = y0 + 16.0, z1 = z0 + 16.0;
        List<Box> out = new ArrayList<>();
        for (Box b : boxes) {
            double ax = Math.max(b.x, 0.0), bx = Math.min(b.X, 16.0);
            double ay = Math.max(b.y, y0), by = Math.min(b.Y, y1);
            double az = Math.max(b.z, z0), bz = Math.min(b.Z, z1);
            if (bx - ax <= 1.0E-6 || by - ay <= 1.0E-6 || bz - az <= 1.0E-6) continue;
            out.add(new Box(ax, ay - y0, az - z0, bx, by - y0, bz - z0, b.material));
        }
        return out;
    }

    /** Shrinks the whole shelf into a 16-cube so the rack reads as a miniature in the GUI. */
    private static List<Box> item() {
        List<Box> out = new ArrayList<>(FULL.size());
        for (Box b : FULL) {
            out.add(new Box(b.x * 0.4 + 4.8, b.y * 0.4 + 1.6, b.z * 0.4 + 1.6,
                    b.X * 0.4 + 4.8, b.Y * 0.4 + 1.6, b.Z * 0.4 + 1.6, b.material));
        }
        return out;
    }

    // === JSON ===

    private static void writeModel(Path path, List<Box> boxes, boolean item, String particle) throws Exception {
        Set<String> materials = new LinkedHashSet<>();
        StringBuilder elements = new StringBuilder();
        for (int i = 0; i < boxes.size(); i++) {
            Box b = boxes.get(i);
            materials.add(b.material);
            elements.append(i == 0 ? "" : ",\n").append("    {\n");
            elements.append("      \"from\": ").append(vec(b.x, b.y, b.z)).append(",\n");
            elements.append("      \"to\": ").append(vec(b.X, b.Y, b.Z)).append(",\n");
            elements.append("      \"faces\": {\n");
            for (int d = 0; d < DIRECTIONS.length; d++) {
                elements.append("        \"").append(DIRECTIONS[d]).append("\": {\"uv\": [0, 0, 16, 16], \"texture\": \"#")
                        .append(b.material).append("\"}")
                        .append(d == DIRECTIONS.length - 1 ? "\n" : ",\n");
            }
            elements.append("      }\n    }");
        }

        StringBuilder textures = new StringBuilder();
        textures.append("    \"particle\": \"#").append(particle).append("\"");
        for (String material : materials) {
            textures.append(",\n    \"").append(material).append("\": \"samogon:block/materials/")
                    .append(material).append("\"");
        }

        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"credit\": \"Original voxel geometry and pixel textures for Moonshiner; generated by tools/GenerateBottleRack.java\",\n");
        json.append("  \"parent\": \"minecraft:block/block\",\n");
        json.append("  \"texture_size\": [16, 16],\n");
        json.append("  \"ambientocclusion\": ").append(item ? "false" : "true").append(",\n");
        if (item) json.append("  \"gui_light\": \"front\",\n");
        json.append("  \"textures\": {\n").append(textures).append("\n  },\n");
        json.append("  \"elements\": [\n").append(elements).append("\n  ],\n");
        json.append("  \"display\": ").append(display(item)).append("\n");
        json.append("}\n");

        Files.createDirectories(path.getParent());
        Files.writeString(path, json.toString(), StandardCharsets.UTF_8);
    }

    private static void writeBlockstate() throws Exception {
        List<String> entries = new ArrayList<>();
        for (int f = 0; f < FACINGS.length; f++) {
            for (int part = 0; part < 4; part++) {
                entries.add("    {\"when\": {\"facing\": \"" + FACINGS[f] + "\", \"part\": \"" + part
                        + "\"}, \"apply\": {\"model\": \"samogon:block/bottle_rack_" + part + "\""
                        + (ROTATION[f] != 0 ? ", \"y\": " + ROTATION[f] : "") + "}}");
            }
        }
        StringBuilder json = new StringBuilder();
        json.append("{\n  \"multipart\": [\n");
        json.append(String.join(",\n", entries)).append("\n  ]\n}\n");
        Path path = ASSETS.resolve("blockstates/bottle_rack.json");
        Files.createDirectories(path.getParent());
        Files.writeString(path, json.toString(), StandardCharsets.UTF_8);
    }

    private static String display(boolean item) {
        String gui = item ? "0.7" : "0.72";
        String ground = "0.4";
        String fixed = item ? "0.55" : "0.65";
        String third = item ? "0.3" : "0.375";
        String first = item ? "0.42" : "0.55";
        return "{\n"
                + "    \"gui\": " + transform("20, 45, 0", "0, 0, 0", gui) + ",\n"
                + "    \"ground\": " + transform("0, 0, 0", "0, 2, 0", ground) + ",\n"
                + "    \"fixed\": " + transform("0, 270, 0", "0, 0, 0", fixed) + ",\n"
                + "    \"thirdperson_righthand\": " + transform("75, 45, 0", "0, 2, 0", third) + ",\n"
                + "    \"thirdperson_lefthand\": " + transform("75, 45, 0", "0, 2, 0", third) + ",\n"
                + "    \"firstperson_righthand\": " + transform("0, 45, 0", "0, 1, 0", first) + ",\n"
                + "    \"firstperson_lefthand\": " + transform("0, 225, 0", "0, 1, 0", first) + "\n"
                + "  }";
    }

    private static String transform(String rotation, String translation, String scale) {
        return "{\"rotation\": [" + rotation + "], \"translation\": [" + translation
                + "], \"scale\": [" + scale + ", " + scale + ", " + scale + "]}";
    }

    private static String vec(double x, double y, double z) {
        return "[" + num(x) + ", " + num(y) + ", " + num(z) + "]";
    }

    private static String num(double value) {
        String text = String.format(Locale.ROOT, "%.4f", value);
        while (text.contains(".") && (text.endsWith("0"))) text = text.substring(0, text.length() - 1);
        if (text.endsWith(".")) text = text.substring(0, text.length() - 1);
        return text;
    }

    private GenerateBottleRack() {}
}
