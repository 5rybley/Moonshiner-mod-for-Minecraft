import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

/**
 * Bakes the static page of the banya stove interface.
 *
 * <p>Like {@code machine_panel.png} and {@code barrel_panel.png}, the page is a
 * cooper's ledger sheet: a dark oak frame with brass corner brackets and rules,
 * slot wells, a stone firebox with logs, a brass thermometer housing and a lever
 * rail. Only the live parts are painted by the screen: the fire, the mercury
 * column, the lever knob, the item seals and every number.</p>
 *
 * <p>Run from the project root: {@code java tools/GenerateBanyaGui.java}</p>
 */
public final class GenerateBanyaGui {
    private static final int MARGIN = 4;
    private static final int CONTENT_W = 180;
    private static final int CONTENT_H = 214;
    private static final int W = CONTENT_W + MARGIN * 2;
    private static final int H = CONTENT_H + MARGIN * 2;

    private static final Path OUT =
            Path.of("src/main/resources/assets/samogon/textures/gui/banya_panel.png");

    private static final int SHADOW_A = 0x50000000;
    private static final int SHADOW_B = 0x71000000;
    private static final int FRAME_BLACK = 0xFF1E110A;
    private static final int FRAME_BROWN = 0xFF3A2417;
    private static final int FRAME_LIGHT = 0xFF5A3A24;
    private static final int BRASS_DARK = 0xFF7A582A;
    private static final int BRASS = 0xFFC09246;
    private static final int BRASS_HI = 0xFFE0BE72;
    private static final int PAGE_HI = 0xFFE2D0AA;
    private static final int PAGE = 0xFFE9D7B0;
    private static final int PAGE_SHADE = 0xFFE4D0A8;
    private static final int PAGE_LIGHT = 0xFFECDAB5;
    private static final int RULE = 0xFFB49468;
    private static final int RULE_HI = 0xFFF2E5C5;
    private static final int WELL_EDGE = 0xFF8A6A48;
    private static final int WELL_INNER_HI = 0xFFBBA680;
    private static final int WELL_INNER_LO = 0xFFC9AE85;
    private static final int WELL_FILL = 0xFFD8BF94;
    private static final int IRON = 0xFF3A2417;
    private static final int IRON_HI = 0xFF5A3A24;
    private static final int IRON_LO = 0xFF1E110A;
    private static final int SOOT = 0xFF0C0806;
    private static final int LOG = 0xFF5A3A24;
    private static final int LOG_HI = 0xFF7A5028;
    private static final int LOG_DARK = 0xFF3F2814;
    private static final int EMBER = 0xFF3A1D0F;
    private static final int EMBER_HI = 0xFF6E3518;
    private static final int GLASS = 0xFF15100C;
    private static final int TICK = 0xFF6E675C;

    private GenerateBanyaGui() {}

    public static void main(String[] args) throws Exception {
        BufferedImage image = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        frame(image);
        paper(image);
        header(image);
        firebox(image);
        thermometer(image);
        lever(image);
        inventory(image);

        Files.createDirectories(OUT.getParent());
        ImageIO.write(image, "png", OUT.toFile());
        System.out.println("wrote " + OUT + " (" + W + "x" + H + ")");
    }

    private static void frame(BufferedImage image) {
        ring(image, 0, 2, SHADOW_A);
        ring(image, 2, 2, SHADOW_B);
        ring(image, 4, 1, FRAME_BLACK);
        ring(image, 5, 1, FRAME_BROWN);
        ring(image, 6, 1, FRAME_LIGHT);
        ring(image, 7, 2, FRAME_BLACK);
        ring(image, 9, 1, BRASS_DARK);
        ring(image, 10, 1, BRASS);
        ring(image, 11, 1, PAGE_HI);
    }

    private static void ring(BufferedImage image, int inset, int thickness, int argb) {
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(argb, true));
        g.fillRect(inset, inset, W - inset * 2, thickness);
        g.fillRect(inset, H - inset - thickness, W - inset * 2, thickness);
        g.fillRect(inset, inset, thickness, H - inset * 2);
        g.fillRect(W - inset - thickness, inset, thickness, H - inset * 2);
        g.dispose();
    }

    private static void paper(BufferedImage image) {
        Random random = new Random(11L);
        for (int y = 8; y < CONTENT_H - 8; y++) {
            for (int x = 8; x < CONTENT_W - 8; x++) {
                int roll = random.nextInt(100);
                int argb = roll < 7 ? PAGE_SHADE : roll < 14 ? PAGE_LIGHT : PAGE;
                image.setRGB(x + MARGIN, y + MARGIN, argb);
            }
        }
        // Corner brackets, as on the other ledgers.
        corner(image, 8, 8, 1, 1);
        corner(image, CONTENT_W - 9, 8, -1, 1);
        corner(image, 8, CONTENT_H - 9, 1, -1);
        corner(image, CONTENT_W - 9, CONTENT_H - 9, -1, -1);
    }

    private static void corner(BufferedImage image, int x, int y, int dx, int dy) {
        fill(image, dx > 0 ? x : x - 7, y, 8, 2, BRASS);
        fill(image, x, dy > 0 ? y : y - 7, 2, 8, BRASS);
        fill(image, dx > 0 ? x : x - 5, dy > 0 ? y : y - 5, 2, 2, BRASS_HI);
    }

    private static void header(BufferedImage image) {
        well(image, 9, 8);
        fill(image, 8, 29, CONTENT_W - 16, 1, RULE);
        fill(image, 8, 30, CONTENT_W - 16, 1, RULE_HI);
        fill(image, 8, 28, 2, 2, BRASS);
        fill(image, CONTENT_W - 10, 28, 2, 2, BRASS);
    }

    private static void firebox(BufferedImage image) {
        fill(image, 10, 33, 54, 68, IRON);
        fill(image, 10, 33, 54, 1, IRON_HI);
        fill(image, 10, 33, 1, 68, IRON_HI);
        fill(image, 10, 100, 54, 1, IRON_LO);
        fill(image, 63, 33, 1, 68, IRON_LO);

        fill(image, 13, 36, 48, 63, FRAME_BLACK);
        fill(image, 13, 36, 48, 3, SOOT);

        // Soot streaks licking up the back of the firehole.
        Random random = new Random(5L);
        for (int streak = 0; streak < 7; streak++) {
            int x = 15 + random.nextInt(44);
            int top = 40 + random.nextInt(16);
            int height = 8 + random.nextInt(18);
            fill(image, x, top, 1, height, 0xFF241710);
        }

        // Rivets on the iron door.
        for (int[] rivet : new int[][]{{12, 35}, {59, 35}, {12, 98}, {59, 98}}) {
            fill(image, rivet[0], rivet[1], 2, 2, BRASS);
            fill(image, rivet[0], rivet[1], 1, 1, BRASS_HI);
        }

        // Ember bed and the logs the flame rises from.
        fill(image, 14, 96, 46, 3, EMBER);
        for (int spark = 0; spark < 12; spark++) {
            fill(image, 16 + random.nextInt(42), 96 + random.nextInt(3), 1, 1,
                    spark % 3 == 0 ? 0xFFA0501F : EMBER_HI);
        }
        fill(image, 18, 87, 38, 7, 0xFF4A2E1B);
        fill(image, 18, 87, 38, 2, 0xFF6E4526);
        fill(image, 15, 91, 44, 6, LOG);
        fill(image, 15, 91, 44, 2, LOG_HI);
        fill(image, 15, 93, 3, 3, 0xFF8A5A2E);
        fill(image, 24, 95, 7, 1, LOG_DARK);
        fill(image, 39, 94, 9, 1, LOG_DARK);

        // The coal well sits in the middle of the fire.
        well(image, 28, 68);
    }

    private static void thermometer(BufferedImage image) {
        fill(image, 70, 33, 22, 68, BRASS_DARK);
        fill(image, 70, 33, 22, 1, BRASS);
        fill(image, 70, 33, 1, 68, BRASS);
        fill(image, 91, 33, 1, 68, FRAME_BLACK);
        fill(image, 71, 34, 20, 66, FRAME_BROWN);

        // Brass cup that carries the bulb, centred on the glass.
        fill(image, 72, 90, 18, 11, BRASS);
        fill(image, 72, 90, 18, 1, BRASS_HI);
        fill(image, 73, 91, 16, 9, FRAME_BROWN);

        // Bulb: 14 px wide with a dark border on both sides and the bottom,
        // so the red well is exactly centred under the tube instead of leaning
        // one pixel to a side.
        fill(image, 74, 91, 14, 8, GLASS);
        fill(image, 74, 91, 1, 8, 0xFF3A2417);
        fill(image, 87, 91, 1, 8, 0xFF3A2417);
        fill(image, 74, 98, 14, 1, 0xFF3A2417);

        // Tube: 8 px of glass between two dark borders. The mercury column is
        // 8 px wide as well, so it fills the tube edge to edge, squarely.
        fill(image, 76, 36, 10, 55, GLASS);
        fill(image, 76, 36, 1, 55, 0xFF3A2417);
        fill(image, 85, 36, 1, 55, 0xFF3A2417);

        for (int step = 0; step <= 6; step++) {
            fill(image, 87, 85 - step * 8, 3, 1, TICK);
        }
    }

    private static void lever(BufferedImage image) {
        fill(image, 96, 60, 72, 12, BRASS_DARK);
        fill(image, 96, 60, 72, 1, BRASS);
        fill(image, 97, 61, 70, 1, BRASS_HI);
        fill(image, 96, 71, 72, 1, FRAME_BLACK);
        fill(image, 98, 62, 68, 8, 0xFF241710);
        // Brass end caps of the rail.
        fill(image, 96, 61, 2, 10, BRASS);
        fill(image, 166, 61, 2, 10, BRASS);
        // Five notch marks along the travel of the handle.
        for (int step = 0; step <= 4; step++) {
            int x = 96 + 5 + 62 * step / 4;
            fill(image, x, 64, 1, 4, TICK);
        }
    }

    private static void inventory(BufferedImage image) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                well(image, 9 + column * 18, 130 + row * 18);
            }
        }
        for (int column = 0; column < 9; column++) {
            well(image, 9 + column * 18, 184);
        }
    }

    /** A sunken slot well, exactly the one the item slots are drawn into. */
    private static void well(BufferedImage image, int x, int y) {
        fill(image, x - 1, y - 1, 18, 18, WELL_EDGE);
        fill(image, x, y, 16, 16, WELL_FILL);
        fill(image, x, y, 16, 1, WELL_INNER_HI);
        fill(image, x, y, 1, 16, WELL_INNER_HI);
        fill(image, x, y + 15, 16, 1, WELL_INNER_LO);
        fill(image, x + 15, y, 1, 16, WELL_INNER_LO);
    }

    private static void fill(BufferedImage image, int x, int y, int width, int height, int argb) {
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(argb, true));
        g.fillRect(x + MARGIN, y + MARGIN, width, height);
        g.dispose();
    }
}