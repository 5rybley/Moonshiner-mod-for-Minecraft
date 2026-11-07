import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Read-only reference renderer for the 2x2x2 aging barrel. Unlike
 * GenerateBarrel (which authors the geometry), this tool parses the
 * already-generated octant block models, re-assembles all eight into the full
 * 32x32x32 footprint for each facing and rasterises isometric previews.
 * Rendering the shipped assets doubles as a check that the eight octants and
 * the four facings really line up.
 */
public final class RenderBarrel {

    record Box(double x, double y, double z, double X, double Y, double Z, String material) {}

    static final Map<String, Integer> MATERIALS = new LinkedHashMap<>();

    static void material(String name, int color) { MATERIALS.put(name, color); }

    static {
        material("copper", 0xb86a3e);
        material("copper_light", 0xe69b61);
        material("copper_dark", 0x73402d);
        material("patina", 0x3d8273);
        material("patina_light", 0x5fa898);
        material("brass", 0xc9a24a);
        material("iron", 0x9caeb1);
        material("iron_dark", 0x3c4c51);
        material("oak", 0x8d6036);
        material("oak_light", 0xb78249);
        material("oak_end", 0x9d7144);
        material("dark_wood", 0x493426);
        material("glass", 0x8cbdc5);
        material("glass_light", 0xd6eff0);
        material("water", 0x4f92b1);
        material("clear", 0x9fc5b6);
        material("amber", 0xa36520);
        material("red", 0x8f3744);
        material("green", 0x466849);
        material("cream", 0xe5d3a7);
        material("ink", 0x243640);
        material("black", 0x232a2c);
        material("charcoal", 0x353b3b);
        material("grain", 0xceb56d);
        material("herb", 0x63874e);
        material("honey", 0xd89b31);
        material("white", 0xf1e6cf);
        material("purple", 0x5e416b);
        material("blue", 0x426e92);
        material("cork", 0xad8655);
        material("paper", 0xd4b778);
        material("gauge", 0xeadcb9);
        material("yeast_label", 0x788c64);
        material("berry_label", 0x914553);
        material("seal", 0x913b36);
    }

    private static final Path ASSETS = Path.of("src/main/resources/assets/samogon");
    private static final String[] FACINGS = {"north", "east", "south", "west"};

    private static final Pattern ELEMENT = Pattern.compile(
            "\"from\"\\s*:\\s*\\[([^\\]]+)\\]\\s*,\\s*\"to\"\\s*:\\s*\\[([^\\]]+)\\]"
                    + "\\s*,\\s*\"faces\"\\s*:\\s*\\{[^}]*?\"texture\"\\s*:\\s*\"#([a-z_]+)\"",
            Pattern.DOTALL);

    public static void main(String[] args) throws Exception {
        Map<String, List<Box>[]> octants = new LinkedHashMap<>();
        for (String facing : FACINGS) {
            List<Box>[] parts = new List[8];
            for (int part = 0; part < 8; part++) {
                parts[part] = parse(ASSETS.resolve(modelName(facing, part)));
            }
            octants.put(facing, parts);
            System.out.println(facing + ": " + assemble(parts).size() + " boxes across 8 octants ("
                    + bounds(assemble(parts)) + ")");
        }
        List<Box> item = parse(ASSETS.resolve("models/item/aging_barrel.json"));

        Path out = Path.of("previews");
        Files.createDirectories(out);
        writeReference(out.resolve("aging_barrel_reference.png"), octants, item);
        System.out.println("Rendered " + out.resolve("aging_barrel_reference.png").toAbsolutePath());
    }

    private static String modelName(String facing, int part) {
        return "models/block/aging_barrel_" + facing + "_" + part + ".json";
    }

    // === MODEL IO ===

    private static List<Box> parse(Path path) throws Exception {
        String json = Files.readString(path, StandardCharsets.UTF_8);
        List<Box> boxes = new ArrayList<>();
        Matcher m = ELEMENT.matcher(json);
        while (m.find()) {
            double[] from = vec(m.group(1));
            double[] to = vec(m.group(2));
            boxes.add(new Box(Math.min(from[0], to[0]), Math.min(from[1], to[1]), Math.min(from[2], to[2]),
                    Math.max(from[0], to[0]), Math.max(from[1], to[1]), Math.max(from[2], to[2]), m.group(3)));
        }
        return boxes;
    }

    private static double[] vec(String text) {
        String[] parts = text.split(",");
        double[] out = new double[3];
        for (int i = 0; i < 3; i++) out[i] = Double.parseDouble(parts[i].trim());
        return out;
    }

    /** Places every octant back into the shared 32x32x32 footprint (index dx*4+dy*2+dz). */
    private static List<Box> assemble(List<Box>[] parts) {
        List<Box> full = new ArrayList<>();
        for (int part = 0; part < 8; part++) {
            double dx = (part >> 2 & 1) * 16.0;
            double dy = (part >> 1 & 1) * 16.0;
            double dz = (part & 1) * 16.0;
            for (Box b : parts[part]) {
                full.add(new Box(b.x + dx, b.y + dy, b.z + dz, b.X + dx, b.Y + dy, b.Z + dz, b.material));
            }
        }
        return full;
    }

    private static String bounds(List<Box> boxes) {
        double x0 = 1e9, y0 = 1e9, z0 = 1e9, x1 = -1e9, y1 = -1e9, z1 = -1e9;
        for (Box b : boxes) {
            x0 = Math.min(x0, b.x); y0 = Math.min(y0, b.y); z0 = Math.min(z0, b.z);
            x1 = Math.max(x1, b.X); y1 = Math.max(y1, b.Y); z1 = Math.max(z1, b.Z);
        }
        return String.format(Locale.ROOT, "x[%.2f..%.2f] y[%.2f..%.2f] z[%.2f..%.2f]", x0, x1, y0, y1, z0, z1);
    }

    // === COMPOSITION ===

    private static void writeReference(Path path, Map<String, List<Box>[]> octants, List<Box> item)
            throws Exception {
        BufferedImage image = new BufferedImage(1500, 1320, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setPaint(new GradientPaint(0, 0, new Color(0x16272f), 0, 1320, new Color(0x0c161a)));
        g.fillRect(0, 0, 1500, 1320);

        g.setFont(new Font("Segoe UI", Font.BOLD, 40));
        g.setColor(new Color(0xe2b783));
        g.drawString("SAMOGON  /  AGING BARREL  \u2014  2\u00d72\u00d72, LIED ON ITS SIDE", 52, 64);
        g.setFont(new Font("Segoe UI", Font.PLAIN, 18));
        g.setColor(new Color(0x9cb2b7));
        g.drawString("eight blocks \u00b7 circular section 2\u00d72 upright, 2 blocks long \u00b7 standing on four legs \u00b7 the axis turns with the four facings", 54, 96);

        // Row 1: the same cask assembled for each facing.
        int cardW = 356;
        for (int i = 0; i < FACINGS.length; i++) {
            String facing = FACINGS[i];
            int x = 32 + i * (cardW + 20);
            panel(g, x, 118, cardW, 556, "facing=" + facing,
                    "axis points " + facing + " \u00b7 tap side " + (i == 0 ? "towards viewer" : "rotated"));
            shadow(g, x + 40, 560, cardW - 80, 74);
            render(g, assemble(octants.get(facing)), x + 6, 152, cardW - 12, 470, cardW / 2.0, 245, 5.6);
            g.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            g.setColor(new Color(0x8fa8ae));
            g.drawString("8 blocks \u00b7 part 0..7", x + 22, 648);
        }

        // Row 2: the eight octants of one facing, exactly as each block ships them.
        g.setFont(new Font("Segoe UI", Font.BOLD, 22));
        g.setColor(new Color(0xcdbc98));
        g.drawString("THE EIGHT BLOCK MODELS  (facing=north)", 32, 726);
        g.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        g.setColor(new Color(0x8098a0));
        g.drawString("each 16\u00b3 octant is one eighth of the master drawing, so all eight click together in the world", 32, 748);
        List<Box>[] north = octants.get("north");
        for (int part = 0; part < 8; part++) {
            int x = 14 + part * 186;
            g.setColor(new Color(0x18292f));
            g.fillRoundRect(x, 762, 178, 268, 12, 12);
            g.setColor(new Color(0x243e48));
            g.drawRoundRect(x, 762, 178, 268, 12, 12);
            render(g, north[part], x + 6, 768, 166, 210, 83, 104, 5.5);
            g.setFont(new Font("Segoe UI", Font.BOLD, 14));
            g.setColor(new Color(0xe7d4b4));
            g.drawString("part=" + part, x + 12, 1000);
            g.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            g.setColor(new Color(0x8fa8ae));
            g.drawString("d=" + ((part >> 2 & 1)) + ((part >> 1 & 1)) + ((part & 1))
                    + " \u00b7 " + north[part].size() + " boxes", x + 12, 1020);
        }

        // Bottom band: a plain side elevation (so the legs read clearly) and the item model.
        panel(g, 32, 1046, 1436, 250, "SIDE VIEW & INVENTORY ITEM",
                "left: the cask seen from the side, standing on its legs \u00b7 right: the item model");
        renderElevation(g, assemble(octants.get("west")), 50, 1092, 340, 190, 32.0);
        shadow(g, 500, 1236, 210, 36);
        render(g, item, 452, 1092, 300, 150, 150, 80, 3.5);
        g.setFont(new Font("Segoe UI", Font.BOLD, 21));
        g.setColor(new Color(0xe7d4b4));
        g.drawString("SAMOGON:AGING_BARREL", 790, 1122);
        g.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        g.setColor(new Color(0x9cb2b7));
        g.drawString("Lies on its side in a 2\u00d72\u00d72 cube; one craft yields 8 sections.", 790, 1152);
        g.drawString("Master block is the lowest corner (min x, min y, min z); the other seven delegate to it.", 790, 1176);
        g.drawString("Oak body with iron hoops, a corked bung, a copper tap, a brass plaque and four dark legs.", 790, 1200);
        g.setColor(new Color(0x8fa8ae));
        g.drawString("The four legs and their foot rails carry the cask so the rounded belly never touches the ground.", 790, 1232);

        g.dispose();
        ImageIO.write(image, "PNG", path.toFile());
    }

    /** Flat orthographic side view (looking along +Z) so the legs are unambiguous. */
    static void renderElevation(Graphics2D g, List<Box> boxes, int x, int y, int width, int height,
                                double units) {
        double scale = Math.min(width / units, height / units);
        double ox = x + (width - units * scale) / 2.0;
        double oy = y + (height - units * scale) / 2.0;
        List<Box> sorted = new ArrayList<>(boxes);
        sorted.sort((a, b) -> Double.compare(b.z, a.z));
        for (Box b : sorted) {
            Color base = new Color(MATERIALS.getOrDefault(b.material, 0xff00ff));
            double shade = 0.58 + 0.42 * (1.0 - Math.min(1.0, b.z / 32.0));
            int rgb = new Color(clamp((int) (base.getRed() * shade)),
                    clamp((int) (base.getGreen() * shade)),
                    clamp((int) (base.getBlue() * shade))).getRGB();
            int x0 = (int) Math.round(ox + b.x * scale);
            int x1 = (int) Math.round(ox + b.X * scale);
            int y0 = (int) Math.round(oy + (units - b.Y) * scale);
            int y1 = (int) Math.round(oy + (units - b.y) * scale);
            g.setColor(new Color(rgb));
            g.fillRect(x0, y0, Math.max(1, x1 - x0), Math.max(1, y1 - y0));
        }
    }

    private static void panel(Graphics2D g, int x, int y, int w, int h, String title, String note) {
        g.setColor(new Color(0x1b2d35));
        g.fillRoundRect(x, y, w, h, 20, 20);
        g.setColor(new Color(0x233d47));
        g.drawRoundRect(x, y, w, h, 20, 20);
        g.setFont(new Font("Segoe UI", Font.BOLD, 19));
        g.setColor(new Color(0xcdbc98));
        g.drawString(title, x + 20, y + 32);
        g.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        g.setColor(new Color(0x8098a0));
        g.drawString(note, x + 20, y + 52);
    }

    private static void shadow(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(new Color(0, 0, 0, 80));
        g.fillOval(x, y, w, h);
    }

    // === SOFTWARE RASTERISER (top / north / west visible) ===

    record Face(double[][] vertices, String material, double light) {}

    static void render(Graphics2D g, List<Box> boxes, int x, int y, int width, int height,
                       double cx, double cy, double scale) {
        int ss = 2;
        int w = width * ss, h = height * ss;
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        float[] depthBuffer = new float[w * h];
        Arrays.fill(depthBuffer, Float.POSITIVE_INFINITY);
        List<Face> faces = new ArrayList<>();
        for (Box b : boxes) {
            face(faces, b.material, 1.12, new double[][]{
                    {b.x, b.Y, b.z}, {b.X, b.Y, b.z}, {b.X, b.Y, b.Z}, {b.x, b.Y, b.Z}});
            face(faces, b.material, 0.84, new double[][]{
                    {b.x, b.y, b.z}, {b.X, b.y, b.z}, {b.X, b.Y, b.z}, {b.x, b.Y, b.z}});
            face(faces, b.material, 0.66, new double[][]{
                    {b.x, b.y, b.Z}, {b.x, b.y, b.z}, {b.x, b.Y, b.z}, {b.x, b.Y, b.Z}});
        }
        for (Face f : faces) {
            double[][] p = new double[4][3];
            for (int i = 0; i < 4; i++) {
                double[] v = f.vertices[i];
                p[i][0] = (cx + (v[0] - v[2]) * scale * 0.866) * ss;
                p[i][1] = (cy + ((v[0] + v[2]) * 0.5 - v[1]) * scale) * ss;
                p[i][2] = v[0] - v[1] + v[2];
            }
            Integer packed = MATERIALS.get(f.material);
            Color base = new Color(packed == null ? 0xff00ff : packed);
            int rgb = new Color(clamp((int) (base.getRed() * f.light)),
                    clamp((int) (base.getGreen() * f.light)),
                    clamp((int) (base.getBlue() * f.light))).getRGB();
            triangle(image, depthBuffer, w, h, p[0], p[1], p[2], rgb);
            triangle(image, depthBuffer, w, h, p[0], p[2], p[3], rgb);
        }
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(image, x, y, width, height, null);
    }

    static void triangle(BufferedImage image, float[] zbuf, int w, int h,
                         double[] a, double[] b, double[] c, int rgb) {
        double minX = Math.max(0, Math.floor(Math.min(a[0], Math.min(b[0], c[0]))));
        double maxX = Math.min(w - 1, Math.ceil(Math.max(a[0], Math.max(b[0], c[0]))));
        double minY = Math.max(0, Math.floor(Math.min(a[1], Math.min(b[1], c[1]))));
        double maxY = Math.min(h - 1, Math.ceil(Math.max(a[1], Math.max(b[1], c[1]))));
        double denom = (b[1] - c[1]) * (a[0] - c[0]) + (c[0] - b[0]) * (a[1] - c[1]);
        if (Math.abs(denom) < 1e-9) return;
        for (int py = (int) minY; py <= (int) maxY; py++) {
            double sy = py + 0.5;
            for (int px = (int) minX; px <= (int) maxX; px++) {
                double sx = px + 0.5;
                double w0 = ((b[1] - c[1]) * (sx - c[0]) + (c[0] - b[0]) * (sy - c[1])) / denom;
                double w1 = ((c[1] - a[1]) * (sx - c[0]) + (a[0] - c[0]) * (sy - c[1])) / denom;
                double w2 = 1.0 - w0 - w1;
                if (w0 < -1e-6 || w1 < -1e-6 || w2 < -1e-6) continue;
                double depth = w0 * a[2] + w1 * b[2] + w2 * c[2];
                int index = py * w + px;
                if (depth < zbuf[index]) {
                    zbuf[index] = (float) depth;
                    image.setRGB(px, py, rgb);
                }
            }
        }
    }

    static void face(List<Face> faces, String material, double light, double[][] vertices) {
        faces.add(new Face(vertices, material, light));
    }

    static int clamp(int value) { return Math.max(0, Math.min(255, value)); }

    private RenderBarrel() {}
}
