package ru.samogon.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import ru.samogon.registry.ModBlocks;

/**
 * The riddling rack: a 1 wide x 2 tall x 2 deep shelf, as tall as a standing
 * player, in which a still wine goes through tirage, the second fermentation,
 * lees ageing, riddling, disgorging and the dosage. Placing one block builds
 * the three remaining parts, so the whole rack appears in one action.
 *
 * <p>The block the player placed is {@link #PART} 0 and owns the inventory and
 * the clock; the other three parts only mirror the state for rendering.
 * {@code PART = dy * 2 + dz}, matching the generated models.</p>
 *
 * <p>The footprint is not square, so the drawing is rotated in 90-degree steps
 * for the four facings. {@link #FACING} points along the rack's two-block depth
 * (away from the player who placed it), and the four part models are turned by
 * the matching blockstate y rotation. Every part carries the shared
 * {@link ProcessBlock} flags, so one drawing per cell and a facing/part
 * multipart blockstate are enough.</p>
 *
 * <p>Breaking any part dismantles the whole structure, drops the contents once
 * and returns exactly one rack item through the loot table of the broken
 * part.</p>
 */
public final class BottleRackBlock extends Block implements EntityBlock, ProcessMachine {
    /** Which of the four 16-cube cells this block shows, index dy * 2 + dz. */
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 3);
    /** The direction the rack's two-block depth runs, away from the placer. */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** How many blocks make up a full rack. */
    public static final int PARTS = 4;

    private static final VoxelShape SHAPE = Shapes.block();
    /** Guards the recursive removals the dismantle path performs itself. */
    private static boolean dismantling = false;

    private final ProcessBlock.Kind kind;

    public BottleRackBlock(Properties properties, ProcessBlock.Kind kind) {
        super(properties);
        this.kind = kind;
        registerDefaultState(stateDefinition.any()
                .setValue(PART, 0)
                .setValue(FACING, Direction.NORTH)
                .setValue(ProcessBlock.HEAT, 0)
                .setValue(ProcessBlock.ACTIVE, false)
                .setValue(ProcessBlock.PROGRESS, 0)
                .setValue(ProcessBlock.READY, false));
    }

    @Override
    public ProcessBlock.Kind kind() {
        return kind;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        // The shared machine flags are the very same properties ProcessBlock
        // uses, so ProcessBlockEntity can drive this block's state unchanged.
        builder.add(PART, FACING, ProcessBlock.HEAT, ProcessBlock.ACTIVE, ProcessBlock.PROGRESS, ProcessBlock.READY);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // The depth runs away from the player, so the open shelves face them.
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    // === STRUCTURE ===

    /** Part index of the block {@code dy} above and {@code dz} along the depth. */
    public static int part(int dy, int dz) {
        return dy * 2 + dz;
    }

    /** The anchor (master) implied by a part's index and its facing. */
    public static BlockPos anchorOf(BlockPos pos, BlockState state) {
        int index = state.getValue(PART);
        return pos.offset(0, -(index >> 1), 0).relative(state.getValue(FACING), -(index & 1));
    }

    /**
     * The master of the rack containing {@code pos}, or null when it is not
     * formed. A lone master - a shelf placed before this shape existed, or one
     * whose neighbours were destroyed by something else - still works as a
     * one-block machine instead of going dead.
     */
    public static BlockPos findAnchor(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof BottleRackBlock)) return null;
        BlockPos anchor = anchorOf(pos, state);
        if (isComplete(level, anchor)) return anchor;
        return anchor.equals(pos) && level.getBlockEntity(pos) instanceof ProcessBlockEntity ? pos : null;
    }

    public static boolean isComplete(BlockGetter level, BlockPos anchor) {
        BlockState state = level.getBlockState(anchor);
        if (!(state.getBlock() instanceof BottleRackBlock) || state.getValue(PART) != 0) return false;
        Direction facing = state.getValue(FACING);
        for (int dy = 0; dy < 2; dy++) {
            for (int dz = 0; dz < 2; dz++) {
                BlockState part = level.getBlockState(anchor.offset(0, dy, 0).relative(facing, dz));
                if (!(part.getBlock() instanceof BottleRackBlock) || part.getValue(FACING) != facing) {
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
        // Only the block the player placed is the master and builds the shelf.
        if (state.getValue(PART) != 0) return;

        Direction facing = state.getValue(FACING);
        if (!hasRoom(level, pos, facing)) {
            boolean creative = false;
            if (placer instanceof Player player) {
                creative = player.getAbilities().instabuild;
                player.displayClientMessage(Component.translatable("message.samogon.rack.no_room"), true);
            }
            // The master is removed without running its loot table, so a survival
            // player would silently lose the item they just placed. Give it back
            // (creative keeps its own copy) before dismantling the lone block.
            if (!creative) {
                Block.popResource(level, pos, stack.copyWithCount(1));
            }
            // hasRoom failed because some footprint cell is blocked, and that
            // block can belong to a neighbouring rack. A plain removeBlock runs
            // onRemove, whose dismantle pass sweeps the whole 2x2 footprint and
            // would delete that neighbour (with its contents) for nothing. The
            // guard makes only this lone master disappear.
            dismantling = true;
            try {
                level.removeBlock(pos, false);
            } finally {
                dismantling = false;
            }
            return;
        }
        assemble(level, pos, facing);
    }

    private static boolean hasRoom(Level level, BlockPos anchor, Direction facing) {
        for (int dy = 0; dy < 2; dy++) {
            for (int dz = 0; dz < 2; dz++) {
                BlockPos partPos = anchor.offset(0, dy, 0).relative(facing, dz);
                if (partPos.equals(anchor)) continue;
                if (!level.getBlockState(partPos).canBeReplaced()) return false;
            }
        }
        return true;
    }

    private static void assemble(Level level, BlockPos anchor, Direction facing) {
        for (int dy = 0; dy < 2; dy++) {
            for (int dz = 0; dz < 2; dz++) {
                level.setBlock(anchor.offset(0, dy, 0).relative(facing, dz), partState(dy, dz, facing), Block.UPDATE_ALL);
            }
        }
    }

    private static BlockState partState(int dy, int dz, Direction facing) {
        return ModBlocks.BOTTLE_RACK.get().defaultBlockState()
                .setValue(PART, part(dy, dz))
                .setValue(FACING, facing);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        // Property changes keep the same block; only a real removal dismantles.
        if (!state.is(newState.getBlock()) && !level.isClientSide && state.getBlock() instanceof BottleRackBlock) {
            if (!dismantling) {
                dismantling = true;
                try {
                    BlockPos anchor = anchorOf(pos, state);
                    Direction facing = state.getValue(FACING);
                    // The master may be this very block or one of the siblings.
                    if (level.getBlockEntity(anchor) instanceof ProcessBlockEntity machine) {
                        machine.dropContents();
                    }
                    for (int dy = 0; dy < 2; dy++) {
                        for (int dz = 0; dz < 2; dz++) {
                            BlockPos other = anchor.offset(0, dy, 0).relative(facing, dz);
                            if (other.equals(pos)) continue;
                            if (level.getBlockState(other).getBlock() instanceof BottleRackBlock) {
                                // getDrops is bypassed here, so only the part
                                // the player broke yields the single rack item.
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
        return state.getValue(PART) == 0 ? new ProcessBlockEntity(pos, state) : null;
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide || state.getValue(PART) != 0 || type != ModBlocks.PROCESS_BLOCK_ENTITY.get()) {
            return null;
        }
        return (world, pos, blockState, entity) -> {
            if (entity instanceof ProcessBlockEntity machine) machine.tick();
        };
    }

    // === INTERACTION ===

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        return use(state, level, pos, player, hand, stack) ? ItemInteractionResult.sidedSuccess(level.isClientSide)
                : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        return use(state, level, pos, player, InteractionHand.MAIN_HAND, ItemStack.EMPTY)
                ? InteractionResult.sidedSuccess(level.isClientSide) : InteractionResult.PASS;
    }

    /**
     * Every part forwards to the master: a held item is served into the rack,
     * an empty hand opens the shelf's panel and Shift+right-click keeps the old
     * quick service shortcut.
     */
    private static boolean use(BlockState state, Level level, BlockPos pos, Player player,
                               InteractionHand hand, ItemStack stack) {
        BlockPos master = findAnchor(level, pos);
        if (master == null) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.samogon.rack.unformed"), true);
            }
            return true;
        }
        if (level.getBlockEntity(master) instanceof ProcessBlockEntity machine) {
            if (stack.isEmpty()) {
                if (player.isShiftKeyDown()) {
                    machine.interact(player, hand, ItemStack.EMPTY);
                } else {
                    machine.openMachine(player);
                }
            } else {
                machine.interact(player, hand, stack);
            }
        }
        return true;
    }
}
