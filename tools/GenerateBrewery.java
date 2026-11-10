import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Generates the block models, the item model and the blockstate for the 2x2x1
 * brewery multiblock, plus the voxel item models of hops, wort and beer.
 *
 * <p>The brewhouse is authored once as a 32x16x32 drawing of a squat copper
 * kettle on a wooden deck, then rotated in 90-degree steps about the shared
 * corner to produce the four horizontal facings. Every rotated drawing is cut
 * into the four 16x16x16 cells of the 2x2 footprint, so the four blocks read as
 * one vessel in the world and no model rotation is needed in the blockstate.</p>
 */
public final class GenerateBrewery {
    private record Box(double x, double y, double z, double X, double Y, double Z, String material) {}

    private static final Path ASSETS = Path.of("src/main/resources/assets/samogon");
    private static final List<Box> FULL = new ArrayList<>();
    private static final String[] DIRECTIONS = {"down", "up", "north", "south", "west", "east"};
    /** Clockwise quarter turns that point the brewery's tap at each facing. */
    private static final String[] FACINGS = {"west", "north", "east", "south"};
    private static final int[] TURNS = {0, 1, 2, 3};

    public static void main(String[] args) throws Exception {
        build();
        int models = 0;
        for (int f = 0; f < FACINGS.length; f++) {
            List<Box> rotated = rotate(FULL, TURNS[f]);
            for (int part = 0; part < 4; part++) {
                writeModel(ASSETS.resolve("models/block/brewery_" + FACINGS[f] + "_" + part + ".json"),
                        clip(rotated, part), false, "copper");
                models++;
            }
        }
        writeModel(ASSETS.resolve("models/item/brewery.json"), item(), true, "copper");
        writeBlockstate();
        writeHops();
        writeBottle("beer_wort", "cream", "grain", false);
        writeBottle("hopped_wort", "amber", "herb", false);
        writeBottle("beer", "amber", "grain", true);
        System.out.println("Generated brewery assets: " + FULL.size() + " source boxes, "
                + models + " block models, 4 facings, 4 parts, 4 item models.");
    }

    // === GEOMETRY ===

    private static void box(String material, double x, double y, double z,
                            double X, double Y, double Z) {
        FULL.add(new Box(Math.min(x, X), Math.min(y, Y), Math.min(z, Z),
                Math.max(x, X), Math.max(y, Y), Math.max(z, Z), material));
    }

    private static void add(List<Box> out, String material, double x, double y, double z,
                            double X, double Y, double Z) {
        out.add(new Box(Math.min(x, X), Math.min(y, Y), Math.min(z, Z),
                Math.max(x, X), Math.max(y, Y), Math.max(z, Z), material));
    }

    /** An upright section of the kettle, approximated by two overlapping cuboids. */
    private static void ring(String material, double y0, double y1, double r) {
        double s = r * 0.63;
        box(material, 16 - s, y0, 16 - r, 16 + s, y1, 16 + r);
        box(material, 16 - r, y0, 16 - s, 16 + r, y1, 16 + s);
    }

    private static void build() {
        // Wooden deck under the kettle.
        box("dark_wood", 1.0, 0.0, 1.0, 31.0, 1.6, 31.0);
        box("oak", 1.4, 1.6, 1.4, 30.6, 2.2, 30.6);

        // Copper body, swept from a sine bulge so the kettle tapers like a cauldron.
        for (double y = 2.2; y < 12.0; y += 1.0) {
            double t = (y - 2.2) / 9.8;
            double r = 10.2 + 1.5 * Math.sin(Math.PI * (0.15 + 0.7 * t));
            ring("copper", y, y + 1.0, r);
        }
        // Rim and a lighter lip over it.
        ring("copper_dark", 12.0, 13.0, 11.9);
        ring("copper_light", 13.0, 13.6, 11.4);
        // Domed lid and a short chimney.
        ring("copper_light", 13.6, 14.3, 9.6);
        ring("copper_light", 14.3, 14.9, 7.4);
        ring("copper_dark", 14.9, 15.9, 2.2);
        ring("iron_dark", 15.6, 15.9, 2.8);

        // Dark copper bands running across the body.
        for (double c : new double[]{12.0, 16.0, 20.0}) {
            box("copper_dark", 2.0, 6.5, c - 0.2, 30.0, 7.3, c + 0.2);
            box("copper_dark", c - 0.2, 6.5, 2.0, c + 0.2, 7.3, 30.0);
        }

        // Copper tap with a red valve on the front lid.
        box("copper_dark", 14.5, 3.4, 3.0, 17.5, 5.4, 5.4);
        box("copper_light", 15.0, 3.9, 1.4, 17.0, 4.9, 3.2);
        box("red", 15.2, 5.4, 3.6, 16.8, 6.0, 4.6);

        // Brass gauge plaque on the +X side.
        box("brass", 26.8, 7.0, 12.0, 27.9, 11.0, 20.0);
        box("cream", 26.8, 7.4, 12.4, 27.9, 10.6, 19.6);
        box("ink", 26.8, 8.0, 13.4, 27.9, 8.6, 16.0);
        box("ink", 26.8, 9.2, 13.4, 27.9, 9.8, 18.4);

        // Wooden mash paddle leaning against the -X side.
        box("oak_light", 1.8, 0.4, 6.6, 3.0, 13.5, 7.8);
        box("oak_light", 1.0, 13.5, 6.0, 3.8, 15.2, 8.4);
    }

    /** Rotates the whole drawing by {@code quarters} x 90 degrees about (16,16). */
    private static List<Box> rotate(List<Box> boxes, int quarters) {
        int q = ((quarters % 4) + 4) % 4;
        if (q == 0) return boxes;
        List<Box> out = new ArrayList<>(boxes.size());
        for (Box b : boxes) {
            double x0 = 1e9, z0 = 1e9, x1 = -1e9, z1 = -1e9;
            for (double[] corner : new double[][]{{b.x, b.z}, {b.X, b.Z}}) {
                double[] r = rotatePoint(corner[0], corner[1], q);
                x0 = Math.min(x0, r[0]);
                x1 = Math.max(x1, r[0]);
                z0 = Math.min(z0, r[1]);
                z1 = Math.max(z1, r[1]);
            }
            out.add(new Box(x0, b.y, z0, x1, b.Y, z1, b.material));
        }
        return out;
    }

    private static double[] rotatePoint(double x, double z, int quarters) {
        double rx = x - 16.0, rz = z - 16.0;
        for (int i = 0; i < quarters; i++) {
            double nx = -rz, nz = rx;
            rx = nx;
            rz = nz;
        }
        return new double[]{rx + 16.0, rz + 16.0};
    }

    /** Cuts the drawing down to one of the four 16-cube cells, index dx*2+dz. */
    private static List<Box> clip(List<Box> boxes, int part) {
        double x0 = (part >> 1 & 1) * 16.0;
        double z0 = (part & 1) * 16.0;
        double y0 = 0.0;
        double x1 = x0 + 16.0, y1 = 16.0, z1 = z0 + 16.0;
        List<Box> out = new ArrayList<>();
        for (Box b : boxes) {
            double ax = Math.max(b.x, x0), bx = Math.min(b.X, x1);
            double ay = Math.max(b.y, y0), by = Math.min(b.Y, y1);
            double az = Math.max(b.z, z0), bz = Math.min(b.Z, z1);
            if (bx - ax <= 1.0E-6 || by - ay <= 1.0E-6 || bz - az <= 1.0E-6) continue;
            out.add(new Box(ax - x0, ay - y0, az - z0, bx - x0, by - y0, bz - z0, b.material));
        }
        return out;
    }

    /** Shrinks the drawing into a 16-cube so the block reads as a mini kettle in the GUI. */
    private static List<Box> item() {
        List<Box> out = new ArrayList<>(FULL.size());
        for (Box b : FULL) {
            out.add(new Box(b.x * 0.4 + 1.6, b.y * 0.4 + 4.8, b.z * 0.4 + 1.6,
                    b.X * 0.4 + 1.6, b.Y * 0.4 + 4.8, b.Z * 0.4 + 1.6, b.material));
        }
        return out;
    }

    // === ITEM MODELS ===

    private static void writeHops() throws Exception {
        List<Box> boxes = new ArrayList<>();
        add(boxes, "green", 5.5, 3.0, 5.5, 10.5, 8.0, 10.5);
        add(boxes, "green", 6.5, 8.0, 6.5, 9.5, 11.0, 9.5);
        add(boxes, "green", 7.2, 11.0, 7.2, 8.8, 13.0, 8.8);
        add(boxes, "herb", 6.8, 2.3, 6.8, 9.2, 3.0, 9.2);
        add(boxes, "dark_wood", 7.6, 13.0, 7.6, 8.4, 14.7, 8.4);
        add(boxes, "herb", 4.0, 6.0, 7.2, 6.0, 7.2, 8.8);
        add(boxes, "herb", 10.0, 6.0, 7.2, 12.0, 7.2, 8.8);
        writeModel(ASSETS.resolve("models/item/hops.json"), boxes, true, "green");
    }

    /** A round-shouldered bottle with a coloured liquid and a small paper label. */
    private static void writeBottle(String id, String liquid, String label, boolean foam) throws Exception {
        List<Box> boxes = new ArrayList<>();
        add(boxes, "glass", 5.0, 1.0, 4.75, 11.0, 9.0, 11.25);
        add(boxes, liquid, 5.4, 1.2, 5.15, 10.6, 7.6, 10.85);
        add(boxes, "glass", 6.4, 9.0, 5.65, 9.6, 11.2, 10.35);
        add(boxes, "glass", 7.1, 11.2, 6.55, 8.9, 13.6, 9.45);
        add(boxes, "glass_light", 6.9, 13.4, 6.35, 9.1, 14.0, 9.65);
        add(boxes, "cork", 7.0, 13.9, 6.45, 9.0, 14.8, 9.55);
        add(boxes, label, 5.5, 3.6, 4.62, 10.5, 6.6, 4.78);
        add(boxes, "ink", 6.2, 5.4, 4.55, 9.8, 5.9, 4.63);
        add(boxes, "ink", 6.2, 4.4, 4.55, 9.0, 4.9, 4.63);
        if (foam) {
            add(boxes, "white", 5.5, 7.6, 5.25, 10.5, 8.4, 10.75);
            add(boxes, "white", 7.3, 11.2, 6.75, 8.7, 12.0, 9.25);
        }
        writeModel(ASSETS.resolve("models/item/" + id + ".json"), boxes, true, "glass");
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
        json.append("  \"credit\": \"Original voxel geometry and pixel textures for Moonshiner; generated by tools/GenerateBrewery.java\",\n");
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
        StringBuilder json = new StringBuilder();
        json.append("{\n  \"multipart\": [\n");
        List<String> entries = new ArrayList<>();
        for (int f = 0; f < FACINGS.length; f++) {
            for (int part = 0; part < 4; part++) {
                entries.add("    {\"when\": {\"facing\": \"" + FACINGS[f] + "\", \"part\": \"" + part
                        + "\"}, \"apply\": {\"model\": \"samogon:block/brewery_"
                        + FACINGS[f] + "_" + part + "\"}}");
            }
        }
        json.append(String.join(",\n", entries)).append("\n  ]\n}\n");
        Path path = ASSETS.resolve("blockstates/brewery.json");
        Files.createDirectories(path.getParent());
        Files.writeString(path, json.toString(), StandardCharsets.UTF_8);
    }

    private static String display(boolean item) {
        String gui = item ? "0.9" : "0.72";
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

    private GenerateBrewery() {}
}
