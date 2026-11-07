package ru.samogon.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import ru.samogon.item.BatchData;
import ru.samogon.item.BatchItem;
import ru.samogon.menu.AgingBarrelMenu;
import ru.samogon.process.Simulation;
import ru.samogon.process.Simulation.Batch;
import ru.samogon.registry.ModBlocks;
import ru.samogon.registry.ModItems;

/**
 * Storage and oak maturation for the 2x2x2 aging cask that lies on its side.
 *
 * <p>Only the lowest-corner block of a formed 2x2x2 structure holds the real
 * inventory; the other seven are thin shells that delegate every interaction
 * to it. Nine bottle slots mature in parallel and each finished bottle costs
 * one oak chip, so a cask can genuinely hold three rows of three bottles.
 * The lowest corner is the master because it is the only position that can be
 * derived from the block coordinates without extra state.</p>
 */
public final class AgingBarrelBlockEntity extends BlockEntity implements WorldlyContainer {
    public static final int BOTTLE_SLOTS = 9;
    public static final int CHIP_SLOT = 9;
    public static final int SLOT_COUNT = 10;
    /** Oak maturation is deliberately slower than a single-bottle cask. */
    public static final int AGE_TICKS = 7200;
    private static final int[] ALL_SLOTS = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
    private static final String PROGRESS_KEY = "samogon_cask_progress";

    /** Values the GUI reads: 0 bottles, 1 chips, 2 progress, 3 ready bottles. */
    public static final int DATA_BOTTLES = 0;
    public static final int DATA_CHIPS = 1;
    public static final int DATA_PROGRESS = 2;
    public static final int DATA_READY = 3;
    public static final int DATA_COUNT = 4;

    private final int[] progress = new int[BOTTLE_SLOTS];

    private final SimpleContainer inventory = new SimpleContainer(SLOT_COUNT) {
        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return canStore(slot, stack);
        }

        @Override
        public int getMaxStackSize() {
            // Chip stacks are allowed; bottle stacks are clamped to one in setItem.
            return 64;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            if (slot < BOTTLE_SLOTS && !stack.isEmpty()) stack.setCount(1);
            super.setItem(slot, stack);
        }

        @Override
        public void setChanged() {
            super.setChanged();
            AgingBarrelBlockEntity.this.setChanged();
        }
    };

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_BOTTLES -> bottleCount();
                case DATA_CHIPS -> chipCount();
                case DATA_PROGRESS -> progressPercent();
                case DATA_READY -> readyCount();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            // The client only mirrors these values.
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public AgingBarrelBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.AGING_BARREL_ENTITY.get(), pos, state);
    }

    public static boolean isBottle(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof BatchItem;
    }

    public static boolean canStore(int slot, ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (slot >= 0 && slot < BOTTLE_SLOTS) return isBottle(stack);
        if (slot == CHIP_SLOT) return stack.is(ModItems.OAK_CHIPS.get());
        return false;
    }

    public ContainerData data() {
        return data;
    }

    /** Opens the cask interface for the player that right-clicked the master block. */
    public void openMenu(Player player) {
        if (!(player instanceof ServerPlayer server)) return;
        server.openMenu(new SimpleMenuProvider(
                        (id, playerInventory, ignored) -> AgingBarrelMenu.server(id, playerInventory, this),
                        getBlockState().getBlock().getName()),
                buffer -> buffer.writeBlockPos(worldPosition));
    }

    // === INTERACTION ===

    /** Inserts a single bottle into the first free bottle slot. */
    public boolean tryInsertBottle(ItemStack held) {
        if (!isBottle(held) || bottleCount() >= BOTTLE_SLOTS) return false;
        for (int slot = 0; slot < BOTTLE_SLOTS; slot++) {
            if (inventory.getItem(slot).isEmpty()) {
                ItemStack one = held.copy();
                one.setCount(1);
                inventory.setItem(slot, one);
                progress[slot] = 0;
                return true;
            }
        }
        return false;
    }

    /** Adds as much of the held oak chip stack as fits and returns the amount moved. */
    public int insertChips(ItemStack held) {
        if (held.isEmpty() || !held.is(ModItems.OAK_CHIPS.get())) return 0;
        ItemStack slot = inventory.getItem(CHIP_SLOT);
        int space = 64 - (slot.isEmpty() ? 0 : slot.getCount());
        int added = Math.min(space, held.getCount());
        if (added <= 0) return 0;
        if (slot.isEmpty()) {
            ItemStack copy = held.copy();
            copy.setCount(added);
            inventory.setItem(CHIP_SLOT, copy);
        } else {
            slot.grow(added);
            inventory.setChanged();
        }
        return added;
    }

    /** Removes the most recently added bottle, so the cask empties from the top. */
    public ItemStack extractBottle() {
        for (int slot = BOTTLE_SLOTS - 1; slot >= 0; slot--) {
            if (!inventory.getItem(slot).isEmpty()) {
                ItemStack out = inventory.removeItem(slot, 1);
                progress[slot] = 0;
                return out;
            }
        }
        return ItemStack.EMPTY;
    }

    public ItemStack extractChip() {
        return inventory.removeItem(CHIP_SLOT, 1);
    }

    public int bottleCount() {
        int filled = 0;
        for (int slot = 0; slot < BOTTLE_SLOTS; slot++) {
            if (!inventory.getItem(slot).isEmpty()) filled++;
        }
        return filled;
    }

    public int chipCount() {
        return inventory.getItem(CHIP_SLOT).getCount();
    }

    public int readyCount() {
        int ready = 0;
        for (int slot = 0; slot < BOTTLE_SLOTS; slot++) {
            if (!inventory.getItem(slot).isEmpty() && progress[slot] >= AGE_TICKS) ready++;
        }
        return ready;
    }

    public int progressPercent() {
        int sum = 0;
        int count = 0;
        for (int slot = 0; slot < BOTTLE_SLOTS; slot++) {
            if (inventory.getItem(slot).isEmpty()) continue;
            sum += Math.min(AGE_TICKS, progress[slot]);
            count++;
        }
        return count == 0 ? 0 : (int) Math.round(sum * 100.0 / (count * (double) AGE_TICKS));
    }

    // === FORMATION ===

    /** Moves bottles and chips from a soon-to-be shell into this master. */
    public void mergeFrom(AgingBarrelBlockEntity source) {
        if (source == this) return;
        for (int from = 0; from < BOTTLE_SLOTS; from++) {
            ItemStack stack = source.inventory.getItem(from);
            if (stack.isEmpty()) continue;
            int target = -1;
            for (int to = 0; to < BOTTLE_SLOTS; to++) {
                if (inventory.getItem(to).isEmpty()) {
                    target = to;
                    break;
                }
            }
            if (target >= 0) {
                inventory.setItem(target, stack.copy());
                progress[target] = source.progress[from];
            } else if (level != null) {
                // Two filled casks cannot share nine slots; never delete the excess.
                Block.popResource(level, worldPosition, stack.copy());
            }
            source.inventory.setItem(from, ItemStack.EMPTY);
            source.progress[from] = 0;
        }
        ItemStack chips = source.inventory.getItem(CHIP_SLOT);
        if (!chips.isEmpty()) {
            int moved = insertChips(chips);
            if (moved < chips.getCount() && level != null) {
                ItemStack overflow = chips.copy();
                overflow.setCount(chips.getCount() - moved);
                Block.popResource(level, worldPosition, overflow);
            }
            source.inventory.setItem(CHIP_SLOT, ItemStack.EMPTY);
        }
        source.setChanged();
        setChanged();
    }

    // === TICKING ===

    /** Runs on the server for every part; only the lowest-corner master advances. */
    public void tick() {
        if (!(level instanceof ServerLevel server)) return;
        // The anchor is always octant 0, so only that block can own the clock.
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof AgingBarrelBlock) || state.getValue(AgingBarrelBlock.PART) != 0) return;
        BlockPos anchor = AgingBarrelBlock.findAnchor(level, worldPosition);
        if (anchor == null || !anchor.equals(worldPosition)) return;
        boolean aging = false;
        int chips = chipCount();
        for (int slot = 0; slot < BOTTLE_SLOTS; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || !(stack.getItem() instanceof BatchItem)) {
                progress[slot] = 0;
                continue;
            }
            Batch batch = BatchData.read(stack);
            if (batch.abv() < 20.0 || batch.abv() > 60.0) {
                progress[slot] = 0;
                continue;
            }
            if (progress[slot] >= AGE_TICKS) {
                if (chips > 0) {
                    chips--;
                    consumeChip();
                    inventory.setItem(slot, mature(stack, batch));
                    progress[slot] = 0;
                    celebrate(server);
                }
                continue;
            }
            progress[slot]++;
            aging = true;
        }
        if (aging) setChanged();
    }

    private ItemStack mature(ItemStack stack, Batch batch) {
        try {
            Batch matured = Simulation.mature(batch, true);
            return BatchData.create(ModItems.OAK_AGED_SPIRIT.get(), matured);
        } catch (IllegalArgumentException exception) {
            // A hand-edited bottle outside 20-60% ABV simply stays untouched.
            return stack;
        }
    }

    private void consumeChip() {
        ItemStack chips = inventory.getItem(CHIP_SLOT);
        if (!chips.isEmpty()) inventory.removeItem(CHIP_SLOT, 1);
    }

    private void celebrate(ServerLevel server) {
        server.sendParticles(ParticleTypes.END_ROD,
                worldPosition.getX() + 1.0, worldPosition.getY() + 1.0, worldPosition.getZ() + 1.0,
                12, 0.8, 0.2, 0.8, 0.01);
        server.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.BLOCKS, 0.45f, 1.4f);
    }

    // === PERSISTENCE ===

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, inventory.getItems(), registries);
        tag.putIntArray(PROGRESS_KEY, progress);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        NonNullList<ItemStack> items = inventory.getItems();
        // Clear in place so a reused client-side instance cannot keep old bottles.
        for (int slot = 0; slot < items.size(); slot++) {
            items.set(slot, ItemStack.EMPTY);
        }
        ContainerHelper.loadAllItems(tag, items, registries);
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            ItemStack stack = items.get(slot);
            if (stack.isEmpty()) continue;
            if (!canStore(slot, stack)) {
                items.set(slot, ItemStack.EMPTY);
            } else if (slot < BOTTLE_SLOTS && stack.getCount() > 1) {
                stack.setCount(1);
            }
        }
        int[] saved = tag.getIntArray(PROGRESS_KEY);
        for (int slot = 0; slot < BOTTLE_SLOTS; slot++) {
            int value = slot < saved.length ? saved[slot] : 0;
            progress[slot] = Math.max(0, Math.min(AGE_TICKS, value));
        }
    }

    // === CONTAINER ===

    @Override
    public int[] getSlotsForFace(Direction side) {
        return ALL_SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int index, ItemStack stack, Direction side) {
        return canStore(index, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int index, ItemStack stack, Direction side) {
        return index >= 0 && index < SLOT_COUNT;
    }

    @Override
    public int getContainerSize() {
        return inventory.getContainerSize();
    }

    @Override
    public boolean isEmpty() {
        return inventory.isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        return inventory.getItem(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        return inventory.removeItem(slot, amount);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return inventory.removeItemNoUpdate(slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot < BOTTLE_SLOTS && !stack.isEmpty()) stack.setCount(1);
        inventory.setItem(slot, stack);
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return canStore(slot, stack);
    }

    @Override
    public int getMaxStackSize() {
        return 64;
    }

    @Override
    public boolean stillValid(Player player) {
        return level != null && player.distanceToSqr(worldPosition.getX() + 0.5,
                worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) <= 64.0;
    }

    @Override
    public void clearContent() {
        inventory.clearContent();
    }
}
