package ru.samogon.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraftforge.common.ForgeHooks;
import ru.samogon.process.GrapeVariety;
import ru.samogon.registry.ModItems;

import java.util.ArrayList;
import java.util.List;

/**
 * A grape vine trained on red supports, grown on farmland from grape seeds.
 *
 * <p>Growth, the eight age stages, bonemeal and the farmland requirement all
 * come from {@link CropBlock}; the block adds only the variety, which decides
 * the colour of the grapes it drops and the wine they make. The model is a
 * trellis roughly as tall as a player, so one block reads as a small bush even
 * though the geometry reaches into the block above.</p>
 *
 * <p>The blockstate property is a thin wrapper around {@link GrapeVariety}:
 * {@link EnumProperty} demands a {@link StringRepresentable} enum, while the
 * gameplay model stays free of Minecraft types so the headless winery test can
 * load it. Because vanilla {@link CropBlock} rebuilds the state from the
 * default one when it grows, this block re-implements the two growth paths so
 * the variety is never lost. Harvest drops come from
 * {@code data/samogon/loot_table/blocks/grape_vine.json}: 2 to 4 bunches of the
 * matching variety once the vine shows its ripe model, and its seeds at any
 * age. The block keeps a code-side fallback for the grapes so a vine that looks
 * ready always pays out even if the loot table is missing or fails to load.</p>
 */
public final class GrapeVineBlock extends CropBlock {
    /**
     * Age from which the blockstate shows the top model. The vine looks ripe
     * for two ages, so both must yield grapes; age 7 alone made ripe-looking
     * vines drop seeds only.
     */
    public static final int RIPE_AGE = 6;

    /** Fewest and most bunches a ripe vine gives, before a Fortune bonus. */
    private static final int MIN_BUNCHES = 2;
    private static final int MAX_BUNCHES = 4;

    /** Seeds returned by every vine, so a harvest can always be replanted. */
    public static final int SEEDS_PER_VINE = 2;

    /** Grape variety the vine carries; picked by the seed that planted it. */
    public static final EnumProperty<Variety> VARIETY = EnumProperty.create("variety", Variety.class);

    /**
     * Blockstate view of a grape variety. Serialized names match {@link
     * GrapeVariety#id()}, so the blockstate and the loot table read
     * {@code variety=cabernet} and never the Java constant name.
     */
    public enum Variety implements StringRepresentable {
        CABERNET(GrapeVariety.CABERNET),
        MERLOT(GrapeVariety.MERLOT),
        CHARDONNAY(GrapeVariety.CHARDONNAY),
        RIESLING(GrapeVariety.RIESLING);

        private final GrapeVariety grape;

        Variety(GrapeVariety grape) {
            this.grape = grape;
        }

        public GrapeVariety grape() {
            return grape;
        }

        @Override
        public String getSerializedName() {
            return grape.id();
        }

        public static Variety of(GrapeVariety grape) {
            for (Variety variety : values()) {
                if (variety.grape == grape) return variety;
            }
            return CABERNET;
        }
    }

    public GrapeVineBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(VARIETY, Variety.CABERNET));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(VARIETY);
    }

    @Override
    protected ItemLike getBaseSeedId() {
        return ModItems.seedFor(GrapeVariety.CABERNET);
    }

    /**
     * Grows one age like {@link CropBlock} but keeps the variety. Vanilla
     * {@code CropBlock#getStateForAge} rebuilds the state from
     * {@code defaultBlockState()} and would silently reset every vine to
     * Cabernet as soon as it grew or was bonemealed, so both growth paths are
     * re-implemented to step the age on the live state instead.
     */
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!level.isAreaLoaded(pos, 1)) return;
        if (level.getRawBrightness(pos, 0) < 9) return;
        int age = this.getAge(state);
        if (age >= this.getMaxAge()) return;
        float speed = getGrowthSpeed(state.getBlock(), level, pos);
        if (ForgeHooks.onCropsGrowPre(level, pos, state, random.nextInt((int) (25.0F / speed) + 1) == 0)) {
            level.setBlock(pos, withAge(state, age + 1), 2);
            ForgeHooks.onCropsGrowPost(level, pos, state);
        }
    }

    @Override
    public void growCrops(Level level, BlockPos pos, BlockState state) {
        int age = Math.min(this.getAge(state) + this.getBonemealAgeIncrease(level), this.getMaxAge());
        level.setBlock(pos, withAge(state, age), 2);
    }

    /** Advances the age on the existing state, so VARIETY survives growth. */
    private BlockState withAge(BlockState state, int age) {
        return state.setValue(this.getAgeProperty(), age);
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return new ItemStack(ModItems.seedFor(state.getValue(VARIETY).grape()));
    }

    /**
     * Guarantees the harvest on top of the loot table result: every vine gives
     * its seeds and a ripe one gives 2 to 4 bunches of the right variety.
     *
     * <p>Normally the table hands out both. The fallback covers a table that is
     * absent, fails to load or stops matching: a parsing slip in the table once
     * made every vine drop no seeds at all, while the code-side grape fallback
     * masked it. Each stack is added only when the table did not already give
     * it, so nothing is doubled. Explosions are left to the table so
     * {@code explosion_decay} keeps working.</p>
     */
    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        List<ItemStack> drops = new ArrayList<>(super.getDrops(state, params));
        if (params.getOptionalParameter(LootContextParams.EXPLOSION_RADIUS) != null) return drops;
        GrapeVariety grape = state.getValue(VARIETY).grape();
        boolean hasSeeds = false;
        boolean hasGrapes = false;
        for (ItemStack drop : drops) {
            if (drop.is(ModItems.seedFor(grape))) hasSeeds = true;
            if (ModItems.isGrapes(drop)) hasGrapes = true;
        }
        if (!hasSeeds) {
            drops.add(new ItemStack(ModItems.seedFor(grape), SEEDS_PER_VINE));
        }
        if (this.getAge(state) >= RIPE_AGE && !hasGrapes) {
            drops.add(new ItemStack(ModItems.grapeItem(grape),
                    Mth.nextInt(params.getLevel().getRandom(), MIN_BUNCHES, MAX_BUNCHES)));
        }
        return drops;
    }
}