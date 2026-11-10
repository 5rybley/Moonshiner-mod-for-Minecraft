package ru.samogon.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import ru.samogon.block.BreweryBlock;
import ru.samogon.block.BreweryBlockEntity;
import ru.samogon.item.BatchData;
import ru.samogon.item.BatchItem;
import ru.samogon.process.Brewing;
import ru.samogon.process.BreweryData;
import ru.samogon.process.Simulation.Batch;
import ru.samogon.registry.ModItems;
import ru.samogon.registry.ModMenus;

/**
 * The brewhouse interface: one input slot, one additive slot and one output
 * slot, plus the player inventory. The two process tabs (mash, boil) are not
 * extra slots: the selected step lives in the block entity and the screen sends
 * it through {@link #clickMenuButton}. Fermentation is not offered here — that
 * happens in the separate beer fermentation barrel. All physics still happens
 * in {@link BreweryBlockEntity}; this menu only exposes and synchronizes it.
 */
public final class BreweryMenu extends AbstractContainerMenu {
    public static final int SLOT_INPUT = BreweryBlockEntity.SLOT_INPUT;
    public static final int SLOT_ADDITIVE = BreweryBlockEntity.SLOT_ADDITIVE;
    public static final int SLOT_OUTPUT = BreweryBlockEntity.SLOT_OUTPUT;

    public static final int INPUT_X = 80;
    public static final int INPUT_Y = 124;
    public static final int ADDITIVE_X = 181;
    public static final int ADDITIVE_Y = 124;
    public static final int OUTPUT_X = 282;
    public static final int OUTPUT_Y = 124;

    public static final int PLAYER_INV_X = 109;
    public static final int PLAYER_INV_Y = 178;
    public static final int HOTBAR_Y = 236;

    private final Container container;
    private final ContainerData data;
    private final BlockPos pos;
    private final BreweryBlockEntity brewery;

    private BreweryMenu(int id, Inventory playerInventory, Container container, ContainerData data,
                        BlockPos pos, BreweryBlockEntity brewery) {
        super(ModMenus.BREWERY.get(), id);
        this.container = container;
        this.data = data;
        this.pos = pos;
        this.brewery = brewery;

        addSlot(new Slot(container, SLOT_INPUT, INPUT_X, INPUT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return acceptsInput(stack);
            }
        });
        addSlot(new Slot(container, SLOT_ADDITIVE, ADDITIVE_X, ADDITIVE_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return acceptsAdditive(stack);
            }
        });
        addSlot(new Slot(container, SLOT_OUTPUT, OUTPUT_X, OUTPUT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(Items.GLASS_BOTTLE) || stack.is(ModItems.WOODEN_MUG.get());
            }

            @Override
            public int getMaxStackSize() {
                // One bottle at a time keeps the automatic bottling simple.
                return 1;
            }
        });

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(playerInventory, column + row * 9 + 9,
                        PLAYER_INV_X + column * 18, PLAYER_INV_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column, PLAYER_INV_X + column * 18, HOTBAR_Y));
        }
        addDataSlots(data);
    }

    public static BreweryMenu server(int id, Inventory playerInventory, BreweryBlockEntity brewery) {
        return new BreweryMenu(id, playerInventory, brewery.inventory(), brewery.data(),
                brewery.getBlockPos(), brewery);
    }

    public static BreweryMenu client(int id, Inventory playerInventory, BlockPos pos) {
        return new BreweryMenu(id, playerInventory, new SimpleContainer(BreweryBlockEntity.SLOT_COUNT),
                new SimpleContainerData(BreweryData.COUNT), pos, null);
    }

    public BlockPos pos() {
        return pos;
    }

    public int data(int index) {
        return data.get(index);
    }

    /** The selected process tab: 0 mash, 1 boil. */
    public int tab() {
        return data.get(BreweryData.TAB);
    }

    /** Grain or a bottled intermediate the vessel can accept. */
    public static boolean acceptsInput(ItemStack stack) {
        if (stack.is(ModItems.MALT.get()) || stack.is(ModItems.ROASTED_MALT.get())
                || stack.is(Items.WHEAT)) {
            return true;
        }
        if (!(stack.getItem() instanceof BatchItem)) return false;
        try {
            Batch batch = BatchData.read(stack);
            return Brewing.isBrew(batch);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public static boolean acceptsAdditive(ItemStack stack) {
        return stack.is(Items.WATER_BUCKET) || stack.is(ModItems.HOPS.get());
    }

    @Override
    public boolean stillValid(Player player) {
        if (brewery != null && brewery.isRemoved()) return false;
        Level level = player.level();
        return level.getBlockState(pos).getBlock() instanceof BreweryBlock
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;

        ItemStack stack = slot.getItem();
        ItemStack result = stack.copy();
        if (index <= SLOT_OUTPUT) {
            if (!moveItemStackTo(stack, SLOT_OUTPUT + 1, slots.size(), true)) return ItemStack.EMPTY;
            slot.setChanged();
        } else {
            int targetStart = -1;
            int targetEnd = -1;
            if (acceptsInput(stack)) {
                targetStart = SLOT_INPUT;
                targetEnd = SLOT_INPUT + 1;
            } else if (acceptsAdditive(stack)) {
                targetStart = SLOT_ADDITIVE;
                targetEnd = SLOT_ADDITIVE + 1;
            } else if (stack.is(Items.GLASS_BOTTLE) || stack.is(ModItems.WOODEN_MUG.get())) {
                targetStart = SLOT_OUTPUT;
                targetEnd = SLOT_OUTPUT + 1;
            }
            if (targetStart < 0 || !moveItemStackTo(stack, targetStart, targetEnd, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == result.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return result;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (brewery == null) return false;
        if (id < 0 || id > 1) return false;
        return brewery.setTab(id);
    }
}
