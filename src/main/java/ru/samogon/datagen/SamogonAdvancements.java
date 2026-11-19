package ru.samogon.datagen;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.critereon.ConsumeItemTrigger;
import net.minecraft.advancements.critereon.EffectsChangedTrigger;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.advancements.critereon.MobEffectsPredicate;
import net.minecraft.advancements.critereon.PlayerTrigger;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.common.data.AdvancementProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import ru.samogon.SamogonMod;
import ru.samogon.registry.ModBlocks;
import ru.samogon.registry.ModEffects;
import ru.samogon.registry.ModItems;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Every Moonshiner advancement, generated from one place.
 *
 * <p>Before this class the tree lived as sixteen hand-written JSON files and
 * the newer systems (brewery, wine, kvass, banya, vessels) simply had no
 * entries. Keeping the whole tree in Java means the parent chain, the icons and
 * the criteria are checked by the compiler and by {@code runData}, and adding a
 * branch is a few lines instead of a new file with a hand-typed id.</p>
 *
 * <p>Order matters: {@link ExistingFileHelper} only accepts a parent that has
 * already been written, so the tree is emitted root first, then branch by
 * branch.</p>
 */
public final class SamogonAdvancements implements AdvancementProvider.AdvancementGenerator {
    private static final String MOD_ID = SamogonMod.MOD_ID;

    @Override
    public void generate(HolderLookup.Provider registries, Consumer<AdvancementHolder> saver,
                         ExistingFileHelper existingFileHelper) {
        AdvancementHolder root = saveRoot(saver, existingFileHelper);

        // === DISTILLING ===
        AdvancementHolder mash = save(saver, existingFileHelper, "first_mash", root,
                new ItemStack(ModItems.MALT.get()), "first_mash", AdvancementType.TASK,
                true, true, false,
                "got_item", items(ModItems.SWEET_WORT.get()));

        AdvancementHolder ferment = save(saver, existingFileHelper, "first_ferment", mash,
                new ItemStack(ModItems.YEAST.get()), "first_ferment", AdvancementType.TASK,
                true, true, false,
                "got_item", items(ModItems.FERMENTED_MASH.get()));

        AdvancementHolder still = save(saver, existingFileHelper, "first_still", ferment,
                new ItemStack(ModBlocks.DISTILLER.get()), "first_still", AdvancementType.TASK,
                true, true, false,
                "got_item", items(ModBlocks.DISTILLER.get()));

        AdvancementHolder hearts = save(saver, existingFileHelper, "first_hearts", still,
                new ItemStack(ModItems.HEARTS_FRACTION.get()), "first_hearts", AdvancementType.GOAL,
                true, true, false,
                "got_item", items(ModItems.HEARTS_FRACTION.get()));

        AdvancementHolder dilution = save(saver, existingFileHelper, "first_dilution", hearts,
                new ItemStack(ModBlocks.DILUTION_BASIN.get()), "first_dilution", AdvancementType.TASK,
                true, true, false,
                "got_item", items(ModItems.DILUTED_SPIRIT.get()));

        save(saver, existingFileHelper, "oak_aged", dilution,
                new ItemStack(ModItems.OAK_AGED_SPIRIT.get()), "oak_aged", AdvancementType.CHALLENGE,
                true, true, false,
                "got_item", items(ModItems.OAK_AGED_SPIRIT.get()));

        save(saver, existingFileHelper, "first_blend", dilution,
                new ItemStack(ModItems.SAMOGON.get()), "first_blend", AdvancementType.GOAL,
                true, true, false,
                "got_item", items(ModItems.SAMOGON.get()));

        AdvancementHolder filter = save(saver, existingFileHelper, "first_filter", hearts,
                new ItemStack(ModItems.ACTIVATED_CHARCOAL.get()), "first_filter", AdvancementType.TASK,
                true, true, false,
                "got_item", items(ModItems.FILTERED_SPIRIT.get()));

        AdvancementHolder neutral = save(saver, existingFileHelper, "first_neutral", filter,
                new ItemStack(ModBlocks.REFLUX_COLUMN.get()), "first_neutral", AdvancementType.GOAL,
                true, true, false,
                "got_item", items(ModItems.NEUTRAL_SPIRIT.get()));

        save(saver, existingFileHelper, "anhydrous", neutral,
                new ItemStack(ModItems.MOLECULAR_SIEVE.get()), "anhydrous", AdvancementType.CHALLENGE,
                true, true, false,
                "got_item", items(ModItems.ANHYDROUS_SPIRIT.get()));

        // === DRINKING ===
        AdvancementHolder drink = save(saver, existingFileHelper, "first_drink", root,
                new ItemStack(ModItems.BERRY_TINCTURE.get()), "first_drink", AdvancementType.GOAL,
                true, true, false,
                "drank", ConsumeItemTrigger.TriggerInstance.usedItem(ItemPredicate.Builder.item()
                        .of(ModItems.DILUTED_SPIRIT.get(), ModItems.FILTERED_SPIRIT.get(),
                                ModItems.HEARTS_FRACTION.get(), ModItems.SAMOGON.get(),
                                ModItems.BERRY_TINCTURE.get(), ModItems.HONEY_TINCTURE.get(),
                                ModItems.BOTANICAL_TINCTURE.get(), ModItems.SPICED_TINCTURE.get(),
                                ModItems.FRUIT_TINCTURE.get(), ModItems.MARINE_TINCTURE.get(),
                                ModItems.OAK_AGED_SPIRIT.get(), ModItems.BEER.get(),
                                ModItems.KVASS.get(), ModItems.WINE.get(),
                                ModItems.SPARKLING_WINE.get())));

        MobEffectsPredicate.Builder drunk = MobEffectsPredicate.Builder.effects()
                .and(ModEffects.INTOXICATION, new MobEffectsPredicate.MobEffectInstancePredicate(
                        MinMaxBounds.Ints.atLeast(4), MinMaxBounds.Ints.ANY,
                        Optional.empty(), Optional.empty()));
        AdvancementHolder levelV = save(saver, existingFileHelper, "level_v", drink,
                new ItemStack(ModItems.HEADS_FRACTION.get()), "level_v", AdvancementType.CHALLENGE,
                true, true, false,
                "drunk", EffectsChangedTrigger.TriggerInstance.hasEffects(drunk));

        save(saver, existingFileHelper, "morning_after", levelV,
                new ItemStack(ModItems.BRINE.get()), "morning_after", AdvancementType.GOAL,
                true, true, false,
                "hangover", EffectsChangedTrigger.TriggerInstance.hasEffects(
                        MobEffectsPredicate.Builder.effects().and(ModEffects.HANGOVER)));

        save(saver, existingFileHelper, "technical_taste", drink,
                new ItemStack(ModItems.TAILS_FRACTION.get()), "technical_taste", AdvancementType.CHALLENGE,
                true, true, false,
                "drank", ConsumeItemTrigger.TriggerInstance.usedItem(ItemPredicate.Builder.item()
                        .of(ModItems.HEADS_FRACTION.get(), ModItems.TAILS_FRACTION.get(),
                                ModItems.NEUTRAL_SPIRIT.get(), ModItems.ANHYDROUS_SPIRIT.get())));

        save(saver, existingFileHelper, "first_crate", root,
                new ItemStack(ModBlocks.SAMOGON_CRATE.get()), "first_crate", AdvancementType.TASK,
                true, true, false,
                "got_item", items(ModBlocks.SAMOGON_CRATE.get()));

        save(saver, existingFileHelper, "first_vessel", drink,
                new ItemStack(ModBlocks.CUP_ITEM.get()), "first_vessel", AdvancementType.TASK,
                true, true, false,
                "got_item", items(ModBlocks.CUP_ITEM.get(), ModBlocks.SHOT_GLASS_ITEM.get()));

        // === BREWERY AND KVASS ===
        AdvancementHolder brewery = save(saver, existingFileHelper, "first_brewery", root,
                new ItemStack(ModBlocks.BREWERY_ITEM.get()), "first_brewery", AdvancementType.TASK,
                true, true, false,
                "got_item", items(ModBlocks.BREWERY_ITEM.get()));

        AdvancementHolder beer = save(saver, existingFileHelper, "first_beer", brewery,
                new ItemStack(ModItems.BEER.get()), "first_beer", AdvancementType.GOAL,
                true, true, false,
                "got_item", items(ModItems.BEER.get()));

        save(saver, existingFileHelper, "first_kvass", beer,
                new ItemStack(ModItems.KVASS.get()), "first_kvass", AdvancementType.TASK,
                true, true, false,
                "got_item", items(ModItems.KVASS.get()));

        // === WINERY ===
        AdvancementHolder grape = save(saver, existingFileHelper, "first_grape", root,
                new ItemStack(ModItems.CABERNET_GRAPES.get()), "first_grape", AdvancementType.TASK,
                true, true, false,
                "got_item", items(ModItems.CABERNET_GRAPES.get(), ModItems.MERLOT_GRAPES.get(),
                        ModItems.CHARDONNAY_GRAPES.get(), ModItems.RIESLING_GRAPES.get()));

        AdvancementHolder wine = save(saver, existingFileHelper, "first_wine", grape,
                new ItemStack(ModItems.WINE.get()), "first_wine", AdvancementType.GOAL,
                true, true, false,
                "got_item", items(ModItems.WINE.get()));

        save(saver, existingFileHelper, "first_sparkling", wine,
                new ItemStack(ModItems.SPARKLING_WINE.get()), "first_sparkling", AdvancementType.CHALLENGE,
                true, true, false,
                "got_item", items(ModItems.SPARKLING_WINE.get()));

        save(saver, existingFileHelper, "first_cellar", wine,
                new ItemStack(ModBlocks.BOTTLE_RACK_ITEM.get()), "first_cellar", AdvancementType.TASK,
                true, true, false,
                "got_item", items(ModBlocks.BOTTLE_RACK_ITEM.get()));

        // === BANYA ===
        AdvancementHolder banya = save(saver, existingFileHelper, "first_banya", root,
                new ItemStack(ModBlocks.BANYA_STOVE_ITEM.get()), "first_banya", AdvancementType.TASK,
                true, true, false,
                "got_item", items(ModBlocks.BANYA_STOVE_ITEM.get()));

        save(saver, existingFileHelper, "banya_glow", banya,
                new ItemStack(ModBlocks.BANYA_STOVE_ITEM.get()), "banya_glow", AdvancementType.CHALLENGE,
                true, true, false,
                "glow", EffectsChangedTrigger.TriggerInstance.hasEffects(
                        MobEffectsPredicate.Builder.effects().and(ModEffects.AFTER_BANYA)));

        save(saver, existingFileHelper, "first_besom", banya,
                new ItemStack(ModItems.BESOM.get()), "first_besom", AdvancementType.TASK,
                true, true, false,
                "got_item", items(ModItems.BESOM.get()));
    }

    private static AdvancementHolder saveRoot(Consumer<AdvancementHolder> saver, ExistingFileHelper helper) {
        return Advancement.Builder.advancement()
                .display(new ItemStack(ModItems.YEAST.get()),
                        Component.translatable("advancement.samogon.root.title"),
                        Component.translatable("advancement.samogon.root.description"),
                        ResourceLocation.fromNamespaceAndPath(MOD_ID,
                                "textures/gui/advancements/backgrounds/workshop.png"),
                        AdvancementType.TASK, true, false, false)
                .addCriterion("tick", PlayerTrigger.TriggerInstance.tick())
                .save(saver, ResourceLocation.fromNamespaceAndPath(MOD_ID, "root"), helper);
    }

    private static AdvancementHolder save(Consumer<AdvancementHolder> saver, ExistingFileHelper helper,
                                          String name, AdvancementHolder parent, ItemStack icon,
                                          String translation, AdvancementType frame,
                                          boolean toast, boolean announce, boolean hidden,
                                          String criterion, net.minecraft.advancements.Criterion<?> trigger) {
        Advancement.Builder builder = Advancement.Builder.advancement()
                .display(icon, Component.translatable("advancement.samogon." + translation + ".title"),
                        Component.translatable("advancement.samogon." + translation + ".description"),
                        null, frame, toast, announce, hidden)
                .addCriterion(criterion, trigger);
        if (parent != null) {
            builder.parent(parent);
        }
        return builder.save(saver, ResourceLocation.fromNamespaceAndPath(MOD_ID, name), helper);
    }

    /**
     * One item predicate covering every listed item, so holding any one of them
     * is enough. Passing the items straight to {@code hasItems} would instead
     * build one predicate per item, and the trigger requires all predicates at
     * once: "any grape" would silently mean "all four grapes".
     */
    private static net.minecraft.advancements.Criterion<InventoryChangeTrigger.TriggerInstance> items(
            ItemLike... items) {
        return InventoryChangeTrigger.TriggerInstance.hasItems(
                ItemPredicate.Builder.item().of(items).build());
    }

    public SamogonAdvancements() {}
}
