import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
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
 * Read-only preview renderer for a single item model. It parses the model's
 * boxes and textures, applies the same display rotation the game uses for the
 * GUI slot (a rotationXYZ quaternion) and rasterises an orthographic view with
 * a depth buffer. Used to check whether details such as a mug handle or the
 * coloured fill actually face the camera in the inventory / guide icon.
 *
 * <p>Usage: {@code java tools/RenderItem.java <model.json> <out.png> [rotX rotY rotZ] [size]}</p>
 */
public final class RenderItem {

    static final Map<String, Integer> MATERIALS = new LinkedHashMap<>();

    static void material(String name, int color) {
        MATERIALS.put(name, color);
    }

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

    static final Pattern ELEMENT = Pattern.compile(
            "\"from\"\\s*:\\s*\\[([^\\]]+)\\]\\s*,\\s*\"to\"\\s*:\\s*\\[([^\\]]+)\\]"
                    + "\\s*,\\s*\"faces\"\\s*:\\s*\\{[^}]*?\"texture\"\\s*:\\s*\"#([a-z_0-9]+)\"",
            Pattern.DOTALL);
    static final Pattern TEXTURE = Pattern.compile(
            "\"([a-z_0-9]+)\"\\s*:\\s*\"samogon:block/materials/([a-z_0-9/]+)\"");
    static final Pattern DISPLAY_ROT = Pattern.compile(
            "\"gui\"\\s*:\\s*\\{[^}]*?\"rotation\"\\s*:\\s*\\[\\s*(-?[0-9.]+)\\s*,\\s*(-?[0-9.]+)\\s*,\\s*(-?[0-9.]+)");

    record Box(double x, double y, double z, double X, double Y, double Z, String material) {}

    record Face(double[][] v, int rgb) {}

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("usage: RenderItem <model.json> <out.png> [rotX rotY rotZ] [size]");
            System.exit(2);
        }
        String json = Files.readString(Path.of(args[0]), StandardCharsets.UTF_8);
        Path out = Path.of(args[1]);
        int size = args.length >= 7 ? Integer.parseInt(args[6]) : 160;

        double rx = 20.0, ry = 45.0, rz = 0.0;
        if (args.length >= 5) {
            rx = Double.parseDouble(args[2]);
            ry = Double.parseDouble(args[3]);
            rz = Double.parseDouble(args[4]);
        } else {
            Matcher m = DISPLAY_ROT.matcher(json);
            if (m.find()) {
                rx = Double.parseDouble(m.group(1));
                ry = Double.parseDouble(m.group(2));
                rz = Double.parseDouble(m.group(3));
            }
        }

        Map<String, String> texMap = new LinkedHashMap<>();
        for (Matcher m = TEXTURE.matcher(json); m.find(); ) {
            texMap.put(m.group(1), m.group(2).substring(m.group(2).lastIndexOf('/') + 1));
        }

        List<Box> boxes = new ArrayList<>();
        for (Matcher m = ELEMENT.matcher(json); m.find(); ) {
            String[] f = m.group(1).trim().split(",");
            String[] t = m.group(2).trim().split(",");
            String key = m.group(3);
            String material = texMap.getOrDefault(key, key);
            boxes.add(new Box(d(f[0]), d(f[1]), d(f[2]), d(t[0]), d(t[1]), d(t[2]), material));
        }
        if (boxes.isEmpty()) {
            System.err.println("no elements parsed from " + args[0]);
            System.exit(3);
        }

        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(0xE9D7B0));
        g.fillRect(0, 0, size, size);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(rasteriseWithBackground(boxes, rx, ry, rz, size), 0, 0, null);
        g.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        g.setColor(new Color(0x6B4E36));
        g.drawString(String.format("gui rot [%.0f, %.0f, %.0f]", rx, ry, rz), 5, size - 5);
        g.dispose();
        Files.createDirectories(out.toAbsolutePath().getParent());
        ImageIO.write(image, "png", out.toFile());
        System.out.println("wrote " + out + "  boxes=" + boxes.size());
    }

    static BufferedImage rasteriseWithBackground(List<Box> boxes, double rx, double ry, double rz, int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        int w = img.getWidth(), h = img.getHeight();
        double[] q = quaternionXYZ(rx, ry, rz);

        List<Face> faces = new ArrayList<>();
        for (Box b : boxes) {
            addFace(faces, q, b, 0, 1, 0, b.Y, b.material, 1.08);   // up
            addFace(faces, q, b, 0, -1, 0, b.y, b.material, 0.55);  // down
            addFace(faces, q, b, 0, 0, 1, b.Z, b.material, 0.86);   // south
            addFace(faces, q, b, 0, 0, -1, b.z, b.material, 0.66);  // north
            addFace(faces, q, b, 1, 0, 0, b.X, b.material, 0.78);   // east
            addFace(faces, q, b, -1, 0, 0, b.x, b.material, 0.70);  // west
        }

        // Fit projected coordinates.
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (Face f : faces) for (double[] p : f.v) {
            minX = Math.min(minX, p[0]); maxX = Math.max(maxX, p[0]);
            minY = Math.min(minY, p[1]); maxY = Math.max(maxY, p[1]);
        }
        double span = Math.max(maxX - minX, maxY - minY);
        double margin = size * 0.10;
        double scale = (size - 2 * margin) / Math.max(1e-6, span);
        double offX = (size - (maxX - minX) * scale) / 2.0 - minX * scale;
        double offY = (size - (maxY - minY) * scale) / 2.0 - minY * scale;

        float[] zbuf = new float[w * h];
        java.util.Arrays.fill(zbuf, Float.NEGATIVE_INFINITY);
        for (Face f : faces) {
            double[] px = new double[4], py = new double[4], pz = new double[4];
            for (int i = 0; i < 4; i++) {
                px[i] = f.v[i][0] * scale + offX;
                py[i] = f.v[i][1] * scale + offY;
                pz[i] = f.v[i][2];
            }
            triangle(img, zbuf, w, h, px[0], py[0], pz[0], px[1], py[1], pz[1], px[2], py[2], pz[2], f.rgb);
            triangle(img, zbuf, w, h, px[0], py[0], pz[0], px[2], py[2], pz[2], px[3], py[3], pz[3], f.rgb);
        }
        return img;
    }

    static void addFace(List<Face> faces, double[] q, Box b, int nx, int ny, int nz, double plane,
                        String material, double light) {
        double[] n = rotate(q, nx, ny, nz);
        if (n[2] <= 1e-6) return; // back-facing
        double[][] v;
        if (ny != 0) {
            v = new double[][]{{b.x, plane, b.z}, {b.X, plane, b.z}, {b.X, plane, b.Z}, {b.x, plane, b.Z}};
        } else if (nz != 0) {
            v = new double[][]{{b.x, b.y, plane}, {b.X, b.y, plane}, {b.X, b.Y, plane}, {b.x, b.Y, plane}};
        } else {
            v = new double[][]{{plane, b.y, b.z}, {plane, b.y, b.Z}, {plane, b.Y, b.Z}, {plane, b.Y, b.z}};
        }
        double[][] r = new double[4][3];
        for (int i = 0; i < 4; i++) {
            double[] p = rotate(q, v[i][0] - 8, v[i][1] - 8, v[i][2] - 8);
            r[i][0] = p[0];
            r[i][1] = -p[1]; // screen Y down
            r[i][2] = p[2];
        }
        Integer packed = MATERIALS.get(material);
        Color base = new Color(packed == null ? 0xff00ff : packed);
        int rgb = new Color(clamp((int) (base.getRed() * light)),
                clamp((int) (base.getGreen() * light)),
                clamp((int) (base.getBlue() * light)), 255).getRGB();
        faces.add(new Face(r, rgb));
    }

    static double[] quaternionXYZ(double ax, double ay, double az) {
        double hx = Math.toRadians(ax) / 2, hy = Math.toRadians(ay) / 2, hz = Math.toRadians(az) / 2;
        double sx = Math.sin(hx), cx = Math.cos(hx);
        double sy = Math.sin(hy), cy = Math.cos(hy);
        double sz = Math.sin(hz), cz = Math.cos(hz);
        // q = qx * qy * qz
        double[] qx = {sx, 0, 0, cx};
        double[] qy = {0, sy, 0, cy};
        double[] qz = {0, 0, sz, cz};
        return mul(mul(qx, qy), qz); // {x,y,z,w}
    }

    static double[] mul(double[] a, double[] b) {
        return new double[]{
                a[3] * b[0] + a[0] * b[3] + a[1] * b[2] - a[2] * b[1],
                a[3] * b[1] - a[0] * b[2] + a[1] * b[3] + a[2] * b[0],
                a[3] * b[2] + a[0] * b[1] - a[1] * b[0] + a[2] * b[3],
                a[3] * b[3] - a[0] * b[0] - a[1] * b[1] - a[2] * b[2]
        };
    }

    static double[] rotate(double[] q, double x, double y, double z) {
        // v' = v + 2*q.xyz x (q.xyz x v + q.w*v)
        double qx = q[0], qy = q[1], qz = q[2], qw = q[3];
        double tx = 2 * (qy * z - qz * y);
        double ty = 2 * (qz * x - qx * z);
        double tz = 2 * (qx * y - qy * x);
        return new double[]{
                x + qw * tx + (qy * tz - qz * ty),
                y + qw * ty + (qz * tx - qx * tz),
                z + qw * tz + (qx * ty - qy * tx)
        };
    }

    static void triangle(BufferedImage image, float[] zbuf, int w, int h,
                         double ax, double ay, double az, double bx, double by, double bz,
                         double cx, double cy, double cz, int rgb) {
        double minX = Math.max(0, Math.floor(Math.min(ax, Math.min(bx, cx))));
        double maxX = Math.min(w - 1, Math.ceil(Math.max(ax, Math.max(bx, cx))));
        double minY = Math.max(0, Math.floor(Math.min(ay, Math.min(by, cy))));
        double maxY = Math.min(h - 1, Math.ceil(Math.max(ay, Math.max(by, cy))));
        double denom = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy);
        if (Math.abs(denom) < 1e-9) return;
        for (int py = (int) minY; py <= (int) maxY; py++) {
            double sy = py + 0.5;
            for (int px = (int) minX; px <= (int) maxX; px++) {
                double sx = px + 0.5;
                double w0 = ((by - cy) * (sx - cx) + (cx - bx) * (sy - cy)) / denom;
                double w1 = ((cy - ay) * (sx - cx) + (ax - cx) * (sy - cy)) / denom;
                double w2 = 1.0 - w0 - w1;
                if (w0 < -1e-6 || w1 < -1e-6 || w2 < -1e-6) continue;
                double depth = w0 * az + w1 * bz + w2 * cz;
                int index = py * w + px;
                if (depth > zbuf[index]) {
                    zbuf[index] = (float) depth;
                    image.setRGB(px, py, rgb);
                }
            }
        }
    }

    static double d(String s) {
        return Double.parseDouble(s.trim());
    }

    static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    private RenderItem() {}
}