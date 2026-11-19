package ru.samogon.item;

import net.minecraft.ChatFormatting;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import ru.samogon.block.PlacedDrinkBlockEntity;
import ru.samogon.effect.Intoxication;
import ru.samogon.process.BeerStyle;
import ru.samogon.process.BlendCatalog;
import ru.samogon.process.GrapeVariety;
import ru.samogon.process.Simulation.Batch;
import ru.samogon.process.SpiritType;
import ru.samogon.process.SparklingStyle;
import ru.samogon.process.Wine;
import ru.samogon.process.WineStyle;
import ru.samogon.registry.ModBlocks;
import ru.samogon.registry.ModItems;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

public final class BatchItem extends Item {
    public enum Tincture {
        NONE("none"),
        BERRY("berry"),
        HONEY("honey"),
        BOTANICAL("botanical"),
        OAK("oak"),
        SPICE("spice"),
        FRUIT("fruit"),
        MARINE("marine");

        private final String id;

        Tincture(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }
    }

    private final double defaultAbv;
    private final boolean drinkable;
    private final double defaultVolume;
    private final Tincture tincture;
    private final boolean technical;
    /**
     * True for the heads and tails fractions: drinking one leaves a five-minute
     * blindness that milk cannot cure, so the player learns not to drink them.
     */
    private boolean fusel;
    /** Empty vessel returned after the last sip; glass bottles unless overridden. */
    private Supplier<Item> emptyContainer = () -> Items.GLASS_BOTTLE;

    public BatchItem(Properties properties, double defaultAbv, boolean drinkable, double defaultVolume) {
        this(properties, defaultAbv, drinkable, defaultVolume, Tincture.NONE, false);
    }

    public BatchItem(Properties properties, double defaultAbv, boolean drinkable, double defaultVolume, Tincture tincture) {
        this(properties, defaultAbv, drinkable, defaultVolume, tincture, false);
    }

    public BatchItem(Properties properties, double defaultAbv, boolean drinkable, double defaultVolume,
                     Tincture tincture, boolean technical) {
        super(properties);
        this.defaultAbv = defaultAbv;
        this.drinkable = drinkable;
        this.defaultVolume = defaultVolume;
        this.tincture = tincture;
        this.technical = technical;
    }

    public double defaultAbv() { return defaultAbv; }
    public boolean drinkable() { return drinkable; }
    public double defaultVolume() { return defaultVolume; }
    public Tincture tincture() { return tincture; }
    public boolean technical() { return technical; }
    public boolean fusel() { return fusel; }

    /** Marks the bottle as a heads or tails fraction (see {@link #fusel}). */
    public BatchItem withFusel(boolean value) {
        this.fusel = value;
        return this;
    }

    /** Finished drinks can be set down in the world; technical fractions cannot. */
    public boolean placeable() {
        return drinkable && !technical;
    }

    /** Sets the empty vessel returned when the drink is finished. */
    public BatchItem withContainer(Supplier<Item> container) {
        this.emptyContainer = container == null ? () -> Items.GLASS_BOTTLE : container;
        return this;
    }

    /** The empty vessel this drink leaves behind when poured out or drunk. */
    public Item container() {
        return emptyContainer.get();
    }

    /**
     * Drink type derived from the batch history. The fixed classic tinctures
     * keep their identity, except when the blend itself has become a distinct
     * drink (gin, absinthe or liqueur). The oak-aged bottle is classified by
     * its origin and process like any other aged spirit.
     */
    public SpiritType typeOf(Batch batch) {
        if (tincture == Tincture.OAK) return SpiritType.of(batch);
        SpiritType derived = SpiritType.of(batch);
        if (tincture == Tincture.NONE) return derived;
        return switch (derived) {
            case GIN, ABSINTHE, LIQUEUR, TINCTURE -> derived;
            default -> SpiritType.TINCTURE;
        };
    }

    @Override
    public Component getName(ItemStack stack) {
        // The name always reflects what the bottle actually is: vodka, whiskey,
        // chacha and so on. A blended artisan bottle puts the type in front of
        // its composition-derived name, for example "Gin «Sunset wind»".
        // The fixed tincture bottles keep their own name, unless the blend has
        // become a distinct drink such as gin, absinthe or liqueur.
        if (!technical && BatchData.has(stack)) {
            Batch batch = BatchData.read(stack);
            SpiritType type = typeOf(batch);
            // Wine is named by colour and, when sparkling, by its dosage:
            // "Red Wine", "White Sparkling Wine (Brut)".
            if (type == SpiritType.WINE || type == SpiritType.SPARKLING_WINE) {
                Component custom = stack.get(DataComponents.CUSTOM_NAME);
                if (custom != null) return custom;
                return wineName(batch);
            }
            // Beer is named by its style, not by the generic "Beer" type:
            // "Wheat Beer", "Dark Beer", "Honey Beer" and so on.
            if (type == SpiritType.BEER) {
                Component custom = stack.get(DataComponents.CUSTOM_NAME);
                if (custom != null) return custom;
                Component style = Component.translatable(
                        BeerStyle.of(batch.origin()).translationKey());
                // A brew blended at the five-slot table keeps its style but
                // gains the composition name, so the mug tells what was added.
                if (batch.composition().isBlank()) return style;
                return BlendCatalog.displayName(batch.origin(), batch.composition(), style);
            }
            boolean ownTinctureName = tincture != Tincture.NONE && tincture != Tincture.OAK
                    && type != SpiritType.GIN && type != SpiritType.ABSINTHE
                    && type != SpiritType.LIQUEUR;
            if (!ownTinctureName && type.isDrink()) {
                if (batch.composition().isBlank()) {
                    if (tincture == Tincture.NONE) return Component.translatable(type.translationKey());
                } else {
                    Component custom = stack.get(DataComponents.CUSTOM_NAME);
                    // Keep a real anvil rename, but replace the auto-generated
                    // blend name (old composition-only or new origin-aware)
                    // with the typed one.
                    boolean auto = custom != null
                            && (custom.getString().equals(BlendCatalog.displayName(batch.composition()).getString())
                            || custom.getString().equals(
                                    BlendCatalog.displayName(batch.origin(), batch.composition()).getString()));
                    if (custom != null && !auto) {
                        return custom;
                    }
                    return BlendCatalog.displayName(batch.origin(), batch.composition(),
                            Component.translatable(type.translationKey())).withStyle(ChatFormatting.GOLD);
                }
            }
        }
        return super.getName(stack);
    }

    /** Technical fractions may be drunk at any strength, but ordinary drinks must be diluted to 60% ABV. */
    public boolean canDrink(Batch batch) {
        return batch.abv() > 0.1 && (technical || (drinkable && batch.abv() <= 60.0));
    }

    /**
     * A single classic tincture keeps its explicit aura. An artisan blend has
     * no fixed tincture field, so its dominant aromatic family chooses one.
     */
    public Tincture effectiveTincture(Batch batch) {
        if (tincture != Tincture.NONE) return tincture;
        if (batch.composition().isBlank()) return Tincture.NONE;
        return switch (BlendCatalog.aura(batch.composition())) {
            case "berry" -> Tincture.BERRY;
            case "honey" -> Tincture.HONEY;
            case "botanical" -> Tincture.BOTANICAL;
            case "oak" -> Tincture.OAK;
            case "spice" -> Tincture.SPICE;
            case "fruit" -> Tincture.FRUIT;
            case "marine" -> Tincture.MARINE;
            default -> Tincture.NONE;
        };
    }

    /**
     * Bottle name for the winery: "Cabernet (Red)" for a still wine and
     * "Cabernet Sparkling Wine (Brut)" for a sparkling one. The variety, the
     * colour and the dosage all come from the origin, so an old bottle keeps
     * its identity and a pre-vineyard batch falls back to the colour's default
     * grape. The name is built here rather than in {@link Wine} so the winery
     * model stays free of Minecraft types and the headless chain test can load
     * it.
     */
    private static Component wineName(Batch batch) {
        Wine.Color color = Wine.colorOf(batch.origin());
        GrapeVariety variety = Wine.varietyOf(batch.origin(), color);
        if (Wine.isSparkling(batch)) {
            SparklingStyle style = Wine.sparklingStyleOf(batch.origin());
            return Component.translatable(style.nameKey(),
                    Component.translatable(variety.nameKey()),
                    Component.translatable(Wine.dosageOf(batch.origin()).translationKey()));
        }
        WineStyle style = Wine.displayStyle(batch);
        GrapeVariety partner = Wine.cuveePartner(batch.origin());
        if (style == WineStyle.CUVEE && partner != null) {
            return Component.translatable("wine.samogon.cuvee.name",
                    Component.translatable(variety.nameKey()),
                    Component.translatable(partner.nameKey()));
        }
        if (style == WineStyle.DRY) {
            return Component.translatable("wine.samogon.still.variety",
                    Component.translatable(variety.nameKey()),
                    Component.translatable(color.nameKey()));
        }
        return Component.translatable(style.nameKey(), Component.translatable(variety.nameKey()));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        Batch batch = BatchData.read(stack);
        lines.add(Component.translatable("tooltip.samogon.abv", String.format(Locale.ROOT, "%.1f", batch.abv()))
                .withStyle(ChatFormatting.AQUA));
        SpiritType type = typeOf(batch);
        if (type == SpiritType.BEER) {
            BeerStyle style = BeerStyle.of(batch.origin());
            lines.add(Component.translatable("tooltip.samogon.type",
                    Component.translatable(type.translationKey()))
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            lines.add(Component.translatable(style.descriptionKey())
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            lines.add(Component.translatable("tooltip.samogon.beer.mug")
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else if (!technical && (type == SpiritType.WASH || (batch.abv() > 0.1 && type.isDrink()))) {
            lines.add(Component.translatable("tooltip.samogon.type",
                    Component.translatable(type.translationKey()))
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            // Wine bottles carry the history of their style, not of the grape:
            // a port tastes of port, and the tooltip should tell the story of
            // port rather than repeat the Merlot family tree.
            String description;
            if (type == SpiritType.WINE) {
                description = Wine.displayStyle(batch).historyKey();
            } else if (type == SpiritType.SPARKLING_WINE) {
                description = Wine.sparklingStyleOf(batch.origin()).historyKey();
            } else {
                description = type.descriptionKey();
            }
            lines.add(Component.translatable(description)
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
        if (type == SpiritType.SPARKLING_WINE) {
            Wine.Dosage dosage = Wine.dosageOf(batch.origin());
            lines.add(Component.translatable("tooltip.samogon.dosage",
                    Component.translatable(dosage.translationKey()),
                    dosage.gramsPerLitre()).withStyle(ChatFormatting.GRAY));
        }
        if (type == SpiritType.WINE) {
            WineStyle style = Wine.displayStyle(batch);
            if (style != WineStyle.DRY) {
                lines.add(Component.translatable("tooltip.samogon.wine.style",
                        Component.translatable(style.titleKey())).withStyle(ChatFormatting.GRAY));
            }
            GrapeVariety partner = Wine.cuveePartner(batch.origin());
            if (partner != null) {
                lines.add(Component.translatable("tooltip.samogon.wine.cuvee",
                        Component.translatable(Wine.varietyOf(batch.origin()).nameKey()),
                        Component.translatable(partner.nameKey())).withStyle(ChatFormatting.GRAY));
            }
        } else if (type == SpiritType.SPARKLING_WINE) {
            SparklingStyle style = Wine.sparklingStyleOf(batch.origin());
            if (style != SparklingStyle.CLASSIC) {
                lines.add(Component.translatable("tooltip.samogon.wine.style",
                        Component.translatable(style.titleKey())).withStyle(ChatFormatting.GRAY));
            }
        }
        if (!batch.composition().isBlank()) {
            lines.add(Component.translatable("tooltip.samogon.composition",
                    BlendCatalog.describe(batch.composition())).withStyle(ChatFormatting.GRAY));
        }
        Map<String, Integer> auras = Intoxication.aurasFor(batch, tincture);
        if (!auras.isEmpty()) {
            lines.add(Component.translatable("tooltip.samogon.blend.effects",
                    Intoxication.auraSummary(auras)).withStyle(ChatFormatting.GREEN));
        }
        if (fusel) {
            // A plain label, no warning: the player finds out what the fraction
            // does by drinking it, not from the tooltip.
            lines.add(Component.translatable("tooltip.samogon.fusel")
                    .withStyle(ChatFormatting.GRAY));
        }
        if (placeable()) {
            lines.add(Component.translatable("tooltip.samogon.place")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    /**
     * Picks the fuel line that agrees with the number. Russian needs a
     * different word for one smelt, two to four and the rest; the English
     * translations keep the same three keys so the lang files stay in step.
     */
    private static String fuelKey(int smelts) {
        int lastTwo = smelts % 100;
        int last = smelts % 10;
        if (last == 1 && lastTwo != 11) {
            return "tooltip.samogon.fuel.one";
        }
        if (last >= 2 && last <= 4 && (lastTwo < 12 || lastTwo > 14)) {
            return "tooltip.samogon.fuel.few";
        }
        return "tooltip.samogon.fuel";
    }

    /**
     * Sneak-right-click on a top face sets the drink down as a block. A regular
     * right-click still drinks, so the gesture cannot be triggered by accident.
     * The rotation turns the mug handle or the bottle label toward the player.
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || !placeable() || !player.isSecondaryUseActive()) {
            return InteractionResult.PASS;
        }
        Level level = context.getLevel();
        BlockPlaceContext place = new BlockPlaceContext(context);
        BlockPos pos = place.getClickedPos();
        BlockPos support = pos.below();
        boolean canPlace = (context.getClickedFace() == Direction.UP || place.replacingClickedOnBlock())
                && level.getBlockState(support).isFaceSturdy(level, support, Direction.UP)
                && place.canPlace()
                && player.mayUseItemAt(pos, Direction.UP, context.getItemInHand());
        if (!canPlace) {
            // Sneaking is the "set it down" gesture. Consume the click so a
            // missed placement cannot fall through to drinking the bottle.
            return InteractionResult.CONSUME;
        }
        if (!level.isClientSide) {
            ItemStack stack = context.getItemInHand();
            int rotation = placementRotation(player, pos);
            level.setBlock(pos, ModBlocks.PLACED_DRINK.get().defaultBlockState(), Block.UPDATE_ALL);
            if (level.getBlockEntity(pos) instanceof PlacedDrinkBlockEntity placed) {
                placed.setDrink(stack, rotation);
            }
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            SoundType sound = container() == ModItems.WOODEN_MUG.get() ? SoundType.WOOD : SoundType.GLASS;
            level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, 0.8f, 0.95f);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * 16-step rotation that turns the model's front toward the player: the mug
     * handle points along +X, while the bottle label faces north (-Z).
     */
    private int placementRotation(Player player, BlockPos pos) {
        double dx = player.getX() - (pos.getX() + 0.5);
        double dz = player.getZ() - (pos.getZ() + 0.5);
        double alpha = Math.toDegrees(Math.atan2(dz, dx));
        double degrees = container() == ModItems.WOODEN_MUG.get() ? -alpha : -90.0 - alpha;
        double step = 360.0 / PlacedDrinkBlockEntity.ROTATION_STEPS;
        return Math.floorMod((int) Math.round(degrees / step), PlacedDrinkBlockEntity.ROTATION_STEPS);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        Batch batch = BatchData.read(stack);
        if (!canDrink(batch)) {
            if (!level.isClientSide) {
                String key = batch.abv() > 60 ? "message.samogon.too_strong" : "message.samogon.not_drinkable";
                player.displayClientMessage(Component.translatable(key), true);
            }
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) { return 32; }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.DRINK; }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide) {
            Batch batch = BatchData.read(stack);
            Player player = entity instanceof Player p ? p : null;
            if (player != null) {
                Intoxication.drink(player, batch, effectiveTincture(batch), technical, fusel);
                // Vanilla fires this from Player.eat; our drink overrides
                // finishUsingItem, so "consume_item" advancements need the nudge.
                if (player instanceof ServerPlayer server) {
                    CriteriaTriggers.CONSUME_ITEM.trigger(server, stack);
                }
            }
            if (player == null || !player.getAbilities().instabuild) {
                stack.shrink(1);
                return new ItemStack(emptyContainer.get());
            }
        }
        return stack;
    }
}
