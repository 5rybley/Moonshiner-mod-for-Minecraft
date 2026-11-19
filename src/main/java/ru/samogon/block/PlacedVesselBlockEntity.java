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

/**
 * The single serving of drink poured into a placed shot glass or cup. The block
 * model shows the vessel, the block entity only remembers which drink is in it
 * so the full model can be swapped back to empty after the last sip.
 */
public final class PlacedVesselBlockEntity extends BlockEntity {
    private static final String DRINK_KEY = "drink";

    private ItemStack drink = ItemStack.EMPTY;

    public PlacedVesselBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.PLACED_VESSEL_ENTITY.get(), pos, state);
    }

    /** Only real, drinkable bottles and mugs may be poured, never technical fractions. */
    public static boolean isPourAble(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof BatchItem item && item.placeable();
    }

    public ItemStack drink() {
        return drink;
    }

    public boolean isEmpty() {
        return drink.isEmpty();
    }

    /** Stores one serving of the poured drink. */
    public void setDrink(ItemStack stack) {
        this.drink = isPourAble(stack) ? stack.copyWithCount(1) : ItemStack.EMPTY;
        setChanged();
    }

    /** Removes the stored drink and returns it. */
    public ItemStack takeDrink() {
        ItemStack taken = drink;
        drink = ItemStack.EMPTY;
        return taken;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!drink.isEmpty()) {
            tag.put(DRINK_KEY, drink.save(registries));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ItemStack loaded = ItemStack.parseOptional(registries, tag.getCompound(DRINK_KEY));
        drink = isPourAble(loaded) ? loaded.copyWithCount(1) : ItemStack.EMPTY;
    }

    @Override
    public void setChanged() {
        super.setChanged();
        if (level != null && !level.isClientSide) {
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