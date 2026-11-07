package ru.samogon.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import ru.samogon.registry.ModEntityTypes;

/**
 * A lump of soul sand thrown up by a very drunk player.
 *
 * <p>It reuses the falling-block look (a full-size block), but its movement is
 * its own: it is tossed like an item the player dropped, falls under gravity and
 * then simply lies on the ground until it expires. A vanilla falling block cannot
 * do that — the moment it touches the ground it either places a real block or
 * removes itself. Because this is a block entity rather than an item, it can
 * never be picked up.
 */
public class PukeBlockEntity extends FallingBlockEntity {
    /** The lump clears itself away after 20 seconds. */
    private static final int LIFETIME_TICKS = 20 * 20;

    private int pukeTicks;

    public PukeBlockEntity(EntityType<? extends PukeBlockEntity> type, Level level) {
        super(type, level);
    }

    /**
     * Creates one lump at {@code pos} with the given toss velocity. The block is
     * applied through NBT because the parent keeps its {@code blockState} private.
     */
    public static PukeBlockEntity toss(Level level, Vec3 pos, Vec3 velocity, BlockState state) {
        PukeBlockEntity puke = new PukeBlockEntity(ModEntityTypes.PUKE.get(), level);
        CompoundTag tag = new CompoundTag();
        tag.put("BlockState", NbtUtils.writeBlockState(state));
        puke.readAdditionalSaveData(tag);
        puke.setPos(pos.x, pos.y, pos.z);
        puke.setDeltaMovement(velocity);
        puke.xo = pos.x;
        puke.yo = pos.y;
        puke.zo = pos.z;
        puke.setStartPos(BlockPos.containing(pos));
        return puke;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        // Without this the lifetime restarts whenever the chunk is reloaded.
        tag.putInt("PukeTicks", pukeTicks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        pukeTicks = tag.getInt("PukeTicks");
    }

    @Override
    public void tick() {
        // Deliberately no super.tick(): the vanilla falling block would place
        // itself into the world (or disappear) as soon as it touched the ground.
        // baseTick() is still needed for the cheap bookkeeping every entity
        // expects (void removal, fire/lava, portals, fluid state).
        this.baseTick();
        if (this.getBlockState().isAir()) {
            this.discard();
            return;
        }

        this.pukeTicks++;
        if (!this.level().isClientSide && this.pukeTicks >= LIFETIME_TICKS) {
            this.discard();
            return;
        }

        // Fly like a thrown item...
        this.applyGravity();
        Vec3 motion = this.getDeltaMovement();
        this.move(MoverType.SELF, motion);

        // ...and then come to rest on the ground instead of vanishing.
        if (this.onGround()) {
            this.setDeltaMovement(motion.x * 0.55, 0.0, motion.z * 0.55);
        }
    }
}
