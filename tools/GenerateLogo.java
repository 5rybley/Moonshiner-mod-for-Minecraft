import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Draws the Moonshiner logo into previews/logo.png, with no external
 * service: a pixel-art medallion (a copper pot still with a worm coil over a
 * dark-oak barrel, framed by a brass ring) on a plank background.
 *
 * The picture is composed at a small logical resolution and then upscaled
 * with nearest-neighbour, which is what gives it the blocky voxel look.
 */
public final class GenerateLogo {
    private static final int BASE = 128;   // logical pixel-art resolution
    private static final int SCALE = 4;    // nearest-neighbour upscale
    private static final int SIZE = BASE * SCALE;

    // --- palette (matches the mod's material atlas) ---
    private static final Color BG1 = new Color(0x3A2817);
    private static final Color BG2 = new Color(0x312214);
    private static final Color SEAM = new Color(0x1E140C);
    private static final Color DISC = new Color(0x241A10);
    private static final Color DISC2 = new Color(0x2C2114);
    private static final Color BRASS = new Color(0xC6964A);
    private static final Color BRASS_LIGHT = new Color(0xE0B86A);
    private static final Color BRASS_DARK = new Color(0x7C5626);
    private static final Color COPPER = new Color(0xC86C3A);
    private static final Color COPPER_LIGHT = new Color(0xE8965C);
    private static final Color COPPER_DARK = new Color(0x8E4624);
    private static final Color OAK = new Color(0x5C3E22);
    private static final Color OAK_LIGHT = new Color(0x7A5630);
    private static final Color OAK_DARK = new Color(0x38240F);
    private static final Color HOOP = new Color(0x6A6A70);
    private static final Color HOOP_DARK = new Color(0x44444A);
    private static final Color CREAM = new Color(0xE2D1AA);
    private static final Color INK = new Color(0x241A12);
    private static final Color AMBER = new Color(0xD6962E);
    private static final Color AMBER_LIGHT = new Color(0xF0BE5A);
    private static final Color STEAM = new Color(0xB9AE96);

    public static void main(String[] args) throws Exception {
        BufferedImage img = new BufferedImage(BASE, BASE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        draw(g);
        g.dispose();

        BufferedImage out = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = out.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2.drawImage(img, 0, 0, SIZE, SIZE, null);
        g2.dispose();

        File file = new File("previews/logo.png");
        if (file.getParentFile() != null) {
            file.getParentFile().mkdirs();
        }
        ImageIO.write(out, "png", file);
        System.out.println("wrote " + file.getAbsolutePath() + " (" + SIZE + "x" + SIZE + ")");
    }

    private static void draw(Graphics2D g) {
        background(g);

        int cx = 64;
        int cy = 72;
        medallion(g, cx, cy);
        barrel(g, cy);
        still(g);

        text(g, "MOONSHINER", 64, 25, 13, CREAM);
        text(g, "MOD", 64, 126, 10, CREAM);
    }

    private static void background(Graphics2D g) {
        g.setColor(BG1);
        g.fillRect(0, 0, BASE, BASE);

        // Wainscot of staggered planks.
        for (int band = 0; band < BASE / 16; band++) {
            int y = band * 16;
            g.setColor((band % 2 == 0) ? BG1 : BG2);
            g.fillRect(0, y, BASE, 16);
            g.setColor(SEAM);
            g.fillRect(0, y, BASE, 1);
            int off = (band % 2 == 0) ? 0 : 16;
            for (int x = off; x < BASE; x += 32) {
                g.fillRect(x, y, 1, 16);
            }
        }

        // Simple framed vignette.
        g.setColor(new Color(0, 0, 0, 60));
        for (int i = 0; i < 5; i++) {
            g.drawRect(i, i, BASE - 1 - 2 * i, BASE - 1 - 2 * i);
        }
    }

    private static void medallion(Graphics2D g, int cx, int cy) {
        disc(g, cx, cy, 45, INK);
        disc(g, cx, cy, 43, BRASS_DARK);
        disc(g, cx, cy, 42, BRASS);
        disc(g, cx, cy, 40, BRASS_LIGHT);
        disc(g, cx, cy, 38, BRASS);
        disc(g, cx, cy, 36, BRASS_DARK);
        disc(g, cx, cy, 35, DISC);
        disc(g, cx, cy, 33, DISC2);   // faint inner sheen
        disc(g, cx, cy, 31, DISC);

        // Studs around the brass ring.
        for (int i = 0; i < 16; i++) {
            double a = Math.toRadians(i * 22.5 + 11.25);
            int rx = cx + (int) Math.round(Math.cos(a) * 39.0);
            int ry = cy + (int) Math.round(Math.sin(a) * 39.0);
            g.setColor(BRASS_DARK);
            g.fillRect(rx - 1, ry - 1, 3, 3);
            g.setColor(BRASS_LIGHT);
            g.fillRect(rx, ry, 1, 1);
        }
    }

    private static void barrel(Graphics2D g, int cy) {
        int x = 38;
        int y = cy + 12;      // 84
        int w = 52;
        int h = 24;

        g.setColor(OAK);
        g.fillRect(x, y, w, h);
        for (int sx = x + 3; sx < x + w - 2; sx += 6) {
            g.setColor(OAK_LIGHT);
            g.fillRect(sx, y + 2, 1, h - 4);
        }
        g.setColor(OAK_DARK);
        g.drawRect(x, y, w - 1, h - 1);

        hoop(g, x, y + 5, w);
        hoop(g, x, y + h - 7, w);
    }

    private static void hoop(Graphics2D g, int x, int y, int w) {
        g.setColor(HOOP);
        g.fillRect(x - 1, y, w + 2, 2);
        g.setColor(HOOP_DARK);
        g.fillRect(x - 1, y + 2, w + 2, 1);
    }

    private static void still(Graphics2D g) {
        // --- boiler: one solid silhouette (body + domed lid) ---
        int px = 36;
        int pw = 26;
        int ptop = 64;
        int pbottom = 84;

        Path2D pot = new Path2D.Double();
        pot.moveTo(px, pbottom);
        pot.lineTo(px, ptop);
        pot.curveTo(px, ptop - 15, px + pw, ptop - 15, px + pw, ptop);
        pot.lineTo(px + pw, pbottom);
        pot.closePath();
        g.setColor(COPPER);
        g.fill(pot);
        g.setColor(COPPER_DARK);
        g.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(pot);

        // highlight down the left cheek
        g.setColor(COPPER_LIGHT);
        g.fillRect(px + 3, ptop + 2, 4, pbottom - ptop - 4);
        // ...and it carries on around the top-left shoulder, tapering off
        g.fillRect(px + 3, ptop - 2, 4, 4);
        g.fillRect(px + 4, ptop - 6, 3, 4);
        g.fillRect(px + 6, ptop - 9, 3, 3);
        g.fillRect(px + 8, ptop - 11, 3, 3);
        g.fillRect(px + 11, ptop - 12, 2, 2);

        // brass lid seam across the shoulder
        g.setColor(BRASS);
        g.fillRect(px - 1, ptop - 1, pw + 2, 3);
        g.setColor(BRASS_DARK);
        g.fillRect(px - 1, ptop + 2, pw + 2, 1);

        // draw-off tap near the base
        g.setColor(BRASS_DARK);
        g.fillRect(px - 4, pbottom - 9, 5, 3);
        g.setColor(BRASS);
        g.fillRect(px - 4, pbottom - 9, 4, 2);

        // --- steam pipe arcing over into the worm ---
        Path2D pipe = new Path2D.Double();
        pipe.moveTo(px + pw / 2, ptop - 8);
        pipe.lineTo(px + pw / 2, 46);
        pipe.curveTo(px + pw / 2, 40, 60, 42, 67, 51);
        pipe.lineTo(68, 54);
        g.setColor(COPPER_DARK);
        g.setStroke(new BasicStroke(5, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(pipe);
        g.setColor(COPPER);
        g.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(pipe);
        g.setColor(COPPER_LIGHT);
        g.setStroke(new BasicStroke(1, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(pipe);

        // --- oak stand carrying the worm ---
        g.setColor(OAK_DARK);
        g.fillRect(63, 56, 6, 28);
        g.setColor(OAK_LIGHT);
        g.fillRect(64, 56, 3, 28);
        g.setColor(OAK);
        g.fillRect(67, 56, 1, 28);
        g.setColor(OAK_DARK);
        g.fillRect(62, 81, 8, 3);
        g.setColor(BRASS);
        g.fillRect(63, 82, 6, 2);

        // --- worm coil (condenser): stacked turns, same look as before, smaller ---
        g.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < 4; i++) {
            int y = 52 + i * 5;
            g.setColor(COPPER_DARK);
            g.drawOval(68, y, 20, 7);
            g.setColor(COPPER);
            g.drawOval(69, y + 1, 18, 5);
        }

        // --- brass clamps bolting the coil to the stand ---
        for (int i = 0; i < 2; i++) {
            int y = (i == 0) ? 54 : 69;
            g.setColor(BRASS_DARK);
            g.fillRect(63, y, 10, 4);
            g.setColor(BRASS);
            g.fillRect(64, y, 8, 3);
            g.setColor(BRASS_LIGHT);
            g.fillRect(64, y, 8, 1);
            g.setColor(BRASS_DARK);
            g.fillRect(65, y + 1, 1, 1);
        }

        // --- copper pipe from the last turn down to the tap ---
        Path2D drawPipe = new Path2D.Double();
        drawPipe.moveTo(78, 73);
        drawPipe.lineTo(78, 78);
        g.setColor(COPPER_DARK);
        g.setStroke(new BasicStroke(4, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(drawPipe);
        g.setColor(COPPER);
        g.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(drawPipe);

        // --- brass tap: body, valve knob and a spout pointing down ---
        g.setColor(BRASS_DARK);
        g.fillRect(73, 79, 10, 4);
        g.setColor(BRASS);
        g.fillRect(74, 79, 8, 3);
        g.setColor(BRASS_LIGHT);
        g.fillRect(74, 79, 8, 1);

        g.setColor(BRASS_DARK);
        g.fillRect(80, 76, 3, 4);
        g.setColor(BRASS);
        g.fillRect(81, 77, 1, 2);

        g.setColor(BRASS_DARK);
        g.fillRect(76, 83, 4, 3);
        g.setColor(BRASS);
        g.fillRect(77, 83, 2, 3);

        // --- spirit dripping from the spout ---
        g.setColor(AMBER_LIGHT);
        g.fillRect(77, 87, 2, 2);
        g.setColor(AMBER);
        g.fillRect(77, 90, 2, 2);
        g.fillRect(78, 93, 2, 2);
    }

    // --- primitives -------------------------------------------------------

    private static void disc(Graphics2D g, int cx, int cy, int r, Color c) {
        g.setColor(c);
        g.fillOval(cx - r, cy - r, 2 * r, 2 * r);
    }

    private static void text(Graphics2D g, String s, int cx, int baseline, int size, Color color) {
        g.setFont(new Font("SansSerif", Font.BOLD, size));
        FontMetrics fm = g.getFontMetrics();
        int x = cx - fm.stringWidth(s) / 2;
        g.setColor(INK);
        g.drawString(s, x + 1, baseline + 1);
        g.setColor(color);
        g.drawString(s, x, baseline);
    }

    private GenerateLogo() {}
}
