package ru.samogon.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import ru.samogon.effect.Intoxication;
import ru.samogon.item.BatchData;
import ru.samogon.item.BatchItem;
import ru.samogon.process.BeerStyle;
import ru.samogon.process.SpiritType;
import ru.samogon.process.Simulation.Batch;
import ru.samogon.process.Wine;

import java.util.ArrayList;
import java.util.List;

/**
 * A small table vessel set down in the world: a shot glass (рюмка) or a cup
 * (чарка). Unlike {@link PlacedDrinkBlock} it is drawn by a plain block model,
 * swapping between an empty and a filled model through the {@link #FILLED}
 * block state, so it needs no block entity renderer.
 *
 * <p>The vessel is placed by hand like any other block. Right-clicking an empty
 * vessel while holding a bottle or a mug pours the whole drink into it and hands
 * the empty container back. Right-clicking a full vessel with an empty hand
 * drinks it, and sneak-right-clicking picks the vessel back up.</p>
 */
public final class PlacedVesselBlock extends Block implements EntityBlock {
    /** True once a drink has been poured in; selects the filled model. */
    public static final BooleanProperty FILLED = BooleanProperty.create("filled");
    /**
     * The colour family of the drink inside. The filled model is tinted by this
     * value, so one model shows vodka, wine, beer or kvass in its own colour.
     */
    public static final EnumProperty<Liquid> LIQUID = EnumProperty.create("liquid", Liquid.class);

    /** The visible colour of the drink: a small palette shared by all bottles. */
    public enum Liquid implements StringRepresentable {
        EMPTY("empty", 0xFFFFFFFF),
        CLEAR("clear", 0xFFDCE8E4),
        GOLD("gold", 0xFFD8A63C),
        AMBER("amber", 0xFFB06A22),
        RED("red", 0xFF7E1E2A),
        ROSE("rose", 0xFFD97F72),
        BROWN("brown", 0xFF5E3517),
        GREEN("green", 0xFF6E8B3A);

        private final String id;
        private final int color;

        Liquid(String id, int color) {
            this.id = id;
            this.color = color;
        }

        /** The tint colour of the liquid; {@code EMPTY} is never visible. */
        public int color() {
            return color;
        }

        @Override
        public String getSerializedName() {
            return id;
        }

        /** Picks the colour family that matches the batch the vessel holds. */
        public static Liquid of(Batch batch) {
            if (batch == null) return EMPTY;
            return switch (SpiritType.of(batch)) {
                case KVASS -> BROWN;
                case BEER -> switch (BeerStyle.of(batch.origin())) {
                    case DARK, PORTER, SCHWARZBIER -> BROWN;
                    case BERRY -> ROSE;
                    default -> GOLD;
                };
                case WINE, SPARKLING_WINE -> switch (Wine.colorOf(batch.origin())) {
                    case RED -> RED;
                    case ROSE -> ROSE;
                    case WHITE -> GOLD;
                };
                case WHISKEY, SCOTCH, RUM, BRANDY, COGNAC, CALVADOS, CACHACA, MEAD, COCOA,
                     LIQUEUR, TINCTURE, CARROT -> AMBER;
                case ABSINTHE, MELON, CACTUS -> GREEN;
                case BEET -> ROSE;
                case BRAGA, MOONSHINE, CHACHA, VODKA, SPIRIT, GIN, POTATO, WASH, MUST -> CLEAR;
            };
        }
    }

    private final VoxelShape shape;

    public PlacedVesselBlock(Properties properties, VoxelShape shape) {
        super(properties);
        this.shape = shape;
        registerDefaultState(stateDefinition.any().setValue(FILLED, false)
                .setValue(LIQUID, Liquid.EMPTY));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FILLED, LIQUID);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FILLED, false).setValue(LIQUID, Liquid.EMPTY);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PlacedVesselBlockEntity(pos, state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shape;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction == Direction.DOWN && !state.canSurvive(level, pos)) {
            // The ground vanished: hand any drink back instead of letting it
            // disappear with the block. The vessel itself is not popped here:
            // removing the block runs getDrops, which already returns exactly
            // one vessel, and popping it as well duplicated the cup or glass.
            if (level instanceof ServerLevel server
                    && level.getBlockEntity(pos) instanceof PlacedVesselBlockEntity vessel) {
                ItemStack drink = vessel.takeDrink();
                if (!drink.isEmpty()) popResource(server, pos, drink);
            }
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        List<ItemStack> drops = new ArrayList<>(2);
        drops.add(new ItemStack(this));
        if (builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY)
                instanceof PlacedVesselBlockEntity vessel) {
            ItemStack drink = vessel.takeDrink();
            if (!drink.isEmpty()) drops.add(drink);
        }
        return drops;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        // Breaking in creative skips the loot table, so return the vessel and the
        // drink by hand; the survival path gets both from getDrops.
        if (player.isCreative() && level instanceof ServerLevel server) {
            popResource(server, pos, new ItemStack(this));
            if (level.getBlockEntity(pos) instanceof PlacedVesselBlockEntity vessel) {
                ItemStack drink = vessel.takeDrink();
                if (!drink.isEmpty()) popResource(server, pos, drink);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof PlacedVesselBlockEntity vessel)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        // A filled vessel is not topped up: it holds one serving already. With
        // something in hand the click is swallowed instead of falling through to
        // the block's own use, so a bottle does not accidentally drink the cup;
        // an empty hand still reaches useWithoutItem and takes the sip.
        if (state.getValue(FILLED) || !vessel.drink().isEmpty()) {
            return stack.isEmpty()
                    ? ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
                    : ItemInteractionResult.SUCCESS;
        }
        if (!(stack.getItem() instanceof BatchItem item) || !item.placeable()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        Batch batch;
        try {
            batch = BatchData.read(stack);
        } catch (RuntimeException ignored) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!item.canDrink(batch)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            vessel.setDrink(stack);
            if (!player.getAbilities().instabuild) {
                ItemStack empty = new ItemStack(item.container());
                stack.shrink(1);
                if (!player.getInventory().add(empty)) {
                    player.drop(empty, false);
                }
            }
            level.setBlock(pos, state.setValue(FILLED, true).setValue(LIQUID, Liquid.of(batch)),
                    Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 0.7f, 1.0f);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof PlacedVesselBlockEntity vessel)) {
            return InteractionResult.PASS;
        }
        ItemStack drink = vessel.drink();
        if (player.isShiftKeyDown()) {
            // Sneak with an empty hand picks the vessel up; a drink inside is
            // returned to the player so nothing is quietly destroyed.
            if (!level.isClientSide) {
                ItemStack stored = vessel.takeDrink();
                level.removeBlock(pos, false);
                ItemStack vesselItem = new ItemStack(this);
                if (!player.getInventory().add(vesselItem)) player.drop(vesselItem, false);
                if (!stored.isEmpty() && !player.getInventory().add(stored)) player.drop(stored, false);
                level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.5f, 1.2f);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (drink.isEmpty() || !(drink.getItem() instanceof BatchItem item)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            Batch batch;
            try {
                batch = BatchData.read(drink);
            } catch (RuntimeException ignored) {
                return InteractionResult.PASS;
            }
            Intoxication.drink(player, batch, item.effectiveTincture(batch), item.technical(), item.fusel());
            vessel.takeDrink();
            level.setBlock(pos, state.setValue(FILLED, false).setValue(LIQUID, Liquid.EMPTY),
                    Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.GENERIC_DRINK, SoundSource.BLOCKS, 0.7f, 1.05f);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}