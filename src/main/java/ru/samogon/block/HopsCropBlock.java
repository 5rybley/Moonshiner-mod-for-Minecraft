package ru.samogon.block;

import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import ru.samogon.registry.ModItems;

/**
 * Hop bine grown on farmland from hop seeds.
 *
 * <p>The growth rules, the eight age stages, bonemeal behaviour and the
 * farmland requirement all come from {@link CropBlock}; only the seed the
 * block reports for pick-block and creative cloning is different. Harvest
 * drops come from {@code data/samogon/loot_table/blocks/hops.json}: cones at
 * full age, seeds at any age.</p>
 */
public final class HopsCropBlock extends CropBlock {
    public HopsCropBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected ItemLike getBaseSeedId() {
        return ModItems.HOPS_SEEDS.get();
    }
}