package ru.samogon.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import ru.samogon.SamogonMod;
import ru.samogon.block.AgingBarrelBlock;
import ru.samogon.block.AgingBarrelBlockEntity;
import ru.samogon.block.BreweryBlock;
import ru.samogon.block.BreweryBlockEntity;
import ru.samogon.block.CondenserBlock;
import ru.samogon.block.CondenserBlockEntity;
import ru.samogon.block.HopsCropBlock;
import ru.samogon.block.PlacedDrinkBlock;
import ru.samogon.block.PlacedDrinkBlockEntity;
import ru.samogon.block.ProcessBlock;
import ru.samogon.block.ProcessBlockEntity;
import ru.samogon.block.SamogonCrateBlock;
import ru.samogon.block.SamogonCrateBlockEntity;
import ru.samogon.item.BreweryItem;
import ru.samogon.item.CondenserItem;
import ru.samogon.item.MachineItem;
import ru.samogon.item.SamogonCrateItem;

import java.util.ArrayList;
import java.util.List;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(SamogonMod.MOD_ID);
    public static final DeferredRegister.Items BLOCK_ITEMS = DeferredRegister.createItems(SamogonMod.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, SamogonMod.MOD_ID);
    public static final List<DeferredBlock<Block>> MACHINES = new ArrayList<>();

    public static final DeferredBlock<Block> MASH_TUN = machine("mash_tun", ProcessBlock.Kind.MASHING);
    public static final DeferredBlock<Block> FERMENTATION_BARREL = machine("fermentation_barrel", ProcessBlock.Kind.FERMENTATION);
    public static final DeferredBlock<Block> DISTILLER = machine("distiller", ProcessBlock.Kind.DISTILLER);
    public static final DeferredBlock<Block> REFLUX_COLUMN = machine("reflux_column", ProcessBlock.Kind.RECTIFIER);
    public static final DeferredBlock<Block> CHARCOAL_FILTER = machine("charcoal_filter", ProcessBlock.Kind.FILTER);
    public static final DeferredBlock<Block> BLENDING_TABLE = machine("blending_table", ProcessBlock.Kind.BLENDING);
    public static final DeferredBlock<Block> DILUTION_BASIN = machine("dilution_basin", ProcessBlock.Kind.DILUTION);
    public static final DeferredBlock<Block> AGING_CASK = machine("aging_cask", ProcessBlock.Kind.AGING);
    public static final DeferredBlock<Block> TASTING_TABLE = machine("tasting_table", ProcessBlock.Kind.TASTING);
    /**
     * A standalone one-block barrel that ferments hopped wort and yeast into
     * beer, so the last brewery step does not need the 2x2 brewhouse.
     */
    public static final DeferredBlock<Block> BEER_FERMENTATION_BARREL =
            machine("beer_fermentation_barrel", ProcessBlock.Kind.BEER_FERMENTATION);
    public static final DeferredBlock<Block> CONDENSER = BLOCKS.register("condenser",
            () -> new CondenserBlock(BlockBehaviour.Properties.of()
                    .strength(2.5f).sound(SoundType.COPPER).noOcclusion()));
    public static final DeferredItem<Item> CONDENSER_ITEM = BLOCK_ITEMS.register("condenser",
            () -> new CondenserItem(CONDENSER.get(), new Item.Properties()));
    /**
     * The hop crop. It deliberately has no block item: seeds plant it, and
     * both the seeds and the harvested hops are ordinary items.
     */
    public static final DeferredBlock<Block> HOPS = BLOCKS.register("hops",
            () -> new HopsCropBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.WHEAT)));
    public static final DeferredBlock<Block> SAMOGON_CRATE = BLOCKS.register("samogon_crate",
            () -> new SamogonCrateBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS).noOcclusion()));
    public static final DeferredItem<Item> SAMOGON_CRATE_ITEM = BLOCK_ITEMS.register("samogon_crate",
            () -> new SamogonCrateItem(SAMOGON_CRATE.get(), new Item.Properties().stacksTo(1)));
    /**
     * A finished drink set down in the world. It has no item of its own: the
     * drink stack is placed with sneak-right-click and returned when broken.
     */
    public static final DeferredBlock<Block> PLACED_DRINK = BLOCKS.register("placed_drink",
            () -> new PlacedDrinkBlock(BlockBehaviour.Properties.of()
                    .instabreak().noCollission().noOcclusion().sound(SoundType.WOOD)
                    .pushReaction(PushReaction.BLOCK)));
    /**
     * The merged 2x2x2 barrel. It has no item of its own: eight
     * {@link #AGING_CASK} blocks placed as a cube build it, and breaking it
     * returns those eight casks.
     */
    public static final DeferredBlock<Block> AGING_BARREL = BLOCKS.register("aging_barrel",
            () -> new AgingBarrelBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS)
                    .strength(2.0f).sound(SoundType.WOOD).noOcclusion()));

    /**
     * The brewery: a 2x2x1 brewhouse. The placed block is the master and builds
     * the three remaining parts, so the whole vessel appears in one action.
     */
    public static final DeferredBlock<Block> BREWERY = BLOCKS.register("brewery",
            () -> new BreweryBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.COPPER_BLOCK)
                    .strength(2.5f)
                    .sound(SoundType.COPPER)
                    .lightLevel(state -> state.getValue(BreweryBlock.READY) ? 11
                            : state.getValue(BreweryBlock.ACTIVE) ? 6 : 0)
                    .noOcclusion()));
    public static final DeferredItem<Item> BREWERY_ITEM = BLOCK_ITEMS.register("brewery",
            () -> new BreweryItem(BREWERY.get(), new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ProcessBlockEntity>> PROCESS_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("process", () -> BlockEntityType.Builder.of(ProcessBlockEntity::new,
                    MACHINES.stream().map(DeferredBlock::get).toArray(Block[]::new)).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SamogonCrateBlockEntity>> SAMOGON_CRATE_ENTITY =
            BLOCK_ENTITIES.register("samogon_crate", () -> BlockEntityType.Builder.of(
                    SamogonCrateBlockEntity::new, SAMOGON_CRATE.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PlacedDrinkBlockEntity>> PLACED_DRINK_ENTITY =
            BLOCK_ENTITIES.register("placed_drink", () -> BlockEntityType.Builder.of(
                    PlacedDrinkBlockEntity::new, PLACED_DRINK.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CondenserBlockEntity>> CONDENSER_ENTITY =
            BLOCK_ENTITIES.register("condenser", () -> BlockEntityType.Builder.of(
                    CondenserBlockEntity::new, CONDENSER.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AgingBarrelBlockEntity>> AGING_BARREL_ENTITY =
            BLOCK_ENTITIES.register("aging_barrel", () -> BlockEntityType.Builder.of(
                    AgingBarrelBlockEntity::new, AGING_BARREL.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BreweryBlockEntity>> BREWERY_ENTITY =
            BLOCK_ENTITIES.register("brewery", () -> BlockEntityType.Builder.of(
                    BreweryBlockEntity::new, BREWERY.get()).build(null));

    private static DeferredBlock<Block> machine(String id, ProcessBlock.Kind kind) {
        DeferredBlock<Block> block = BLOCKS.register(id, () -> new ProcessBlock(
                BlockBehaviour.Properties.ofFullCopy(Blocks.COPPER_BLOCK)
                        .strength(2.5f)
                        .sound(SoundType.COPPER)
                        .lightLevel(state -> Math.max(
                                state.getValue(ProcessBlock.HEAT) * 2,
                                state.getValue(ProcessBlock.READY) ? 11
                                        : state.getValue(ProcessBlock.ACTIVE)
                                        ? 3 + state.getValue(ProcessBlock.PROGRESS) : 0))
                        .noOcclusion(), kind));
        MACHINES.add(block);
        BLOCK_ITEMS.register(id, () -> new MachineItem(block.get(), new Item.Properties()));
        return block;
    }

    private ModBlocks() {}
}
