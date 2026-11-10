import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

/** Validates generated models, textures, blockstates, recipes, loot tables and language keys. */
public final class VerifyAssets {
    static final Path ASSETS = Path.of("src/main/resources/assets/samogon");
    static final Path DATA = Path.of("src/main/resources/data");
    static final List<String> MACHINES = List.of("mash_tun", "fermentation_barrel", "beer_fermentation_barrel", "distiller",
            "reflux_column", "charcoal_filter", "dilution_basin", "blending_table", "aging_cask", "condenser",
            "tasting_table");
    static final List<String> BLOCKS = List.of("mash_tun", "fermentation_barrel", "beer_fermentation_barrel", "distiller",
            "reflux_column", "charcoal_filter", "dilution_basin", "blending_table", "aging_cask", "condenser",
            "samogon_crate", "tasting_table", "brewery");
    /** Multi-block structures that are built in the world and have no item of their own. */
    static final List<String> STRUCTURES = List.of("aging_barrel");
    static final List<String> ITEMS = List.of("sweet_wort", "fermented_mash", "heads_fraction",
            "hearts_fraction", "tails_fraction", "neutral_spirit", "anhydrous_spirit", "filtered_spirit",
            "diluted_spirit", "berry_tincture", "honey_tincture", "botanical_tincture", "oak_aged_spirit",
            "spiced_tincture", "fruit_tincture", "marine_tincture", "brine",
            "artisan_samogon", "hops", "beer_wort", "hopped_wort", "beer",
            "roasted_malt", "wooden_mug",
            "yeast", "malt", "activated_charcoal", "botanical_bundle", "oak_chips", "molecular_sieve",
            "thermometer", "hydrometer", "distillers_guide", "brewers_guide");
    static final Pattern TEXTURE_REF = Pattern.compile("\"texture\"\\s*:\\s*\"#([a-z0-9_]+)\"");
    /** Element rotation angle; Minecraft rejects anything but -45/-22.5/0/22.5/45. */
    static final Pattern ANGLE_REF = Pattern.compile("\"angle\"\\s*:\\s*(-?[0-9.]+)");
    static final Pattern PARTICLE_REF = Pattern.compile("\"particle\"\\s*:\\s*\"(?:#([a-z0-9_]+)|samogon:([a-z0-9_/]+))\"");
    static final Pattern TEXTURE_VALUE = Pattern.compile("\"([a-z0-9_]+)\"\\s*:\\s*\"(samogon:[^\"]+)\"");
    static final Pattern MODEL_REF = Pattern.compile("\"model\"\\s*:\\s*\"samogon:([^\"]+)\"");
    static final Pattern RESULT_ID = Pattern.compile("\"result\"\\s*:\\s*\\{\\s*\"id\"\\s*:\\s*\"samogon:([a-z0-9_]+)\"");
    static final Pattern LEGACY_PLACEHOLDER = Pattern.compile("\\{\\d+\\}");
    static final Pattern FORMAT_TOKEN = Pattern.compile("%(?:(\\d+)\\$)?([A-Za-z%]|$)");
    static final Set<String> LANG_KEYS = new TreeSet<>();

    public static void main(String[] args) throws Exception {
        int models = 0;
        int textures = 0;
        for (Path model : files(ASSETS.resolve("models"))) {
            if (!model.toString().endsWith(".json")) continue;
            String json = read(model);
            if (!json.contains("\"textures\"")) continue;
            models++;
            // Models backed by a built-in entity renderer (custom item renderers)
            // carry no geometry of their own; only their textures are checked.
            boolean builtinEntity = json.contains("builtin/entity");
            for (Matcher matcher = TEXTURE_REF.matcher(json); matcher.find(); ) {
                require(json.contains("\"" + matcher.group(1) + "\":") || json.contains("\"" + matcher.group(1) + "\" :"),
                        model + " references missing texture key " + matcher.group(1));
            }
            for (Matcher angle = ANGLE_REF.matcher(json); angle.find(); ) {
                String value = angle.group(1);
                require(value.equals("-45") || value.equals("-22.5") || value.equals("0")
                                || value.equals("22.5") || value.equals("45"),
                        model + " uses an unsupported element rotation angle " + value
                                + " (only -45/-22.5/0/22.5/45 allowed)");
            }
            Matcher particle = PARTICLE_REF.matcher(json);
            require(particle.find(), model + " is missing a particle texture");
            if (particle.group(1) != null) {
                require(json.contains("\"" + particle.group(1) + "\":"), model + " particle key is not declared");
            } else {
                require(Files.exists(ASSETS.resolve("textures/" + particle.group(2) + ".png")),
                        model + " particle points at missing texture samogon:" + particle.group(2));
            }
            for (Matcher matcher = TEXTURE_VALUE.matcher(json); matcher.find(); ) {
                String reference = matcher.group(2);
                String relative = reference.substring("samogon:".length());
                boolean materialTexture = relative.startsWith("block/materials/");
                Path path = materialTexture
                        ? ASSETS.resolve("textures/" + relative + ".png")
                        : ASSETS.resolve("models/" + relative + ".json");
                require(Files.exists(path), model + " points at missing " + reference);
                if (materialTexture) textures++;
            }
            if (!builtinEntity) {
                require(Pattern.compile("\"uv\"\\s*:\\s*\\[\\s*0\\s*,\\s*0\\s*,\\s*16\\s*,\\s*16\\s*\\]").matcher(json).find(),
                        model + " is missing explicit full-face UVs");
                require(Pattern.compile("\"elements\"\\s*:\\s*\\[").matcher(json).find(), model + " has no geometry");
            }
        }
        List<String> allBlocks = new ArrayList<>(BLOCKS);
        allBlocks.addAll(STRUCTURES);
        for (String id : allBlocks) {
            String state = read(ASSETS.resolve("blockstates/" + id + ".json"));
            for (Matcher matcher = MODEL_REF.matcher(state); matcher.find(); ) {
                require(Files.exists(ASSETS.resolve("models/" + matcher.group(1) + ".json")),
                        "blockstate " + id + " points at missing model " + matcher.group(1));
            }
            if (!id.equals("condenser") && MACHINES.contains(id)) {
                for (int heat = 0; heat <= 4; heat++) {
                    for (boolean active : new boolean[]{false, true}) {
                        for (int progress = 0; progress <= 4; progress++) {
                            for (boolean ready : new boolean[]{false, true}) {
                                String key = "heat=" + heat + ",active=" + active
                                        + ",progress=" + progress + ",ready=" + ready;
                                require(state.contains(key), "blockstate " + id + " lacks " + key);
                            }
                        }
                    }
                }
                // Every machine turns to face its placer, so all four horizontal
                // facings and their model rotations must be present.
                for (String facing : List.of("north", "east", "south", "west")) {
                    require(state.contains("facing=" + facing + ",heat=0"),
                            "blockstate " + id + " lacks facing " + facing);
                }
                require(state.contains("\"y\":90") && state.contains("\"y\":180") && state.contains("\"y\":270"),
                        "blockstate " + id + " is missing the rotated model variants");
            } else if (id.equals("condenser")) {
                for (String facing : List.of("north", "east", "south", "west")) {
                    require(state.contains("facing=" + facing),
                            "blockstate " + id + " lacks facing " + facing);
                }
            }
        }
        for (String id : BLOCKS) {
            require(read(DATA.resolve("samogon/loot_table/blocks/" + id + ".json")).contains("samogon:" + id),
                    "loot table for " + id + " does not drop the block");
        }

        Set<String> modelItems = new TreeSet<>();
        for (Path item : files(ASSETS.resolve("models/item"))) modelItems.add(item.getFileName().toString().replace(".json", ""));
        for (String id : ITEMS) require(modelItems.contains(id), "missing item model " + id);
        for (String id : BLOCKS) require(modelItems.contains(id), "missing block item model " + id);

        int recipes = 0;
        for (Path recipe : files(DATA.resolve("samogon/recipe"))) {
            if (!recipe.toString().endsWith(".json")) continue;
            recipes++;
            Matcher matcher = RESULT_ID.matcher(read(recipe));
            if (matcher.find()) {
                String result = matcher.group(1);
                require(modelItems.contains(result), "recipe " + recipe.getFileName() + " returns unknown item " + result);
            }
        }
        require(!Files.exists(DATA.resolve("samogon/recipes")), "old singular/plural recipe folder must not exist");

        String pickaxe = read(DATA.resolve("minecraft/tags/block/mineable/pickaxe.json"));
        for (String id : MACHINES) require(pickaxe.contains("samogon:" + id), "pickaxe tag lacks " + id);
        String axe = read(DATA.resolve("minecraft/tags/block/mineable/axe.json"));
        for (String id : List.of("samogon_crate", "aging_barrel")) {
            require(axe.contains("samogon:" + id), "axe tag lacks " + id);
        }

        parseLang(Path.of("src/main/resources/assets/samogon/lang/ru_ru.json"));
        parseLang(Path.of("src/main/resources/assets/samogon/lang/en_us.json"));
        for (String id : BLOCKS) require(LANG_KEYS.contains("block.samogon." + id), "missing language key for block " + id);
        for (String id : STRUCTURES) require(LANG_KEYS.contains("block.samogon." + id), "missing language key for block " + id);
        for (String id : ITEMS) require(LANG_KEYS.contains("item.samogon." + id), "missing language key for item " + id);
        for (String advancement : List.of("root.title", "root.description", "first_still.title",
                "first_still.description", "first_hearts.title", "first_hearts.description",
                "oak_aged.title", "oak_aged.description")) {
            require(LANG_KEYS.contains("advancement.samogon." + advancement),
                    "missing advancement language key " + advancement);
        }
        for (String page : List.of("overview", "mash", "raw", "still", "clean", "dilute",
                "blend", "age", "trouble", "memo")) {
            require(LANG_KEYS.contains("guide.samogon.page." + page + ".title"),
                    "missing guide page language key " + page);
            require(LANG_KEYS.contains("guide.samogon.page." + page + ".tag"),
                    "missing guide tag language key " + page);
        }
        for (String key : List.of("guide.samogon.book.title", "guide.samogon.book.subtitle",
                "guide.samogon.book.chapters", "guide.samogon.book.close",
                "guide.samogon.toc.overview", "guide.samogon.toc.mash", "guide.samogon.toc.raw",
                "guide.samogon.toc.still", "guide.samogon.toc.clean", "guide.samogon.toc.dilute",
                "guide.samogon.toc.blend", "guide.samogon.toc.age", "guide.samogon.toc.trouble",
                "guide.samogon.toc.memo")) {
            require(LANG_KEYS.contains(key), "missing guide language key " + key);
        }

        // The brewer's handbook mirrors the distiller's guide chapters.
        for (String page : List.of("overview", "malt", "mash", "boil", "ferment",
                "bottle", "trouble", "memo")) {
            require(LANG_KEYS.contains("guide.samogon.brewer.page." + page + ".title"),
                    "missing brewer guide page language key " + page);
            require(LANG_KEYS.contains("guide.samogon.brewer.page." + page + ".tag"),
                    "missing brewer guide tag language key " + page);
        }
        for (String key : List.of("guide.samogon.brewer.title", "guide.samogon.brewer.book.title",
                "guide.samogon.brewer.book.subtitle", "guide.samogon.brewer.tab.guide",
                "guide.samogon.brewer.tab.recipes")) {
            require(LANG_KEYS.contains(key), "missing brewer guide language key " + key);
        }

        // Beer styles and their recipe lines, including the hidden specialities.
        for (String style : List.of("light", "wheat", "dark", "honey", "berry", "apple",
                "ipa", "porter", "pilsner", "schwarz", "bock")) {
            require(LANG_KEYS.contains("beer.samogon." + style),
                    "missing beer style name " + style);
            require(LANG_KEYS.contains("desc.samogon.beer." + style),
                    "missing beer style description " + style);
            require(LANG_KEYS.contains("guide.samogon.brewer.style.origin." + style),
                    "missing beer style origin key " + style);
        }
        for (String grain : List.of("barley", "wheat", "roasted")) {
            require(LANG_KEYS.contains("guide.samogon.brewer.recipe.step.mash." + grain),
                    "missing beer mash step " + grain);
        }
        for (String additive : List.of("none", "honey", "berry", "apple", "hop", "cocoa")) {
            require(LANG_KEYS.contains("guide.samogon.brewer.recipe.step.ferment." + additive),
                    "missing beer fermentation step " + additive);
        }
        for (String family : List.of("ale", "lager")) {
            require(LANG_KEYS.contains("guide.samogon.brewer.recipe.step.family." + family),
                    "missing beer family step " + family);
        }

        // Every custom-model override on the beer mug must point at a real
        // variant model, so a new style never renders as a missing texture.
        String beerModel = read(ASSETS.resolve("models/item/beer.json"));
        for (Matcher matcher = MODEL_REF.matcher(beerModel); matcher.find(); ) {
            require(Files.exists(ASSETS.resolve("models/" + matcher.group(1) + ".json")),
                    "beer.json points at missing model " + matcher.group(1));
        }
        Set<Integer> beerVariants = new TreeSet<>();
        Matcher variantMatcher = Pattern.compile("\"custom_model_data\"\\s*:\\s*(\\d+)").matcher(beerModel);
        while (variantMatcher.find()) beerVariants.add(Integer.parseInt(variantMatcher.group(1)));
        require(beerVariants.size() >= 10, "beer.json lacks variant overrides, found " + beerVariants);

        Set<String> effects = new TreeSet<>();
        Matcher effectMatcher = Pattern.compile("EFFECTS\\.register\\(\"([a-z0-9_]+)\"")
                .matcher(read(Path.of("src/main/java/ru/samogon/registry/ModEffects.java")));
        while (effectMatcher.find()) effects.add(effectMatcher.group(1));
        require(effects.size() >= 10, "Expected at least 10 registered effects, found " + effects.size());
        for (String id : effects) {
            Path icon = ASSETS.resolve("textures/mob_effect/" + id + ".png");
            require(Files.exists(icon), "missing effect icon for " + id);
            BufferedImage image = ImageIO.read(icon.toFile());
            require(image != null && image.getWidth() == 18 && image.getHeight() == 18,
                    "effect icon " + id + " must be 18x18, found "
                            + (image == null ? "unreadable" : image.getWidth() + "x" + image.getHeight()));
        }

        Set<String> sounds = new TreeSet<>();
        Matcher soundMatcher = Pattern.compile("register\\(\"([a-z0-9_]+)\"\\)")
                .matcher(read(Path.of("src/main/java/ru/samogon/registry/ModSounds.java")));
        while (soundMatcher.find()) sounds.add(soundMatcher.group(1));
        require(sounds.size() >= 3, "Expected the mod's own sound events, found " + sounds.size());
        String soundsJson = read(ASSETS.resolve("sounds.json"));
        for (String id : sounds) {
            require(soundsJson.contains("\"" + id + "\":"), "sounds.json lacks " + id);
            Path ogg = ASSETS.resolve("sounds/" + id + ".ogg");
            require(Files.exists(ogg), "missing sound file " + id + ".ogg");
            byte[] header = Files.readAllBytes(ogg);
            require(header.length > 4 && header[0] == 'O' && header[1] == 'g'
                            && header[2] == 'g' && header[3] == 'S',
                    id + ".ogg is not an Ogg stream");
            require(LANG_KEYS.contains("subtitles.samogon." + id), "missing subtitle key for " + id);
        }

        long pngs = Files.list(ASSETS.resolve("textures/block/materials"))
                .filter(path -> path.toString().endsWith(".png")).count();
        require(pngs >= 30, "Expected at least 30 material PNGs, found " + pngs);
        require(!Files.exists(ASSETS.resolve("textures/materials")),
                "material textures must live under textures/block so the block atlas stitches them");
        require(Files.exists(ASSETS.resolve("textures/gui/advancements/backgrounds/workshop.png")),
                "advancement background is missing");
        require(Files.exists(ASSETS.resolve("textures/gui/guide/book.png")),
                "illustrated guide parchment is missing");
        require(Files.exists(ASSETS.resolve("textures/gui/guide/icons.png")),
                "illustrated guide icon sheet is missing");
        System.out.println("Asset verification passed: " + models + " models, " + modelItems.size()
                + " item models, " + textures + " texture bindings, " + recipes + " recipes, "
                + effects.size() + " effect icons, " + sounds.size() + " sounds, " + pngs + " material PNGs");
    }

    static void parseLang(Path path) throws Exception {
        String text = read(path);
        Pattern entryPattern = Pattern.compile("\"([a-z0-9_.]+)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
        for (Matcher entry = entryPattern.matcher(text); entry.find(); ) {
            String key = entry.group(1);
            String value = entry.group(2);
            Matcher legacy = LEGACY_PLACEHOLDER.matcher(value);
            require(!legacy.find(), path + " key " + key
                    + " uses a legacy {n} placeholder; Minecraft uses %1$s-style format tokens");
            for (Matcher token = FORMAT_TOKEN.matcher(value); token.find(); ) {
                require("s".equals(token.group(2)) || "%%".equals(token.group(0)),
                        path + " key " + key + " uses an unsupported format token " + token.group(0));
            }
        }
        Set<String> found = new TreeSet<>();
        for (Matcher matcher = Pattern.compile("\"([a-z0-9_.]+)\"\\s*:").matcher(text); matcher.find(); ) {
            found.add(matcher.group(1));
        }
        if (LANG_KEYS.isEmpty()) LANG_KEYS.addAll(found);
        else require(LANG_KEYS.equals(found), "language files disagree: " + path);
    }

    static String read(Path path) throws Exception {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    static List<Path> files(Path root) throws Exception {
        if (!Files.exists(root)) return List.of();
        try (var stream = Files.walk(root)) {
            return stream.filter(Files::isRegularFile).toList();
        }
    }

    static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
