import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Generates the block models, the item model and the blockstate for the
 * 2x2x2 aging barrel that lies on its side.
 *
 * <p>The cask is authored once as a 32x32x32 drawing of a lying cask whose
 * axis runs along X, then rotated in 90-degree steps about the shared vertical
 * corner to produce the four horizontal facings. Every rotated drawing is cut
 * into the eight 16x16x16 octants of the 2x2x2 structure, so the eight blocks
 * read as one vessel in the world and no model rotation is needed in the
 * blockstate.</p>
 */
public final class GenerateBarrel {
    private record Box(double x, double y, double z, double X, double Y, double Z, String material) {}

    private static final Path ASSETS = Path.of("src/main/resources/assets/samogon");
    private static final List<Box> FULL = new ArrayList<>();
    private static final String[] DIRECTIONS = {"down", "up", "north", "south", "west", "east"};
    /** Clockwise quarter turns that point the barrel's tap side at each facing. */
    private static final String[] FACINGS = {"west", "north", "east", "south"};
    private static final int[] TURNS = {0, 1, 2, 3};
    /** Upper-half octants that own the support pads of a barrel stacked on top. */
    private static final int[] TOP_PARTS = {2, 3, 6, 7};
    /**
     * The support pads bridge the gap between the curved shoulder of a cask and
     * the foot of the cask standing on it. They start just inside the barrel top
     * so they never float and end a hair below the block boundary so their top
     * face cannot z-fight with the foot resting on it.
     */
    private static final double SUPPORT_BASE = 12.4;
    private static final double SUPPORT_TOP = 15.999;

    public static void main(String[] args) throws Exception {
        build();
        settle();
        addLegs();
        int models = 0;
        int supports = 0;
        for (int f = 0; f < FACINGS.length; f++) {
            List<Box> rotated = rotate(FULL, TURNS[f]);
            for (int part = 0; part < 8; part++) {
                List<Box> quadrant = clip(rotated, part);
                writeModel(ASSETS.resolve("models/block/aging_barrel_" + FACINGS[f] + "_" + part + ".json"),
                        quadrant, false);
                models++;
            }
            for (int part : TOP_PARTS) {
                // A top octant sits directly above the bottom octant two indices
                // lower, so the foot rail below gives the exact pad footprint.
                List<Box> pads = supports(clip(rotated, part - 2));
                writeSupportModel(ASSETS.resolve(
                        "models/block/aging_barrel_" + FACINGS[f] + "_" + part + "_support.json"), pads);
                supports++;
            }
        }
        writeModel(ASSETS.resolve("models/item/aging_barrel.json"), item(), true);
        writeBlockstate();
        System.out.println("Generated aging barrel assets: " + FULL.size() + " source boxes, "
                + models + " block models, " + supports + " support models, 4 facings, 8 parts.");
    }

    // === GEOMETRY ===

    private static void box(String material, double x, double y, double z,
                            double X, double Y, double Z) {
        FULL.add(new Box(Math.min(x, X), Math.min(y, Y), Math.min(z, Z),
                Math.max(x, X), Math.max(y, Y), Math.max(z, Z), material));
    }

    /**
     * A slice of the lying cask between x0 and x1. The circular cross-section
     * sits in the YZ plane, centred on y=16, z=16, and is approximated by two
     * overlapping cuboids exactly like the upright machines in the mod.
     */
    private static void ring(String material, double x0, double x1, double r) {
        double s = r * 0.63;
        box(material, x0, 16 - s, 16 - r, x1, 16 + s, 16 + r);
        box(material, x0, 16 - r, 16 - s, x1, 16 + r, 16 + s);
    }

    private static void build() {
        // Barrel lying on its side, axis along X, tap end at -X (the front).
        // The body is swept from a sine bulge so the lid ends taper like a cask.
        ring("oak_end", 0.8, 2.0, 11.8);
        for (double x = 2.0; x < 30.0; x += 2.0) {
            double t = (x + 1.0 - 2.0) / 28.0;
            ring("oak", x, x + 2.0, 11.8 + 2.8 * Math.sin(Math.PI * t));
        }
        ring("oak_end", 30.0, 31.2, 11.8);

        // Dark rims around both lids.
        ring("dark_wood", 0.6, 0.95, 12.1);
        ring("dark_wood", 31.05, 31.4, 12.1);

        // Three iron hoops: over both shoulders and the widest bulge.
        ring("iron_dark", 4.4, 5.6, 13.1);
        ring("iron_dark", 15.4, 16.6, 15.1);
        ring("iron_dark", 26.4, 27.6, 13.1);

        // Stave seams running along the widest part of the body.
        for (double c : new double[]{12.5, 16.0, 19.5}) {
            box("dark_wood", 11.0, 30.45, c - 0.22, 21.0, 30.70, c + 0.22);
            box("dark_wood", 11.0, 1.30, c - 0.22, 21.0, 1.55, c + 0.22);
            box("dark_wood", c - 0.22, 30.45, 11.0, c + 0.22, 30.70, 21.0);
            box("dark_wood", c - 0.22, 1.30, 11.0, c + 0.22, 1.55, 21.0);
        }

        // Bung in the bunghole on top, with a brass collar.
        box("cork", 15.1, 30.2, 15.1, 16.9, 31.5, 16.9);
        box("brass", 14.85, 31.4, 14.85, 17.15, 31.7, 17.15);

        // Copper tap on the front lid: neck, spout and a red valve handle.
        box("copper_dark", 0.10, 6.2, 15.0, 0.85, 8.2, 17.0);
        box("copper_light", 0.25, 5.6, 15.6, 0.75, 6.2, 16.4);
        box("red", 0.35, 8.2, 15.5, 0.65, 8.7, 16.5);

        // Brass label plaque with an engraved line, on the -Z side.
        box("brass", 11.0, 11.0, 1.30, 21.0, 15.0, 1.70);
        box("cream", 11.4, 11.4, 1.22, 20.6, 14.6, 1.55);
        box("ink", 12.8, 12.1, 1.10, 19.2, 12.7, 1.25);
        box("ink", 13.4, 13.3, 1.10, 18.6, 13.9, 1.25);
    }

    // === STAND ===

    /** Fraction the cask is shrunk by so it clears the ground. */
    private static final double SCALE = 0.86;
    /** Height the shrunk cask is lifted to make room for the legs. */
    private static final double LIFT = 2.4;

    /** Shrinks the cask about its section centre and lifts it off the ground. */
    private static void settle() {
        List<Box> out = new ArrayList<>(FULL.size());
        for (Box b : FULL) {
            out.add(new Box(sx(b.x), sy(b.y), sz(b.z), sx(b.X), sy(b.Y), sz(b.Z), b.material));
        }
        FULL.clear();
        FULL.addAll(out);
    }

    private static double sx(double v) { return 16.0 + (v - 16.0) * SCALE; }

    private static double sz(double v) { return 16.0 + (v - 16.0) * SCALE; }

    private static double sy(double v) { return 16.0 + (v - 16.0) * SCALE + LIFT; }

    /** Four dark posts with a foot rail at each end, so the barrel stands on legs. */
    private static void addLegs() {
        for (double[] end : new double[][]{{3.0, 5.0}, {27.0, 29.0}}) {
            for (double[] side : new double[][]{{11.0, 13.4}, {18.6, 21.0}}) {
                box("dark_wood", end[0], 0.0, side[0], end[1], 9.0, side[1]);
            }
            box("dark_wood", end[0] - 0.4, 0.0, 10.4, end[1] + 0.4, 1.4, 21.6);
        }
    }

    /**
     * Pads that carry the feet of a cask stacked above. The low foot rail of the
     * bottom octant below is used as the footprint, so the pad lines up with the
     * real leg and covers the tall post too.
     */
    private static List<Box> supports(List<Box> below) {
        List<Box> out = new ArrayList<>();
        for (Box b : below) {
            if (!b.material.equals("dark_wood") || b.y > 0.1 || b.Y > 2.0) continue;
            out.add(new Box(b.x, SUPPORT_BASE, b.z, b.X, SUPPORT_TOP, b.Z, "dark_wood"));
        }
        return out;
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

    /** Cuts the drawing down to one of the eight 16-cube octants, index dx*4+dy*2+dz. */
    private static List<Box> clip(List<Box> boxes, int part) {
        double x0 = (part >> 2 & 1) * 16.0;
        double y0 = (part >> 1 & 1) * 16.0;
        double z0 = (part & 1) * 16.0;
        double x1 = x0 + 16.0, y1 = y0 + 16.0, z1 = z0 + 16.0;
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

    /** Shrinks the drawing into a 16-cube so the block reads as a mini cask in the GUI. */
    private static List<Box> item() {
        List<Box> out = new ArrayList<>(FULL.size());
        for (Box b : FULL) {
            out.add(new Box(b.x * 0.4 + 1.6, b.y * 0.4 + 1.6, b.z * 0.4 + 1.6,
                    b.X * 0.4 + 1.6, b.Y * 0.4 + 1.6, b.Z * 0.4 + 1.6, b.material));
        }
        return out;
    }

    // === JSON ===

    private static void writeModel(Path path, List<Box> boxes, boolean item) throws Exception {
        writeModel(path, boxes, item, "oak");
    }

    private static void writeSupportModel(Path path, List<Box> boxes) throws Exception {
        writeModel(path, boxes, false, "dark_wood");
    }

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
        json.append("  \"credit\": \"Original voxel geometry and pixel textures for Moonshiner; generated by tools/GenerateBarrel.java\",\n");
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
            for (int part = 0; part < 8; part++) {
                entries.add("    {\"when\": {\"facing\": \"" + FACINGS[f] + "\", \"part\": \"" + part
                        + "\"}, \"apply\": {\"model\": \"samogon:block/aging_barrel_"
                        + FACINGS[f] + "_" + part + "\"}}");
            }
        }
        // The support pads only show while another cask stands on this one.
        for (int f = 0; f < FACINGS.length; f++) {
            for (int part : TOP_PARTS) {
                entries.add("    {\"when\": {\"facing\": \"" + FACINGS[f] + "\", \"part\": \"" + part
                        + "\", \"stacked\": \"true\"}, \"apply\": {\"model\": \"samogon:block/aging_barrel_"
                        + FACINGS[f] + "_" + part + "_support\"}}");
            }
        }
        json.append(String.join(",\n", entries)).append("\n  ]\n}\n");
        Path path = ASSETS.resolve("blockstates/aging_barrel.json");
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

    private GenerateBarrel() {}
}
