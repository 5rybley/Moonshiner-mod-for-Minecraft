package ru.samogon.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

/**
 * A drink set down in the world, drawn by {@link PlacedDrinkBlockEntity}'s
 * renderer instead of a block model.
 *
 * <p>The block is placed with sneak-right-click on a top face and stores the
 * exact drink stack plus a 16-step rotation that turns the mug handle or the
 * bottle label toward the placing player. Right-clicking it with an empty
 * hand takes the drink back; breaking it drops the same stack.</p>
 */
public final class PlacedDrinkBlock extends Block implements EntityBlock {
    private static final VoxelShape MUG_SHAPE = Shapes.box(0.25, 0.0, 0.25, 0.75, 0.625, 0.75);
    private static final VoxelShape BOTTLE_SHAPE = Shapes.box(0.25, 0.0, 0.25, 0.75, 0.9375, 0.75);

    public PlacedDrinkBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PlacedDrinkBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        // The drink itself is drawn by the block entity renderer.
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return level.getBlockEntity(pos) instanceof PlacedDrinkBlockEntity drink && drink.isBottle()
                ? BOTTLE_SHAPE : MUG_SHAPE;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction == Direction.DOWN && !state.canSurvive(level, pos)) {
            // The ground under the drink is gone: hand the drink back instead of
            // leaving it floating in mid-air. The stack is taken out of the
            // block entity first, so a repeated shape update or the engine's
            // own block removal cannot drop the same drink twice.
            if (level instanceof ServerLevel server
                    && level.getBlockEntity(pos) instanceof PlacedDrinkBlockEntity placed) {
                ItemStack drop = placed.takeDrink();
                if (!drop.isEmpty()) {
                    popResource(server, pos, drop);
                }
            }
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        if (builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY)
                instanceof PlacedDrinkBlockEntity placed && !placed.drink().isEmpty()) {
            return List.of(placed.drink().copy());
        }
        return List.of();
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        // Breaking in creative skips the loot table, so a drink set down by hand
        // would vanish; hand the very same stack back before the block is gone.
        // Taking it out of the block entity first keeps the drop single.
        if (player.isCreative() && level instanceof ServerLevel server
                && level.getBlockEntity(pos) instanceof PlacedDrinkBlockEntity placed) {
            ItemStack drop = placed.takeDrink();
            if (!drop.isEmpty()) {
                popResource(server, pos, drop);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof PlacedDrinkBlockEntity placed) || placed.drink().isEmpty()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            ItemStack taken = placed.takeDrink();
            level.removeBlock(pos, false);
            if (!player.getInventory().add(taken)) {
                player.drop(taken, false);
            }
            level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.5f, 1.2f);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
