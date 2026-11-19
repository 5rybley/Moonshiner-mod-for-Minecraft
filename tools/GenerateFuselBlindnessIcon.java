import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Bakes the icon of the "Fusel Blindness" effect: an eye crossed out with a
 * red bar, so the badge reads at a glance as "you cannot see".
 *
 * <p>Run from the project root with JDK 21:
 * {@code java tools/GenerateFuselBlindnessIcon.java}</p>
 *
 * <p>This tool writes exactly one file and never touches the icons produced by
 * {@code GenerateNewAssets.java} or {@code GenerateAfterBanyaIcon.java}.</p>
 */
public final class GenerateFuselBlindnessIcon {
    private static final Path OUT = Path.of(
            "src/main/resources/assets/samogon/textures/mob_effect/fusel_blindness.png");

    private static final int OUTLINE = 0x241b33;
    private static final int SCLERA = 0xefe6d2;
    private static final int IRIS = 0x5a4a72;
    private static final int PUPIL = 0x1d1630;
    private static final int HIGHLIGHT = 0xfff8e8;
    private static final int RED = 0xe24b3f;
    private static final int RED_DARK = 0x7e241c;

    private GenerateFuselBlindnessIcon() {}

    public static void main(String[] args) throws Exception {
        BufferedImage img = new BufferedImage(18, 18, BufferedImage.TYPE_INT_ARGB);

        // The almond eye: a cream sclera inside a dark outline.
        double cx = 8.5;
        double cy = 9.5;
        double a = 6.2;
        double b = 4.4;
        for (int y = 0; y < 18; y++) {
            for (int x = 0; x < 18; x++) {
                double d = sq((x + 0.5 - cx) / a) + sq((y + 0.5 - cy) / b);
                if (d > 1.0 && d <= 1.45) {
                    px(img, x, y, OUTLINE);
                } else if (d <= 1.0) {
                    px(img, x, y, SCLERA);
                }
            }
        }

        // Iris and pupil.
        for (int y = 0; y < 18; y++) {
            for (int x = 0; x < 18; x++) {
                double d = Math.hypot(x + 0.5 - cx, y + 0.5 - cy);
                if (d <= 3.0) {
                    px(img, x, y, IRIS);
                }
                if (d <= 1.9) {
                    px(img, x, y, PUPIL);
                }
            }
        }
        px(img, 7, 8, HIGHLIGHT);

        // The stroke that crosses it out: a thin red bar through the eye.
        for (int y = 0; y < 18; y++) {
            for (int x = 0; x < 18; x++) {
                double dist = distToSegment(x + 0.5, y + 0.5, 4.0, 14.5, 14.5, 4.0);
                if (dist <= 0.62) {
                    px(img, x, y, RED);
                } else if (dist <= 1.20) {
                    px(img, x, y, RED_DARK);
                }
            }
        }

        Files.createDirectories(OUT.getParent());
        ImageIO.write(img, "PNG", OUT.toFile());
        System.out.println("wrote " + OUT);
    }

    private static double sq(double value) {
        return value * value;
    }

    /** Distance from point (px,py) to the segment (ax,ay)-(bx,by). */
    private static double distToSegment(double px, double py, double ax, double ay, double bx, double by) {
        double dx = bx - ax;
        double dy = by - ay;
        double lengthSq = dx * dx + dy * dy;
        double t = lengthSq == 0.0 ? 0.0 : ((px - ax) * dx + (py - ay) * dy) / lengthSq;
        t = Math.max(0.0, Math.min(1.0, t));
        double ox = ax + t * dx;
        double oy = ay + t * dy;
        return Math.hypot(px - ox, py - oy);
    }

    private static void px(BufferedImage img, int x, int y, int rgb) {
        if (x >= 0 && y >= 0 && x < img.getWidth() && y < img.getHeight()) {
            img.setRGB(x, y, 0xff000000 | rgb);
        }
    }
}
