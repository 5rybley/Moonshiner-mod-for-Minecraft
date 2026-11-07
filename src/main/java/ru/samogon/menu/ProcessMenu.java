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
import ru.samogon.block.ProcessBlock;
import ru.samogon.block.ProcessBlockEntity;
import ru.samogon.item.BatchData;
import ru.samogon.item.BatchItem;
import ru.samogon.process.BlendCatalog;
import ru.samogon.process.MachineData;
import ru.samogon.process.Simulation;
import ru.samogon.process.Simulation.Batch;
import ru.samogon.process.Simulation.Fraction;
import ru.samogon.registry.ModItems;
import ru.samogon.registry.ModMenus;

/**
 * One container menu shared by all eight processing blocks. The machine slots
 * are input, up to five blend additives and output; other machines expose only
 * the first additive slot. The kind changes the labels, the icons in the status
 * header and which items are accepted. All physics still happens
 * in {@link ProcessBlockEntity}; this menu only exposes and synchronizes it.
 */
public final class ProcessMenu extends AbstractContainerMenu {
    public static final int SLOT_INPUT = ProcessBlockEntity.SLOT_INPUT;
    public static final int SLOT_ADDITIVE = ProcessBlockEntity.SLOT_ADDITIVE;
    public static final int SLOT_ADDITIVE_LAST = ProcessBlockEntity.SLOT_ADDITIVE_LAST;
    public static final int SLOT_OUTPUT = ProcessBlockEntity.SLOT_OUTPUT;

    public static final int INPUT_X = 80;
    public static final int INPUT_Y = 124;
    public static final int ADDITIVE_X = 181;
    public static final int ADDITIVE_Y = 124;
    public static final int OUTPUT_X = 282;
    public static final int OUTPUT_Y = 124;

    /** The blending table uses a five-slot tray and moves the outer slots apart. */
    public static final int BLEND_INPUT_X = 54;
    public static final int BLEND_OUTPUT_X = 306;
    public static final int BLEND_ADDITIVE_X = 132;
    public static final int BLEND_ADDITIVE_STEP = 22;

    public static final int PLAYER_INV_X = 109;
    public static final int PLAYER_INV_Y = 178;
    public static final int HOTBAR_Y = 236;

    public static int inputX(ProcessBlock.Kind kind) {
        return kind == ProcessBlock.Kind.BLENDING ? BLEND_INPUT_X : INPUT_X;
    }

    public static int outputX(ProcessBlock.Kind kind) {
        return kind == ProcessBlock.Kind.BLENDING ? BLEND_OUTPUT_X : OUTPUT_X;
    }

    public static int additiveX(ProcessBlock.Kind kind, int index) {
        if (kind != ProcessBlock.Kind.BLENDING && index > 0) return -1000;
        return kind == ProcessBlock.Kind.BLENDING
                ? BLEND_ADDITIVE_X + index * BLEND_ADDITIVE_STEP
                : ADDITIVE_X;
    }

    private final Container container;
    private final ContainerData data;
    private final ProcessBlock.Kind kind;
    private final BlockPos pos;
    private final ProcessBlockEntity machine;

    private ProcessMenu(int id, Inventory playerInventory, Container container, ContainerData data,
                        ProcessBlock.Kind kind, BlockPos pos, ProcessBlockEntity machine) {
        super(ModMenus.PROCESS.get(), id);
        this.container = container;
        this.data = data;
        this.kind = kind;
        this.pos = pos;
        this.machine = machine;

        addSlot(new Slot(container, SLOT_INPUT, inputX(kind), INPUT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return acceptsInput(kind, stack);
            }
        });
        for (int i = 0; i < ProcessBlockEntity.SLOT_ADDITIVE_COUNT; i++) {
            int slotIndex = SLOT_ADDITIVE + i;
            int slotX = additiveX(kind, i);
            addSlot(new Slot(container, slotIndex, slotX, ADDITIVE_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return (kind == ProcessBlock.Kind.BLENDING || slotIndex == SLOT_ADDITIVE)
                            && acceptsAdditive(kind, stack);
                }

                @Override
                public int getMaxStackSize() {
                    // Each tray slot represents one deliberate ingredient.
                    return kind == ProcessBlock.Kind.BLENDING ? 1 : super.getMaxStackSize();
                }
            });
        }
        addSlot(new Slot(container, SLOT_OUTPUT, outputX(kind), OUTPUT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return kind != ProcessBlock.Kind.TASTING && isOutputContainer(stack);
            }

            @Override
            public boolean isActive() {
                // The tasting table has no product: the sample stays in the
                // input slot and nothing is ever bottled.
                return kind != ProcessBlock.Kind.TASTING;
            }

            @Override
            public int getMaxStackSize() {
                // One bottle at a time keeps the automatic bottling simple and
                // prevents an entire stack disappearing into one result slot.
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

    public static ProcessMenu server(int id, Inventory playerInventory, ProcessBlockEntity machine) {
        return new ProcessMenu(id, playerInventory, machine.inventory(), machine.data(),
                machine.kind(), machine.getBlockPos(), machine);
    }

    public static ProcessMenu client(int id, Inventory playerInventory, BlockPos pos, ProcessBlock.Kind kind) {
        return new ProcessMenu(id, playerInventory, new SimpleContainer(ProcessBlockEntity.SLOT_COUNT),
                new SimpleContainerData(MachineData.COUNT), kind, pos, null);
    }

    public ProcessBlock.Kind kind() {
        return kind;
    }

    public BlockPos pos() {
        return pos;
    }

    public int data(int index) {
        return data.get(index);
    }

    public Fraction receiver() {
        int ordinal = Math.max(0, Math.min(Fraction.values().length - 1,
                data.get(MachineData.RECEIVER)));
        return Fraction.values()[ordinal];
    }

    public int agingKind() {
        return data.get(MachineData.AGING_KIND);
    }

    public int additiveCount() {
        return data.get(MachineData.BLEND_ADDITIVES);
    }

    public static boolean acceptsInput(ProcessBlock.Kind kind, ItemStack stack) {
        if (kind == ProcessBlock.Kind.MASHING) {
            return stack.is(ModItems.MALT.get())
                    || stack.is(Items.WHEAT)
                    || stack.is(Items.SUGAR)
                    || stack.is(Items.SWEET_BERRIES)
                    || stack.is(Items.HONEY_BOTTLE)
                    || stack.is(Items.POTATO)
                    || stack.is(Items.BEETROOT)
                    || stack.is(Items.SUGAR_CANE)
                    || stack.is(Items.APPLE);
        }
        if (!(stack.getItem() instanceof BatchItem)) return false;
        try {
            Batch batch = BatchData.read(stack);
            if (batch == null) return false;
            return switch (kind) {
                case FERMENTATION -> batch.abv() <= 0.5;
                case DISTILLER -> batch.abv() >= 1 && batch.volume() >= 100;
                case RECTIFIER -> batch.abv() >= 1 && batch.volume() >= 10;
                case FILTER -> batch.abv() >= 1;
                case BLENDING -> true;
                case DILUTION -> batch.abv() > 40 && batch.alcohol() * 100 / 40.0 <= 4000.0 + 1.0E-9;
                case AGING -> Simulation.canAge(batch.abv());
                case TASTING -> true;
                case MASHING -> false;
            };
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public static boolean acceptsAdditive(ProcessBlock.Kind kind, ItemStack stack) {
        return switch (kind) {
            case MASHING -> false;
            case FERMENTATION -> stack.is(ModItems.YEAST.get());
            case DISTILLER -> stack.is(Items.WATER_BUCKET);
            case RECTIFIER -> stack.is(ModItems.MOLECULAR_SIEVE.get());
            case FILTER -> stack.is(ModItems.ACTIVATED_CHARCOAL.get());
            case BLENDING -> stack.is(Items.WATER_BUCKET) || BlendCatalog.find(stack) != null;
            case DILUTION -> stack.is(Items.WATER_BUCKET);
            case AGING -> stack.is(ModItems.OAK_CHIPS.get());
            case TASTING -> stack.is(ModItems.HYDROMETER.get());
        };
    }

    public static boolean isOutputContainer(ItemStack stack) {
        return stack.is(Items.GLASS_BOTTLE) || stack.is(Items.BUCKET);
    }

    @Override
    public boolean stillValid(Player player) {
        if (machine != null && machine.isRemoved()) return false;
        Level level = player.level();
        return level.getBlockState(pos).getBlock() instanceof ProcessBlock block
                && block.kind() == kind
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;

        ItemStack stack = slot.getItem();
        ItemStack result = stack.copy();
        if (index < SLOT_OUTPUT + 1) {
            if (!moveItemStackTo(stack, SLOT_OUTPUT + 1, slots.size(), true)) return ItemStack.EMPTY;
            slot.setChanged();
        } else {
            int targetStart = -1;
            int targetEnd = -1;
            if (acceptsInput(kind, stack)) {
                targetStart = SLOT_INPUT;
                targetEnd = SLOT_INPUT + 1;
            } else if (acceptsAdditive(kind, stack)) {
                targetStart = SLOT_ADDITIVE;
                targetEnd = kind == ProcessBlock.Kind.BLENDING ? SLOT_ADDITIVE_LAST + 1 : SLOT_ADDITIVE + 1;
            } else if (isOutputContainer(stack)) {
                if (kind == ProcessBlock.Kind.TASTING) return ItemStack.EMPTY;
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
        // A partial move must not report the whole stack as moved: with tray
        // slots limited to one item this otherwise leaves a client-side ghost.
        if (stack.getCount() == result.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return result;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (machine == null) return false;
        if (kind == ProcessBlock.Kind.BLENDING && id == 2) {
            return machine.startBlending(player);
        }
        if (kind == ProcessBlock.Kind.RECTIFIER && id == 4) {
            return machine.cycleColumnRuns();
        }
        if (kind != ProcessBlock.Kind.DISTILLER) return false;
        return switch (id) {
            case 0 -> machine.cycleReceiver();
            case 1 -> machine.clearStill();
            case 3 -> machine.toggleFractional();
            default -> false;
        };
    }
}
