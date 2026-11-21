package ru.samogon.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestDontPrefix;
import net.minecraftforge.gametest.GameTestHolder;
import ru.samogon.SamogonMod;
import ru.samogon.block.AgingBarrelBlock;
import ru.samogon.block.AgingBarrelBlockEntity;
import ru.samogon.block.BreweryBlock;
import ru.samogon.block.BreweryBlockEntity;
import ru.samogon.block.ProcessBlockEntity;
import ru.samogon.config.SamogonConfig;
import ru.samogon.item.BatchData;
import ru.samogon.process.BeerStyle;
import ru.samogon.process.Brewing;
import ru.samogon.process.BreweryData;
import ru.samogon.process.Simulation;
import ru.samogon.process.Simulation.Batch;
import ru.samogon.registry.ModBlocks;
import ru.samogon.registry.ModItems;

/**
 * Server-side game tests for the three machines whose state machines are easiest
 * to break: the brewery, the 2x2x2 aging barrel and the fermentation barrel.
 *
 * <p>The tests build a real multiblock in an empty template, drive the block
 * entity directly (so a test does not spend half a minute of wall clock on one
 * boil) and then read the public machine data. They run under {@code
 * gradlew runGameTestServer}.</p>
 */
@GameTestHolder(value = SamogonMod.MOD_ID, namespace = SamogonMod.MOD_ID)
@GameTestDontPrefix
public final class SamogonGameTests {
    private static final BlockPos BREWERY = new BlockPos(1, 1, 1);
    private static final BlockPos HEAT = new BlockPos(1, 0, 1);

    /**
     * A heated boil consumes the hops and turns wort into hopped wort. This is
     * the normal path the brewery GUI and automation both use.
     */
    @GameTest(template = "empty", batch = "samogon", timeoutTicks = 100)
    public static void breweryBoilCompletes(GameTestHelper helper) {
        withMachineSpeed(20.0, () -> {
            BreweryBlockEntity brewery = placeBrewery(helper);
            brewery.inventory().setItem(BreweryBlockEntity.SLOT_INPUT,
                    BatchData.create(ModItems.BEER_WORT.get(), Brewing.wort(BeerStyle.Grain.BARLEY.id())));
            brewery.inventory().setItem(BreweryBlockEntity.SLOT_ADDITIVE,
                    new ItemStack(ModItems.HOPS.get()));
            brewery.setTab(1);
            // Pull the bottled wort into the vessel first: that returns the
            // empty glass bottle to the output slot, and a boil that finishes
            // with an empty vessel waiting would bottle itself and clear
            // READY before the assertion. Drop the bottle so the test reads
            // the finished boil in the vessel.
            brewery.tick();
            brewery.inventory().setItem(BreweryBlockEntity.SLOT_OUTPUT, ItemStack.EMPTY);

            for (int i = 0; i < 80; i++) brewery.tick();

            helper.assertTrue(brewery.data().get(BreweryData.STEP) == Brewing.STAGE_HOPPED,
                    "boil should finish as hopped wort");
            helper.assertTrue(brewery.data().get(BreweryData.READY) == 1,
                    "finished boil should be ready to bottle");
        });
        helper.succeed();
    }

    /**
     * Pulling the fire out from under a running boil must not leave the brewery
     * soft-locked: the run stops, the wort stays in the vessel and the player
     * can switch tabs or bottle it again.
     */
    @GameTest(template = "empty", batch = "samogon", timeoutTicks = 100)
    public static void breweryHeatLossDoesNotDeadlock(GameTestHelper helper) {
        withMachineSpeed(20.0, () -> {
            BreweryBlockEntity brewery = placeBrewery(helper);
            brewery.inventory().setItem(BreweryBlockEntity.SLOT_INPUT,
                    BatchData.create(ModItems.BEER_WORT.get(), Brewing.wort(BeerStyle.Grain.BARLEY.id())));
            brewery.inventory().setItem(BreweryBlockEntity.SLOT_ADDITIVE,
                    new ItemStack(ModItems.HOPS.get()));
            brewery.setTab(1);
            // Start the boil and drop the glass bottle it returned, so a
            // stopped boil stays in the vessel instead of bottling itself.
            brewery.tick();
            brewery.inventory().setItem(BreweryBlockEntity.SLOT_OUTPUT, ItemStack.EMPTY);
            for (int i = 0; i < 5; i++) brewery.tick();

            helper.setBlock(HEAT, Blocks.AIR);
            for (int i = 0; i < 5; i++) brewery.tick();

            helper.assertTrue(brewery.data().get(BreweryData.ACTIVE) == 0,
                    "a boil without fire must not report itself active");
            helper.assertTrue(brewery.data().get(BreweryData.READY) == 1,
                    "the wort must become bottleable when the fire dies");
            helper.assertTrue(brewery.setTab(0),
                    "the tab must be switchable after the boil stops");
        });
        helper.succeed();
    }

    /**
     * A bottle of drinkable spirit matures after the configured time, spends
     * exactly one oak chip and stays an oak-aged spirit.
     */
    @GameTest(template = "empty", batch = "samogon", timeoutTicks = 100)
    public static void agingBarrelMaturesBottle(GameTestHelper helper) {
        withMachineSpeed(100.0, () -> {
            AgingBarrelBlockEntity barrel = placeAgingBarrel(helper);
            Batch spirit = new Batch(500, 40, 70, 10, 1, true, "grain", 0);
            helper.assertTrue(barrel.tryInsertBottle(
                            BatchData.create(ModItems.DILUTED_SPIRIT.get(), spirit)),
                    "the cask should accept a bottle");
            helper.assertTrue(barrel.insertChips(new ItemStack(ModItems.OAK_CHIPS.get(), 2)) == 2,
                    "the cask should accept oak chips");

            for (int i = 0; i < 120; i++) barrel.tick();

            helper.assertTrue(barrel.getItem(0).is(ModItems.OAK_AGED_SPIRIT.get()),
                    "the matured bottle should be oak-aged spirit");
            helper.assertTrue(barrel.chipCount() == 1,
                    "exactly one chip should be spent per matured bottle");
            helper.assertTrue(barrel.readyCount() == 0,
                    "the matured bottle should reset its progress");
        });
        helper.succeed();
    }

    /**
     * The fermentation barrel loads a wash, waits for yeast, ferments and puts
     * the finished mash into the waiting glass bottle without player clicks.
     */
    @GameTest(template = "empty", batch = "samogon", timeoutTicks = 100)
    public static void fermentationBarrelBottlesWash(GameTestHelper helper) {
        withMachineSpeed(100.0, () -> {
            helper.setBlock(1, 1, 1, ModBlocks.FERMENTATION_BARREL.get().defaultBlockState());
            ProcessBlockEntity barrel = helper.getBlockEntity(new BlockPos(1, 1, 1));
            barrel.inventory().setItem(ProcessBlockEntity.SLOT_INPUT,
                    BatchData.create(ModItems.SWEET_WORT.get(), Simulation.mash("grain", 4)));
            barrel.inventory().setItem(ProcessBlockEntity.SLOT_ADDITIVE,
                    new ItemStack(ModItems.YEAST.get()));
            barrel.inventory().setItem(ProcessBlockEntity.SLOT_OUTPUT,
                    new ItemStack(Items.GLASS_BOTTLE));

            for (int i = 0; i < 260; i++) barrel.tick();

            helper.assertTrue(barrel.inventory().getItem(ProcessBlockEntity.SLOT_OUTPUT)
                            .is(ModItems.FERMENTED_MASH.get()),
                    "the barrel should bottle the finished mash by itself");
        });
        helper.succeed();
    }

    /**
     * A bottle of neutral spirit is accepted by a real furnace, lights the
     * firebox, smelts the input and leaves the empty bottle in the fuel slot.
     */
    @GameTest(template = "empty", batch = "samogon", timeoutTicks = 400)
    public static void spiritFuelsFurnace(GameTestHelper helper) {
        BlockPos furnacePos = new BlockPos(1, 1, 1);
        helper.setBlock(furnacePos, Blocks.FURNACE.defaultBlockState());
        AbstractFurnaceBlockEntity furnace = helper.getBlockEntity(furnacePos);
        BlockPos absolute = helper.absolutePos(furnacePos);
        Batch spirit = new Batch(100, 96, 90, 0, 0, false, "sugar", 0);
        ItemStack fuel = BatchData.create(ModItems.NEUTRAL_SPIRIT.get(), spirit);
        helper.assertTrue(fuel.getBurnTime(RecipeType.SMELTING) == 48 * 200,
                "96% neutral spirit must burn for forty-eight smelts");
        furnace.setItem(0, new ItemStack(Items.RAW_IRON));
        furnace.setItem(1, fuel);

        for (int i = 0; i < 210; i++) {
            AbstractFurnaceBlockEntity.serverTick(helper.getLevel(), absolute,
                    helper.getLevel().getBlockState(absolute), furnace);
        }

        helper.assertTrue(furnace.getItem(2).is(Items.IRON_INGOT),
                "spirit fuel must smelt the iron in the input slot");
        helper.assertTrue(furnace.getItem(1).is(Items.GLASS_BOTTLE),
                "the empty bottle must be left in the fuel slot");
        helper.succeed();
    }

    // === HELPERS ===

    private static BreweryBlockEntity placeBrewery(GameTestHelper helper) {
        for (int dx = 0; dx < 2; dx++) {
            for (int dz = 0; dz < 2; dz++) {
                BlockState state = ModBlocks.BREWERY.get().defaultBlockState()
                        .setValue(BreweryBlock.PART, BreweryBlock.part(dx, dz))
                        .setValue(BreweryBlock.FACING, Direction.NORTH);
                helper.setBlock(1 + dx, 1, 1 + dz, state);
            }
        }
        helper.setBlock(HEAT, Blocks.CAMPFIRE.defaultBlockState());
        return helper.getBlockEntity(BREWERY);
    }

    private static AgingBarrelBlockEntity placeAgingBarrel(GameTestHelper helper) {
        for (int dx = 0; dx < 2; dx++) {
            for (int dy = 0; dy < 2; dy++) {
                for (int dz = 0; dz < 2; dz++) {
                    BlockState state = ModBlocks.AGING_BARREL.get().defaultBlockState()
                            .setValue(AgingBarrelBlock.PART, AgingBarrelBlock.part(dx, dy, dz))
                            .setValue(AgingBarrelBlock.FACING, Direction.NORTH);
                    helper.setBlock(1 + dx, 1 + dy, 1 + dz, state);
                }
            }
        }
        return helper.getBlockEntity(BREWERY);
    }

    /** Runs the body with a temporary machine-speed multiplier and always restores it. */
    private static void withMachineSpeed(double speed, Runnable body) {
        double previous = SamogonConfig.MACHINE_SPEED_MULTIPLIER.get();
        SamogonConfig.MACHINE_SPEED_MULTIPLIER.set(speed);
        try {
            body.run();
        } finally {
            SamogonConfig.MACHINE_SPEED_MULTIPLIER.set(previous);
        }
    }

    private SamogonGameTests() {}
}