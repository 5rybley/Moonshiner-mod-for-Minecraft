import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import java.util.*;
import javax.imageio.ImageIO;

/**
 * Reproducible, original voxel assets for Moonshiner.
 * Run with JDK 21: java tools/GenerateAssets.java
 */
public class GenerateAssets {
    static final Path ROOT = Path.of("src/main/resources/assets/samogon");
    static final Path DATA = Path.of("src/main/resources/data");
    static final Map<String, Integer> MATERIALS = new LinkedHashMap<>();
    static final Map<String, List<Box>> BLOCKS = new LinkedHashMap<>();
    static final Map<String, String> BLOCK_TITLES = new LinkedHashMap<>();
    static final Map<String, String> BLOCK_NOTES = Map.of(
            "mash_tun", "heat state · starch conversion",
            "fermentation_barrel", "heat state · sealed fermentation",
            "distiller", "heat state · three fraction receivers",
            "reflux_column", "heat state · rectification plates",
            "condenser", "decorative copper worm coil",
            "charcoal_filter", "gravity feed · no heat",
            "dilution_basin", "water dilution · no heat",
            "blending_table", "aroma blending · no heat",
            "aging_cask", "oak maturation · no heat",
            "tasting_table", "tasting flight · no heat");
    static final Map<String, List<Box>> ITEMS = new LinkedHashMap<>();
    static final String[] DIRECTIONS = {"down", "up", "north", "south", "west", "east"};
    /** Horizontal facings a machine can face, with the block model y rotation that turns its north front to each. */
    static final String[] FACINGS = {"north", "east", "south", "west"};
    static final int[] FACING_Y = {0, 90, 180, 270};
    static final List<String> MACHINES = List.of("mash_tun", "fermentation_barrel", "distiller",
            "reflux_column", "charcoal_filter", "dilution_basin", "blending_table", "aging_cask", "condenser",
            "tasting_table");
    static final List<String> ITEM_NAMES = List.of("sweet_wort", "fermented_mash", "heads_fraction",
            "hearts_fraction", "tails_fraction", "neutral_spirit", "anhydrous_spirit", "filtered_spirit",
            "diluted_spirit", "berry_tincture", "honey_tincture", "botanical_tincture", "oak_aged_spirit",
            "yeast", "malt", "activated_charcoal", "botanical_bundle", "oak_chips", "molecular_sieve",
            "thermometer", "hydrometer", "distillers_guide");
    static BufferedImage atlas;
    static final Random RANDOM = new Random(41207);

    record Box(double x, double y, double z, double X, double Y, double Z, String material) {}

    static void material(String name, int color) { MATERIALS.put(name, color); }

    static void box(List<Box> m, String mat, double x, double y, double z, double X, double Y, double Z) {
        m.add(new Box(Math.min(x, X), Math.min(y, Y), Math.min(z, Z),
                Math.max(x, X), Math.max(y, Y), Math.max(z, Z), mat));
    }

    /** Octagonal cross-section approximated by two overlapping cuboids. */
    static void oct(List<Box> m, String mat, double cx, double cz, double r, double y, double Y) {
        double s = r * 0.63;
        box(m, mat, cx - s, y, cz - r, cx + s, Y, cz + r);
        box(m, mat, cx - r, y, cz - s, cx + r, Y, cz + s);
    }

    /** Hollow square basin wall; leaves the middle open so liquid is visible. */
    static void basinRing(List<Box> m, String mat, double cx, double cz,
                          double outer, double inner, double y, double top) {
        box(m, mat, cx - outer, y, cz - outer, cx + outer, top, cz - inner);
        box(m, mat, cx - outer, y, cz + inner, cx + outer, top, cz + outer);
        box(m, mat, cx - outer, y, cz - inner, cx - inner, top, cz + inner);
        box(m, mat, cx + inner, y, cz - inner, cx + outer, top, cz + inner);
    }

    static void rivets(List<Box> m, double cx, double cz, double r, double y, double size) {
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2 * i / 8;
            double x = cx + Math.cos(a) * r;
            double z = cz + Math.sin(a) * r;
            box(m, "copper_light", x - size / 2, y, z - size / 2, x + size / 2, y + size * 0.55, z + size / 2);
        }
    }

    static void gauge(List<Box> m, double x, double y, double z) {
        box(m, "copper_dark", x - 0.25, y - 0.25, z, x + 3.25, y + 3.25, z + 0.55);
        box(m, "iron_dark", x, y, z + 0.5, x + 3, y + 3, z + 0.85);
        box(m, "gauge", x + 0.3, y + 0.3, z + 0.82, x + 2.7, y + 2.7, z + 0.95);
        box(m, "iron_dark", x + 1.35, y - 0.7, z + 0.2, x + 1.65, y, z + 0.45);
    }

    static void tap(List<Box> m, double x, double y, double z) {
        box(m, "copper_dark", x, y, z - 0.9, x + 1.3, y + 1.3, z + 1.2);
        box(m, "copper_light", x + 0.15, y - 1.1, z - 0.8, x + 1.15, y + 0.4, z + 0.1);
        box(m, "iron_dark", x + 0.45, y + 1.3, z + 0.1, x + 0.85, y + 2.1, z + 0.55);
        box(m, "red", x - 0.45, y + 1.9, z - 0.25, x + 1.75, y + 2.3, z + 0.9);
    }

    /** Small decorative jar; liquid stays visible because only edges are glass. */
    static void jar(List<Box> m, double cx, double y, double cz, double r, double h, String liquid) {
        oct(m, "glass", cx, cz, r, y, y + 0.45);
        oct(m, liquid, cx, cz, r - 0.32, y + 0.4, y + h - 0.7);
        for (double sx : new double[]{-1, 1}) {
            box(m, "glass_light", cx + sx * (r - 0.35) - 0.18, y + 0.3, cz - r * 0.65,
                    cx + sx * (r - 0.35) + 0.18, y + h - 0.4, cz - r * 0.65 + 0.3);
            box(m, "glass", cx + sx * (r - 0.35) - 0.18, y + 0.3, cz + r * 0.65 - 0.3,
                    cx + sx * (r - 0.35) + 0.18, y + h - 0.4, cz + r * 0.65);
        }
        oct(m, "glass", cx, cz, r, y + h - 0.6, y + h - 0.15);
        oct(m, "cork", cx, cz, r * 0.45, y + h - 0.2, y + h + 0.45);
    }

    public static void main(String[] args) throws Exception {
        createMaterials();
        createAtlas();
        createBlocks();
        createItems();
        writeBlockModels();
        writeItemModels();
        writeData();
        renderCatalog();
        System.out.printf("Generated %d block models, %d item models, material textures, data and previews.%n",
                BLOCKS.size(), ITEMS.size());
    }

    static void createMaterials() {
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

    static void createAtlas() throws IOException {
        atlas = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        int tile = 0;
        for (var entry : MATERIALS.entrySet()) {
            int ox = tile % 8 * 16, oy = tile / 8 * 16;
            Color base = new Color(entry.getValue());
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    int n = RANDOM.nextInt(13) - 6;
                    String key = entry.getKey();
                    if (key.contains("oak") || key.equals("dark_wood") || key.equals("cork")) {
                        n += (x % 5 == 0 ? -24 : 0) + (int) (Math.sin(y * 0.8 + x * 1.7) * 5);
                        if (key.equals("oak_end")) n += ((x + y) % 6 < 2 ? -14 : 7);
                    } else if (key.startsWith("copper") || key.startsWith("iron") || key.equals("brass")) {
                        n += y == 0 || y == 15 ? 20 : y == 1 || y == 14 ? -15 : 0;
                        n += (int) (Math.sin(x * 1.3 + y * 0.4) * 6);
                    } else if (key.equals("grain") || key.equals("herb") || key.equals("charcoal")) {
                        n *= 2;
                    } else if (key.equals("glass") || key.equals("glass_light")) {
                        n += (x + y) % 11 < 2 ? 22 : 0;
                    } else if (key.equals("paper") || key.equals("cream")) {
                        n += (x * 7 + y * 3) % 13 == 0 ? -17 : 0;
                    } else if (key.equals("seal") || key.equals("berry_label") || key.equals("red")) {
                        n += (x * y) % 9 == 0 ? -15 : 0;
                    }
                    atlas.setRGB(ox + x, oy + y, shade(base, n));
                }
            }
            if (entry.getKey().equals("gauge")) drawGaugeTexture(ox, oy);
            tile++;
        }
        Path texture = ROOT.resolve("textures/shared/workshop_atlas.png");
        Files.createDirectories(texture.getParent());
        ImageIO.write(atlas, "PNG", texture.toFile());

        int index = 0;
        for (var entry : MATERIALS.entrySet()) {
            int ox = index % 8 * 16, oy = index / 8 * 16;
            Path materialPath = ROOT.resolve("textures/block/materials/" + entry.getKey() + ".png");
            Files.createDirectories(materialPath.getParent());
            ImageIO.write(atlas.getSubimage(ox, oy, 16, 16), "PNG", materialPath.toFile());
            index++;
        }
    }

    static int shade(Color color, int delta) {
        return new Color(clamp(color.getRed() + delta), clamp(color.getGreen() + delta),
                clamp(color.getBlue() + delta)).getRGB();
    }

    static void drawGaugeTexture(int ox, int oy) {
        Graphics2D g = atlas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0x3c4c51));
        g.drawArc(ox + 2, oy + 2, 12, 12, 5, 170);
        for (int i = 0; i < 7; i++) {
            int x = ox + 3 + i * 2;
            int y = oy + 7 - (int) Math.round(Math.sin(Math.PI * i / 6) * 5);
            g.fillRect(x, y, 1, 2);
        }
        g.setColor(new Color(0x913b36));
        g.drawLine(ox + 8, oy + 11, ox + 11, oy + 5);
        g.setColor(new Color(0x243640));
        g.fillRect(ox + 7, oy + 10, 2, 2);
        g.dispose();
    }

    static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    static void createBlocks() {
        mashTun();
        fermentationBarrel();
        distiller();
        refluxColumn();
        charcoalFilter();
        dilutionBasin();
        blendingTable();
        agingCask();
        condenser();
        tastingTable();
    }

    static void distiller() {
        List<Box> m = new ArrayList<>();
        feet(m);
        // Firebox and grate.
        oct(m, "copper_dark", 8, 8, 6.25, 2.0, 3.2);
        box(m, "iron_dark", 3.0, 2.25, 7.1, 13.0, 2.6, 8.9);
        box(m, "charcoal", 4.2, 2.3, 7.3, 11.8, 2.55, 8.7);
        // Onion-shaped copper pot.
        oct(m, "copper_dark", 8, 8, 6.2, 3.1, 4.2);
        oct(m, "copper", 8, 8, 6.0, 4.2, 7.8);
        oct(m, "copper_light", 8, 8, 6.18, 7.7, 8.25);
        oct(m, "copper", 8, 8, 5.7, 8.2, 10.6);
        oct(m, "copper_light", 8, 8, 4.6, 10.5, 11.5);
        oct(m, "copper", 8, 8, 3.4, 11.4, 12.6);
        oct(m, "copper_light", 8, 8, 2.2, 12.6, 13.2);
        oct(m, "copper_dark", 8, 8, 2.35, 13.2, 13.55);
        rivets(m, 8, 8, 6.18, 7.9, 0.45);
        // Swan neck, lyne arm and condenser.
        box(m, "copper", 7.2, 13.45, 7.2, 8.8, 14.65, 8.8);
        box(m, "copper_light", 8.0, 14.25, 7.2, 13.2, 15.25, 8.8);
        box(m, "copper", 12.2, 13.9, 7.2, 13.4, 15.25, 8.8);
        oct(m, "patina", 12.8, 8, 2.3, 4.0, 13.8);
        oct(m, "copper_light", 12.8, 8, 2.55, 9.2, 9.8);
        oct(m, "copper_light", 12.8, 8, 2.55, 5.2, 5.8);
        oct(m, "copper_light", 12.8, 8, 2.5, 13.6, 14.15);
        oct(m, "copper_dark", 12.8, 8, 2.4, 3.4, 4.05);
        box(m, "blue", 14.6, 8.9, 7.5, 15.8, 9.5, 8.5);
        box(m, "blue", 14.6, 5.3, 7.5, 15.8, 5.9, 8.5);
        box(m, "copper", 12.2, 2.7, 7.45, 13.4, 4.05, 8.55);
        tap(m, 12.2, 2.9, 7.0);
        jar(m, 12.8, 0.55, 8.0, 2.1, 3.2, "amber");
        // Front sight glass, gauge, tap and handles.
        box(m, "iron_dark", 7.15, 4.8, 1.5, 8.85, 7.6, 1.95);
        box(m, "amber", 7.4, 5.05, 1.38, 8.6, 7.35, 1.52);
        box(m, "glass_light", 7.45, 5.1, 1.32, 7.7, 7.3, 1.4);
        gauge(m, 4.55, 7.55, 1.5);
        tap(m, 7.35, 3.65, 1.95);
        box(m, "copper_light", 1.35, 8.0, 7.15, 2.15, 9.3, 8.85);
        box(m, "copper_light", 13.85, 8.0, 7.15, 14.65, 9.3, 8.85);
        box(m, "iron_dark", 7.5, 13.2, 5.9, 8.5, 13.8, 6.45);
        box(m, "red", 7.15, 13.75, 5.7, 8.85, 14.15, 6.5);
        BLOCKS.put("distiller", m);
        BLOCK_TITLES.put("distiller", "COPPER POT STILL");
    }

    static void mashTun() {
        List<Box> m = new ArrayList<>();
        feet(m);
        box(m, "dark_wood", 1.4, 0.4, 1.4, 14.6, 1.3, 14.6);
        for (int i = 0; i < 6; i++) {
            box(m, "oak_light", 2.0 + i * 2.0, 1.3, 1.6, 2.7 + i * 2.0, 1.5, 14.4);
        }
        oct(m, "copper_dark", 8, 8, 6.2, 1.3, 2.2);
        oct(m, "copper", 8, 8, 6.0, 2.2, 10.0);
        oct(m, "copper_light", 8, 8, 6.18, 2.2, 2.8);
        oct(m, "copper_light", 8, 8, 6.18, 9.6, 10.3);
        rivets(m, 8, 8, 6.15, 2.55, 0.42);
        rivets(m, 8, 8, 6.15, 9.85, 0.42);
        // Wooden lid, rim and stirring paddle.
        oct(m, "copper_light", 8, 8, 6.2, 10.2, 10.55);
        oct(m, "oak_end", 8, 8, 5.9, 10.5, 11.25);
        box(m, "copper_dark", 7.2, 11.25, 7.2, 8.8, 11.55, 8.8);
        box(m, "dark_wood", 7.35, 11.5, 7.35, 8.65, 13.65, 8.65);
        box(m, "oak_light", 5.4, 13.55, 7.55, 10.6, 14.25, 8.45);
        box(m, "copper", 11.3, 10.9, 7.35, 12.5, 13.7, 8.65);
        box(m, "copper_light", 11.15, 13.7, 7.2, 12.65, 14.15, 8.8);
        // Front inspection hatch with grain.
        box(m, "iron_dark", 5.65, 4.8, 1.5, 10.35, 7.9, 2.0);
        box(m, "grain", 5.95, 5.05, 1.38, 10.05, 7.65, 1.52);
        box(m, "glass_light", 6.0, 5.1, 1.32, 6.3, 7.6, 1.4);
        gauge(m, 10.35, 7.45, 1.5);
        tap(m, 7.35, 3.3, 1.95);
        box(m, "cream", 4.0, 5.8, 1.5, 4.55, 9.2, 1.78);
        box(m, "red", 4.15, 5.95, 1.45, 4.4, 7.7, 1.5);
        box(m, "copper_light", 1.3, 7.0, 7.15, 2.1, 8.2, 8.85);
        box(m, "copper_light", 13.9, 7.0, 7.15, 14.7, 8.2, 8.85);
        BLOCKS.put("mash_tun", m);
        BLOCK_TITLES.put("mash_tun", "MASH TUN");
    }

    static void fermentationBarrel() {
        List<Box> m = barrelBase("oak_light", "oak", "oak_end");
        box(m, "iron_dark", 6.0, 5.6, 1.42, 10.0, 8.7, 1.78);
        box(m, "amber", 6.25, 5.85, 1.3, 9.75, 8.45, 1.44);
        box(m, "cream", 6.2, 7.7, 1.28, 9.8, 8.3, 1.37);
        box(m, "glass_light", 6.3, 5.9, 1.25, 6.55, 8.4, 1.32);
        for (int i = 0; i < 5; i++) {
            box(m, "glass_light", 6.8 + i * 0.55, 6.3 + (i % 3) * 0.7, 1.26,
                    7.0 + i * 0.55, 6.5 + (i % 3) * 0.7, 1.34);
        }
        // Airlock.
        box(m, "cork", 7.25, 13.5, 7.25, 8.75, 14.05, 8.75);
        box(m, "glass", 7.6, 14.0, 7.6, 8.4, 15.45, 8.4);
        box(m, "water", 7.7, 14.2, 7.7, 8.3, 14.9, 8.3);
        box(m, "glass_light", 8.15, 14.1, 7.65, 8.35, 15.3, 7.85);
        box(m, "copper_light", 7.45, 15.35, 7.45, 8.55, 15.7, 8.55);
        tap(m, 7.35, 3.45, 1.7);
        label(m, 5.0, 2.9, 10.9, 4.3);
        BLOCKS.put("fermentation_barrel", m);
        BLOCK_TITLES.put("fermentation_barrel", "FERMENTATION BARREL");
    }

    static void agingCask() {
        List<Box> m = barrelBase("oak", "dark_wood", "oak_end");
        box(m, "brass", 6.0, 5.4, 1.38, 10.0, 8.8, 1.72);
        box(m, "cream", 6.25, 5.65, 1.3, 9.75, 8.55, 1.4);
        box(m, "ink", 6.55, 7.55, 1.25, 9.45, 7.9, 1.32);
        box(m, "ink", 6.85, 6.85, 1.25, 9.15, 7.15, 1.32);
        box(m, "seal", 7.15, 6.0, 1.22, 8.85, 6.6, 1.3);
        box(m, "cork", 7.15, 13.4, 7.15, 8.85, 14.15, 8.85);
        box(m, "brass", 7.55, 14.15, 7.55, 8.45, 14.4, 8.45);
        tap(m, 7.35, 3.4, 1.65);
        jar(m, 13.1, 0.55, 3.2, 1.6, 2.8, "amber");
        BLOCKS.put("aging_cask", m);
        BLOCK_TITLES.put("aging_cask", "OAK AGING CASK");
    }

    static List<Box> barrelBase(String body, String dark, String end) {
        List<Box> m = new ArrayList<>();
        oct(m, dark, 8, 8, 5.1, 0.0, 1.0);
        oct(m, body, 8, 8, 5.5, 1.0, 3.0);
        oct(m, body, 8, 8, 6.05, 3.0, 10.9);
        oct(m, body, 8, 8, 5.55, 10.9, 12.8);
        oct(m, end, 8, 8, 5.15, 12.8, 13.5);
        oct(m, "iron_dark", 8, 8, 6.25, 2.4, 3.2);
        oct(m, "iron_dark", 8, 8, 6.25, 9.8, 10.6);
        oct(m, "iron_dark", 8, 8, 5.75, 12.4, 13.0);
        for (double x : new double[]{4.8, 7.9, 11.0}) {
            box(m, dark, x, 3.2, 1.72, x + 0.18, 10.4, 1.9);
            box(m, dark, x, 3.2, 14.1, x + 0.18, 10.4, 14.28);
            box(m, dark, 1.72, 3.2, x, 1.9, 10.4, x + 0.18);
            box(m, dark, 14.1, 3.2, x, 14.28, 10.4, x + 0.18);
        }
        for (double y : new double[]{3.4, 5.7, 8.0}) {
            box(m, "iron_dark", 1.55, y, 7.1, 2.05, y + 0.45, 8.9);
        }
        return m;
    }

    static void refluxColumn() {
        List<Box> m = new ArrayList<>();
        box(m, "iron_dark", 1.8, 0.0, 1.8, 14.2, 1.1, 14.2);
        oct(m, "copper_dark", 8, 8, 5.0, 1.0, 1.9);
        oct(m, "copper", 8, 8, 4.2, 1.8, 2.7);
        oct(m, "copper", 8, 8, 3.0, 2.6, 13.4);
        for (double y : new double[]{4.0, 6.2, 8.4, 10.6, 12.8}) {
            oct(m, "copper_light", 8, 8, 3.25, y, y + 0.55);
            box(m, "iron_dark", 6.35, y - 0.15, 4.82, 9.65, y + 0.95, 5.25);
            box(m, "clear", 6.6, y + 0.1, 4.7, 9.4, y + 0.7, 4.85);
            box(m, "glass_light", 6.7, y + 0.15, 4.64, 6.95, y + 0.65, 4.72);
        }
        oct(m, "copper_light", 8, 8, 3.8, 13.3, 14.35);
        oct(m, "copper_dark", 8, 8, 2.6, 14.35, 14.95);
        box(m, "copper", 8.0, 14.4, 7.2, 13.0, 15.45, 8.8);
        box(m, "copper", 12.2, 8.0, 7.2, 13.4, 15.45, 8.8);
        oct(m, "patina", 12.8, 8, 2.0, 3.0, 8.0);
        oct(m, "copper_light", 12.8, 8, 2.2, 7.6, 8.2);
        box(m, "blue", 14.4, 13.0, 7.4, 15.7, 13.6, 8.6);
        box(m, "blue", 0.3, 13.0, 7.4, 1.6, 13.6, 8.6);
        tap(m, 12.2, 2.7, 7.0);
        jar(m, 12.8, 0.55, 8.0, 1.9, 2.7, "clear");
        gauge(m, 5.0, 10.9, 4.9);
        box(m, "cream", 4.2, 7.2, 4.95, 4.7, 10.3, 5.18);
        box(m, "red", 4.35, 7.35, 4.9, 4.55, 9.0, 4.96);
        box(m, "copper_light", 13.85, 2.0, 7.2, 14.65, 3.0, 8.8);
        BLOCKS.put("reflux_column", m);
        BLOCK_TITLES.put("reflux_column", "REFLUX COLUMN");
    }

    static void condenser() {
        List<Box> m = new ArrayList<>();
        feet(m);
        box(m, "copper", 2.0, 1.2, 2.0, 14.0, 2.1, 14.0);
        oct(m, "copper_dark", 8, 8, 4.8, 2.0, 2.7);
        oct(m, "patina", 8, 8, 4.6, 3.0, 11.4);
        oct(m, "patina_light", 8, 8, 4.62, 5.4, 5.9);
        oct(m, "copper_light", 8, 8, 4.8, 3.0, 3.6);
        oct(m, "copper_light", 8, 8, 4.8, 10.8, 11.5);
        oct(m, "copper", 8, 8, 2.0, 2.6, 13.5);
        oct(m, "copper_light", 8, 8, 2.2, 13.3, 13.9);
        oct(m, "copper_dark", 8, 8, 2.2, 2.4, 2.9);
        for (int i = 0; i < 5; i++) {
            double y = 4.2 + i * 1.55;
            box(m, "copper_light", 3.1, y, 3.6, 12.9, y + 0.7, 4.45);
            box(m, "copper", 3.1, y, 3.6, 3.9, y + 1.5, 4.45);
            box(m, "copper", 12.1, y, 3.6, 12.9, y + 1.5, 4.45);
        }
        box(m, "blue", 14.4, 8.9, 7.3, 15.7, 9.5, 8.7);
        box(m, "blue", 0.3, 5.3, 7.3, 1.6, 5.9, 8.7);
        box(m, "copper", 7.35, 13.5, 7.35, 8.65, 15.0, 8.65);
        box(m, "copper", 2.5, 14.2, 7.35, 8.65, 15.3, 8.65);
        tap(m, 7.3, 2.1, 2.0);
        jar(m, 7.35, 0.55, 2.0, 1.7, 2.6, "clear");
        gauge(m, 4.7, 7.2, 3.2);
        box(m, "copper_light", 1.3, 6.0, 7.2, 2.0, 7.2, 8.8);
        box(m, "copper_light", 14.0, 6.0, 7.2, 14.7, 7.2, 8.8);
        BLOCKS.put("condenser", m);
        BLOCK_TITLES.put("condenser", "WORM CONDENSER");
    }

    static void charcoalFilter() {
        List<Box> m = new ArrayList<>();
        box(m, "dark_wood", 1.5, 0.0, 1.5, 14.5, 1.3, 14.5);
        box(m, "iron_dark", 1.5, 1.3, 1.5, 14.5, 1.8, 14.5);
        for (int x : new int[]{2, 12}) {
            box(m, "iron_dark", x, 1.8, 2.5, x + 1.2, 13.0, 3.7);
            box(m, "iron_dark", x, 1.8, 12.3, x + 1.2, 13.0, 13.5);
        }
        box(m, "iron_dark", 2.0, 11.8, 2.0, 14.0, 12.6, 14.0);
        oct(m, "iron_dark", 8, 8, 3.9, 2.4, 3.4);
        oct(m, "charcoal", 8, 8, 3.3, 4.2, 8.4);
        oct(m, "cream", 8, 8, 3.35, 8.4, 9.3);
        oct(m, "charcoal", 8, 8, 3.3, 9.3, 10.6);
        oct(m, "cream", 8, 8, 3.35, 10.6, 11.2);
        for (int i = 0; i < 6; i++) {
            double a = Math.PI * 2 * i / 6;
            double x = 8 + Math.cos(a) * 3.45;
            double z = 8 + Math.sin(a) * 3.45;
            box(m, "glass_light", x - 0.2, 3.4, z - 0.2, x + 0.2, 11.2, z + 0.2);
        }
        oct(m, "glass", 8, 8, 3.45, 3.2, 3.75);
        oct(m, "glass", 8, 8, 3.45, 10.9, 11.45);
        oct(m, "copper", 8, 8, 3.9, 12.0, 12.9);
        oct(m, "copper_light", 8, 8, 4.2, 12.6, 13.0);
        oct(m, "copper_dark", 8, 8, 3.7, 11.5, 12.0);
        box(m, "copper", 7.3, 12.7, 7.3, 8.7, 14.3, 8.7);
        box(m, "copper_light", 6.0, 14.2, 7.0, 10.0, 15.0, 9.0);
        box(m, "copper", 7.3, 2.2, 7.3, 8.7, 2.6, 8.7);
        tap(m, 7.3, 1.9, 2.1);
        jar(m, 7.35, 0.45, 2.1, 1.6, 2.4, "clear");
        gauge(m, 3.0, 5.2, 3.8);
        box(m, "cream", 6.2, 5.5, 4.25, 9.8, 6.4, 4.45);
        box(m, "ink", 6.5, 5.8, 4.2, 9.5, 6.1, 4.25);
        BLOCKS.put("charcoal_filter", m);
        BLOCK_TITLES.put("charcoal_filter", "CARBON FILTER");
    }

    static void dilutionBasin() {
        List<Box> m = new ArrayList<>();
        // Low workshop legs with a cross brace.
        for (int x : new int[]{2, 11}) {
            for (int z : new int[]{2, 11}) {
                box(m, "iron_dark", x, 0.0, z, x + 2.3, 3.1, z + 2.3);
                box(m, "iron", x - 0.1, 0.0, z - 0.1, x + 2.4, 0.5, z + 2.4);
            }
        }
        box(m, "iron_dark", 4.0, 1.2, 2.7, 12.0, 1.7, 3.15);
        box(m, "iron_dark", 4.0, 1.2, 12.85, 12.0, 1.7, 13.3);
        // Wide shallow copper basin: a solid bottom, hollow walls and an open
        // water surface so the liquid is visible from above in-world.
        basinRing(m, "copper", 8, 8, 6.4, 5.3, 2.15, 5.1);
        basinRing(m, "copper_light", 8, 8, 6.55, 5.2, 5.05, 5.45);
        oct(m, "water", 8, 8, 5.25, 2.15, 4.58);
        box(m, "glass_light", 5.7, 4.58, 7.3, 8.0, 4.68, 7.8);
        box(m, "glass_light", 9.1, 4.58, 8.2, 10.7, 4.68, 8.7);
        rivets(m, 8, 8, 6.05, 5.2, 0.35);
        // Tap at the front and a wooden stirrer leaning on the rim.
        tap(m, 7.3, 2.35, 1.85);
        box(m, "dark_wood", 11.3, 3.0, 10.2, 11.9, 12.3, 10.8);
        box(m, "oak", 11.0, 1.9, 10.0, 12.2, 4.15, 11.1);
        box(m, "oak", 10.4, 10.8, 10.2, 12.8, 12.0, 11.1);
        // A hydrometer floats in the basin, so the target is visible in-world.
        box(m, "glass", 4.8, 4.35, 8.8, 5.2, 7.25, 9.2);
        box(m, "brass", 4.7, 7.25, 8.7, 5.3, 7.85, 9.3);
        box(m, "paper", 4.85, 5.0, 8.75, 5.15, 6.9, 8.85);
        for (int i = 0; i < 4; i++) {
            box(m, "ink", 5.17, 5.3 + i * 0.35, 8.78, 5.23, 5.38 + i * 0.35, 8.82);
        }
        // Front plate with a simple stamped mark.
        box(m, "cream", 6.0, 2.1, 1.3, 10.0, 2.95, 1.43);
        box(m, "ink", 6.35, 2.45, 1.24, 9.65, 2.75, 1.31);
        box(m, "copper_light", 7.75, 2.55, 1.2, 8.25, 2.65, 1.26);
        BLOCKS.put("dilution_basin", m);
        BLOCK_TITLES.put("dilution_basin", "DILUTION BASIN");
    }

    static void blendingTable() {
        List<Box> m = new ArrayList<>();
        box(m, "oak", 1.0, 9.0, 1.0, 15.0, 10.0, 15.0);
        box(m, "dark_wood", 0.8, 9.0, 0.8, 15.2, 9.45, 15.2);
        box(m, "dark_wood", 1.0, 10.0, 1.0, 15.0, 10.3, 15.0);
        for (int x : new int[]{2, 12}) {
            for (int z : new int[]{2, 12}) {
                box(m, "dark_wood", x, 0.0, z, x + 2.0, 9.0, z + 2.0);
                box(m, "iron_dark", x - 0.1, 0.0, z - 0.1, x + 2.1, 1.0, z + 2.1);
            }
        }
        // Carboy, measuring cylinder and copper funnel.
        jar(m, 4.1, 10.0, 5.0, 2.3, 4.2, "clear");
        oct(m, "glass", 8.3, 5.0, 1.25, 10.0, 10.5);
        oct(m, "clear", 8.3, 5.0, 1.0, 10.4, 13.1);
        box(m, "glass_light", 7.15, 10.0, 4.2, 7.35, 14.4, 4.4);
        box(m, "cream", 8.9, 10.6, 4.15, 9.55, 13.2, 4.28);
        for (int i = 0; i < 5; i++) box(m, "ink", 9.0, 10.9 + i * 0.45, 4.12, 9.45, 11.0 + i * 0.45, 4.17);
        oct(m, "copper", 8.3, 5.0, 1.9, 14.2, 15.2);
        box(m, "copper", 7.95, 13.4, 4.65, 8.65, 14.3, 5.35);
        // Notebook, scale and mixing bowl.
        box(m, "paper", 9.8, 10.0, 9.4, 13.3, 10.3, 12.8);
        box(m, "ink", 10.1, 10.32, 9.8, 12.9, 10.38, 10.0);
        box(m, "ink", 10.1, 10.32, 10.5, 12.6, 10.38, 10.7);
        box(m, "ink", 10.1, 10.32, 11.6, 12.3, 10.38, 11.8);
        box(m, "dark_wood", 12.4, 10.3, 10.0, 13.2, 10.9, 10.35);
        box(m, "iron_dark", 2.5, 10.0, 10.3, 5.8, 10.7, 12.6);
        box(m, "cream", 3.2, 10.65, 10.55, 5.1, 11.35, 12.35);
        box(m, "red", 4.1, 10.75, 11.45, 4.35, 11.3, 11.7);
        oct(m, "copper", 11.0, 11.0, 1.9, 10.0, 11.0);
        oct(m, "copper_light", 11.0, 11.0, 1.65, 10.6, 10.9);
        // Back shelf with three small bottles.
        box(m, "dark_wood", 2.0, 10.2, 13.4, 14.0, 10.7, 14.7);
        for (int i = 0; i < 3; i++) {
            double x = 3.5 + i * 2.2;
            box(m, "glass", x, 10.7, 13.8, x + 1.2, 12.7, 14.4);
            box(m, i == 0 ? "red" : i == 1 ? "honey" : "green", x + 0.25, 10.75, 13.75,
                    x + 0.95, 12.1, 13.85);
            box(m, "cork", x + 0.35, 12.7, 13.9, x + 0.85, 13.1, 14.3);
        }
        BLOCKS.put("blending_table", m);
        BLOCK_TITLES.put("blending_table", "BLENDING TABLE");
    }

    static void tastingTable() {
        List<Box> m = new ArrayList<>();
        // Low round tasting table on four sturdy legs.
        box(m, "dark_wood", 1.4, 8.4, 1.4, 14.6, 9.0, 14.6);
        oct(m, "oak", 8, 8, 6.6, 9.0, 9.9);
        oct(m, "oak_light", 8, 8, 6.7, 9.9, 10.25);
        for (int x : new int[]{3, 11}) {
            for (int z : new int[]{3, 11}) {
                box(m, "dark_wood", x, 0.0, z, x + 2.0, 8.4, z + 2.0);
                box(m, "iron_dark", x - 0.1, 0.0, z - 0.1, x + 2.1, 0.9, z + 2.1);
            }
        }
        // Tasting glass with a measure of amber spirit.
        oct(m, "glass", 5.0, 12.0, 1.15, 10.25, 11.6);
        oct(m, "amber", 5.0, 12.0, 0.95, 10.3, 11.35);
        box(m, "glass_light", 4.55, 11.0, 11.9, 4.75, 11.7, 12.1);
        // Hydrometer floating in a measuring cylinder.
        oct(m, "glass", 8.9, 8.4, 1.25, 10.2, 13.2);
        oct(m, "water", 8.9, 8.4, 1.0, 10.3, 11.9);
        box(m, "glass_light", 8.75, 10.45, 8.25, 8.95, 13.1, 8.45);
        oct(m, "cork", 8.9, 8.4, 0.55, 12.0, 13.5);
        box(m, "red", 8.72, 10.6, 8.85, 9.02, 10.9, 9.15);
        // Open tasting notebook with ruled lines and a brass paperweight.
        box(m, "paper", 10.2, 10.3, 1.8, 14.2, 10.62, 5.4);
        box(m, "ink", 10.6, 10.64, 2.3, 13.6, 10.7, 2.55);
        box(m, "ink", 10.6, 10.64, 3.0, 13.3, 10.7, 3.25);
        box(m, "ink", 10.6, 10.64, 3.7, 13.5, 10.7, 3.95);
        box(m, "ink", 10.6, 10.64, 4.4, 12.9, 10.7, 4.65);
        box(m, "brass", 10.9, 10.3, 4.9, 13.1, 10.95, 5.2);
        // Slate chart of tasting notes on the back edge.
        box(m, "dark_wood", 9.8, 10.9, 13.0, 14.4, 14.0, 13.5);
        box(m, "black", 10.0, 11.1, 13.42, 14.2, 13.8, 13.55);
        box(m, "cream", 10.3, 13.3, 13.4, 13.9, 13.6, 13.48);
        box(m, "red", 10.3, 12.6, 13.4, 11.4, 12.9, 13.48);
        box(m, "green", 11.7, 12.6, 13.4, 12.8, 12.9, 13.48);
        box(m, "copper_light", 13.0, 12.6, 13.4, 13.9, 12.9, 13.48);
        BLOCKS.put("tasting_table", m);
        BLOCK_TITLES.put("tasting_table", "TASTING TABLE");
    }

    static void label(List<Box> m, double x1, double y1, double x2, double y2) {
        box(m, "cream", x1, y1, 1.28, x2, y2, 1.4);
        box(m, "ink", x1 + 0.35, y2 - 0.45, 1.22, x2 - 0.35, y2 - 0.15, 1.29);
        box(m, "ink", x1 + 0.55, y1 + 0.35, 1.22, x2 - 0.55, y1 + 0.6, 1.29);
        box(m, "seal", x1 + 0.25, y1 + 0.15, 1.2, x1 + 0.65, y1 + 0.55, 1.28);
    }

    static void feet(List<Box> m) {
        for (int x : new int[]{3, 11}) {
            for (int z : new int[]{3, 11}) {
                box(m, "iron_dark", x, 0.0, z, x + 2.0, 2.2, z + 2.0);
                box(m, "iron", x - 0.1, 0.0, z - 0.1, x + 2.1, 0.5, z + 2.1);
            }
        }
        box(m, "iron_dark", 4.0, 1.2, 3.2, 12.0, 1.7, 3.65);
        box(m, "iron_dark", 4.0, 1.2, 12.35, 12.0, 1.7, 12.8);
    }

    // === ITEMS ===

    static void createItems() {
        String[] bottles = {"sweet_wort", "fermented_mash", "heads_fraction", "hearts_fraction", "tails_fraction",
                "neutral_spirit", "anhydrous_spirit", "filtered_spirit", "diluted_spirit", "berry_tincture",
                "honey_tincture", "botanical_tincture", "oak_aged_spirit"};
        String[] fluids = {"honey", "amber", "clear", "clear", "amber", "clear", "glass_light", "clear",
                "clear", "red", "honey", "green", "amber"};
        String[] labels = {"grain", "red", "red", "cream", "dark_wood", "blue", "red", "white", "white",
                "berry_label", "honey", "green", "copper_dark"};
        for (int i = 0; i < bottles.length; i++) {
            boolean jar = i <= 1;
            ITEMS.put(bottles[i], bottle(fluids[i], labels[i], jar));
        }

        List<Box> m = new ArrayList<>();
        box(m, "cream", 4, 2, 5, 12, 12, 11);
        box(m, "paper", 4.5, 12, 5.5, 11.5, 13, 10.5);
        box(m, "yeast_label", 4.5, 5, 4.8, 11.5, 10, 5.05);
        box(m, "white", 7, 6.1, 4.5, 9, 8.9, 4.85);
        box(m, "white", 6.2, 7, 4.5, 9.8, 8, 4.85);
        box(m, "ink", 6.8, 7.55, 4.45, 9.2, 7.8, 4.55);
        ITEMS.put("yeast", m);

        m = new ArrayList<>();
        box(m, "paper", 3, 2, 4, 13, 10.5, 12);
        box(m, "grain", 3.3, 10.5, 4.3, 12.7, 11.5, 11.7);
        box(m, "dark_wood", 3, 5.3, 3.8, 13, 6, 4.1);
        for (int j = 0; j < 12; j++) {
            double x = 4 + j % 4 * 2.3, z = 5 + j / 4 * 2.2;
            box(m, "grain", x, 11.4, z, x + 1.5, 12.1 + j % 2 * 0.4, z + 0.8);
        }
        ITEMS.put("malt", m);

        m = new ArrayList<>();
        box(m, "dark_wood", 3, 2, 4, 13, 4, 12);
        for (int j = 0; j < 10; j++) {
            double x = 3.4 + j % 3 * 3.0, z = 4.4 + j / 3 * 2.3;
            box(m, j % 2 == 0 ? "black" : "charcoal", x, 4, z, x + 2.6, 7 + j % 3, z + 2.1);
        }
        ITEMS.put("activated_charcoal", m);

        m = new ArrayList<>();
        for (int j = 0; j < 7; j++) {
            double x = 4 + j * 1.1;
            box(m, "dark_wood", x, 2, 7, x + 0.55, 12 + j % 3, 7.6);
            box(m, "herb", x - 1, 8 + j % 3, 6, x + 1.5, 9 + j % 3, 8.6);
            box(m, j % 2 == 0 ? "herb" : "green", x - 0.6, 11 + j % 3, 6.3, x + 1, 12 + j % 3, 8.2);
        }
        box(m, "cream", 3.7, 4.5, 6.8, 10.6, 5.3, 8);
        box(m, "purple", 4, 11, 6, 5.3, 12.4, 7.4);
        box(m, "purple", 8, 12.5, 6, 9.3, 13.9, 7.4);
        ITEMS.put("botanical_bundle", m);

        m = new ArrayList<>();
        for (int j = 0; j < 8; j++) {
            double x = 3 + j % 3 * 3.1, z = 4 + j / 3 * 2.8;
            box(m, j % 2 == 0 ? "oak" : "oak_light", x, 3 + j % 3, z, x + 2.3, 4.4 + j % 3, z + 4.5);
        }
        ITEMS.put("oak_chips", m);

        m = new ArrayList<>();
        box(m, "iron_dark", 3, 2, 4, 13, 3.2, 12);
        box(m, "iron", 3, 3.2, 4, 4, 10, 12);
        box(m, "iron", 12, 3.2, 4, 13, 10, 12);
        box(m, "iron", 4, 3.2, 11, 12, 10, 12);
        box(m, "cream", 4, 3.2, 5, 12, 8, 11);
        for (int j = 0; j < 6; j++) box(m, "iron", 4 + j * 1.4, 8.2, 4, 4.5 + j * 1.4, 10, 11);
        box(m, "blue", 5, 4, 3.8, 11, 6.7, 4.1);
        ITEMS.put("molecular_sieve", m);

        ITEMS.put("thermometer", thermometer());
        ITEMS.put("hydrometer", hydrometer());

        m = new ArrayList<>();
        box(m, "green", 3, 2, 3.5, 13, 3, 13);
        box(m, "cream", 3.5, 3, 4, 12.5, 5, 12.5);
        box(m, "green", 3, 5, 3.5, 13, 6, 13);
        box(m, "dark_wood", 3, 2, 3.5, 4, 6, 13);
        box(m, "copper_light", 5, 6, 5.5, 11, 6.3, 11);
        box(m, "green", 5.7, 6.3, 6.2, 10.3, 6.5, 10.3);
        box(m, "copper_light", 7.3, 6.5, 7, 8.8, 6.8, 9.6);
        box(m, "seal", 10, 2, 3, 11.2, 5.4, 4.2);
        box(m, "paper", 4.2, 3.2, 3.2, 8.4, 4.6, 3.55);
        box(m, "ink", 4.5, 4.1, 3.15, 8.0, 4.3, 3.3);
        ITEMS.put("distillers_guide", m);
    }

    static List<Box> bottle(String liquid, String label, boolean jar) {
        List<Box> m = new ArrayList<>();
        double r = jar ? 4.3 : 3.15;
        double bottom = 1, shoulder = jar ? 9.2 : 10.4;
        oct(m, "glass", 8, 8, r, bottom, bottom + 0.7);
        oct(m, liquid, 8, 8, r - 0.35, bottom + 0.65, shoulder - 0.75);
        for (double x : new double[]{8 - r + 0.2, 8 + r - 0.7}) {
            box(m, "glass_light", x, bottom + 0.5, 8 - r * 0.63, x + 0.5, shoulder, 8 - r * 0.63 + 0.4);
            box(m, "glass", x, bottom + 0.5, 8 + r * 0.63 - 0.4, x + 0.5, shoulder, 8 + r * 0.63);
        }
        oct(m, "glass", 8, 8, r, shoulder - 0.7, shoulder + 0.2);
        oct(m, "glass", 8, 8, r * 0.75, shoulder + 0.2, shoulder + 1);
        double neck = jar ? 2.7 : 1.3;
        oct(m, "glass", 8, 8, neck, shoulder + 1, 13.9);
        box(m, "glass_light", 8 - neck * 0.6, shoulder + 1, 8 - neck - 0.05,
                8 - neck * 0.6 + 0.35, 13.8, 8 - neck + 0.2);
        oct(m, jar ? "iron_dark" : "cork", 8, 8, neck + 0.2, 13.9, 14.9);
        if (!jar) oct(m, label, 8, 8, neck + 0.25, 12.8, 13.8);
        box(m, label, 8 - r * 0.68, 4.1, 8 - r - 0.13, 8 + r * 0.68, 7.7, 8 - r + 0.08);
        box(m, "cream", 8 - r * 0.55, 4.5, 8 - r - 0.19, 8 + r * 0.55, 7.3, 8 - r - 0.12);
        box(m, "ink", 8 - r * 0.38, 6.5, 8 - r - 0.25, 8 + r * 0.38, 6.9, 8 - r - 0.18);
        box(m, label, 7.5, 5, 8 - r - 0.25, 8.5, 6.1, 8 - r - 0.18);
        return m;
    }

    static List<Box> thermometer() {
        List<Box> m = new ArrayList<>();
        box(m, "copper_dark", 6.9, 1.0, 6.9, 9.1, 3.0, 9.1);
        box(m, "brass", 7.15, 2.7, 7.15, 8.85, 3.2, 8.85);
        box(m, "glass_light", 7.35, 3.0, 7.35, 8.65, 13.4, 8.65);
        box(m, "white", 7.55, 3.2, 7.45, 8.45, 12.9, 7.55);
        box(m, "red", 7.7, 3.2, 7.5, 8.3, 8.4, 7.6);
        for (int i = 0; i < 9; i++) {
            box(m, "ink", 8.0, 8.0 + i * 0.52, 7.42, 8.45, 8.12 + i * 0.52, 7.48);
        }
        box(m, "copper_light", 7.15, 13.2, 7.15, 8.85, 14.0, 8.85);
        box(m, "copper", 7.55, 14.0, 7.55, 8.45, 14.6, 8.45);
        return m;
    }

    static List<Box> hydrometer() {
        List<Box> m = new ArrayList<>();
        oct(m, "glass", 8, 8, 3.2, 1.0, 5.2);
        oct(m, "iron_dark", 8, 8, 2.5, 1.2, 3.2);
        box(m, "glass", 7.3, 4.8, 7.3, 8.7, 13.8, 8.7);
        box(m, "glass_light", 7.55, 5.0, 7.25, 7.85, 13.6, 7.35);
        box(m, "paper", 8.1, 5.5, 7.28, 8.65, 12.9, 7.4);
        for (int i = 0; i < 10; i++) {
            box(m, "ink", 8.2, 5.9 + i * 0.65, 7.24, 8.55, 6.0 + i * 0.65, 7.3);
        }
        box(m, "copper_light", 7.65, 12.9, 7.65, 8.35, 13.5, 8.35);
        box(m, "brass", 7.4, 13.5, 7.4, 8.6, 14.3, 8.6);
        return m;
    }

    // === MODEL WRITING ===

    static void writeBlockModels() throws IOException {
        for (var entry : BLOCKS.entrySet()) {
            writeModel("block/" + entry.getKey(), entry.getValue(), false);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("parent", "samogon:block/" + entry.getKey());
            write(ROOT.resolve("models/item/" + entry.getKey() + ".json"), item);

            // Every machine turns to face the player who placed it, so each of
            // the four horizontal facings needs its own model rotation. The
            // generated block model has its working front on the north side.
            Map<String, Object> variants = new LinkedHashMap<>();
            if (entry.getKey().equals("condenser")) {
                for (int f = 0; f < FACINGS.length; f++) {
                    variants.put("facing=" + FACINGS[f],
                            modelRef("samogon:block/" + entry.getKey(), FACING_Y[f]));
                }
            } else {
                for (int f = 0; f < FACINGS.length; f++) {
                    int y = FACING_Y[f];
                    for (int heat = 0; heat <= 4; heat++) {
                        for (boolean active : new boolean[]{false, true}) {
                            for (int progress = 0; progress <= 4; progress++) {
                                for (boolean ready : new boolean[]{false, true}) {
                                    variants.put("facing=" + FACINGS[f] + ",heat=" + heat
                                                    + ",active=" + active + ",progress=" + progress
                                                    + ",ready=" + ready,
                                            modelRef("samogon:block/" + entry.getKey(), y));
                                }
                            }
                        }
                    }
                }
            }
            Map<String, Object> blockstate = new LinkedHashMap<>();
            blockstate.put("variants", variants);
            write(ROOT.resolve("blockstates/" + entry.getKey() + ".json"), blockstate);
        }
    }

    static void writeItemModels() throws IOException {
        for (var entry : ITEMS.entrySet()) writeModel("item/" + entry.getKey(), entry.getValue(), true);
    }

    static void writeModel(String path, List<Box> boxes, boolean item) throws IOException {
        List<Object> elements = new ArrayList<>();
        Set<String> used = new LinkedHashSet<>();
        for (Box b : boxes) {
            used.add(b.material);
            Map<String, Object> faces = new LinkedHashMap<>();
            for (String direction : DIRECTIONS) {
                Map<String, Object> face = new LinkedHashMap<>();
                face.put("uv", List.of(0, 0, 16, 16));
                face.put("texture", "#" + b.material);
                faces.put(direction, face);
            }
            Map<String, Object> element = new LinkedHashMap<>();
            element.put("from", List.of(b.x, b.y, b.z));
            element.put("to", List.of(b.X, b.Y, b.Z));
            element.put("faces", faces);
            elements.add(element);
        }
        Map<String, Object> textures = new LinkedHashMap<>();
        for (String material : used) {
            if (!MATERIALS.containsKey(material)) {
                throw new IllegalStateException(path + " uses undefined material " + material);
            }
            textures.put(material, "samogon:block/materials/" + material);
        }
        textures.put("particle", "#" + boxes.get(0).material);

        String id = path.substring(path.lastIndexOf('/') + 1);
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("credit", "Original voxel geometry and pixel textures for Moonshiner; generated by tools/GenerateAssets.java");
        model.put("parent", "minecraft:block/block");
        model.put("texture_size", List.of(16, 16));
        model.put("ambientocclusion", !item);
        if (item) model.put("gui_light", "front");
        model.put("textures", textures);
        model.put("elements", elements);
        model.put("display", display(item));
        write(ROOT.resolve("models/" + path + ".json"), model);
    }

    static Map<String, Object> display(boolean item) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("gui", transform(List.of(25, 225, 0), List.of(0, 0, 0), item ? 0.95 : 0.72));
        d.put("ground", transform(List.of(0, 0, 0), List.of(0, 3, 0), 0.4));
        d.put("fixed", transform(List.of(0, 180, 0), List.of(0, 0, 0), 0.65));
        d.put("thirdperson_righthand", transform(List.of(75, 45, 0), List.of(0, 2.5, 0), item ? 0.5 : 0.375));
        d.put("thirdperson_lefthand", transform(List.of(75, 45, 0), List.of(0, 2.5, 0), item ? 0.5 : 0.375));
        d.put("firstperson_righthand", transform(List.of(0, 45, 0), List.of(0, 1, 0), 0.55));
        d.put("firstperson_lefthand", transform(List.of(0, 225, 0), List.of(0, 1, 0), 0.55));
        return d;
    }

    static Object transform(List<?> rotation, List<?> translation, double scale) {
        return Map.of("rotation", rotation, "translation", translation, "scale", List.of(scale, scale, scale));
    }

    /** A blockstate variant model reference, with a y rotation only when it is not north. */
    static Map<String, Object> modelRef(String model, int y) {
        Map<String, Object> ref = new LinkedHashMap<>();
        ref.put("model", model);
        if (y != 0) ref.put("y", y);
        return ref;
    }

    // === DATA ===

    static void writeData() throws IOException {
        for (String id : MACHINES) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("type", "minecraft:item");
            entry.put("name", "samogon:" + id);
            Map<String, Object> condition = new LinkedHashMap<>();
            condition.put("condition", "minecraft:survives_explosion");
            Map<String, Object> pool = new LinkedHashMap<>();
            pool.put("rolls", 1);
            pool.put("bonus_rolls", 0);
            pool.put("entries", List.of(entry));
            pool.put("conditions", List.of(condition));
            Map<String, Object> table = new LinkedHashMap<>();
            table.put("type", "minecraft:block");
            table.put("random_sequence", "samogon:blocks/" + id);
            table.put("pools", List.of(pool));
            write(DATA.resolve("samogon/loot_table/blocks/" + id + ".json"), table);
        }

        writeMinecraftTag("block/mineable/pickaxe", MACHINES);
        writeMinecraftTag("block/needs_stone_tool", MACHINES);
        writeAdvancements();
        writeBackground();
        writeGuideBook();
        writeGuideIcons();
    }

    static void writeMinecraftTag(String path, List<String> values) throws IOException {
        Map<String, Object> tag = new LinkedHashMap<>();
        tag.put("replace", false);
        tag.put("values", values.stream().map(id -> "samogon:" + id).toList());
        write(DATA.resolve("minecraft/tags/" + path + ".json"), tag);
    }

    static void writeAdvancements() throws IOException {
        Map<String, Object> rootDisplay = new LinkedHashMap<>();
        rootDisplay.put("icon", Map.of("id", "samogon:yeast"));
        rootDisplay.put("title", Map.of("translate", "advancement.samogon.root.title"));
        rootDisplay.put("description", Map.of("translate", "advancement.samogon.root.description"));
        rootDisplay.put("background", "samogon:textures/gui/advancements/backgrounds/workshop.png");
        rootDisplay.put("frame", "task");
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("display", rootDisplay);
        root.put("criteria", Map.of("tick", Map.of("trigger", "minecraft:tick")));
        write(DATA.resolve("samogon/advancement/root.json"), root);

        advancement("first_still", "samogon:distiller", "advancement.samogon.first_still.title",
                "advancement.samogon.first_still.description", "task", "samogon:distiller");
        advancement("first_hearts", "samogon:hearts_fraction", "advancement.samogon.first_hearts.title",
                "advancement.samogon.first_hearts.description", "goal", "samogon:hearts_fraction");
        advancement("oak_aged", "samogon:oak_aged_spirit", "advancement.samogon.oak_aged.title",
                "advancement.samogon.oak_aged.description", "challenge", "samogon:oak_aged_spirit");
    }

    static void advancement(String name, String icon, String title, String description,
                            String frame, String item) throws IOException {
        Map<String, Object> display = new LinkedHashMap<>();
        display.put("icon", Map.of("id", icon));
        display.put("title", Map.of("translate", title));
        display.put("description", Map.of("translate", description));
        display.put("frame", frame);
        Map<String, Object> conditions = new LinkedHashMap<>();
        conditions.put("items", List.of(Map.of("items", item)));
        Map<String, Object> criterion = new LinkedHashMap<>();
        criterion.put("trigger", "minecraft:inventory_changed");
        criterion.put("conditions", conditions);
        Map<String, Object> advancement = new LinkedHashMap<>();
        advancement.put("parent", "samogon:root");
        advancement.put("display", display);
        advancement.put("criteria", Map.of("got_item", criterion));
        write(DATA.resolve("samogon/advancement/" + name + ".json"), advancement);
    }

    static void writeBackground() throws IOException {
        BufferedImage bg = new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = bg.createGraphics();
        g.setColor(new Color(0x17262d));
        g.fillRect(0, 0, 256, 256);
        g.setColor(new Color(0x1d3038));
        for (int i = 0; i < 12; i++) {
            g.fillRoundRect((i * 53) % 256 - 20, (i * 91) % 256 - 20, 90, 90, 18, 18);
        }
        g.setColor(new Color(0x243a43, true));
        g.setStroke(new BasicStroke(2f));
        for (int i = 0; i < 8; i++) {
            g.drawLine(0, 32 * i, 256, 32 * i);
            g.drawLine(32 * i, 0, 32 * i, 256);
        }
        g.dispose();
        Path path = ROOT.resolve("textures/gui/advancements/backgrounds/workshop.png");
        Files.createDirectories(path.getParent());
        ImageIO.write(bg, "PNG", path.toFile());
    }

    /** An original two-page parchment spread for the illustrated in-game guide. */
    static void writeGuideBook() throws IOException {
        int width = 920;
        int height = 530;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.scale(2.0, 2.0);

        Random random = new Random(0x5A6B0C);
        g.setColor(new Color(0, 0, 0, 95));
        g.fillRoundRect(4, 5, 452, 258, 14, 14);
        g.setColor(new Color(0x27150C));
        g.fillRoundRect(0, 0, 460, 265, 12, 12);
        g.setColor(new Color(0x5A3922));
        g.fillRoundRect(2, 2, 456, 261, 11, 11);
        g.setColor(new Color(0x3A2214));
        g.fillRoundRect(6, 6, 448, 253, 8, 8);
        g.setColor(new Color(0, 0, 0, 80));
        g.fillRect(12, 10, 438, 243);

        paintGuidePage(g, 12, 10, 154, 243, new Color(0xE1C99B), random.nextLong(), true);
        paintGuidePage(g, 174, 10, 274, 243, new Color(0xECD9AD), random.nextLong(), false);

        // Page block edges and the stitched leather spine.
        for (int i = 0; i < 4; i++) {
            g.setColor(new Color(0xD8BF90));
            g.fillRect(8 + i, 11, 1, 241);
            g.fillRect(449 + i, 11, 1, 241);
            g.fillRect(11, 8 + i, 438, 1);
            g.fillRect(11, 253 + i, 438, 1);
        }
        g.setColor(new Color(0xB59A6B));
        g.fillRect(8, 9, 443, 1);
        g.fillRect(8, 256, 443, 1);

        g.setColor(new Color(0x2A170D));
        g.fillRect(166, 8, 8, 247);
        g.setColor(new Color(0x5F3B22));
        g.fillRect(167, 10, 2, 243);
        g.setColor(new Color(0x1D0F08));
        g.fillRect(172, 8, 2, 247);
        g.setColor(withAlpha(0xB08A55, 110));
        for (int y = 18; y < 250; y += 13) {
            g.fillRect(168, y, 4, 1);
        }

        g.setPaint(new GradientPaint(174, 0, new Color(0, 0, 0, 70), 188, 0, new Color(0, 0, 0, 0)));
        g.fillRect(174, 10, 14, 243);
        g.setPaint(new GradientPaint(156, 0, new Color(0, 0, 0, 0), 166, 0, new Color(0, 0, 0, 55)));
        g.fillRect(156, 10, 10, 243);

        // Worn brass corner strips.
        g.setColor(new Color(0x9B7137));
        g.fillRect(22, 2, 30, 3);
        g.fillRect(2, 22, 3, 30);
        g.fillRect(408, 2, 30, 3);
        g.fillRect(455, 22, 3, 30);
        g.fillRect(22, 260, 30, 3);
        g.fillRect(2, 213, 3, 30);
        g.fillRect(408, 260, 30, 3);
        g.fillRect(455, 213, 3, 30);
        g.setColor(new Color(0xD1A65B));
        g.fillRect(23, 3, 28, 1);
        g.fillRect(3, 23, 1, 28);
        g.fillRect(409, 3, 28, 1);
        g.fillRect(456, 23, 1, 28);
        g.fillRect(23, 261, 28, 1);
        g.fillRect(3, 214, 1, 28);
        g.fillRect(409, 261, 28, 1);
        g.fillRect(456, 214, 1, 28);
        for (int[] rivet : new int[][]{{27, 8}, {433, 8}, {27, 257}, {433, 257}}) {
            g.setColor(new Color(0x4A2B14));
            g.fillOval(rivet[0] - 1, rivet[1] - 1, 4, 4);
            g.setColor(new Color(0xE0B96A));
            g.fillOval(rivet[0], rivet[1], 2, 2);
        }

        // Cloth bookmark hanging over the right edge.
        Path2D bookmark = new Path2D.Double();
        bookmark.moveTo(441, 0);
        bookmark.lineTo(449, 0);
        bookmark.lineTo(449, 53);
        bookmark.lineTo(445, 47);
        bookmark.lineTo(441, 53);
        bookmark.closePath();
        g.setColor(new Color(0x76201C));
        g.fill(bookmark);
        g.setColor(new Color(0xAF4A36));
        g.drawLine(443, 2, 443, 45);
        g.setColor(new Color(0x4C1513));
        g.draw(bookmark);

        g.dispose();
        Path path = ROOT.resolve("textures/gui/guide/book.png");
        Files.createDirectories(path.getParent());
        ImageIO.write(image, "PNG", path.toFile());
    }

    static void paintGuidePage(Graphics2D g, int x, int y, int w, int h, Color base, long seed, boolean left) {
        Random random = new Random(seed);
        g.setPaint(new GradientPaint(x, y, base.brighter(), x + w, y + h, base.darker()));
        g.fillRect(x, y, w, h);

        Shape oldClip = g.getClip();
        g.clipRect(x, y, w, h);
        for (int i = 0; i < 520; i++) {
            int px = x + random.nextInt(w);
            int py = y + random.nextInt(h);
            g.setColor(withAlpha(0x8A6A3E, 7 + random.nextInt(15)));
            g.fillRect(px, py, 1, 1);
        }
        for (int i = 0; i < 95; i++) {
            int px = x + random.nextInt(w);
            int py = y + random.nextInt(h);
            g.setColor(withAlpha(0xB08A55, 8 + random.nextInt(12)));
            g.drawLine(px, py, px + 1 + random.nextInt(8), py + random.nextInt(3) - 1);
        }
        for (int i = 0; i < 16; i++) {
            int px = x + random.nextInt(w);
            int py = y + random.nextInt(h);
            int s = 10 + random.nextInt(46);
            g.setColor(withAlpha(0x9B723F, 5 + random.nextInt(9)));
            g.fillOval(px - s / 2, py - s / 3, s, s * 2 / 3);
        }
        g.setClip(oldClip);

        g.setColor(withAlpha(0x8E6E42, 85));
        g.drawRect(x, y, w - 1, h - 1);
        g.setColor(withAlpha(0x5C3B22, 34));
        g.fillRect(x, y, w, 2);
        g.fillRect(x, y + h - 2, w, 2);
        g.fillRect(x, y, 2, h);
        g.fillRect(x + w - 2, y, 2, h);
        if (left) {
            g.setColor(withAlpha(0x8A6A3E, 18));
            g.fillRect(x, y, w, h);
        }
    }

    /** Small hand-inked marginal symbols used by the guide diagrams. */
    static void writeGuideIcons() throws IOException {
        BufferedImage image = new BufferedImage(64, 48, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        int ink = 0x4A3220;
        int copper = 0x9A5528;
        int gold = 0xB8863A;
        int blue = 0x4F707A;
        int green = 0x52643B;
        int red = 0x8A2E24;
        int pale = 0xF0DFB6;
        int cork = 0x8A5A2B;

        // 0: arrow right
        iconRect(g, 2, 7, 9, 2, ink);
        iconPoly(g, ink, 9, 2, 15, 8, 9, 14);
        iconLine(g, 3, 7, 10, 7, gold);
        // 1: arrow down
        iconRect(g, 23, 2, 2, 8, ink);
        iconPoly(g, ink, 18, 8, 24, 15, 30, 8);
        iconLine(g, 23, 3, 23, 10, gold);
        // 2: flame
        iconPoly(g, copper, 40, 1, 44, 5, 45, 9, 43, 14, 37, 14, 35, 11, 36, 7, 38, 9, 38, 5);
        iconPoly(g, gold, 40, 6, 42, 9, 41, 12, 39, 12, 38, 10, 39, 8);
        iconRect(g, 40, 8, 1, 2, pale);
        // 3: drop
        iconPoly(g, blue, 56, 1, 60, 7, 60, 10, 58, 13, 54, 13, 52, 10, 52, 7);
        iconLine(g, 54, 6, 54, 9, pale);
        iconRect(g, 54, 5, 1, 2, pale);
        // 4: bubbles
        g.setColor(new Color(blue));
        g.drawOval(2, 20, 7, 7);
        g.drawOval(8, 17, 5, 5);
        g.drawOval(10, 25, 4, 4);
        g.setColor(new Color(pale));
        g.fillOval(3, 21, 2, 2);
        g.fillOval(9, 18, 1, 1);
        // 5: clock
        g.setColor(new Color(pale));
        g.fillOval(18, 18, 12, 12);
        g.setColor(new Color(ink));
        g.drawOval(18, 18, 12, 12);
        iconLine(g, 24, 20, 24, 25, ink);
        iconLine(g, 24, 24, 27, 26, ink);
        iconRect(g, 23, 23, 2, 2, copper);
        // 6: star
        iconPoly(g, ink, 40, 17, 42, 22, 47, 24, 42, 26, 40, 31, 38, 26, 33, 24, 38, 22);
        iconPoly(g, gold, 40, 19, 41, 23, 45, 24, 41, 25, 40, 29, 39, 25, 35, 24, 39, 23);
        // 7: warning
        iconPoly(g, red, 56, 17, 63, 30, 49, 30);
        iconPoly(g, pale, 56, 21, 60, 28, 52, 28);
        iconRect(g, 55, 22, 2, 4, red);
        iconRect(g, 55, 27, 2, 2, red);
        // 8: oak leaf
        iconPoly(g, green, 8, 33, 12, 37, 14, 41, 11, 46, 8, 44, 5, 46, 2, 41, 4, 37);
        iconLine(g, 8, 35, 8, 45, 0x3A4727);
        iconLine(g, 7, 38, 4, 36, gold);
        iconLine(g, 9, 40, 12, 38, gold);
        // 9: check
        g.setStroke(new BasicStroke(2f));
        g.setColor(new Color(green));
        g.drawLine(19, 40, 23, 44);
        g.drawLine(23, 44, 30, 35);
        g.setStroke(new BasicStroke(1f));
        // 10: thermometer
        g.setColor(new Color(pale));
        g.fillRoundRect(38, 33, 4, 9, 2, 2);
        g.setColor(new Color(ink));
        g.drawRoundRect(38, 33, 4, 9, 2, 2);
        iconRect(g, 39, 38, 2, 5, red);
        g.setColor(new Color(red));
        g.fillOval(37, 41, 6, 6);
        g.setColor(new Color(ink));
        g.drawOval(37, 41, 6, 6);
        // 11: bottle
        iconRect(g, 55, 33, 2, 2, cork);
        iconRect(g, 55, 35, 2, 3, 0x6E7D77);
        g.setColor(new Color(pale));
        g.fillRoundRect(52, 37, 8, 9, 2, 2);
        g.setColor(new Color(0x6E7D77));
        g.drawRoundRect(52, 37, 8, 9, 2, 2);
        iconRect(g, 54, 42, 4, 3, gold);
        iconLine(g, 53, 39, 53, 40, pale);

        g.dispose();
        Path path = ROOT.resolve("textures/gui/guide/icons.png");
        Files.createDirectories(path.getParent());
        ImageIO.write(image, "PNG", path.toFile());
    }

    static Color withAlpha(int rgb, int alpha) {
        return new Color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, alpha);
    }

    static void iconRect(Graphics2D g, int x, int y, int w, int h, int rgb) {
        g.setColor(new Color(rgb));
        g.fillRect(x, y, w, h);
    }

    static void iconLine(Graphics2D g, int x1, int y1, int x2, int y2, int rgb) {
        g.setColor(new Color(rgb));
        g.drawLine(x1, y1, x2, y2);
    }

    static void iconPoly(Graphics2D g, int rgb, int... xy) {
        int[] xs = new int[xy.length / 2];
        int[] ys = new int[xy.length / 2];
        for (int i = 0; i < xy.length / 2; i++) {
            xs[i] = xy[i * 2];
            ys[i] = xy[i * 2 + 1];
        }
        g.setColor(new Color(rgb));
        g.fillPolygon(xs, ys, xs.length);
    }

    static void write(Path path, Object value) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, json(value) + "\n", StandardCharsets.UTF_8);
    }

    static String json(Object object) {
        if (object instanceof String s) return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        if (object instanceof Map<?, ?> map) {
            List<String> entries = new ArrayList<>();
            map.forEach((k, v) -> entries.add(json(k.toString()) + ":" + json(v)));
            return "{" + String.join(",", entries) + "}";
        }
        if (object instanceof List<?> list) {
            return "[" + String.join(",", list.stream().map(GenerateAssets::json).toList()) + "]";
        }
        return object.toString();
    }

    // === PREVIEW ===

    record Face(double[][] vertices, String material, double light) {}

    /**
     * Small software rasterizer with a z-buffer. The old painter's algorithm
     * let large base plates occlude columns, which made the catalogue lie about
     * the actual model geometry.
     */
    static void render(Graphics2D g, List<Box> boxes, int x, int y, int width, int height,
                       double cx, double cy, double scale) {
        int ss = 2;
        int w = width * ss, h = height * ss;
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        float[] depthBuffer = new float[w * h];
        Arrays.fill(depthBuffer, Float.POSITIVE_INFINITY);
        List<Face> faces = new ArrayList<>();
        for (Box b : boxes) {
            // Camera sits at (-x, +y, -z): top, north and west faces are visible.
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
            Color base = new Color(MATERIALS.get(f.material));
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

    static void renderCatalog() throws IOException {
        Path previews = Path.of("previews");
        Files.createDirectories(previews);
        BufferedImage image = new BufferedImage(1800, 2100, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0x101c22));
        g.fillRect(0, 0, 1800, 2100);
        g.setPaint(new GradientPaint(0, 0, new Color(0x172b34), 0, 2100, new Color(0x0d171c)));
        g.fillRect(0, 0, 1800, 2100);
        g.setFont(new Font("Segoe UI", Font.BOLD, 46));
        g.setColor(new Color(0xe2b783));
        g.drawString("SAMOGON  /  COPPER & OAK", 58, 78);
        g.setFont(new Font("Segoe UI", Font.PLAIN, 20));
        g.setColor(new Color(0x9cb2b7));
        g.drawString("Original voxel equipment · Minecraft 1.21.1 · NeoForge 21.1", 60, 112);
        g.drawString("Eight working machines, a copper condenser, fraction receivers and workshop tools", 60, 140);

        int n = 0;
        for (var entry : BLOCKS.entrySet()) {
            int x = 42 + n % 5 * 348, y = 172 + n / 5 * 402;
            g.setColor(new Color(0x1b2d35));
            g.fillRoundRect(x, y, 324, 380, 20, 20);
            g.setColor(new Color(0x233d47));
            g.drawRoundRect(x, y, 324, 380, 20, 20);
            g.setColor(new Color(0, 0, 0, 70));
            g.fillOval(x + 58, y + 270, 208, 58);
            render(g, entry.getValue(), x + 2, y + 4, 320, 318, 200, 240, 12.2);
            g.setFont(new Font("Segoe UI", Font.BOLD, 19));
            g.setColor(new Color(0xe7d4b4));
            g.drawString(BLOCK_TITLES.get(entry.getKey()), x + 16, y + 332);
            g.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            g.setColor(new Color(0x8fa8ae));
            g.drawString(entry.getValue().size() + " cuboids · "
                    + BLOCK_NOTES.getOrDefault(entry.getKey(), "generated preview"), x + 16, y + 354);
            n++;
        }
        g.setFont(new Font("Segoe UI", Font.BOLD, 25));
        g.setColor(new Color(0xe2b783));
        g.drawString("BOTTLES, FRACTIONS, LAB GLASS & WORKSHOP SUPPLIES", 58, 1000);
        n = 0;
        for (var entry : ITEMS.entrySet()) {
            int x = 48 + n % 8 * 214, y = 1035 + n / 8 * 300;
            g.setColor(new Color(0x16252c));
            g.fillRoundRect(x - 12, y - 10, 198, 270, 14, 14);
            render(g, entry.getValue(), x - 8, y, 188, 205, 94, 168, 7.4);
            g.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            g.setColor(new Color(0xb5c7c7));
            String[] words = entry.getKey().split("_");
            int line = y + 225;
            for (String word : words) {
                String upper = word.toUpperCase(Locale.ROOT);
                g.drawString(upper, x + 86 - g.getFontMetrics().stringWidth(upper) / 2, line);
                line += 16;
            }
            n++;
        }
        g.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        g.setColor(new Color(0x708b92));
        g.drawString("Game simulation only. Technical fractions are not beverages. Not a real-production guide.", 58, 2045);
        g.dispose();
        ImageIO.write(image, "PNG", previews.resolve("models_catalog.png").toFile());

        for (var entry : BLOCKS.entrySet()) writeBlockPreview(previews, entry.getKey(), entry.getValue());
    }

    static void writeBlockPreview(Path previews, String id, List<Box> boxes) throws IOException {
        BufferedImage image = new BufferedImage(900, 900, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setPaint(new GradientPaint(0, 0, new Color(0x1b3039), 0, 900, new Color(0x0e191e)));
        g.fillRect(0, 0, 900, 900);
        g.setColor(new Color(0x233d47));
        g.setStroke(new BasicStroke(2f));
        g.drawRoundRect(30, 30, 840, 840, 24, 24);
        g.setColor(new Color(0, 0, 0, 90));
        g.fillOval(200, 545, 500, 125);
        render(g, boxes, 30, 60, 840, 680, 420, 300, 22.0);
        g.setFont(new Font("Segoe UI", Font.BOLD, 28));
        g.setColor(new Color(0xe2b783));
        g.drawString(BLOCK_TITLES.get(id), 58, 806);
        g.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        g.setColor(new Color(0x8fa8ae));
        g.drawString(boxes.size() + " cuboids · " + BLOCK_NOTES.getOrDefault(id, "generated preview"), 58, 833);
        g.dispose();
        ImageIO.write(image, "PNG", previews.resolve("block_" + id + ".png").toFile());
    }
}
