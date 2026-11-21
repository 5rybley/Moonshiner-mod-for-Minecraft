package ru.samogon.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import ru.samogon.SamogonMod;
import ru.samogon.block.AgingBarrelBlock;
import ru.samogon.block.AgingBarrelBlockEntity;
import ru.samogon.block.BanyaStoveBlock;
import ru.samogon.block.BanyaStoveBlockEntity;
import ru.samogon.block.BottleRackBlock;
import ru.samogon.block.BreweryBlock;
import ru.samogon.block.BreweryBlockEntity;
import ru.samogon.block.CondenserBlock;
import ru.samogon.block.CondenserBlockEntity;
import ru.samogon.block.GrapeVineBlock;
import ru.samogon.block.HopsCropBlock;
import ru.samogon.block.PlacedDrinkBlock;
import ru.samogon.block.PlacedDrinkBlockEntity;
import ru.samogon.block.PlacedVesselBlock;
import ru.samogon.block.PlacedVesselBlockEntity;
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
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, SamogonMod.MOD_ID);
    public static final DeferredRegister<Item> BLOCK_ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, SamogonMod.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, SamogonMod.MOD_ID);
    public static final List<RegistryObject<Block>> MACHINES = new ArrayList<>();

    public static final RegistryObject<Block> MASH_TUN = machine("mash_tun", ProcessBlock.Kind.MASHING);
    public static final RegistryObject<Block> FERMENTATION_BARREL = machine("fermentation_barrel", ProcessBlock.Kind.FERMENTATION);
    public static final RegistryObject<Block> DISTILLER = machine("distiller", ProcessBlock.Kind.DISTILLER);
    public static final RegistryObject<Block> REFLUX_COLUMN = machine("reflux_column", ProcessBlock.Kind.RECTIFIER);
    public static final RegistryObject<Block> CHARCOAL_FILTER = machine("charcoal_filter", ProcessBlock.Kind.FILTER);
    public static final RegistryObject<Block> BLENDING_TABLE = machine("blending_table", ProcessBlock.Kind.BLENDING);
    public static final RegistryObject<Block> DILUTION_BASIN = machine("dilution_basin", ProcessBlock.Kind.DILUTION);
    public static final RegistryObject<Block> AGING_CASK = machine("aging_cask", ProcessBlock.Kind.AGING);
    public static final RegistryObject<Block> TASTING_TABLE = machine("tasting_table", ProcessBlock.Kind.TASTING);
    /**
     * A standalone one-block barrel that ferments hopped wort and yeast into
     * beer, so the last brewery step does not need the 2x2 brewhouse.
     */
    public static final RegistryObject<Block> BEER_FERMENTATION_BARREL =
            machine("beer_fermentation_barrel", ProcessBlock.Kind.BEER_FERMENTATION);
    /** The wine press: grapes in, grape must out; skin contact decides red or rosé. */
    public static final RegistryObject<Block> WINE_PRESS =
            machine("wine_press", ProcessBlock.Kind.PRESSING);
    /**
     * The riddling rack: bottle fermentation, lees ageing, disgorging and
     * dosage. A 1 wide x 2 tall x 2 deep shelf; the placed block is the master
     * and builds the three remaining parts, like the brewery.
     */
    public static final RegistryObject<Block> BOTTLE_RACK = BLOCKS.register("bottle_rack",
            () -> new BottleRackBlock(machineProperties(), ProcessBlock.Kind.SPARKLING));

    static {
        // The rack shares the machines' block entity, so it joins their type.
        MACHINES.add(BOTTLE_RACK);
    }

    public static final RegistryObject<Item> BOTTLE_RACK_ITEM = BLOCK_ITEMS.register("bottle_rack",
            () -> new MachineItem(BOTTLE_RACK.get(), new Item.Properties()));
    public static final RegistryObject<Block> CONDENSER = BLOCKS.register("condenser",
            () -> new CondenserBlock(BlockBehaviour.Properties.of()
                    .strength(2.5f).sound(SoundType.COPPER).noOcclusion()));
    public static final RegistryObject<Item> CONDENSER_ITEM = BLOCK_ITEMS.register("condenser",
            () -> new CondenserItem(CONDENSER.get(), new Item.Properties()));
    /**
     * The banya stove: the first block of the Russian line. A fire underneath
     * heats the stones and a poured bucket of water turns into hangover-clearing
     * steam. Deliberately a plain block with no block entity.
     */
    public static final RegistryObject<Block> BANYA_STOVE = BLOCKS.register("banya_stove",
            () -> new BanyaStoveBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.STONE_BRICKS)
                    .strength(2.5f).sound(SoundType.STONE).noOcclusion()));
    public static final RegistryObject<Item> BANYA_STOVE_ITEM = BLOCK_ITEMS.register("banya_stove",
            () -> new BlockItem(BANYA_STOVE.get(), new Item.Properties()));
    /**
     * The hop crop. It deliberately has no block item: seeds plant it, and
     * both the seeds and the harvested hops are ordinary items.
     */
    public static final RegistryObject<Block> HOPS = BLOCKS.register("hops",
            () -> new HopsCropBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.WHEAT)));
    /**
     * The grape vine. Like the hop crop it has no block item: seeds plant it,
     * and the harvested grapes and the seeds are ordinary items. The single
     * block carries a variety property and a trellis model about as tall as a
     * player.
     */
    public static final RegistryObject<Block> GRAPE_VINE = BLOCKS.register("grape_vine",
            () -> new GrapeVineBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.WHEAT)));
    public static final RegistryObject<Block> SAMOGON_CRATE = BLOCKS.register("samogon_crate",
            () -> new SamogonCrateBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS).noOcclusion()));
    public static final RegistryObject<Item> SAMOGON_CRATE_ITEM = BLOCK_ITEMS.register("samogon_crate",
            () -> new SamogonCrateItem(SAMOGON_CRATE.get(), new Item.Properties().stacksTo(1)));
    /**
     * A finished drink set down in the world. It has no item of its own: the
     * drink stack is placed with sneak-right-click and returned when broken.
     */
    public static final RegistryObject<Block> PLACED_DRINK = BLOCKS.register("placed_drink",
            () -> new PlacedDrinkBlock(BlockBehaviour.Properties.of()
                    .instabreak().noCollission().noOcclusion().sound(SoundType.WOOD)
                    .pushReaction(PushReaction.BLOCK)));
    /**
     * The small stacka (стопка): a short straight glass set on a table. It is
     * filled from a bottle and drunk straight from the world.
     */
    public static final RegistryObject<Block> SHOT_GLASS = BLOCKS.register("shot_glass",
            () -> new PlacedVesselBlock(BlockBehaviour.Properties.of()
                    .instabreak().noCollission().noOcclusion().sound(SoundType.GLASS)
                    .pushReaction(PushReaction.BLOCK),
                    Shapes.box(0.3125, 0.0, 0.3125, 0.6875, 0.4375, 0.6875)));
    public static final RegistryObject<Item> SHOT_GLASS_ITEM = BLOCK_ITEMS.register("shot_glass",
            () -> new BlockItem(SHOT_GLASS.get(), new Item.Properties().stacksTo(16)));
    /**
     * The cup (чарка): a wider wooden cup for a bigger pour. Same behaviour as
     * the shot glass, only a different vessel.
     */
    public static final RegistryObject<Block> CUP = BLOCKS.register("cup",
            () -> new PlacedVesselBlock(BlockBehaviour.Properties.of()
                    .instabreak().noCollission().noOcclusion().sound(SoundType.WOOD)
                    .pushReaction(PushReaction.BLOCK),
                    Shapes.box(0.21875, 0.0, 0.21875, 0.78125, 0.375, 0.78125)));
    public static final RegistryObject<Item> CUP_ITEM = BLOCK_ITEMS.register("cup",
            () -> new BlockItem(CUP.get(), new Item.Properties().stacksTo(16)));
    /**
     * The merged 2x2x2 barrel. It has no item of its own: eight
     * {@link #AGING_CASK} blocks placed as a cube build it, and breaking it
     * returns those eight casks.
     */
    public static final RegistryObject<Block> AGING_BARREL = BLOCKS.register("aging_barrel",
            () -> new AgingBarrelBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS)
                    .strength(2.0f).sound(SoundType.WOOD).noOcclusion()));

    /**
     * The brewery: a 2x2x1 brewhouse. The placed block is the master and builds
     * the three remaining parts, so the whole vessel appears in one action.
     */
    public static final RegistryObject<Block> BREWERY = BLOCKS.register("brewery",
            () -> new BreweryBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.COPPER_BLOCK)
                    .strength(2.5f)
                    .sound(SoundType.COPPER)
                    .lightLevel(state -> state.getValue(BreweryBlock.READY) ? 11
                            : state.getValue(BreweryBlock.ACTIVE) ? 6 : 0)
                    .noOcclusion()));
    public static final RegistryObject<Item> BREWERY_ITEM = BLOCK_ITEMS.register("brewery",
            () -> new BreweryItem(BREWERY.get(), new Item.Properties().stacksTo(1)));

    public static final RegistryObject<BlockEntityType<ProcessBlockEntity>> PROCESS_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("process", () -> BlockEntityType.Builder.of(ProcessBlockEntity::new,
                    MACHINES.stream().map(RegistryObject::get).toArray(Block[]::new)).build(null));
    public static final RegistryObject<BlockEntityType<SamogonCrateBlockEntity>> SAMOGON_CRATE_ENTITY =
            BLOCK_ENTITIES.register("samogon_crate", () -> BlockEntityType.Builder.of(
                    SamogonCrateBlockEntity::new, SAMOGON_CRATE.get()).build(null));
    public static final RegistryObject<BlockEntityType<PlacedDrinkBlockEntity>> PLACED_DRINK_ENTITY =
            BLOCK_ENTITIES.register("placed_drink", () -> BlockEntityType.Builder.of(
                    PlacedDrinkBlockEntity::new, PLACED_DRINK.get()).build(null));
    public static final RegistryObject<BlockEntityType<PlacedVesselBlockEntity>> PLACED_VESSEL_ENTITY =
            BLOCK_ENTITIES.register("placed_vessel", () -> BlockEntityType.Builder.of(
                    PlacedVesselBlockEntity::new, SHOT_GLASS.get(), CUP.get()).build(null));
    public static final RegistryObject<BlockEntityType<CondenserBlockEntity>> CONDENSER_ENTITY =
            BLOCK_ENTITIES.register("condenser", () -> BlockEntityType.Builder.of(
                    CondenserBlockEntity::new, CONDENSER.get()).build(null));
    public static final RegistryObject<BlockEntityType<AgingBarrelBlockEntity>> AGING_BARREL_ENTITY =
            BLOCK_ENTITIES.register("aging_barrel", () -> BlockEntityType.Builder.of(
                    AgingBarrelBlockEntity::new, AGING_BARREL.get()).build(null));
    public static final RegistryObject<BlockEntityType<BreweryBlockEntity>> BREWERY_ENTITY =
            BLOCK_ENTITIES.register("brewery", () -> BlockEntityType.Builder.of(
                    BreweryBlockEntity::new, BREWERY.get()).build(null));
    public static final RegistryObject<BlockEntityType<BanyaStoveBlockEntity>> BANYA_STOVE_ENTITY =
            BLOCK_ENTITIES.register("banya_stove", () -> BlockEntityType.Builder.of(
                    BanyaStoveBlockEntity::new, BANYA_STOVE.get()).build(null));

    private static RegistryObject<Block> machine(String id, ProcessBlock.Kind kind) {
        RegistryObject<Block> block = BLOCKS.register(id, () -> new ProcessBlock(machineProperties(), kind));
        MACHINES.add(block);
        BLOCK_ITEMS.register(id, () -> new MachineItem(block.get(), new Item.Properties()));
        return block;
    }

    /**
     * Shared behaviour of every machine block: copper strength and sound, a
     * light that follows the heat and the progress band, and no occlusion so
     * the models read correctly. The bottle rack reuses it verbatim.
     */
    private static BlockBehaviour.Properties machineProperties() {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.COPPER_BLOCK)
                .strength(2.5f)
                .sound(SoundType.COPPER)
                .lightLevel(state -> Math.max(
                        state.getValue(ProcessBlock.HEAT) * 2,
                        state.getValue(ProcessBlock.READY) ? 11
                                : state.getValue(ProcessBlock.ACTIVE)
                                ? 3 + state.getValue(ProcessBlock.PROGRESS) : 0))
                .noOcclusion();
    }

    private ModBlocks() {}
}
