package ru.samogon.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;
import java.util.Locale;

/**
 * A copper condenser: the water reservoir that keeps a still cool.
 *
 * <p>Right-click with a water bucket to pour it in, right-click empty-handed to
 * read the level. Any still placed next to a condenser pulls its coolant from
 * it automatically. A comparator reads the water level, and breaking the block
 * keeps the remaining water on the dropped item.</p>
 */
public final class CondenserBlock extends Block implements EntityBlock {
    private static final VoxelShape SHAPE = Shapes.box(0.0, 0.0, 0.0, 1.0, 0.875, 1.0);
    /** Horizontal direction the tap and gauge of the reservoir point at. */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    public CondenserBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CondenserBlockEntity(pos, state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        ItemStack drop = new ItemStack(this.asItem());
        if (builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof CondenserBlockEntity condenser
                && condenser.water() > 0) {
            CompoundTag tag = condenser.saveWithoutMetadata(builder.getLevel().registryAccess());
            drop.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(tag));
        }
        return List.of(drop);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof CondenserBlockEntity condenser)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!stack.is(Items.WATER_BUCKET)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            if (condenser.isFull()) {
                player.displayClientMessage(Component.translatable("message.samogon.condenser.full"), true);
            } else {
                condenser.addWater(ProcessBlockEntity.COOLANT_PER_BUCKET);
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                    ItemStack bucket = new ItemStack(Items.BUCKET);
                    if (!player.getInventory().add(bucket)) player.drop(bucket, false);
                }
                level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 0.7f, 1.0f);
                player.displayClientMessage(Component.translatable("message.samogon.condenser.added",
                        buckets(condenser.water())), false);
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof CondenserBlockEntity condenser)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            player.displayClientMessage(Component.translatable("message.samogon.condenser.status",
                    buckets(condenser.water())), false);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof CondenserBlockEntity condenser
                ? condenser.comparatorOutput() : 0;
    }

    private static String buckets(int water) {
        return String.format(Locale.ROOT, "%.1f", water / (double) ProcessBlockEntity.COOLANT_PER_BUCKET);
    }
}
