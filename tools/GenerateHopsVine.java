import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Draft vine form for the hop crop: a permanent trellis with a bine winding up
 * it, exactly the way the grape trellis works.
 *
 * <p>This is deliberately a <em>prototype</em>. The player asked for the hop
 * plant to read as a climbing vine on supports, and said the cone detail would
 * be refined by hand later. So the generator never rebuilds the hand-made hop
 * plant: it reads the existing {@code hops_stage0..3.json} files and copies
 * their {@code elements} array verbatim into the new models. The cones, the
 * leaves and every existing rotation stay byte-for-byte untouched.</p>
 *
 * <p>What is added on top is only the structure the plant climbs:</p>
 * <ul>
 *   <li>a fixed red trellis — two posts, feet, a top crossbar and a mid rail,
 *       exactly as tall on every stage (29 px, just under two blocks);</li>
 *   <li>a green bine spiralling around the central line of the crop, growing
 *       with the stage but always wrapped around the trellis axis;</li>
 *   <li>short leaves on the bine and tendrils tying it to both posts.</li>
 * </ul>
 *
 * <p>Writes only {@code hops_vine_stage0..3} and the {@code hops} blockstate.
 * It never writes {@code hops_stage*.json}, never runs {@code GenerateHops}
 * and never touches the {@code backup-hops-models-*} folders. Run with JDK 21:
 * {@code java tools/GenerateHopsVine.java}.</p>
 */
public final class GenerateHopsVine {
    static final Path ASSETS = Path.of("src/main/resources/assets/samogon");
    static final Path BLOCKS = Path.of("src/main/resources/assets/samogon/models/block");

    /** Height of the trellis in model pixels; constant on every stage. */
    static final double TRELLIS_TOP = 29.0;

    /** How far up the trellis the bine has climbed on each visible stage. */
    static final double[] STAGE_HEIGHT = {0.36, 0.60, 0.82, 0.96};

    static final String[] DIRECTIONS = {"north", "east", "south", "west", "up", "down"};

    record Box(double x, double y, double z, double X, double Y, double Z, String material) {}

    public static void main(String[] args) throws Exception {
        int copied = 0;
        for (int stage = 0; stage <= 3; stage++) {
            copied += writeVine(stage);
        }
        writeBlockstate();
        System.out.println("Generated the hop vine: 4 trellis models and the blockstate; "
                + "copied " + copied + " untouched elements from hops_stage0..3.");
    }

    /**
     * Builds {@code hops_vine_stageN} from the new climbing structure plus the
     * existing (hand-made) plant elements, copied without modification.
     */
    static int writeVine(int stage) throws Exception {
        List<Box> structure = new ArrayList<>();
        trellis(structure);
        bine(structure, stage);
        tendrils(structure, stage);

        String existing = existingElements(BLOCKS.resolve("hops_stage" + stage + ".json"));
        int existingCount = countElements(existing);

        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("\t\"format_version\": \"26.3\",\n");
        json.append("\t\"credit\": \"Draft hop vine: trellis and winding bine by tools/GenerateHopsVine.java; "
                + "existing hop plant elements copied verbatim from hops_stage" + stage + ".json\",\n");
        json.append("\t\"parent\": \"minecraft:block/block\",\n");
        json.append("\t\"ambientocclusion\": false,\n");
        json.append("\t\"textures\": {\n");
        json.append("\t\t\"particle\": \"samogon:block/materials/herb\",\n");
        json.append("\t\t\"herb\": \"samogon:block/materials/herb\",\n");
        json.append("\t\t\"green\": \"samogon:block/materials/green\",\n");
        json.append("\t\t\"dark_wood\": \"samogon:block/materials/dark_wood\",\n");
        json.append("\t\t\"red\": \"samogon:block/materials/red\"\n");
        json.append("\t},\n");
        json.append("\t\"elements\": [\n");
        for (int i = 0; i < structure.size(); i++) {
            json.append("\t\t").append(element(structure.get(i)));
            json.append(",\n");
        }
        json.append("\t\t").append(existing.trim()).append("\n");
        json.append("\t]\n}\n");

        Path out = BLOCKS.resolve("hops_vine_stage" + stage + ".json");
        Files.createDirectories(out.getParent());
        Files.writeString(out, json.toString(), StandardCharsets.UTF_8);
        System.out.println("wrote " + out.getFileName() + "  structure=" + structure.size()
                + " existing=" + existingCount);
        return existingCount;
    }

    /** The permanent red trellis: identical on every stage. */
    static void trellis(List<Box> m) {
        // Two posts, planted on chunky feet.
        box(m, "red", 2.0, 0.0, 7.3, 3.0, TRELLIS_TOP, 8.7);
        box(m, "red", 13.0, 0.0, 7.3, 14.0, TRELLIS_TOP, 8.7);
        box(m, "red", 1.6, 0.0, 6.9, 3.4, 1.2, 9.1);
        box(m, "red", 12.6, 0.0, 6.9, 14.4, 1.2, 9.1);
        // Top crossbar and the mid rail the bine is tied to.
        box(m, "red", 2.0, TRELLIS_TOP - 1.4, 7.4, 14.0, TRELLIS_TOP, 8.6);
        box(m, "red", 2.0, 14.2, 7.7, 14.0, 14.9, 8.3);
    }

    /**
     * A green bine winding around the central axis of the crop. The helix
     * advances 60 degrees every 0.9 px, so it visibly wraps the trellis line on
     * every stage; it grows taller with the stage instead of changing shape.
     */
    static void bine(List<Box> m, int stage) {
        double top = TRELLIS_TOP * STAGE_HEIGHT[stage];
        double radius = 2.8;
        double y = 0.7;
        int step = 0;
        double previousX = Double.NaN, previousY = Double.NaN, previousZ = Double.NaN;
        while (y < top) {
            double angle = Math.toRadians(step * 45.0);
            double cx = 8.0 + radius * Math.cos(angle);
            double cz = 8.0 + radius * Math.sin(angle);
            // A node, plus a connector to the previous node so the bine is a
            // continuous stair rather than a row of separate cubes.
            if (Double.isNaN(previousX)) {
                box(m, "green", cx - 0.3, y - 0.5, cz - 0.3, cx + 0.3, y + 0.5, cz + 0.3);
            } else {
                box(m, "green",
                        Math.min(previousX, cx) - 0.22, Math.min(previousY, y) - 0.45,
                        Math.min(previousZ, cz) - 0.22,
                        Math.max(previousX, cx) + 0.22, Math.max(previousY, y) + 0.45,
                        Math.max(previousZ, cz) + 0.22);
            }
            if (step % 5 == 2) {
                leaf(m, cx, y, cz, angle);
            }
            previousX = cx;
            previousY = y;
            previousZ = cz;
            y += 0.7;
            step++;
        }
    }

    /** A flat hop leaf reaching out from the winding bine. */
    static void leaf(List<Box> m, double cx, double y, double cz, double angle) {
        double dx = Math.cos(angle);
        double dz = Math.sin(angle);
        double reach = 1.5;
        double ox = cx + dx * reach;
        double oz = cz + dz * reach;
        box(m, "herb",
                Math.min(cx, ox) - 0.28, y + 0.15, Math.min(cz, oz) - 0.28,
                Math.max(cx, ox) + 0.28, y + 0.6, Math.max(cz, oz) + 0.28);
    }

    /** Short green ties from the bine to both posts, so it reads as supported. */
    static void tendrils(List<Box> m, int stage) {
        double top = TRELLIS_TOP * STAGE_HEIGHT[stage];
        for (double fraction : new double[]{0.40, 0.70, 0.92}) {
            double y = top * fraction;
            if (y < 2.0 || y > TRELLIS_TOP - 1.5) continue;
            box(m, "green", 3.0, y, 7.55, 6.7, y + 0.55, 8.45);
            box(m, "green", 9.3, y, 7.55, 13.0, y + 0.55, 8.45);
        }
    }

    // === EXISTING PLANT ===

    /**
     * Returns the raw {@code elements} array body of an existing hop model.
     * Bracket matching skips strings, so the copied text keeps the original
     * geometry, its rotations and its origins exactly as written by hand.
     */
    static String existingElements(Path path) throws Exception {
        String text = Files.readString(path, StandardCharsets.UTF_8);
        int key = text.indexOf("\"elements\"");
        if (key < 0) throw new IllegalStateException(path + " has no elements array");
        int start = text.indexOf('[', key);
        if (start < 0) throw new IllegalStateException(path + " has a broken elements array");
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escape) escape = false;
                else if (c == '\\') escape = true;
                else if (c == '"') inString = false;
                continue;
            }
            if (c == '"') inString = true;
            else if (c == '[') depth++;
            else if (c == ']') {
                depth--;
                if (depth == 0) return text.substring(start + 1, i);
            }
        }
        throw new IllegalStateException(path + " has an unterminated elements array");
    }

    /** Counts elements (one {@code "from"} key each) in the copied body. */
    static int countElements(String body) {
        int count = 0;
        int index = 0;
        while ((index = body.indexOf("\"from\"", index)) >= 0) {
            count++;
            index += 6;
        }
        return count;
    }

    // === WRITING ===

    static void writeBlockstate() throws Exception {
        StringBuilder json = new StringBuilder();
        json.append("{\n\t\"variants\": {\n");
        for (int age = 0; age <= 7; age++) {
            json.append("\t\t\"age=").append(age).append("\": {\"model\": \"samogon:block/hops_vine_stage")
                    .append(age / 2).append("\"}");
            json.append(age == 7 ? "\n" : ",\n");
        }
        json.append("\t}\n}\n");
        Path out = ASSETS.resolve("blockstates/hops.json");
        Files.createDirectories(out.getParent());
        Files.writeString(out, json.toString(), StandardCharsets.UTF_8);
        System.out.println("wrote blockstates/hops.json -> hops_vine_stage0..3");
    }

    static String element(Box b) {
        StringBuilder json = new StringBuilder();
        json.append("{\"from\":[").append(number(b.x())).append(",").append(number(b.y()))
                .append(",").append(number(b.z())).append("],\"to\":[").append(number(b.X()))
                .append(",").append(number(b.Y())).append(",").append(number(b.Z()))
                .append("],\"faces\":{");
        for (int i = 0; i < DIRECTIONS.length; i++) {
            json.append("\"").append(DIRECTIONS[i])
                    .append("\":{\"uv\":[0,0,16,16],\"texture\":\"#").append(b.material()).append("\"}");
            if (i < DIRECTIONS.length - 1) json.append(",");
        }
        json.append("}}");
        return json.toString();
    }

    static String number(double value) {
        if (value == Math.rint(value)) return Integer.toString((int) value);
        return String.format(Locale.ROOT, "%.3f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    static void box(List<Box> m, String material, double x, double y, double z,
                    double X, double Y, double Z) {
        m.add(new Box(Math.min(x, X), Math.min(y, Y), Math.min(z, Z),
                Math.max(x, X), Math.max(y, Y), Math.max(z, Z), material));
    }

    private GenerateHopsVine() {}
}