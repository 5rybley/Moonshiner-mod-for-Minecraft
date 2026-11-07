package ru.samogon.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
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
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import ru.samogon.item.BatchData;
import ru.samogon.process.Simulation.Batch;
import ru.samogon.registry.ModBlocks;
import ru.samogon.registry.ModItems;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * The large barrel that eight {@link ProcessBlock aging casks} merge into when
 * placed as a 2x2x2 cube. It lies on its side, matures up to nine bottles with
 * oak chips and opens a container interface.
 *
 * <p>The round section stands two blocks tall and two blocks deep, and the
 * barrel is two blocks long along the {@link #FACING} axis chosen when the cube
 * was built. The block at the lowest corner (min x, min y, min z) is the master
 * and owns the inventory; every other part forwards interactions to it. Each
 * block renders one of the eight 16-cube octants of a single large drawing, so
 * the eight blocks read as one vessel in the world. Breaking any part
 * dismantles the whole barrel, returns the eight casks it was built from and
 * drops the bottles and chips it was holding.</p>
 */
public final class AgingBarrelBlock extends Block implements EntityBlock {
    /** Which of the eight 16-cube octants this block shows, index dx*4+dy*2+dz. */
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 7);
    /** Horizontal direction the cask's axis and its tapped lid point at. */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** True on the upper layer when another cask stands on top and needs support pads. */
    public static final BooleanProperty STACKED = BooleanProperty.create("stacked");
    /** How many sections a dismantled cask returns. */
    public static final int SECTIONS = 8;

    private static final VoxelShape SHAPE = Shapes.block();
    /** Guards {@link #form} against the recursive onPlace calls it causes itself. */
    private static boolean forming = false;

    public AgingBarrelBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(PART, 0)
                .setValue(FACING, Direction.NORTH).setValue(STACKED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART, FACING, STACKED);
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
        return new AgingBarrelBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return (lvl, pos, st, be) -> {
            if (be instanceof AgingBarrelBlockEntity cask) cask.tick();
            updateStacked(lvl, pos);
        };
    }

    /**
     * Keeps the support pads of a top octant in step with the cask above it.
     * Running every tick makes the flag self-healing: it survives old worlds,
     * stacking, unstacking and half-broken clusters without extra bookkeeping.
     */
    private static void updateStacked(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof AgingBarrelBlock)) return;
        // Only the upper layer (octants 2, 3, 6 and 7) can carry the pads.
        if ((state.getValue(PART) & 2) == 0) return;
        boolean stacked = level.getBlockState(pos.above()).getBlock() instanceof AgingBarrelBlock;
        if (state.getValue(STACKED) != stacked) {
            level.setBlock(pos, state.setValue(STACKED, stacked), Block.UPDATE_ALL);
        }
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide) form(level, pos);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, net.minecraft.world.entity.LivingEntity placer,
                            ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        // Kept in step with onPlace for any future placement path; a formed
        // barrel is normally created by AgingBarrelBlock.tryAssemble instead.
        if (!level.isClientSide) form(level, pos);
    }

    // === STRUCTURE ===

    /** Octant index of the block {@code (dx, dy, dz)} blocks above the anchor. */
    public static int part(int dx, int dy, int dz) {
        return (dx << 2) | (dy << 1) | dz;
    }

    /**
     * The lowest corner of the barrel this block belongs to. Every part stores
     * its own octant in {@link #PART}, so the anchor can be read straight off
     * the block. Barrels may stand side by side or stacked, and two
     * neighbouring cubes overlap, so searching for "a" complete cube would
     * always return the lowest one and mix the two barrels up; the stored
     * octant is the only unambiguous answer.
     */
    public static BlockPos findAnchor(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof AgingBarrelBlock) {
            BlockPos anchor = anchorOf(pos, state);
            if (isComplete(level, anchor)) return anchor;
        }
        // A stray or half-broken part has no trustworthy octant: fall back to
        // the lowest complete cube around it so broken clusters still resolve.
        BlockPos best = null;
        for (int dx = -1; dx <= 0; dx++) {
            for (int dy = -1; dy <= 0; dy++) {
                for (int dz = -1; dz <= 0; dz++) {
                    BlockPos anchor = pos.offset(dx, dy, dz);
                    if (!isComplete(level, anchor)) continue;
                    if (best == null || compare(anchor, best) < 0) best = anchor;
                }
            }
        }
        return best;
    }

    /** Lowest corner implied by a part's own octant index. */
    public static BlockPos anchorOf(BlockPos pos, BlockState state) {
        int octant = state.getValue(PART);
        return pos.offset(-((octant >> 2) & 1), -((octant >> 1) & 1), -(octant & 1));
    }

    private static boolean isComplete(BlockGetter level, BlockPos anchor) {
        for (int dx = 0; dx < 2; dx++) {
            for (int dy = 0; dy < 2; dy++) {
                for (int dz = 0; dz < 2; dz++) {
                    if (!(level.getBlockState(anchor.offset(dx, dy, dz)).getBlock() instanceof AgingBarrelBlock)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static int compare(BlockPos a, BlockPos b) {
        int x = Integer.compare(a.getX(), b.getX());
        if (x != 0) return x;
        int y = Integer.compare(a.getY(), b.getY());
        return y != 0 ? y : Integer.compare(a.getZ(), b.getZ());
    }

    /**
     * Repairs the single 2x2x2 barrel a freshly placed part belongs to. Only
     * external placement (/setblock, a structure) reaches this; the normal
     * player path goes through {@link #tryAssemble}, and the assembly and
     * normalize routines suppress this one while they run.
     */
    static void form(Level level, BlockPos pos) {
        if (forming) return;
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof AgingBarrelBlock)) return;
        forming = true;
        try {
            BlockPos anchor = anchorOf(pos, state);
            if (isComplete(level, anchor)) assign(level, anchor, state.getValue(FACING));
        } finally {
            forming = false;
        }
    }

    /**
     * Re-tiles a connected cluster of parts into clean 2x2x2 barrels, lowest
     * corner first, and rewrites every octant and facing to match. Because a
     * barrel's master can no longer be found by searching for "a" cube, this is
     * what keeps stacked and side-by-side barrels from bleeding into each
     * other: it is idempotent, so a correct cluster is left untouched, while a
     * world saved before the octants were written correctly heals the first
     * time any of its parts is used.
     */
    static void normalize(Level level, BlockPos pos) {
        if (forming) return;
        if (!(level.getBlockState(pos).getBlock() instanceof AgingBarrelBlock)) return;
        forming = true;
        try {
            Set<BlockPos> cluster = new HashSet<>();
            Deque<BlockPos> queue = new ArrayDeque<>();
            queue.add(pos.immutable());
            while (!queue.isEmpty()) {
                BlockPos here = queue.poll();
                if (!cluster.add(here)) continue;
                for (Direction side : Direction.values()) {
                    BlockPos next = here.relative(side);
                    if (!cluster.contains(next)
                            && level.getBlockState(next).getBlock() instanceof AgingBarrelBlock) {
                        queue.add(next.immutable());
                    }
                }
            }
            TreeSet<BlockPos> remaining = new TreeSet<>(AgingBarrelBlock::compare);
            remaining.addAll(cluster);
            while (!remaining.isEmpty()) {
                BlockPos anchor = remaining.first();
                boolean complete = true;
                for (int dx = 0; dx < 2 && complete; dx++) {
                    for (int dy = 0; dy < 2 && complete; dy++) {
                        for (int dz = 0; dz < 2; dz++) {
                            if (!remaining.contains(anchor.offset(dx, dy, dz))) {
                                complete = false;
                                break;
                            }
                        }
                    }
                }
                if (!complete) {
                    // An irregular leftover cannot become a barrel; leave it be.
                    remaining.remove(anchor);
                    continue;
                }
                for (int dx = 0; dx < 2; dx++) {
                    for (int dy = 0; dy < 2; dy++) {
                        for (int dz = 0; dz < 2; dz++) {
                            remaining.remove(anchor.offset(dx, dy, dz));
                        }
                    }
                }
                BlockState atAnchor = level.getBlockState(anchor);
                Direction facing = atAnchor.getBlock() instanceof AgingBarrelBlock
                        ? atAnchor.getValue(FACING) : Direction.NORTH;
                assign(level, anchor, facing);
            }
        } finally {
            forming = false;
        }
    }

    /** Writes every octant and facing of one 2x2x2 barrel and funnels its shells into the master. */
    private static void assign(Level level, BlockPos anchor, Direction facing) {
        if (!(level.getBlockEntity(anchor) instanceof AgingBarrelBlockEntity master)) return;
        for (int dx = 0; dx < 2; dx++) {
            for (int dy = 0; dy < 2; dy++) {
                for (int dz = 0; dz < 2; dz++) {
                    BlockPos part = anchor.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(part);
                    if (!(state.getBlock() instanceof AgingBarrelBlock)) continue;
                    int wanted = part(dx, dy, dz);
                    BlockState updated = state;
                    if (updated.getValue(FACING) != facing) updated = updated.setValue(FACING, facing);
                    if (updated.getValue(PART) != wanted) updated = updated.setValue(PART, wanted);
                    if (updated != state) {
                        level.setBlock(part, updated, Block.UPDATE_ALL);
                        if (!part.equals(anchor)
                                && level.getBlockEntity(part) instanceof AgingBarrelBlockEntity shell) {
                            master.mergeFrom(shell);
                        }
                    }
                }
            }
        }
    }

    // === ASSEMBLY ===

    /**
     * Builds a merged barrel once eight aging casks of a 2x2x2 cube are
     * present. Called by {@link ProcessBlock} each time another cask is placed,
     * so the block that completes the cube is what turns the cluster into one
     * barrel. The eight casks are replaced in place, carrying over the facing
     * the player was looking at; a freshly built barrel starts empty.
     */
    static void tryAssemble(Level level, BlockPos pos, Direction facing) {
        BlockPos anchor = findCaskAnchor(level, pos);
        if (anchor == null) return;
        Direction axis = facing == null || facing.getAxis() == Direction.Axis.Y
                ? Direction.NORTH : facing;
        Block barrelBlock = ModBlocks.AGING_BARREL.get();
        // Suppress onPlace -> form while the eight blocks go in one by one.
        // Without this, a part of the already-finished neighbour below is seen
        // as a fresh complete cube and gets its octants rewritten mid-assembly,
        // which is what used to scramble two stacked barrels.
        if (forming) return;
        forming = true;
        try {
            for (int dx = 0; dx < 2; dx++) {
                for (int dy = 0; dy < 2; dy++) {
                    for (int dz = 0; dz < 2; dz++) {
                        BlockState barrel = barrelBlock.defaultBlockState()
                                .setValue(PART, part(dx, dy, dz))
                                .setValue(FACING, axis);
                        level.setBlock(anchor.offset(dx, dy, dz), barrel, Block.UPDATE_ALL);
                    }
                }
            }
        } finally {
            forming = false;
        }
    }

    /** Lowest corner of a complete 2x2x2 cube of aging casks that contains {@code pos}. */
    private static BlockPos findCaskAnchor(BlockGetter level, BlockPos pos) {
        BlockPos best = null;
        for (int dx = -1; dx <= 0; dx++) {
            for (int dy = -1; dy <= 0; dy++) {
                for (int dz = -1; dz <= 0; dz++) {
                    BlockPos anchor = pos.offset(dx, dy, dz);
                    if (!isCaskCube(level, anchor)) continue;
                    if (best == null || compare(anchor, best) < 0) best = anchor;
                }
            }
        }
        return best;
    }

    private static boolean isCaskCube(BlockGetter level, BlockPos anchor) {
        for (int dx = 0; dx < 2; dx++) {
            for (int dy = 0; dy < 2; dy++) {
                for (int dz = 0; dz < 2; dz++) {
                    if (!(level.getBlockState(anchor.offset(dx, dy, dz)).getBlock() instanceof ProcessBlock cask)
                            || cask.kind() != ProcessBlock.Kind.AGING) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * Removes the whole cask when any part is broken. Returns true when a
     * complete barrel was found and dismantled.
     */
    static boolean breakStructure(Level level, BlockPos pos) {
        normalize(level, pos);
        BlockPos anchor = findAnchor(level, pos);
        if (anchor == null) return false;
        if (!(level.getBlockEntity(anchor) instanceof AgingBarrelBlockEntity master)) return false;
        for (int dx = 0; dx < 2; dx++) {
            for (int dy = 0; dy < 2; dy++) {
                for (int dz = 0; dz < 2; dz++) {
                    BlockPos part = anchor.offset(dx, dy, dz);
                    if (part.equals(pos)) continue;
                    if (level.getBlockState(part).getBlock() instanceof AgingBarrelBlock) {
                        // Only the part the player actually broke runs getDrops,
                        // so when the master is one of the other parts its bottles
                        // and chips must be released here or they would vanish
                        // with removeBlock.
                        if (part.equals(anchor)) dropContents(level, master);
                        level.removeBlock(part, false);
                    }
                }
            }
        }
        return true;
    }

    private static void dropContents(Level level, AgingBarrelBlockEntity barrel) {
        BlockPos origin = barrel.getBlockPos();
        for (int slot = 0; slot < AgingBarrelBlockEntity.BOTTLE_SLOTS; slot++) {
            ItemStack bottle = barrel.getItem(slot);
            if (!bottle.isEmpty()) Block.popResource(level, origin, bottle.copy());
        }
        ItemStack chips = barrel.getItem(AgingBarrelBlockEntity.CHIP_SLOT);
        if (!chips.isEmpty()) Block.popResource(level, origin, chips.copy());
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide) {
            boolean dismantled = breakStructure(level, pos);
            if (dismantled && !player.getAbilities().instabuild) {
                // getDrops yields one cask for the broken part; the other seven
                // are handed over here so a full barrel always returns exactly
                // eight. Dropping all eight in getDrops would multiply the whole
                // barrel whenever a part is broken by anything but a player.
                for (int i = 0; i < SECTIONS - 1; i++) {
                    Block.popResource(level, pos, new ItemStack(ModBlocks.AGING_CASK.get()));
                }
            }
            // A creative player never triggers getDrops: if they broke the master
            // themselves, release its contents here instead. Re-read the state,
            // because breakStructure may have re-tiled the parts first.
            BlockState current = level.getBlockState(pos);
            if (dismantled && player.getAbilities().instabuild && current.getValue(PART) == 0
                    && level.getBlockEntity(pos) instanceof AgingBarrelBlockEntity master) {
                dropContents(level, master);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    // === DROPS ===

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        // Exactly one cask per part. A player dismantling a complete barrel gets
        // the other seven parts handed to them in playerWillDestroy, so the total
        // is always eight; this also keeps an explosion, a piston or /setblock
        // that breaks isolated parts from multiplying the structure.
        List<ItemStack> drops = new ArrayList<>(SECTIONS);
        drops.add(new ItemStack(ModBlocks.AGING_CASK.get()));
        // The lowest corner (octant 0) owns the inventory. Add its bottles and
        // chips so a non-player break cannot swallow them either.
        if (state.getValue(PART) == 0
                && builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof AgingBarrelBlockEntity barrel) {
            for (int slot = 0; slot < AgingBarrelBlockEntity.BOTTLE_SLOTS; slot++) {
                ItemStack bottle = barrel.getItem(slot);
                if (!bottle.isEmpty()) drops.add(bottle.copy());
            }
            ItemStack chips = barrel.getItem(AgingBarrelBlockEntity.CHIP_SLOT);
            if (!chips.isEmpty()) drops.add(chips.copy());
        }
        return drops;
    }

    // === INTERACTION ===

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide) normalize(level, pos);
        BlockPos master = findAnchor(level, pos);
        if (master == null) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.samogon.barrel.unformed"), true);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!(level.getBlockEntity(master) instanceof AgingBarrelBlockEntity cask)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (stack.is(ModItems.HYDROMETER.get())) {
            if (!level.isClientSide) reportBottles(player, cask);
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }

        if (player.isSecondaryUseActive()) {
            if (!level.isClientSide) takeOne(level, pos, player, cask);
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }

        if (stack.is(ModItems.OAK_CHIPS.get())) {
            if (!level.isClientSide) {
                int added = cask.insertChips(stack);
                if (added > 0) {
                    if (!player.getAbilities().instabuild) stack.shrink(added);
                    level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.3f, 1.3f);
                    player.displayClientMessage(Component.translatable(
                            "message.samogon.barrel.chips.added", cask.chipCount()), true);
                } else {
                    player.displayClientMessage(Component.translatable(
                            "message.samogon.barrel.chips.full"), true);
                }
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }

        if (AgingBarrelBlockEntity.isBottle(stack)) {
            if (!level.isClientSide) {
                Batch batch = BatchData.read(stack);
                if (batch.abv() < 20.0 || batch.abv() > 60.0) {
                    player.displayClientMessage(Component.translatable("message.samogon.strength_range"), true);
                } else if (stack.is(ModItems.OAK_AGED_SPIRIT.get())) {
                    player.displayClientMessage(Component.translatable(
                            "message.samogon.barrel.already_aged"), true);
                } else if (cask.tryInsertBottle(stack)) {
                    if (!player.getAbilities().instabuild) stack.shrink(1);
                    level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 0.35f, 1.2f);
                    player.displayClientMessage(Component.translatable("message.samogon.barrel.added",
                            cask.bottleCount(), AgingBarrelBlockEntity.BOTTLE_SLOTS), true);
                } else {
                    player.displayClientMessage(Component.translatable(
                            "message.samogon.barrel.full"), true);
                }
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }

        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide) normalize(level, pos);
        BlockPos master = findAnchor(level, pos);
        if (master == null) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.samogon.barrel.unformed"), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!(level.getBlockEntity(master) instanceof AgingBarrelBlockEntity cask)) {
            return InteractionResult.PASS;
        }
        // Empty hand opens the cask interface; Shift-right-click keeps the old
        // quick "take one bottle / chip" shortcut.
        if (player.isSecondaryUseActive()) {
            if (!level.isClientSide) takeOne(level, pos, player, cask);
        } else {
            cask.openMenu(player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static void takeOne(Level level, BlockPos pos, Player player, AgingBarrelBlockEntity cask) {
        ItemStack taken = cask.extractBottle();
        if (!taken.isEmpty()) {
            giveOrDrop(player, taken);
            level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.35f, 1.1f);
            player.displayClientMessage(Component.translatable("message.samogon.barrel.taken",
                    cask.bottleCount(), AgingBarrelBlockEntity.BOTTLE_SLOTS), true);
            return;
        }
        taken = cask.extractChip();
        if (!taken.isEmpty()) {
            giveOrDrop(player, taken);
            level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.35f, 1.1f);
            player.displayClientMessage(Component.translatable(
                    "message.samogon.barrel.chips.taken", cask.chipCount()), true);
            return;
        }
        player.displayClientMessage(Component.translatable("message.samogon.barrel.empty"), true);
    }

    private static void giveOrDrop(Player player, ItemStack stack) {
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }

    private static void reportBottles(Player player, AgingBarrelBlockEntity cask) {
        int filled = cask.bottleCount();
        if (filled == 0) {
            player.displayClientMessage(Component.translatable(
                    "message.samogon.hydrometer.barrel.empty"), true);
            return;
        }
        player.displayClientMessage(Component.translatable("message.samogon.hydrometer.barrel",
                filled, AgingBarrelBlockEntity.BOTTLE_SLOTS), false);
        for (int slot = 0; slot < AgingBarrelBlockEntity.BOTTLE_SLOTS; slot++) {
            ItemStack bottle = cask.getItem(slot);
            if (!AgingBarrelBlockEntity.isBottle(bottle)) continue;
            Batch batch = BatchData.read(bottle);
            player.displayClientMessage(Component.translatable("message.samogon.hydrometer.barrel.entry",
                    bottle.getHoverName(), f(batch.abv()), f(batch.volume()), batch.quality()), false);
        }
    }

    private static String f(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
