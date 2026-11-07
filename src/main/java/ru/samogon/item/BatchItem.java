package ru.samogon.item;

import net.minecraft.ChatFormatting;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import ru.samogon.effect.Intoxication;
import ru.samogon.process.BlendCatalog;
import ru.samogon.process.Simulation.Batch;
import ru.samogon.process.SpiritType;

import java.util.List;
import java.util.Locale;
import java.util.Map;

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

    /**
     * Drink type derived from the batch history. The fixed classic tinctures
     * keep their identity; the oak-aged bottle is classified by its origin and
     * process like any other aged spirit.
     */
    public SpiritType typeOf(Batch batch) {
        if (tincture == Tincture.OAK) return SpiritType.of(batch);
        if (tincture != Tincture.NONE) return SpiritType.TINCTURE;
        return SpiritType.of(batch);
    }

    @Override
    public Component getName(ItemStack stack) {
        // The name always reflects what the bottle actually is: vodka, whiskey,
        // chacha and so on. A blended artisan bottle puts the type in front of
        // its composition-derived name, for example "Chacha «Sunset wind»".
        // The fixed tincture bottles keep their own name.
        if (!technical && (tincture == Tincture.NONE || tincture == Tincture.OAK) && BatchData.has(stack)) {
            Batch batch = BatchData.read(stack);
            SpiritType type = typeOf(batch);
            if (type.isDrink()) {
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

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        Batch batch = BatchData.read(stack);
        lines.add(Component.translatable("tooltip.samogon.abv", String.format(Locale.ROOT, "%.1f", batch.abv()))
                .withStyle(ChatFormatting.AQUA));
        SpiritType type = typeOf(batch);
        if (!technical && (type == SpiritType.WASH || (batch.abv() > 0.1 && type.isDrink()))) {
            lines.add(Component.translatable("tooltip.samogon.type",
                    Component.translatable(type.translationKey()))
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            lines.add(Component.translatable(type.descriptionKey())
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
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
                Intoxication.drink(player, batch, effectiveTincture(batch), technical);
                // Vanilla fires this from Player.eat; our drink overrides
                // finishUsingItem, so "consume_item" advancements need the nudge.
                if (player instanceof ServerPlayer server) {
                    CriteriaTriggers.CONSUME_ITEM.trigger(server, stack);
                }
            }
            if (player == null || !player.getAbilities().instabuild) {
                stack.shrink(1);
                return new ItemStack(Items.GLASS_BOTTLE);
            }
        }
        return stack;
    }
}
