package ru.samogon.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import ru.samogon.item.BatchItem;
import ru.samogon.registry.ModBlocks;

/**
 * An open-topped crate that visibly holds up to four finished bottles.
 *
 * <p>Bottles are added one at a time by right-clicking with a bottle and are
 * taken back one at a time with a sneaking right-click. The stored bottles are
 * synced to nearby clients so the block entity renderer can show them sitting
 * inside the crate. Breaking the crate keeps its contents on the dropped crate
 * item instead of spilling them.</p>
 */
public final class SamogonCrateBlockEntity extends BlockEntity implements WorldlyContainer {
    public static final int SLOT_COUNT = 4;
    /** NBT key on the dropped item that records how many bottles it carries. */
    public static final String BOTTLE_COUNT_KEY = "samogon:bottles";
    private static final int[] ALL_SLOTS = {0, 1, 2, 3};

    private final SimpleContainer inventory = new SimpleContainer(SLOT_COUNT) {
        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return isBottle(stack);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public void setChanged() {
            super.setChanged();
            SamogonCrateBlockEntity.this.setChanged();
        }
    };

    public SamogonCrateBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.SAMOGON_CRATE_ENTITY.get(), pos, state);
    }

    public static boolean isBottle(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof BatchItem;
    }

    /** Inserts a single bottle into the first free slot. */
    public boolean tryInsert(ItemStack stack) {
        if (!isBottle(stack)) return false;
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            if (inventory.getItem(slot).isEmpty()) {
                ItemStack one = stack.copy();
                one.setCount(1);
                inventory.setItem(slot, one);
                return true;
            }
        }
        return false;
    }

    /** Removes the most recently added bottle, so the crate empties from the top. */
    public ItemStack extractOne() {
        for (int slot = SLOT_COUNT - 1; slot >= 0; slot--) {
            if (!inventory.getItem(slot).isEmpty()) {
                return inventory.removeItem(slot, 1);
            }
        }
        return ItemStack.EMPTY;
    }

    public int filledSlots() {
        int filled = 0;
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            if (!inventory.getItem(slot).isEmpty()) filled++;
        }
        return filled;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, inventory.getItems(), registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        NonNullList<ItemStack> items = inventory.getItems();
        // ContainerHelper.loadAllItems only writes the slots present in the tag
        // and never clears the rest. Client-side updates reuse this instance, so
        // without an explicit reset a removed bottle would stay visible in the
        // crate. The raw list is cleared in place to avoid notifying setChanged.
        for (int slot = 0; slot < items.size(); slot++) {
            items.set(slot, ItemStack.EMPTY);
        }
        ContainerHelper.loadAllItems(tag, items, registries);
        // Legacy or hand-edited data must not put arbitrary items into an
        // open display crate. Slots are also reduced to a single bottle.
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            ItemStack stack = items.get(slot);
            if (stack.isEmpty()) continue;
            if (!isBottle(stack)) {
                items.set(slot, ItemStack.EMPTY);
            } else if (stack.getCount() > 1) {
                stack.setCount(1);
            }
        }
    }

    @Override
    public void setChanged() {
        super.setChanged();
        if (level != null && !level.isClientSide) {
            // Push the new contents to clients so the rendered bottles match.
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return ALL_SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int index, ItemStack stack, Direction side) {
        return index >= 0 && index < SLOT_COUNT && isBottle(stack);
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
        inventory.setItem(slot, stack);
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return isBottle(stack);
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
                worldPosition.getZ() + 0.5) <= 64.0;
    }

    @Override
    public void clearContent() {
        inventory.clearContent();
    }
}
