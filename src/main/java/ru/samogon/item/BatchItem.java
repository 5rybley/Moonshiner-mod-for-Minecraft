package ru.samogon.item;

import net.minecraft.ChatFormatting;
import net.minecraft.advancements.CriteriaTriggers;
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

import java.util.List;
import java.util.Locale;

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
        lines.add(Component.translatable("tooltip.samogon.volume", String.format(Locale.ROOT, "%.1f", batch.volume()))
                .withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.samogon.quality", batch.quality(), batch.impurities())
                .withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.samogon.origin", Component.translatable("origin.samogon." + batch.origin()),
                batch.passes()).withStyle(ChatFormatting.GRAY));
        if (batch.filtered()) lines.add(Component.translatable("tooltip.samogon.filtered").withStyle(ChatFormatting.GREEN));
        if (batch.age() > 0) lines.add(Component.translatable("tooltip.samogon.age", batch.age()).withStyle(ChatFormatting.GOLD));
        if (!batch.composition().isBlank()) {
            lines.add(Component.translatable("tooltip.samogon.composition",
                    BlendCatalog.describe(batch.composition())).withStyle(ChatFormatting.GOLD));
            lines.add(Component.translatable("tooltip.samogon.blend.aroma",
                    Component.translatable("aroma.samogon." + BlendCatalog.dominant(batch.composition()).id()))
                    .withStyle(ChatFormatting.YELLOW));
        }
        if (technical && batch.abv() > 0.1) {
            lines.add(Component.translatable("tooltip.samogon.technical.drinkable").withStyle(ChatFormatting.RED));
        } else if (!drinkable) {
            lines.add(Component.translatable(batch.abv() > 60
                    ? "message.samogon.too_strong" : "tooltip.samogon.not_drinkable")
                    .withStyle(ChatFormatting.RED));
        } else if (batch.abv() > 60) {
            lines.add(Component.translatable("message.samogon.too_strong").withStyle(ChatFormatting.RED));
        }
        if (canDrink(batch)) {
            lines.add(Component.translatable("tooltip.samogon.intoxication").withStyle(ChatFormatting.GOLD));
            Tincture effective = effectiveTincture(batch);
            if (effective != Tincture.NONE) {
                lines.add(Component.translatable("tooltip.samogon.tincture." + effective.id())
                        .withStyle(ChatFormatting.YELLOW));
            }
        }
        lines.add(Component.translatable("tooltip.samogon.simulation").withStyle(ChatFormatting.DARK_GRAY));
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
