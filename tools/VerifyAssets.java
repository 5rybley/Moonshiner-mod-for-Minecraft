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
            "tasting_table", "wine_press");
    /** Blocks that are mined with a pickaxe; the rack joins them but has its own multipart blockstate. */
    static final List<String> PICKAXE = List.of("mash_tun", "fermentation_barrel", "beer_fermentation_barrel", "distiller",
            "reflux_column", "charcoal_filter", "dilution_basin", "blending_table", "aging_cask", "condenser",
            "tasting_table", "wine_press", "bottle_rack", "brewery", "banya_stove");
    static final List<String> BLOCKS = List.of("mash_tun", "fermentation_barrel", "beer_fermentation_barrel", "distiller",
            "reflux_column", "charcoal_filter", "dilution_basin", "blending_table", "aging_cask", "condenser",
            "samogon_crate", "tasting_table", "wine_press", "bottle_rack", "brewery", "hops", "grape_vine",
            "banya_stove", "shot_glass", "cup");
    /** Crops that are planted by seeds and have no block item of their own. */
    static final List<String> CROPS = List.of("grape_vine");
    /** Multi-block structures that are built in the world and have no item of their own. */
    static final List<String> STRUCTURES = List.of("aging_barrel");
    /** Blocks placed directly by a drink item; they have no item or loot table of their own. */
    static final List<String> DECOR = List.of("placed_drink");
    static final List<String> ITEMS = List.of("sweet_wort", "fermented_mash", "heads_fraction",
            "hearts_fraction", "tails_fraction", "neutral_spirit", "anhydrous_spirit", "filtered_spirit",
            "diluted_spirit", "berry_tincture", "honey_tincture", "botanical_tincture", "oak_aged_spirit",
            "spiced_tincture", "fruit_tincture", "marine_tincture", "brine",
            "artisan_samogon", "hops", "hops_seeds", "beer_wort", "hopped_wort", "beer",
            "roasted_malt", "wooden_mug", "rusks", "kvass_wort", "kvass",
            "yeast", "malt", "activated_charcoal", "botanical_bundle", "oak_chips", "molecular_sieve",
            "thermometer", "hydrometer", "besom", "distillers_guide", "brewers_guide", "winemakers_guide",
            "red_grapes", "white_grapes", "grape_must", "wine", "sparkling_wine",
            "cabernet_grapes", "merlot_grapes", "chardonnay_grapes", "riesling_grapes",
            "cabernet_seeds", "merlot_seeds", "chardonnay_seeds", "riesling_seeds");
    static final Pattern TEXTURE_REF = Pattern.compile("\"texture\"\\s*:\\s*\"#([a-z0-9_]+)\"");
    /** Element rotation angle; Minecraft rejects anything but -45/-22.5/0/22.5/45. */
    static final Pattern ANGLE_REF = Pattern.compile("\"angle\"\\s*:\\s*(-?[0-9.]+)");
    /** An element rotation object; must carry an angle, axis and origin, never the Bedrock {x,y,z} form. */
    static final Pattern ROTATION_OBJECT = Pattern.compile("\"rotation\"\\s*:\\s*\\{([^{}]*)\\}", Pattern.DOTALL);
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
            for (Matcher rotation = ROTATION_OBJECT.matcher(json); rotation.find(); ) {
                String body = rotation.group(1);
                require(body.contains("\"angle\""),
                        model + " element rotation has no angle (Bedrock {x,y,z} form is invalid in Java)");
                require(body.contains("\"axis\""), model + " element rotation has no axis");
                require(body.contains("\"origin\""), model + " element rotation has no origin");
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
        allBlocks.addAll(DECOR);
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

        // The bottle rack is a 1x2x2 multiblock: its blockstate is multipart,
        // keyed on facing and part, and each facing turns the four part models
        // by the matching y rotation.
        String rack = read(ASSETS.resolve("blockstates/bottle_rack.json"));
        require(rack.contains("\"multipart\""), "bottle_rack blockstate must be multipart");
        for (String facing : List.of("north", "east", "south", "west")) {
            require(rack.contains("\"facing\": \"" + facing + "\""),
                    "bottle_rack blockstate lacks facing " + facing);
        }
        for (int part = 0; part < 4; part++) {
            require(rack.contains("\"part\": \"" + part + "\""),
                    "bottle_rack blockstate lacks part " + part);
            require(rack.contains("bottle_rack_" + part),
                    "bottle_rack blockstate lacks a model for part " + part);
        }
        require(rack.contains("\"y\": 90") && rack.contains("\"y\": 180") && rack.contains("\"y\": 270"),
                "bottle_rack blockstate is missing the rotated models");
        for (String id : BLOCKS) {
            require(read(DATA.resolve("samogon/loot_table/blocks/" + id + ".json")).contains("samogon:" + id),
                    "loot table for " + id + " does not drop the block");
        }

        // Loot functions are typed by a "function" key. Declaring one as
        // {"type": "minecraft:set_count"} makes the whole table unparseable and
        // silently removes every drop in it; the grape table once lost all its
        // seeds this way. Check every function name in every table.
        for (Path table : files(DATA.resolve("samogon/loot_table"))) {
            if (!table.toString().endsWith(".json")) continue;
            String loot = read(table);
            for (String function : List.of("minecraft:set_count", "minecraft:apply_bonus",
                    "minecraft:explosion_decay", "minecraft:set_components",
                    "minecraft:set_name", "minecraft:set_nbt")) {
                int at = loot.indexOf(function);
                while (at >= 0) {
                    int object = loot.lastIndexOf('{', at);
                    require(object >= 0 && loot.substring(object, at).contains("\"function\""),
                            "loot table " + table.getFileName() + " declares " + function
                                    + " with a wrong key; loot functions need 'function', not 'type'");
                    at = loot.indexOf(function, at + 1);
                }
            }
        }

        // The vine must hand out the variety's own seeds and whole bunches:
        // four seed stacks of two plus the 2-4 grape count in every grape pool.
        String grapeLoot = read(DATA.resolve("samogon/loot_table/blocks/grape_vine.json"));
        for (String variety : List.of("cabernet", "merlot", "chardonnay", "riesling")) {
            require(grapeLoot.contains("samogon:" + variety + "_seeds"),
                    "grape loot table does not drop " + variety + " seeds");
            require(grapeLoot.contains("samogon:" + variety + "_grapes"),
                    "grape loot table does not drop " + variety + " grapes");
            require(grapeLoot.contains("\"function\":\"minecraft:set_count\",\"count\":2.0"),
                    "grape seeds must drop exactly two per vine");
        }

        Set<String> modelItems = new TreeSet<>();
        for (Path item : files(ASSETS.resolve("models/item"))) modelItems.add(item.getFileName().toString().replace(".json", ""));
        for (String id : ITEMS) require(modelItems.contains(id), "missing item model " + id);
        for (String id : BLOCKS) {
            if (CROPS.contains(id)) continue;
            require(modelItems.contains(id), "missing block item model " + id);
        }

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
        for (String id : PICKAXE) require(pickaxe.contains("samogon:" + id), "pickaxe tag lacks " + id);
        String axe = read(DATA.resolve("minecraft/tags/block/mineable/axe.json"));
        for (String id : List.of("samogon_crate", "aging_barrel")) {
            require(axe.contains("samogon:" + id), "axe tag lacks " + id);
        }

        parseLang(Path.of("src/main/resources/assets/samogon/lang/ru_ru.json"));
        parseLang(Path.of("src/main/resources/assets/samogon/lang/en_us.json"));
        for (String id : BLOCKS) require(LANG_KEYS.contains("block.samogon." + id), "missing language key for block " + id);
        for (String id : STRUCTURES) require(LANG_KEYS.contains("block.samogon." + id), "missing language key for block " + id);
        for (String id : DECOR) require(LANG_KEYS.contains("block.samogon." + id), "missing language key for block " + id);
        for (String id : ITEMS) require(LANG_KEYS.contains("item.samogon." + id), "missing language key for item " + id);
        for (String advancement : List.of("root.title", "root.description", "first_still.title",
                "first_still.description", "first_hearts.title", "first_hearts.description",
                "oak_aged.title", "oak_aged.description")) {
            require(LANG_KEYS.contains("advancement.samogon." + advancement),
                    "missing advancement language key " + advancement);
        }

        // The whole advancement tree is generated into src/generated/resources.
        // Every file there must have both display keys in both languages and a
        // parent that actually exists, so a renamed or newly added branch cannot
        // ship as a blank tooltip or an orphaned tab.
        Path advancements = Path.of("src/generated/resources/data/samogon/advancement");
        List<Path> advancementFiles = files(advancements).stream()
                .filter(path -> path.toString().endsWith(".json")).toList();
        require(!advancementFiles.isEmpty(), "no generated advancements found");
        for (Path file : advancementFiles) {
            String name = file.getFileName().toString().replace(".json", "");
            require(LANG_KEYS.contains("advancement.samogon." + name + ".title"),
                    "missing advancement title " + name);
            require(LANG_KEYS.contains("advancement.samogon." + name + ".description"),
                    "missing advancement description " + name);
            String json = read(file);
            Matcher parent = Pattern.compile("\"parent\"\\s*:\\s*\"samogon:([a-z0-9_/]+)\"").matcher(json);
            if (parent.find()) {
                Path parentFile = advancements.resolve(parent.group(1) + ".json");
                require(Files.exists(parentFile),
                        "advancement " + name + " points at missing parent " + parent.group(1));
            }
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

        // The winemaker's handbook covers the winery only: pressing, primary
        // fermentation, the cask, the traditional method and the dosage bands.
        for (String page : List.of("overview", "grapes", "press", "ferment", "age",
                "sparkling", "dosage", "trouble", "memo")) {
            require(LANG_KEYS.contains("guide.samogon.winemaker.page." + page + ".title"),
                    "missing winemaker guide page language key " + page);
            require(LANG_KEYS.contains("guide.samogon.winemaker.page." + page + ".tag"),
                    "missing winemaker guide tag language key " + page);
        }
        for (String key : List.of("guide.samogon.winemaker.title", "guide.samogon.winemaker.book.title",
                "guide.samogon.winemaker.book.subtitle", "guide.samogon.winemaker.tab.guide",
                "guide.samogon.winemaker.tab.colours", "guide.samogon.winemaker.colours.progress")) {
            require(LANG_KEYS.contains(key), "missing winemaker guide language key " + key);
        }
        // The vineyard grows four varieties; the book's wines tab and the bottle
        // names both key off them.
        require(LANG_KEYS.contains("wine.samogon.still.variety"),
                "missing variety still-wine name key");
        for (String variety : List.of("cabernet", "merlot", "chardonnay", "riesling")) {
            require(LANG_KEYS.contains("wine.samogon.variety." + variety),
                    "missing grape variety name " + variety);
            require(LANG_KEYS.contains("guide.samogon.winemaker.variety." + variety + ".origin"),
                    "missing wine variety origin " + variety);
            require(LANG_KEYS.contains("guide.samogon.winemaker.variety." + variety + ".description"),
                    "missing wine variety description " + variety);
            for (int step = 1; step <= 3; step++) {
                require(LANG_KEYS.contains("guide.samogon.winemaker.variety." + variety + ".step" + step),
                        "missing wine variety step " + variety + " " + step);
            }
        }
        for (String key : List.of("guide.samogon.winemaker.varieties.title",
                "guide.samogon.winemaker.varieties.tag")) {
            require(LANG_KEYS.contains(key), "missing winemaker varieties key " + key);
        }

        // The recipes tab lists every still style and sparkling method, so each
        // enum needs a name, a description, an origin line and its steps.
        for (String key : List.of("guide.samogon.winemaker.tab.recipes",
                "guide.samogon.winemaker.recipes.title", "guide.samogon.winemaker.recipes.tag",
                "guide.samogon.winemaker.recipes.progress", "guide.samogon.winemaker.recipes.origin",
                "guide.samogon.winemaker.recipes.steps", "tooltip.samogon.wine.style")) {
            require(LANG_KEYS.contains(key), "missing winemaker recipe key " + key);
        }
        for (String style : List.of("dry", "oaked", "off_dry", "semi_sweet", "sweet", "dessert",
                "vermouth", "mulled", "sangria", "honeyed", "cuvee", "port", "sherry")) {
            require(LANG_KEYS.contains("wine.samogon.style." + style),
                    "missing wine style name " + style);
        }
        for (String style : List.of("classic", "blanc_de_blancs", "rose", "red")) {
            require(LANG_KEYS.contains("wine.samogon.sparkling." + style),
                    "missing sparkling style name " + style);
        }
        // The step count comes from the enums, so a recipe with four steps (port,
        // sherry) can never show a raw translation key for its last step.
        int wineRecipes = 0;
        for (String file : List.of("src/main/java/ru/samogon/process/WineStyle.java",
                "src/main/java/ru/samogon/process/SparklingStyle.java")) {
            Matcher recipeMatcher = Pattern.compile(
                    "\"([a-z_]+)\",\\s*Wine\\.Color\\.[A-Z]+,\\s*(\\d+)").matcher(read(Path.of(file)));
            while (recipeMatcher.find()) {
                String recipe = recipeMatcher.group(1);
                int steps = Integer.parseInt(recipeMatcher.group(2));
                String prefix = "guide.samogon.winemaker.recipe." + recipe + ".";
                require(LANG_KEYS.contains(prefix + "name"), "missing wine recipe name " + recipe);
                require(LANG_KEYS.contains(prefix + "desc"),
                        "missing wine recipe description " + recipe);
                require(LANG_KEYS.contains(prefix + "origin"),
                        "missing wine recipe origin " + recipe);
                for (int step = 1; step <= steps; step++) {
                    require(LANG_KEYS.contains(prefix + "step" + step),
                            "missing wine recipe step " + recipe + " " + step);
                }
                wineRecipes++;
            }
        }
        require(wineRecipes == 17, "expected 17 wine recipes, found " + wineRecipes);

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
        require(Files.exists(ASSETS.resolve("textures/gui/banya_panel.png")),
                "banya stove panel is missing");
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
