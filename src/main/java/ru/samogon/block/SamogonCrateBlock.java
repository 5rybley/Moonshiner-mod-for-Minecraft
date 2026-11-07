package ru.samogon.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import ru.samogon.item.BatchData;
import ru.samogon.process.Quality;
import ru.samogon.process.Simulation.Batch;
import ru.samogon.registry.ModItems;

import java.util.List;
import java.util.Locale;

/**
 * Open-topped crate that holds up to four finished bottles in plain sight.
 *
 * <p>Right-click with a bottle to drop one in, sneak-right-click to take one
 * back out. The crate keeps its bottles when broken: the dropped crate item
 * carries the stack, so nothing spills on the ground.</p>
 */
public final class SamogonCrateBlock extends Block implements EntityBlock {
    private static final VoxelShape SHAPE = Shapes.box(0.0, 0.0, 0.0, 1.0, 0.8125, 1.0);

    public SamogonCrateBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SamogonCrateBlockEntity(pos, state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        ItemStack drop = new ItemStack(this.asItem());
        if (builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof SamogonCrateBlockEntity crate) {
            int filled = crate.filledSlots();
            if (filled > 0) {
                CompoundTag tag = crate.saveWithoutMetadata(builder.getLevel().registryAccess());
                tag.putInt(SamogonCrateBlockEntity.BOTTLE_COUNT_KEY, filled);
                drop.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(tag));
            }
        }
        return List.of(drop);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof SamogonCrateBlockEntity crate)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (stack.is(ModItems.HYDROMETER.get())) {
            if (!level.isClientSide) {
                reportBottles(player, crate);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }

        if (player.isSecondaryUseActive()) {
            if (!level.isClientSide) {
                takeOne(level, pos, player, crate);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }

        if (SamogonCrateBlockEntity.isBottle(stack)) {
            if (!level.isClientSide) {
                if (crate.tryInsert(stack)) {
                    if (!player.getAbilities().instabuild) {
                        stack.shrink(1);
                    }
                    level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 0.35f, 1.25f);
                    player.displayClientMessage(Component.translatable("message.samogon.crate.added",
                            crate.filledSlots(), SamogonCrateBlockEntity.SLOT_COUNT), true);
                } else {
                    player.displayClientMessage(Component.translatable("message.samogon.crate.full"), true);
                }
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }

        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof SamogonCrateBlockEntity crate)) {
            return InteractionResult.PASS;
        }
        if (player.isSecondaryUseActive()) {
            if (!level.isClientSide) {
                takeOne(level, pos, player, crate);
            }
        } else if (!level.isClientSide) {
            player.displayClientMessage(Component.translatable("message.samogon.crate.status",
                    crate.filledSlots(), SamogonCrateBlockEntity.SLOT_COUNT), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static void reportBottles(Player player, SamogonCrateBlockEntity crate) {
        int filled = crate.filledSlots();
        if (filled == 0) {
            player.displayClientMessage(Component.translatable("message.samogon.hydrometer.crate.empty"), true);
            return;
        }
        player.displayClientMessage(Component.translatable("message.samogon.hydrometer.crate",
                filled, SamogonCrateBlockEntity.SLOT_COUNT), false);
        for (int slot = 0; slot < SamogonCrateBlockEntity.SLOT_COUNT; slot++) {
            ItemStack bottle = crate.getItem(slot);
            if (!SamogonCrateBlockEntity.isBottle(bottle)) continue;
            Batch batch = BatchData.read(bottle);
            player.displayClientMessage(Component.translatable("message.samogon.hydrometer.crate.entry",
                    bottle.getHoverName(), f(batch.abv()), f(batch.volume()), Quality.label(batch.quality())), false);
        }
    }

    private static String f(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static void takeOne(Level level, BlockPos pos, Player player, SamogonCrateBlockEntity crate) {
        ItemStack taken = crate.extractOne();
        if (taken.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.samogon.crate.empty"), true);
            return;
        }
        if (!player.getInventory().add(taken)) {
            player.drop(taken, false);
        }
        level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.35f, 1.1f);
        player.displayClientMessage(Component.translatable("message.samogon.crate.taken",
                crate.filledSlots(), SamogonCrateBlockEntity.SLOT_COUNT), true);
    }
}
