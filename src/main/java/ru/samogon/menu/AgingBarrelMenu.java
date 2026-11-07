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
import net.minecraft.world.level.Level;
import ru.samogon.block.AgingBarrelBlock;
import ru.samogon.block.AgingBarrelBlockEntity;
import ru.samogon.registry.ModItems;
import ru.samogon.registry.ModMenus;

/**
 * The aging cask interface: a three-by-three tray of nine bottles, one oak-chip
 * slot and the player inventory. The bottle slots hold a single bottle each, so
 * the cask matures them in parallel; the chip slot is a plain stack. All physics
 * still happens in {@link AgingBarrelBlockEntity}; this menu only exposes and
 * synchronizes it.
 */
public final class AgingBarrelMenu extends AbstractContainerMenu {
    public static final int BOTTLE_X = 30;
    public static final int BOTTLE_Y = 52;
    public static final int BOTTLE_STEP = 18;
    public static final int CHIP_X = 100;
    public static final int CHIP_Y = 52;
    public static final int PLAYER_INV_X = 29;
    public static final int PLAYER_INV_Y = 132;
    public static final int HOTBAR_Y = 190;

    private final Container container;
    private final ContainerData data;
    private final BlockPos pos;
    private final AgingBarrelBlockEntity cask;

    private AgingBarrelMenu(int id, Inventory playerInventory, Container container, ContainerData data,
                          BlockPos pos, AgingBarrelBlockEntity cask) {
        super(ModMenus.AGING_BARREL.get(), id);
        this.container = container;
        this.data = data;
        this.pos = pos;
        this.cask = cask;

        for (int slot = 0; slot < AgingBarrelBlockEntity.BOTTLE_SLOTS; slot++) {
            int column = slot % 3;
            int row = slot / 3;
            addSlot(new Slot(container, slot,
                    BOTTLE_X + column * BOTTLE_STEP, BOTTLE_Y + row * BOTTLE_STEP) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return AgingBarrelBlockEntity.isBottle(stack);
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }
            });
        }
        addSlot(new Slot(container, AgingBarrelBlockEntity.CHIP_SLOT, CHIP_X, CHIP_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(ModItems.OAK_CHIPS.get());
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

    public static AgingBarrelMenu server(int id, Inventory playerInventory, AgingBarrelBlockEntity cask) {
        return new AgingBarrelMenu(id, playerInventory, cask, cask.data(), cask.getBlockPos(), cask);
    }

    public static AgingBarrelMenu client(int id, Inventory playerInventory, BlockPos pos) {
        return new AgingBarrelMenu(id, playerInventory,
                new SimpleContainer(AgingBarrelBlockEntity.SLOT_COUNT),
                new SimpleContainerData(AgingBarrelBlockEntity.DATA_COUNT), pos, null);
    }

    public BlockPos pos() {
        return pos;
    }

    public int data(int index) {
        return data.get(index);
    }

    public int bottles() {
        return data.get(AgingBarrelBlockEntity.DATA_BOTTLES);
    }

    public int chips() {
        return data.get(AgingBarrelBlockEntity.DATA_CHIPS);
    }

    public int progress() {
        return data.get(AgingBarrelBlockEntity.DATA_PROGRESS);
    }

    public int ready() {
        return data.get(AgingBarrelBlockEntity.DATA_READY);
    }

    @Override
    public boolean stillValid(Player player) {
        if (cask != null && cask.isRemoved()) return false;
        Level level = player.level();
        return level.getBlockState(pos).getBlock() instanceof AgingBarrelBlock
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;

        ItemStack stack = slot.getItem();
        ItemStack result = stack.copy();
        int machineSlots = AgingBarrelBlockEntity.SLOT_COUNT;
        if (index < machineSlots) {
            if (!moveItemStackTo(stack, machineSlots, slots.size(), true)) return ItemStack.EMPTY;
            slot.setChanged();
        } else {
            int targetStart = -1;
            int targetEnd = -1;
            if (AgingBarrelBlockEntity.isBottle(stack)) {
                targetStart = 0;
                targetEnd = AgingBarrelBlockEntity.BOTTLE_SLOTS;
            } else if (stack.is(ModItems.OAK_CHIPS.get())) {
                targetStart = AgingBarrelBlockEntity.CHIP_SLOT;
                targetEnd = AgingBarrelBlockEntity.CHIP_SLOT + 1;
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
}
