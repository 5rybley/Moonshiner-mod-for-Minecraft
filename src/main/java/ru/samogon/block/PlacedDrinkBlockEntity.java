package ru.samogon.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import ru.samogon.item.BatchItem;
import ru.samogon.registry.ModBlocks;
import ru.samogon.registry.ModItems;

/**
 * One finished drink set down in the world: a mug of beer or a bottle of
 * spirit.
 *
 * <p>The block itself is invisible; the stored stack is drawn by the block
 * entity renderer. The rotation is quantised to 16 steps of 22.5 degrees so
 * the mug handle or the bottle label can be turned toward the player who
 * placed it. The stack and the rotation are synced to clients, and breaking
 * the block returns the very same stack.</p>
 */
public final class PlacedDrinkBlockEntity extends BlockEntity {
    private static final String DRINK_KEY = "drink";
    private static final String ROTATION_KEY = "rotation";
    /** Number of horizontal steps; 360 degrees / 16 = 22.5 degrees. */
    public static final int ROTATION_STEPS = 16;

    private ItemStack drink = ItemStack.EMPTY;
    private int rotation;

    public PlacedDrinkBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.PLACED_DRINK_ENTITY.get(), pos, state);
    }

    /** Only real, drinkable bottles and mugs may be displayed, never technical fractions. */
    public static boolean isPlaceable(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof BatchItem item && item.placeable();
    }

    public ItemStack drink() {
        return drink;
    }

    public int rotation() {
        return rotation;
    }

    /** True when the stored drink is served in a glass bottle rather than a wooden mug. */
    public boolean isBottle() {
        return !drink.isEmpty() && drink.getItem() instanceof BatchItem item
                && item.container() != ModItems.WOODEN_MUG.get();
    }

    /** Stores a single serving of the drink and its quantised rotation. */
    public void setDrink(ItemStack stack, int rotation) {
        this.drink = isPlaceable(stack) ? stack.copyWithCount(1) : ItemStack.EMPTY;
        this.rotation = Math.floorMod(rotation, ROTATION_STEPS);
        setChanged();
    }

    /**
     * Removes the stored drink and returns it. Used when the block under the
     * drink is removed: taking the stack out first makes sure the engine's own
     * block destruction cannot drop the same drink a second time.
     */
    public ItemStack takeDrink() {
        ItemStack taken = drink;
        drink = ItemStack.EMPTY;
        return taken;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(ROTATION_KEY, rotation);
        if (!drink.isEmpty()) {
            tag.put(DRINK_KEY, drink.save(registries));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        rotation = Math.floorMod(tag.getInt(ROTATION_KEY), ROTATION_STEPS);
        ItemStack loaded = ItemStack.parseOptional(registries, tag.getCompound(DRINK_KEY));
        drink = isPlaceable(loaded) ? loaded.copyWithCount(1) : ItemStack.EMPTY;
    }

    @Override
    public void setChanged() {
        super.setChanged();
        if (level != null && !level.isClientSide) {
            // Push the new drink and rotation to clients so the renderer matches.
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
}
