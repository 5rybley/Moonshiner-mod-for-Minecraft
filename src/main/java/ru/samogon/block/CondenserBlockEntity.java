package ru.samogon.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import ru.samogon.registry.ModBlocks;

/**
 * A water reservoir that feeds nearby stills.
 *
 * <p>Water is stored in the same units the machines use for coolant, so a
 * bucket is {@link ProcessBlockEntity#COOLANT_PER_BUCKET} and the tank holds
 * {@link ProcessBlockEntity#MAX_COOLANT} (three buckets). A still standing next
 * to a condenser draws coolant from it automatically, so the player only has to
 * keep the condenser topped up.</p>
 */
public final class CondenserBlockEntity extends BlockEntity {
    /** NBT key that also travels on the dropped item so water is not lost. */
    public static final String WATER_KEY = "Water";

    private int water;

    public CondenserBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.CONDENSER_ENTITY.get(), pos, state);
    }

    public int water() {
        return water;
    }

    public boolean isEmpty() {
        return water <= 0;
    }

    public boolean isFull() {
        return water >= ProcessBlockEntity.MAX_COOLANT;
    }

    /** Free space left in the tank, in coolant units. */
    public int remaining() {
        return Math.max(0, ProcessBlockEntity.MAX_COOLANT - water);
    }

    /** Adds water and returns how much was actually accepted. */
    public int addWater(int amount) {
        if (amount <= 0) return 0;
        int accepted = Math.min(amount, ProcessBlockEntity.MAX_COOLANT - water);
        if (accepted <= 0) return 0;
        water += accepted;
        onWaterChanged();
        return accepted;
    }

    /** Removes water and returns how much was actually taken. */
    public int drain(int amount) {
        if (amount <= 0 || water <= 0) return 0;
        int removed = Math.min(amount, water);
        water -= removed;
        onWaterChanged();
        return removed;
    }

    private void onWaterChanged() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
    }

    /** Redstone comparator strength 0..15 derived from the stored water. */
    public int comparatorOutput() {
        return (int) Math.round(water * 15.0 / ProcessBlockEntity.MAX_COOLANT);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(WATER_KEY, water);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        water = Math.max(0, Math.min(ProcessBlockEntity.MAX_COOLANT, tag.getInt(WATER_KEY)));
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
