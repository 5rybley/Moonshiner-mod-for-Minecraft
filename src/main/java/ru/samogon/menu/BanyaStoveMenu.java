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
import ru.samogon.block.BanyaStoveBlockEntity;
import ru.samogon.registry.ModBlocks;
import ru.samogon.registry.ModMenus;

/**
 * The banya stove interface: one coal slot in the firebox, the lever that sets
 * the power, and the player inventory. The stove heats the whole closed room,
 * so the layout is deliberately small, like a furnace.
 */
public final class BanyaStoveMenu extends AbstractContainerMenu {
    public static final int FUEL_X = 28;
    public static final int FUEL_Y = 68;
    public static final int PLAYER_INV_X = 9;
    public static final int PLAYER_INV_Y = 130;
    public static final int HOTBAR_Y = 184;

    /** Menu button ids above this offset carry the lever position. */
    public static final int POWER_BUTTON_BASE = 100;

    private final Container container;
    private final ContainerData data;
    private final BlockPos pos;
    private final BanyaStoveBlockEntity stove;

    private BanyaStoveMenu(int id, Inventory playerInventory, Container container, ContainerData data,
                           BlockPos pos, BanyaStoveBlockEntity stove) {
        super(ModMenus.BANYA_STOVE.get(), id);
        this.container = container;
        this.data = data;
        this.pos = pos;
        this.stove = stove;

        addSlot(new Slot(container, BanyaStoveBlockEntity.SLOT_FUEL, FUEL_X, FUEL_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return BanyaStoveBlockEntity.isFuel(stack);
            }
        });

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(playerInventory, column + row * 9 + 9,
                        PLAYER_INV_X + column * 18, PLAYER_INV_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column,
                    PLAYER_INV_X + column * 18, HOTBAR_Y));
        }
        addDataSlots(data);
    }

    public static BanyaStoveMenu server(int id, Inventory playerInventory, BanyaStoveBlockEntity stove) {
        return new BanyaStoveMenu(id, playerInventory, stove.inventory(), stove.data(),
                stove.getBlockPos(), stove);
    }

    public static BanyaStoveMenu client(int id, Inventory playerInventory, BlockPos pos) {
        return new BanyaStoveMenu(id, playerInventory,
                new SimpleContainer(BanyaStoveBlockEntity.SLOT_COUNT),
                new SimpleContainerData(BanyaStoveBlockEntity.DATA_COUNT), pos, null);
    }

    public int data(int index) {
        return data.get(index);
    }

    public int power() {
        return data(BanyaStoveBlockEntity.DATA_POWER);
    }

    public int temperatureTenths() {
        return data(BanyaStoveBlockEntity.DATA_TEMPERATURE);
    }

    public int targetTenths() {
        return data(BanyaStoveBlockEntity.DATA_TARGET);
    }

    public int roomVolume() {
        return data(BanyaStoveBlockEntity.DATA_ROOM_VOLUME);
    }

    public boolean roomValid() {
        return data(BanyaStoveBlockEntity.DATA_ROOM_VALID) == 1;
    }

    @Override
    public boolean stillValid(Player player) {
        if (stove != null && stove.isRemoved()) {
            return false;
        }
        Level level = player.level();
        return level.getBlockState(pos).is(ModBlocks.BANYA_STOVE.get())
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) {
            return ItemStack.EMPTY;
        }
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack result = stack.copy();
        if (index == BanyaStoveBlockEntity.SLOT_FUEL) {
            if (!moveItemStackTo(stack, 1, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (BanyaStoveBlockEntity.isFuel(stack)) {
            if (!moveItemStackTo(stack, BanyaStoveBlockEntity.SLOT_FUEL,
                    BanyaStoveBlockEntity.SLOT_FUEL + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == result.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return result;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (stove == null || id < POWER_BUTTON_BASE || id > POWER_BUTTON_BASE + BanyaStoveBlockEntity.MAX_POWER) {
            return false;
        }
        stove.setPower(id - POWER_BUTTON_BASE);
        return true;
    }
}