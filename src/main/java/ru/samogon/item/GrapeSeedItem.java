package ru.samogon.item;

import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import javax.annotation.Nullable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import ru.samogon.block.GrapeVineBlock;
import ru.samogon.process.GrapeVariety;

/**
 * Grape seeds: plant on farmland to grow the vine of a named variety.
 *
 * <p>One item exists per variety so the player chooses what to grow. The item
 * stamps its variety into the {@link GrapeVineBlock#VARIETY} property as the
 * block is placed; everything else (placement rules, farmland, the seed name)
 * comes from {@link ItemNameBlockItem}.</p>
 */
public final class GrapeSeedItem extends ItemNameBlockItem {
    private final GrapeVariety variety;

    public GrapeSeedItem(Block block, Properties properties, GrapeVariety variety) {
        super(block, properties);
        this.variety = variety;
    }

    public GrapeVariety variety() {
        return variety;
    }

    @Override
    @Nullable
    protected BlockState getPlacementState(BlockPlaceContext context) {
        BlockState state = super.getPlacementState(context);
        if (state == null) return null;
        // The blockstate property is a StringRepresentable wrapper; translate
        // the gameplay variety into the block's view of it.
        return state.setValue(GrapeVineBlock.VARIETY, GrapeVineBlock.Variety.of(variety));
    }
}
