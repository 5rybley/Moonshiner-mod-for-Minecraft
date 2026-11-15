import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Offline orthographic preview of a block model that honours element rotation.
 *
 * <p>Usage: {@code java tools/RenderHops.java <models/block/hops_stage3.json> <out.png>}
 *
 * <p>Renders four views (front / side / back / three-quarter) into one strip so a
 * floating leaf or a cone that does not leave its leaf is obvious without
 * launching the game.
 */
public final class RenderHops {
    static final Map<String, Integer> MATERIALS = new LinkedHashMap<>();
    static {
        MATERIALS.put("green", 0x466849);
        MATERIALS.put("herb", 0x63874e);
        MATERIALS.put("dark_wood", 0x493426);
        MATERIALS.put("grain", 0xceb56d);
    }

    static final Pattern ELEMENT = Pattern.compile(
            "\"from\"\\s*:\\s*\\[([^\\]]+)\\]\\s*,\\s*\"to\"\\s*:\\s*\\[([^\\]]+)\\]\\s*,\\s*"
                    + "(?:\"rotation\"\\s*:\\s*\\{([^}]*)\\}\\s*,\\s*)?"
                    + "\"faces\"\\s*:\\s*\\{[^}]*?\"texture\"\\s*:\\s*\"#([a-z_0-9]+)\"",
            Pattern.DOTALL);
    static final Pattern TEXTURE = Pattern.compile(
            "\"([a-z_0-9]+)\"\\s*:\\s*\"samogon:block/materials/([a-z_0-9/]+)\"");
    static final Pattern ROT_ANGLE = Pattern.compile("\"angle\"\\s*:\\s*(-?[0-9.]+)");
    static final Pattern ROT_AXIS = Pattern.compile("\"axis\"\\s*:\\s*\"([xyz])\"");
    static final Pattern ROT_ORIGIN = Pattern.compile("\"origin\"\\s*:\\s*\\[([^\\]]+)\\]");

    static final int[][] FACE_CORNERS = {
            {2, 3, 7, 6}, {0, 4, 5, 1}, {4, 5, 7, 6}, {0, 2, 3, 1}, {1, 5, 7, 3}, {0, 4, 6, 2},
    };
    static final double[][] FACE_NORMALS = {
            {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}, {1, 0, 0}, {-1, 0, 0},
    };

    /** A box as eight world-space corners plus the element rotation, so its faces can be shaded. */
    record Box(double[][] corners, double[] rotation, String material) {}

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("usage: RenderHops <model.json> <out.png>");
            System.exit(2);
        }
        String json = Files.readString(Path.of(args[0]), StandardCharsets.UTF_8);
        Map<String, String> texMap = new LinkedHashMap<>();
        for (Matcher m = TEXTURE.matcher(json); m.find(); ) {
            texMap.put(m.group(1), m.group(2).substring(m.group(2).lastIndexOf('/') + 1));
        }
        List<Box> boxes = new ArrayList<>();
        for (Matcher m = ELEMENT.matcher(json); m.find(); ) {
            double[] rotation = rotation(m.group(3));
            String material = texMap.getOrDefault(m.group(4), m.group(4));
            boxes.add(new Box(corners(vec(m.group(1)), vec(m.group(2)), rotation), rotation, material));
        }
        if (boxes.isEmpty()) {
            System.err.println("no elements parsed from " + args[0]);
            System.exit(3);
        }
        if (args.length >= 3 && args[2].equals("ground")) {
            MATERIALS.put("ground", 0xd9c79a);
            // Farmland is 15/16 tall, so its surface is 1/16 below the crop block origin.
            boxes.add(new Box(corners(new double[]{0, -0.3, 0}, new double[]{16, -0.0625, 16}, new double[5]),
                    new double[5], "ground"));
        }
        double[][] views = {{0, 15}, {55, 0}, {0, 195}, {35, 55}};
        String[] names = {"front", "side", "back", "3/4"};
        int cell = args.length >= 4 ? Integer.parseInt(args[3]) : 220;
        BufferedImage strip = new BufferedImage(cell * views.length, cell + 18, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = strip.createGraphics();
        g.setColor(new Color(0xE9D7B0));
        g.fillRect(0, 0, strip.getWidth(), strip.getHeight());
        g.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        for (int i = 0; i < views.length; i++) {
            g.drawImage(render(boxes, views[i][0], views[i][1], cell), i * cell, 0, null);
            g.setColor(new Color(0x6B4E36));
            g.drawString(names[i], i * cell + 6, cell + 13);
            g.setColor(new Color(0xC9AD82));
            g.drawLine((i + 1) * cell, 0, (i + 1) * cell, cell);
        }
        g.dispose();
        Path out = Path.of(args[1]);
        Files.createDirectories(out.toAbsolutePath().getParent());
        ImageIO.write(strip, "png", out.toFile());
        System.out.println("wrote " + out + "  boxes=" + boxes.size());
    }

    /** {@code {angle, axisIndex, ox, oy, oz}}; zero angle means no rotation. */
    static double[] rotation(String text) {
        if (text == null) return new double[]{0, 1, 0, 0, 0};
        Matcher angle = ROT_ANGLE.matcher(text);
        if (!angle.find()) return new double[]{0, 1, 0, 0, 0};
        Matcher axis = ROT_AXIS.matcher(text);
        double[] origin = new double[3];
        Matcher m = ROT_ORIGIN.matcher(text);
        if (m.find()) origin = vec(m.group(1));
        return new double[]{Double.parseDouble(angle.group(1)), axis.find() ? "xyz".indexOf(axis.group(1)) : 1,
                origin[0], origin[1], origin[2]};
    }

    static double[][] corners(double[] from, double[] to, double[] rotation) {
        double[][] c = new double[8][3];
        for (int i = 0; i < 8; i++) {
            c[i][0] = (i & 1) == 0 ? from[0] : to[0];
            c[i][1] = (i & 2) == 0 ? from[1] : to[1];
            c[i][2] = (i & 4) == 0 ? from[2] : to[2];
        }
        if (rotation[0] != 0) {
            for (double[] p : c) rotateAbout(p, rotation, new double[]{rotation[2], rotation[3], rotation[4]});
        }
        return c;
    }

    /** Rotates a point around {@code origin} about the rotation's axis by its angle. */
    static double[] rotateAbout(double[] p, double[] rotation, double[] origin) {
        double rad = Math.toRadians(rotation[0]), cos = Math.cos(rad), sin = Math.sin(rad);
        double x = p[0] - origin[0], y = p[1] - origin[1], z = p[2] - origin[2];
        double nx, ny, nz;
        if ((int) rotation[1] == 0) {
            nx = x; ny = y * cos - z * sin; nz = y * sin + z * cos;
        } else if ((int) rotation[1] == 1) {
            nx = x * cos + z * sin; ny = y; nz = -x * sin + z * cos;
        } else {
            nx = x * cos - y * sin; ny = x * sin + y * cos; nz = z;
        }
        p[0] = nx + origin[0];
        p[1] = ny + origin[1];
        p[2] = nz + origin[2];
        return p;
    }

    static BufferedImage render(List<Box> boxes, double yaw, double pitch, int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Color bg = new Color(0xE9D7B0);
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) img.setRGB(x, y, bg.getRGB());
        float[] zbuf = new float[size * size];
        java.util.Arrays.fill(zbuf, -Float.MAX_VALUE);
        double ry = Math.toRadians(yaw), rx = Math.toRadians(pitch);
        double[] light = normalize(new double[]{0.35, 0.8, 0.45});
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        List<double[][]> projected = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        for (Box b : boxes) {
            for (int f = 0; f < 6; f++) {
                double[] worldNormal = rotateAbout(FACE_NORMALS[f].clone(), b.rotation(), new double[3]);
                double[] n = view(worldNormal, ry, rx);
                if (n[2] <= 1e-6) continue;
                double[][] quad = new double[4][3];
                for (int i = 0; i < 4; i++) {
                    double[] v = view(sub(b.corners()[FACE_CORNERS[f][i]], 8, 8, 8), ry, rx);
                    quad[i] = new double[]{v[0], -v[1], v[2]};
                    minX = Math.min(minX, quad[i][0]);
                    maxX = Math.max(maxX, quad[i][0]);
                    minY = Math.min(minY, quad[i][1]);
                    maxY = Math.max(maxY, quad[i][1]);
                }
                Integer base = MATERIALS.get(b.material());
                double shade = 0.6 + 0.45 * Math.max(0, dot(worldNormal, light));
                Color color = new Color(base == null ? 0xff00ff : base);
                projected.add(quad);
                colors.add(new Color(clamp((int) (color.getRed() * shade)),
                        clamp((int) (color.getGreen() * shade)),
                        clamp((int) (color.getBlue() * shade))).getRGB());
            }
        }
        double span = Math.max(maxX - minX, maxY - minY);
        double margin = size * 0.12;
        double scale = (size - 2 * margin) / Math.max(1e-6, span);
        double offX = (size - (maxX - minX) * scale) / 2.0 - minX * scale;
        double offY = (size - (maxY - minY) * scale) / 2.0 - minY * scale;
        for (int i = 0; i < projected.size(); i++) {
            double[][] q = projected.get(i);
            double[][] s = new double[4][3];
            for (int j = 0; j < 4; j++) {
                s[j][0] = q[j][0] * scale + offX;
                s[j][1] = q[j][1] * scale + offY;
                s[j][2] = q[j][2];
            }
            int rgb = colors.get(i);
            triangle(img, zbuf, size, s[0][0], s[0][1], s[0][2], s[1][0], s[1][1], s[1][2], s[2][0], s[2][1], s[2][2], rgb);
            triangle(img, zbuf, size, s[0][0], s[0][1], s[0][2], s[2][0], s[2][1], s[2][2], s[3][0], s[3][1], s[3][2], rgb);
        }
        return img;
    }

    static double[] view(double[] p, double ry, double rx) {
        double x = p[0] * Math.cos(ry) + p[2] * Math.sin(ry);
        double z = -p[0] * Math.sin(ry) + p[2] * Math.cos(ry);
        double y = p[1] * Math.cos(rx) - z * Math.sin(rx);
        double z2 = p[1] * Math.sin(rx) + z * Math.cos(rx);
        return new double[]{x, y, z2};
    }

    static double[] sub(double[] p, double x, double y, double z) {
        return new double[]{p[0] - x, p[1] - y, p[2] - z};
    }

    static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    static double[] normalize(double[] v) {
        double length = Math.sqrt(dot(v, v));
        return new double[]{v[0] / length, v[1] / length, v[2] / length};
    }

    static void triangle(BufferedImage image, float[] zbuf, int size,
                         double ax, double ay, double az, double bx, double by, double bz,
                         double cx, double cy, double cz, int rgb) {
        int minX = (int) Math.max(0, Math.floor(Math.min(ax, Math.min(bx, cx))));
        int maxX = (int) Math.min(size - 1, Math.ceil(Math.max(ax, Math.max(bx, cx))));
        int minY = (int) Math.max(0, Math.floor(Math.min(ay, Math.min(by, cy))));
        int maxY = (int) Math.min(size - 1, Math.ceil(Math.max(ay, Math.max(by, cy))));
        double denom = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy);
        if (Math.abs(denom) < 1e-9) return;
        for (int py = minY; py <= maxY; py++) {
            for (int px = minX; px <= maxX; px++) {
                double sx = px + 0.5, sy = py + 0.5;
                double w0 = ((by - cy) * (sx - cx) + (cx - bx) * (sy - cy)) / denom;
                double w1 = ((cy - ay) * (sx - cx) + (ax - cx) * (sy - cy)) / denom;
                double w2 = 1 - w0 - w1;
                if (w0 < -1e-6 || w1 < -1e-6 || w2 < -1e-6) continue;
                double depth = w0 * az + w1 * bz + w2 * cz;
                if (depth > zbuf[py * size + px]) {
                    zbuf[py * size + px] = (float) depth;
                    image.setRGB(px, py, rgb);
                }
            }
        }
    }

    static double[] vec(String csv) {
        String[] parts = csv.trim().split(",");
        return new double[]{Double.parseDouble(parts[0].trim()), Double.parseDouble(parts[1].trim()),
                Double.parseDouble(parts[2].trim())};
    }

    static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    private RenderHops() {}
}
