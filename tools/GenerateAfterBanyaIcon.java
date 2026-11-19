import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Bakes the icon of the "After the Banya" effect: a birch besom on the same
 * little bottle the other effect icons use, so the whole set reads as one
 * family.
 *
 * <p>Run from the project root with JDK 21:
 * {@code java tools/GenerateAfterBanyaIcon.java}</p>
 *
 * <p>This tool writes exactly one file and never touches the icons produced by
 * {@code GenerateNewAssets.java}.</p>
 */
public final class GenerateAfterBanyaIcon {
    private static final Path OUT = Path.of(
            "src/main/resources/assets/samogon/textures/mob_effect/after_banya.png");

    private static final int INK = 0x3a2618;
    private static final int CREAM = 0xe8dfc8;
    private static final int HIGHLIGHT = 0xfff8e8;

    private GenerateAfterBanyaIcon() {}

    public static void main(String[] args) throws Exception {
        BufferedImage image = bottle(0xf0d6ae, 0xe3b26e);

        // A birch besom: leaves fanned out above a short bound handle.
        int leafLight = 0xa9c86a;
        int leaf = 0x6f9440;
        int leafDark = 0x44602a;
        int wood = 0x8a5a2e;
        int woodDark = 0x5e3a1c;

        // Handle and binding.
        rect(image, 7, 12, 10, 12, wood);
        px(image, 8, 13, wood);
        px(image, 9, 13, woodDark);

        // The fan of leaves.
        px(image, 7, 9, leaf);
        px(image, 8, 9, leafLight);
        px(image, 9, 9, leaf);
        px(image, 10, 9, leafDark);
        px(image, 6, 10, leafDark);
        px(image, 7, 10, leaf);
        px(image, 8, 10, leafLight);
        px(image, 9, 10, leaf);
        px(image, 10, 10, leaf);
        px(image, 11, 10, leafDark);
        px(image, 6, 11, leafDark);
        px(image, 7, 11, leaf);
        px(image, 8, 11, leafLight);
        px(image, 9, 11, leaf);
        px(image, 10, 11, leaf);
        px(image, 11, 11, leafDark);

        // A wisp of steam in the corner.
        px(image, 15, 1, 0xd9f5ff);
        px(image, 14, 2, 0xd9f5ff);
        px(image, 15, 2, HIGHLIGHT);
        px(image, 16, 2, 0xd9f5ff);
        px(image, 15, 3, 0xd9f5ff);

        Files.createDirectories(OUT.getParent());
        ImageIO.write(image, "PNG", OUT.toFile());
        System.out.println("wrote " + OUT);
    }

    /** The shared effect bottle; {@code main} colours the label field. */
    private static BufferedImage bottle(int light, int main) {
        BufferedImage img = new BufferedImage(18, 18, BufferedImage.TYPE_INT_ARGB);

        // Cork and neck.
        rect(img, 7, 0, 10, 0, 0x9a6a3a);
        rect(img, 7, 1, 10, 1, 0x6b4526);
        rect(img, 7, 2, 7, 5, INK);
        rect(img, 8, 2, 9, 5, CREAM);
        rect(img, 10, 2, 10, 5, INK);

        // Bottle body: outline, label field and the glass highlight.
        rect(img, 3, 6, 14, 6, INK);
        rect(img, 3, 7, 3, 15, INK);
        rect(img, 14, 7, 14, 15, INK);
        rect(img, 4, 7, 13, 7, CREAM);
        px(img, 4, 8, HIGHLIGHT);
        rect(img, 5, 8, 11, 8, light);
        px(img, 12, 8, HIGHLIGHT);
        px(img, 13, 8, CREAM);
        rect(img, 4, 9, 4, 13, HIGHLIGHT);
        rect(img, 5, 9, 12, 13, main);
        rect(img, 13, 9, 13, 13, CREAM);
        px(img, 4, 14, CREAM);
        rect(img, 5, 14, 12, 14, main);
        px(img, 13, 14, CREAM);
        px(img, 4, 15, CREAM);
        rect(img, 5, 15, 12, 15, light);
        px(img, 13, 15, CREAM);
        rect(img, 3, 16, 14, 16, INK);
        return img;
    }

    private static void px(BufferedImage img, int x, int y, int rgb) {
        if (x >= 0 && y >= 0 && x < img.getWidth() && y < img.getHeight()) {
            img.setRGB(x, y, 0xff000000 | rgb);
        }
    }

    private static void rect(BufferedImage img, int x1, int y1, int x2, int y2, int rgb) {
        for (int y = y1; y <= y2; y++) {
            for (int x = x1; x <= x2; x++) {
                px(img, x, y, rgb);
            }
        }
    }
}