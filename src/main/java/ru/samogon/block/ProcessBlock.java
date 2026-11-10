package ru.samogon.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
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
import ru.samogon.registry.ModBlocks;

public final class ProcessBlock extends Block implements EntityBlock {
    /**
     * New kinds must be appended: the kind is synced to the client as its
     * ordinal, so inserting one would reinterpret existing machines.
     * {@code BEER_FERMENTATION} is the standalone one-block barrel that turns
     * hopped wort and yeast into beer, separate from the brewery multiblock.
     */
    public enum Kind { MASHING, FERMENTATION, DISTILLER, RECTIFIER, FILTER, BLENDING, DILUTION, AGING, TASTING, BEER_FERMENTATION }

    public static final IntegerProperty HEAT = IntegerProperty.create("heat", 0, 4);
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    /** Coarse progress band used by the client for particles and light. */
    public static final IntegerProperty PROGRESS = IntegerProperty.create("progress", 0, 4);
    /** A finished machine glows and emits golden sparkles until it is emptied. */
    public static final BooleanProperty READY = BooleanProperty.create("ready");
    /** Horizontal direction the machine's working front (tap, gauge, hatch) points at. */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    private final Kind kind;

    public ProcessBlock(Properties properties, Kind kind) {
        super(properties);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    public Kind kind() {
        return kind;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HEAT, ACTIVE, PROGRESS, READY);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // The working front faces the player who placed the machine, exactly
        // like a furnace: getHorizontalDirection() is the way the player looks,
        // so the opposite is the side they are standing on.
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ProcessBlockEntity(pos, state);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        // onRemove also fires for plain property changes (HEAT/ACTIVE/...),
        // where the block stays in place and must keep its contents. Only a
        // real removal - broken, exploded, replaced or /setblock - drops loot.
        // Doing it here instead of in playerWillDestroy also covers every
        // non-player removal path.
        if (!state.is(newState.getBlock())
                && !level.isClientSide
                && level.getBlockEntity(pos) instanceof ProcessBlockEntity machine) {
            machine.dropContents();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        // Placing the eighth aging cask of a 2x2x2 cube merges the eight blocks
        // into one large barrel. Any other machine ignores this.
        if (kind == Kind.AGING && !level.isClientSide) {
            AgingBarrelBlock.tryAssemble(level, pos, state.getValue(FACING));
        }
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide || type != ModBlocks.PROCESS_BLOCK_ENTITY.get() ? null
                : (world, pos, blockState, entity) -> ((ProcessBlockEntity) entity).tick();
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                             Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof ProcessBlockEntity machine) {
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
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof ProcessBlockEntity machine) {
            // Empty hand opens the machine panel. Sneak-right-click keeps the
            // old service shortcut (receiver cycling / clearing a still) so
            // existing instructions remain valid.
            if (player.isShiftKeyDown()) {
                machine.interact(player, InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            } else {
                machine.openMachine(player);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        boolean active = state.getValue(ACTIVE);
        boolean ready = state.getValue(READY);
        int progress = state.getValue(PROGRESS);
        if (!active && !ready) return;

        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.5;
        double z = pos.getZ() + 0.5;
        int heat = state.getValue(HEAT);

        // Finished machines are visible from a distance: a pulsing golden crown
        // means "take the product", while an idle machine stays dark.
        if (ready) {
            if (random.nextInt(5) == 0) {
                level.addParticle(ParticleTypes.END_ROD, x + spread(random, 0.32),
                        pos.getY() + 1.05, z + spread(random, 0.32), 0, 0.018, 0);
            }
            if (random.nextInt(8) == 0) {
                level.addParticle(ParticleTypes.ELECTRIC_SPARK, x + spread(random, 0.4),
                        pos.getY() + 0.65, z + spread(random, 0.4), 0, 0.01, 0);
            }
            if (random.nextInt(110) == 0) {
                level.playLocalSound(x, y + 0.8, z, SoundEvents.AMETHYST_BLOCK_CHIME,
                        SoundSource.BLOCKS, 0.16f, 1.7f, false);
            }
        }
        if (!active) return;

        switch (kind) {
            case MASHING -> {
                if (heat > 0) {
                    int rate = Math.max(2, 8 - progress);
                    if (random.nextInt(rate) == 0) {
                        level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                                x + spread(random, 0.38), pos.getY() + 0.04, z + spread(random, 0.38),
                                0, 0.02, 0);
                    }
                    if (random.nextInt(14) == 0) {
                        level.addParticle(ParticleTypes.SPORE_BLOSSOM_AIR,
                                x + spread(random, 0.3), pos.getY() + 1.02, z + spread(random, 0.3),
                                0, 0.015, 0);
                    }
                }
                if (random.nextInt(90) == 0) {
                    level.playLocalSound(x, y, z, SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS,
                            0.25f, 0.85f + random.nextFloat() * 0.2f, false);
                }
            }
            case FERMENTATION, BEER_FERMENTATION -> {
                int rate = Math.max(3, 17 - progress * 2);
                if (random.nextInt(rate) == 0) {
                    level.addParticle(ParticleTypes.BUBBLE_POP, x + spread(random, 0.32),
                            pos.getY() + 0.86, z + spread(random, 0.32), 0, 0.01, 0);
                }
                if (progress >= 2 && random.nextInt(12) == 0) {
                    level.addParticle(ParticleTypes.BUBBLE, x + spread(random, 0.22),
                            pos.getY() + 0.55, z + spread(random, 0.22), 0, 0.02, 0);
                }
                if (random.nextInt(160) == 0) {
                    level.playLocalSound(x, y + 0.8, z, SoundEvents.BUBBLE_COLUMN_BUBBLE_POP,
                            SoundSource.BLOCKS, 0.2f, 0.8f + random.nextFloat() * 0.4f, false);
                }
            }
            case DISTILLER -> {
                if (heat > 0 && random.nextInt(4) == 0) {
                    level.addParticle(ParticleTypes.SMOKE, x + spread(random, 0.22),
                            pos.getY() - 0.35, z + spread(random, 0.22), 0, 0.02, 0);
                }
                if (heat > 1 && random.nextInt(8) == 0) {
                    level.addParticle(ParticleTypes.FLAME, x + spread(random, 0.2),
                            pos.getY() - 0.45, z + spread(random, 0.2), 0, 0.008, 0);
                }
                if (progress >= 1 && random.nextInt(16) == 0) {
                    level.addParticle(ParticleTypes.CLOUD, x + spread(random, 0.18),
                            pos.getY() + 1.02, z + spread(random, 0.18), 0, 0.012, 0);
                }
                if (random.nextInt(18) == 0) {
                    ParticleOptions type = progress <= 1 ? ParticleTypes.SMOKE
                            : progress >= 4 ? ParticleTypes.DRIPPING_WATER : ParticleTypes.DRIPPING_HONEY;
                    level.addParticle(type, x + 0.35, pos.getY() + 0.55, z + 0.3, 0, -0.02, 0);
                }
                if (progress >= 2 && random.nextInt(24) == 0) {
                    level.addParticle(ParticleTypes.SPLASH, x + 0.35, pos.getY() + 0.48,
                            z + 0.3, 0, 0.01, 0);
                }
                if (random.nextInt(120) == 0) {
                    level.playLocalSound(x, y, z, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS,
                            0.18f, 1.2f, false);
                }
            }
            case RECTIFIER -> {
                int rate = Math.max(3, 12 - progress);
                if (random.nextInt(rate) == 0) {
                    level.addParticle(ParticleTypes.CLOUD, x + spread(random, 0.15),
                            pos.getY() + 1.02, z + spread(random, 0.15), 0, 0.01, 0);
                }
                if (heat > 0 && random.nextInt(10) == 0) {
                    level.addParticle(ParticleTypes.FLAME, x + spread(random, 0.16),
                            pos.getY() - 0.4, z + spread(random, 0.16), 0, 0.01, 0);
                }
                if (random.nextInt(18) == 0) {
                    level.addParticle(ParticleTypes.DRIPPING_WATER, x + 0.36, pos.getY() + 0.55,
                            z + 0.28, 0, -0.02, 0);
                }
                if (random.nextInt(140) == 0) {
                    level.playLocalSound(x, y, z, SoundEvents.BUBBLE_COLUMN_BUBBLE_POP,
                            SoundSource.BLOCKS, 0.2f, 0.7f, false);
                }
            }
            case FILTER -> {
                if (random.nextInt(Math.max(5, 14 - progress)) == 0) {
                    level.addParticle(ParticleTypes.DRIPPING_WATER, x + spread(random, 0.15),
                            pos.getY() + 0.35, z + spread(random, 0.15), 0, -0.02, 0);
                }
                if (progress >= 2 && random.nextInt(22) == 0) {
                    level.addParticle(ParticleTypes.SMOKE, x + spread(random, 0.2),
                            pos.getY() + 0.7, z + spread(random, 0.2), 0, 0.008, 0);
                }
            }
            case BLENDING -> {
                int rate = Math.max(4, 24 - progress * 3);
                if (random.nextInt(rate) == 0) {
                    level.addParticle(ParticleTypes.SPLASH, x + spread(random, 0.25),
                            pos.getY() + 0.78, z + spread(random, 0.25), 0, 0.01, 0);
                }
                if (random.nextInt(18) == 0) {
                    level.addParticle(ParticleTypes.BUBBLE_POP, x + spread(random, 0.25),
                            pos.getY() + 0.75, z + spread(random, 0.25), 0, 0.01, 0);
                }
            }
            case DILUTION -> {
                int rate = Math.max(3, 15 - progress * 2);
                if (random.nextInt(rate) == 0) {
                    level.addParticle(ParticleTypes.SPLASH, x + spread(random, 0.32),
                            pos.getY() + 0.58, z + spread(random, 0.32), 0, 0.01, 0);
                }
                if (random.nextInt(20) == 0) {
                    level.addParticle(ParticleTypes.BUBBLE_POP, x + spread(random, 0.3),
                            pos.getY() + 0.62, z + spread(random, 0.3), 0, 0.01, 0);
                }
                if (random.nextInt(90) == 0) {
                    level.playLocalSound(x, y, z, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS,
                            0.18f, 1.35f, false);
                }
            }
            case AGING -> {
                if (random.nextInt(12) == 0) {
                    level.addParticle(ParticleTypes.FALLING_HONEY, x + spread(random, 0.22),
                            pos.getY() + 1.05, z + spread(random, 0.22), 0, -0.01, 0);
                }
                if (random.nextInt(200) == 0) {
                    level.playLocalSound(x, y, z, SoundEvents.COPPER_BREAK, SoundSource.BLOCKS,
                            0.08f, 1.8f, false);
                }
            }
            case TASTING -> {
                // The tasting table has no process to animate: it only reads
                // the bottle and the hydrometer that sit on it.
            }
        }
    }

    private static double spread(RandomSource random, double radius) {
        return (random.nextDouble() - 0.5) * 2 * radius;
    }
}
