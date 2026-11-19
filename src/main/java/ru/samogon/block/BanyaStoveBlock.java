package ru.samogon.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import ru.samogon.registry.ModBlocks;

/**
 * The banya stove: a cast-iron heater crowned with stones.
 *
 * <p>The stove heats the closed room it stands in. The room may be built from
 * absolutely any blocks - stone, planks, glass, doors, even leaves - as long as
 * the air above the stove is completely enclosed. {@link BanyaRoom} measures
 * the volume and {@link BanyaStoveBlockEntity} warms it up; the volume is the
 * heat capacity, so a small washroom heats quickly and a large steam room
 * slowly. Coal goes into the firebox, and the lever in the interface sets both
 * the target temperature and how fast the fire burns.</p>
 *
 * <p>Steam appears by itself once the room is warm, and the heat drives a
 * hangover out of everyone inside. A bucket of water thrown on the hot stones
 * gives an instant burst of steam.</p>
 */
public final class BanyaStoveBlock extends Block implements EntityBlock {
    /** Horizontal direction of the firedoor. */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** Whether the firebox has a live fire, for particles and the model. */
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    private static final VoxelShape SHAPE = Shapes.box(0.0, 0.0, 0.0, 1.0, 0.9375, 1.0);

    public BanyaStoveBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BanyaStoveBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                 BlockEntityType<T> type) {
        return level.isClientSide || type != ModBlocks.BANYA_STOVE_ENTITY.get() ? null
                : (world, pos, blockState, entity) -> ((BanyaStoveBlockEntity) entity).tick();
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())
                && !level.isClientSide
                && level.getBlockEntity(pos) instanceof BanyaStoveBlockEntity stove) {
            stove.dropContents();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof Player player) {
            // Tell the builder straight away whether the box is actually closed.
            BanyaRoom room = BanyaRoom.scan(level, pos, BanyaRoom.DEFAULT_LIMIT);
            player.displayClientMessage(Component.translatable(room.valid()
                    ? "message.samogon.banya.room_ok" : "message.samogon.banya.room_open",
                    room.volume()), true);
        }
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(Items.WATER_BUCKET)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!(level.getBlockEntity(pos) instanceof BanyaStoveBlockEntity stove)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!(level instanceof ServerLevel server)) {
            return ItemInteractionResult.sidedSuccess(true);
        }
        stove.rescanNow();
        if (!stove.room().valid()) {
            player.displayClientMessage(Component.translatable("message.samogon.banya.room_open"), true);
            return ItemInteractionResult.sidedSuccess(false);
        }
        if (stove.temperature() < stove.steamThreshold()) {
            player.displayClientMessage(Component.translatable("message.samogon.banya.cold"), true);
            return ItemInteractionResult.sidedSuccess(false);
        }
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
            ItemStack bucket = new ItemStack(Items.BUCKET);
            if (!player.getInventory().add(bucket)) {
                player.drop(bucket, false);
            }
        }
        stove.splash(server);
        player.displayClientMessage(Component.translatable("message.samogon.banya.steam"), true);
        return ItemInteractionResult.sidedSuccess(false);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof BanyaStoveBlockEntity stove) {
            stove.openMenu(player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) {
            return;
        }
        Direction facing = state.getValue(FACING);
        double x = pos.getX() + 0.5 + facing.getStepX() * 0.46;
        double y = pos.getY() + 0.42;
        double z = pos.getZ() + 0.5 + facing.getStepZ() * 0.46;

        if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.SMALL_FLAME, x + spread(random, 0.14), y - 0.08,
                    z + spread(random, 0.14), 0.0, 0.012, 0.0);
        }
        if (random.nextInt(9) == 0) {
            level.addParticle(ParticleTypes.SMOKE, x + spread(random, 0.16), y + 0.28,
                    z + spread(random, 0.16), 0.0, 0.02, 0.0);
        }
        if (random.nextInt(120) == 0) {
            level.playLocalSound(x, y, z, SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS,
                    0.28f, 0.85f + random.nextFloat() * 0.2f, false);
        }
    }

    private static double spread(RandomSource random, double radius) {
        return (random.nextDouble() - 0.5) * 2 * radius;
    }
}