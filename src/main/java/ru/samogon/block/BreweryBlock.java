package ru.samogon.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import ru.samogon.registry.ModBlocks;

/**
 * The brewery: a 2x2x1 multiblock brewhouse. Placing one block builds the three
 * remaining parts, so the whole vessel appears in one action.
 *
 * <p>The block the player placed is {@link #PART} 0 and owns the inventory and
 * the clock. The other three parts sit at +x / +z and only mirror the state for
 * rendering. {@code PART = dx * 2 + dz}, matching the generated models. The
 * horizontal {@link #FACING} is baked into each part's model (four rotated
 * drawings), so the footprint never changes and the vessel simply turns in
 * place.</p>
 *
 * <p>Breaking any part dismantles the whole structure, drops the contents once
 * and returns exactly one brewery item through the loot table of the broken
 * part.</p>
 */
public final class BreweryBlock extends Block implements EntityBlock {
    /** Which of the four 16x16 footprints this block shows, index dx * 2 + dz. */
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 3);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    /** Coarse progress band used by the client for particles. */
    public static final IntegerProperty PROGRESS = IntegerProperty.create("progress", 0, 4);
    /** True when a finished brew waits to be bottled. */
    public static final BooleanProperty READY = BooleanProperty.create("ready");

    private static final VoxelShape SHAPE = Shapes.block();
    /** Guards the recursive removals the dismantle path performs itself. */
    private static boolean dismantling = false;

    public BreweryBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(PART, 0)
                .setValue(FACING, Direction.NORTH)
                .setValue(ACTIVE, false)
                .setValue(PROGRESS, 0)
                .setValue(READY, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART, FACING, ACTIVE, PROGRESS, READY);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    // === STRUCTURE ===

    /** Part index of the block {@code dx} east and {@code dz} south of the anchor. */
    public static int part(int dx, int dz) {
        return dx * 2 + dz;
    }

    /** The anchor (master) implied by a part's own index. */
    public static BlockPos anchorOf(BlockPos pos, BlockState state) {
        int index = state.getValue(PART);
        return pos.offset(-(index >> 1), 0, -(index & 1));
    }

    /** The master of the brewery containing {@code pos}, or null when it is not formed. */
    public static BlockPos findAnchor(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof BreweryBlock)) return null;
        BlockPos anchor = anchorOf(pos, state);
        return isComplete(level, anchor) ? anchor : null;
    }

    public static boolean isComplete(BlockGetter level, BlockPos anchor) {
        BlockState state = level.getBlockState(anchor);
        if (!(state.getBlock() instanceof BreweryBlock) || state.getValue(PART) != 0) return false;
        for (int dx = 0; dx < 2; dx++) {
            for (int dz = 0; dz < 2; dz++) {
                if (!(level.getBlockState(anchor.offset(dx, 0, dz)).getBlock() instanceof BreweryBlock)) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide) return;
        // Only the block the player placed is the master and builds the vessel.
        if (state.getValue(PART) != 0) return;

        if (!hasRoom(level, pos)) {
            boolean creative = false;
            if (placer instanceof Player player) {
                creative = player.getAbilities().instabuild;
                player.displayClientMessage(Component.translatable("message.samogon.brewery.no_room"), true);
            }
            // The master is removed without running its loot table, so a survival
            // player would silently lose the item they just placed. Give it back
            // (creative keeps its own copy) before dismantling the lone block.
            if (!creative) {
                Block.popResource(level, pos, stack.copyWithCount(1));
            }
            level.removeBlock(pos, false);
            return;
        }
        assemble(level, pos, state.getValue(FACING));
    }

    private static boolean hasRoom(Level level, BlockPos anchor) {
        for (int dx = 0; dx < 2; dx++) {
            for (int dz = 0; dz < 2; dz++) {
                BlockPos partPos = anchor.offset(dx, 0, dz);
                if (partPos.equals(anchor)) continue;
                if (!level.getBlockState(partPos).canBeReplaced()) return false;
            }
        }
        return true;
    }

    private static void assemble(Level level, BlockPos anchor, Direction facing) {
        for (int dx = 0; dx < 2; dx++) {
            for (int dz = 0; dz < 2; dz++) {
                BlockState partState = defaultState(level, anchor, dx, dz, facing);
                level.setBlock(anchor.offset(dx, 0, dz), partState, Block.UPDATE_ALL);
            }
        }
    }

    private static BlockState defaultState(Level level, BlockPos anchor, int dx, int dz, Direction facing) {
        return ModBlocks.BREWERY.get().defaultBlockState()
                .setValue(PART, part(dx, dz))
                .setValue(FACING, facing);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        // Property changes keep the same block; only a real removal dismantles.
        if (!state.is(newState.getBlock()) && !level.isClientSide && state.getBlock() instanceof BreweryBlock) {
            if (!dismantling) {
                dismantling = true;
                try {
                    BlockPos anchor = anchorOf(pos, state);
                    // The master may be this very block or one of the siblings.
                    if (level.getBlockEntity(anchor) instanceof BreweryBlockEntity brewery) {
                        brewery.dropContents();
                    }
                    for (int dx = 0; dx < 2; dx++) {
                        for (int dz = 0; dz < 2; dz++) {
                            BlockPos other = anchor.offset(dx, 0, dz);
                            if (other.equals(pos)) continue;
                            if (level.getBlockState(other).getBlock() instanceof BreweryBlock) {
                                // getDrops is bypassed here, so only the part
                                // the player broke yields the single brewery item.
                                level.removeBlock(other, false);
                            }
                        }
                    }
                } finally {
                    dismantling = false;
                }
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    // === BLOCK ENTITY ===

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        // Every part is a shell except the master, which owns the inventory.
        return state.getValue(PART) == 0 ? new BreweryBlockEntity(pos, state) : null;
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide || state.getValue(PART) != 0 || type != ModBlocks.BREWERY_ENTITY.get()) {
            return null;
        }
        return (world, pos, blockState, entity) -> {
            if (entity instanceof BreweryBlockEntity brewery) brewery.tick();
        };
    }

    // === INTERACTION ===

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        return open(level, pos, player) ? ItemInteractionResult.sidedSuccess(level.isClientSide)
                : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        return open(level, pos, player) ? InteractionResult.sidedSuccess(level.isClientSide)
                : InteractionResult.PASS;
    }

    private static boolean open(Level level, BlockPos pos, Player player) {
        BlockPos master = findAnchor(level, pos);
        if (master == null) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.samogon.brewery.unformed"), true);
            }
            return true;
        }
        if (!level.isClientSide && level.getBlockEntity(master) instanceof BreweryBlockEntity brewery) {
            brewery.openMenu(player);
        }
        return true;
    }

    // === AMBIENCE ===

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        boolean active = state.getValue(ACTIVE);
        boolean ready = state.getValue(READY);
        if (!active && !ready) return;
        double x = pos.getX() + 0.5;
        double z = pos.getZ() + 0.5;
        if (active) {
            if (random.nextInt(3) == 0) {
                level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                        x + spread(random, 0.7), pos.getY() + 1.02, z + spread(random, 0.7),
                        0, 0.02, 0);
            }
            if (random.nextInt(18) == 0) {
                level.playLocalSound(x, pos.getY() + 0.6, z, SoundEvents.BREWING_STAND_BREW,
                        SoundSource.BLOCKS, 0.18f, 1.15f, false);
            }
        }
        if (ready && random.nextInt(6) == 0) {
            level.addParticle(ParticleTypes.END_ROD,
                    x + spread(random, 0.6), pos.getY() + 1.05, z + spread(random, 0.6),
                    0, 0.015, 0);
        }
    }

    private static double spread(RandomSource random, double radius) {
        return (random.nextDouble() - 0.5) * 2 * radius;
    }
}