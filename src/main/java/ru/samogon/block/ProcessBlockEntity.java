package ru.samogon.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import ru.samogon.item.BatchData;
import ru.samogon.item.BatchItem;
import ru.samogon.menu.ProcessMenu;
import ru.samogon.process.BatchNbt;
import ru.samogon.process.BlendCatalog;
import ru.samogon.process.Environment;
import ru.samogon.process.MachineData;
import ru.samogon.process.Simulation;
import ru.samogon.process.Simulation.Batch;
import ru.samogon.process.Simulation.Fraction;
import ru.samogon.process.StillNbt;
import ru.samogon.process.StillRun;
import ru.samogon.registry.ModBlocks;
import ru.samogon.registry.ModItems;
import ru.samogon.registry.ModSounds;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Shared block entity for every processing machine. The state machine is
 * deliberately small; the interesting physics lives in Simulation and StillRun.
 *
 * Stage conventions:
 * 0 = empty, 1 = loaded, 2 = waiting for an ingredient, 3 = processing,
 * 4 = finished product ready to be bottled.
 */
public final class ProcessBlockEntity extends BlockEntity {
    private static final int MASH_TICKS = 400;
    private static final int FERMENT_TICKS = 6000;
    private static final int RECTIFY_TICKS = 1200;
    private static final int FILTER_TICKS = 600;
    private static final int BLEND_TICKS = 200;
    private static final int DILUTION_TICKS = 200;
    private static final int AGE_TICKS = 6000;
    private static final int INFUSE_TICKS = 3000;
    public static final int COOLANT_PER_BUCKET = 6000;
    public static final int MAX_COOLANT = 18000;
    private static final int BOTTLE = StillRun.BOTTLE_VOLUME;
    private static final int JAR = 1000;

    public static final int SLOT_INPUT = 0;
    public static final int SLOT_ADDITIVE = 1;
    public static final int SLOT_ADDITIVE_COUNT = BlendCatalog.MAX_ADDITIVES;
    public static final int SLOT_ADDITIVE_LAST = SLOT_ADDITIVE + SLOT_ADDITIVE_COUNT - 1;
    public static final int SLOT_OUTPUT = SLOT_ADDITIVE_LAST + 1;
    public static final int SLOT_COUNT = SLOT_OUTPUT + 1;

    /** Old machines used input / additive / output at slots 0 / 1 / 2. */
    private static final int INVENTORY_LAYOUT = 2;

    /**
     * The first five slots after the batch/raw input are ingredients for the
     * blending table. Every other machine only uses the first additive slot.
     * The bottle/container result lives after all five additive slots. Keeping
     * the inventory in the block entity means the menu can use ordinary slot
     * syncing and the contents survive a world reload.
     */
    private final SimpleContainer inventory = new SimpleContainer(SLOT_COUNT) {
        @Override
        public void setChanged() {
            super.setChanged();
            ProcessBlockEntity.this.setChanged();
        }
    };

    private final ContainerData machineData = new ContainerData() {
        @Override
        public int get(int index) {
            Batch b = batch;
            StillRun s = still;
            return switch (index) {
                case MachineData.STAGE -> stage;
                case MachineData.PROGRESS -> progressPercent();
                case MachineData.DURATION -> duration;
                case MachineData.ACTIVE -> active ? 1 : 0;
                case MachineData.READY -> isReady() ? 1 : 0;
                case MachineData.TEMPERATURE -> (int) Math.round(temperature * 10.0);
                case MachineData.AMBIENT -> (int) Math.round(ambient * 10.0);
                case MachineData.HEAT -> (int) Math.round(Environment.heatStrength(level, worldPosition.below()) * 100);
                case MachineData.COOLANT_MINUTES -> (int) Math.round(coolant / 360.0);
                case MachineData.BATCH_VOLUME -> b == null ? 0 : (int) Math.round(b.volume());
                case MachineData.BATCH_ABV -> b == null ? 0 : (int) Math.round(b.abv() * 10.0);
                case MachineData.BATCH_QUALITY -> b == null ? 0 : b.quality();
                case MachineData.BATCH_IMPURITIES -> b == null ? 0 : b.impurities();
                case MachineData.BATCH_AGE -> b == null ? 0 : b.age();
                case MachineData.BATCH_FILTERED -> b != null && b.filtered() ? 1 : 0;
                case MachineData.STILL_PRESENT -> s == null ? 0 : 1;
                case MachineData.STILL_TEMPERATURE -> s == null ? 0 : (int) Math.round(s.temperature() * 10.0);
                case MachineData.STILL_ABV -> s == null ? 0 : (int) Math.round(s.abv() * 10.0);
                case MachineData.STILL_PRESSURE -> s == null ? 0 : (int) Math.round(s.pressure() * 100.0);
                case MachineData.HEADS_VOLUME -> s == null ? 0 : (int) Math.round(s.cutVolume(Fraction.HEADS));
                case MachineData.HEARTS_VOLUME -> s == null ? 0 : (int) Math.round(s.cutVolume(Fraction.HEARTS));
                case MachineData.TAILS_VOLUME -> s == null ? 0 : (int) Math.round(s.cutVolume(Fraction.TAILS));
                case MachineData.STILL_FINISHED -> s != null && s.finished() ? 1 : 0;
                case MachineData.RECEIVER -> receiver.ordinal();
                case MachineData.SIEVE -> sieve ? 1 : 0;
                case MachineData.AGING_KIND -> agingKindId();
                case MachineData.STILL_CLEARABLE ->
                        s != null && s.finished() && totalCuts() <= 0.0001 ? 1 : 0;
                case MachineData.BLEND_ADDITIVES -> additiveCount();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            // The client only mirrors these values; all mutations go through
            // the machine's server-side methods.
        }

        @Override
        public int getCount() {
            return MachineData.COUNT;
        }
    };

    private int stage;
    private int progress;
    private int duration;
    private boolean active;
    private int coolant;
    private int tickCounter;
    private int settle;
    private double temperature = 20;
    private double ambient = 20;
    private double deviationSum;
    private int deviationSamples;
    private double ambientSum;
    private int ambientSamples;
    private boolean sieve;
    private boolean announcedDone;
    private Batch batch;
    private StillRun still;
    private String agingKind = "";
    private String blendComposition = "";
    private Fraction receiver = Fraction.HEARTS;

    public ProcessBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.PROCESS_BLOCK_ENTITY.get(), pos, state);
    }

    public ProcessBlock.Kind kind() {
        return ((ProcessBlock) getBlockState().getBlock()).kind();
    }

    public Container inventory() {
        return inventory;
    }

    public ContainerData data() {
        return machineData;
    }

    public Fraction receiver() {
        return receiver;
    }

    public void openMachine(Player player) {
        if (!(player instanceof ServerPlayer server)) return;
        server.openMenu(new SimpleMenuProvider(
                        (id, playerInventory, ignored) -> ProcessMenu.server(id, playerInventory, this),
                        getBlockState().getBlock().getName()),
                buffer -> {
                    buffer.writeBlockPos(worldPosition);
                    buffer.writeByte(kind().ordinal());
                });
    }

    /** Called when the block is broken so GUI contents are not silently lost. */
    public void dropContents() {
        if (level == null) return;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()) {
                Block.popResource(level, worldPosition, stack.copy());
                inventory.setItem(i, ItemStack.EMPTY);
            }
        }
        dropProcess();
    }

    /**
     * Bottles the liquid still inside the machine before it is removed, so
     * breaking a machine mid-process does not silently destroy a wash, a still
     * charge or a batch that was waiting to be bottled.
     */
    private void dropProcess() {
        if (still != null) {
            for (Fraction fraction : Fraction.values()) {
                for (int guard = 0; guard < 64 && still.cutVolume(fraction) > 0.0001; guard++) {
                    double available = still.cutVolume(fraction);
                    int amount = (int) Math.min(BOTTLE, Math.ceil(available));
                    Batch portion = still.drain(fraction, amount);
                    if (portion == null) break;
                    Block.popResource(level, worldPosition, BatchData.create(cutItem(fraction), portion));
                }
            }
            still = null;
        }
        if (batch != null) {
            Item output = inProcessItem();
            double portionSize = output instanceof BatchItem item && item.defaultVolume() > 0
                    ? item.defaultVolume() : BOTTLE;
            int guard = 0;
            while (batch != null && guard++ < 64) {
                Batch portion = takePortion(portionSize);
                Block.popResource(level, worldPosition, BatchData.create(output, portion));
            }
        }
        stage = 0;
        progress = 0;
        duration = 0;
        active = false;
        sieve = false;
        agingKind = "";
        blendComposition = "";
    }

    /** Bottle item that best matches the batch currently inside this machine. */
    private Item inProcessItem() {
        return switch (kind()) {
            case MASHING -> ModItems.SWEET_WORT.get();
            case FERMENTATION -> stage <= 1 ? ModItems.SWEET_WORT.get() : ModItems.FERMENTED_MASH.get();
            case DISTILLER -> ModItems.HEARTS_FRACTION.get();
            case RECTIFIER -> batch != null && batch.abv() >= 99
                    ? ModItems.ANHYDROUS_SPIRIT.get() : ModItems.NEUTRAL_SPIRIT.get();
            case FILTER -> ModItems.FILTERED_SPIRIT.get();
            case BLENDING -> ModItems.SAMOGON.get();
            case DILUTION -> ModItems.DILUTED_SPIRIT.get();
            case AGING -> ModItems.OAK_AGED_SPIRIT.get();
        };
    }

    public boolean cycleReceiver() {
        if (kind() != ProcessBlock.Kind.DISTILLER) return false;
        receiver = receiver == Fraction.HEADS ? Fraction.HEARTS
                : receiver == Fraction.HEARTS ? Fraction.TAILS : Fraction.HEADS;
        setChanged();
        return true;
    }

    public boolean clearStill() {
        if (kind() != ProcessBlock.Kind.DISTILLER || still == null
                || !still.finished() || totalCuts() > 0.0001) {
            return false;
        }
        still = null;
        stage = 0;
        active = false;
        progress = 0;
        duration = 0;
        coolant = 0;
        announcedDone = false;
        setChanged();
        return true;
    }

    private int agingKindId() {
        return switch (agingKind) {
            case "dilute" -> 1;
            case "berry" -> 2;
            case "honey" -> 3;
            case "botanical" -> 4;
            case "blend" -> 5;
            default -> 0;
        };
    }

    /** Number of occupied ingredient slots in the five-slot blending tray. */
    public int additiveCount() {
        int count = 0;
        for (int i = SLOT_ADDITIVE; i <= SLOT_ADDITIVE_LAST; i++) {
            if (!inventory.getItem(i).isEmpty()) count++;
        }
        return count;
    }

    private int firstEmptyAdditiveSlot() {
        for (int i = SLOT_ADDITIVE; i <= SLOT_ADDITIVE_LAST; i++) {
            if (inventory.getItem(i).isEmpty()) return i;
        }
        return -1;
    }

    private static boolean isBlendAdditive(ItemStack stack) {
        return stack.is(Items.WATER_BUCKET) || BlendCatalog.find(stack) != null;
    }

    /**
     * Starts a blend from the GUI button or from Shift+right-click with an
     * empty hand. Water alone keeps the old dilution-in-the-blending-table
     * behaviour; water mixed with aromatic ingredients is rejected so the
     * five-slot recipe cannot be accidentally diluted.
     */
    public boolean startBlending(Player player) {
        if (kind() != ProcessBlock.Kind.BLENDING || stage != 1 || batch == null) {
            if (player != null) {
                player.displayClientMessage(Component.translatable("message.samogon.blend.not_ready"), true);
            }
            return false;
        }

        boolean hasWater = false;
        int waterSlots = 0;
        List<BlendCatalog.Additive> additives = new ArrayList<>();
        List<Integer> filledSlots = new ArrayList<>();
        for (int i = SLOT_ADDITIVE; i <= SLOT_ADDITIVE_LAST; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) continue;
            filledSlots.add(i);
            if (stack.is(Items.WATER_BUCKET)) {
                hasWater = true;
                waterSlots++;
                continue;
            }
            BlendCatalog.Additive additive = BlendCatalog.find(stack);
            if (additive == null) {
                if (player != null) {
                    player.displayClientMessage(Component.translatable("message.samogon.blend.invalid",
                            stack.getHoverName()), true);
                }
                return false;
            }
            additives.add(additive);
        }

        if (hasWater && (additives.size() > 0 || waterSlots > 1)) {

            if (player != null) {
                player.displayClientMessage(Component.translatable("message.samogon.blend.water_only"), true);
            }
            return false;
        }
        if (additives.isEmpty() && !hasWater) {
            if (player != null) {
                player.displayClientMessage(Component.translatable("message.samogon.blend.no_additives"), true);
            }
            return false;
        }

        if (hasWater) {
            // A single bucket is the old dilution operation. It is deliberately
            // not part of a five-ingredient maceration.
            if (batch.abv() <= 40) {
                if (player != null) {
                    player.displayClientMessage(Component.translatable("message.samogon.dilute_needed"), true);
                }
                return false;
            }
            if (!fitsDilutionBasin(batch)) {
                if (player != null) {
                    player.displayClientMessage(Component.translatable("message.samogon.dilution_failed"), true);
                }
                return false;
            }
            for (int slot : filledSlots) {
                consumeSlot(slot);
                returnContainer(Items.BUCKET);
            }
            agingKind = "dilute";
            blendComposition = "";
            stage = 3;
            progress = 0;
            duration = BLEND_TICKS;
            startBlendMessage(player);
            setChanged();
            return true;
        }

        if (batch.abv() < 20 || batch.abv() > 60) {
            if (player != null) {
                player.displayClientMessage(Component.translatable("message.samogon.strength_range"), true);
            }
            return false;
        }

        blendComposition = BlendCatalog.encode(additives);
        for (int slot : filledSlots) {
            ItemStack stack = inventory.getItem(slot);
            Item container = stack.is(Items.HONEY_BOTTLE) ? Items.GLASS_BOTTLE
                    : stack.is(Items.WATER_BUCKET) ? Items.BUCKET : null;
            consumeSlot(slot);
            returnContainer(container);
        }
        agingKind = "blend";
        stage = 3;
        progress = 0;
        duration = INFUSE_TICKS;
        startBlendMessage(player);
        setChanged();
        return true;
    }

    private void startBlendMessage(Player player) {
        Component message = Component.translatable("message.samogon.blend.started");
        if (player != null) player.displayClientMessage(message, false);
        notifyNearby("message.samogon.blend.started");
    }

    /** Adds one manually held additive to the first free tray slot. */
    private boolean addBlendAdditive(Player player, ItemStack stack) {
        if (!isBlendAdditive(stack)) return false;
        int slot = firstEmptyAdditiveSlot();
        if (slot < 0) {
            player.displayClientMessage(Component.translatable("message.samogon.blend.full"), true);
            return true;
        }
        Component additiveName = stack.getHoverName();
        inventory.setItem(slot, stack.copyWithCount(1));
        // Keep the container tied to the stored tray item. startBlending
        // returns the bottle/bucket when it consumes the tray, so returning it
        // here as well would duplicate it.
        consume(player, stack, null);
        player.displayClientMessage(Component.translatable("message.samogon.blend.added",
                additiveName, slot - SLOT_ADDITIVE + 1), true);
        setChanged();
        return true;
    }

    public void tick() {
        if (level == null || level.isClientSide) return;
        tickCounter++;
        if (tickCounter % 20 == 0) ambient = Environment.temperature(level, worldPosition);
        processAutomation();
        switch (kind()) {
            case MASHING -> tickMashing();
            case FERMENTATION -> tickFermentation();
            case DISTILLER -> tickDistiller();
            case RECTIFIER -> tickRectifier();
            case FILTER -> tickFilter();
            case BLENDING -> tickBlending();
            case DILUTION -> tickDilution();
            case AGING -> tickAging();
        }
        updateTemperature();
        updateVisualState();
        emitEffects();
        if (tickCounter % 100 == 0) setChanged();
    }

    // === CONTAINER AUTOMATION ===

    /**
     * The GUI is deliberately just another way to use the machine. Every
     * operation which used to require a right-click is performed from the
     * machine slots here, on the server, without consuming the player's held
     * item. Hand interactions remain available for players who prefer them.
     */
    private void processAutomation() {
        if (kind() == ProcessBlock.Kind.DISTILLER) {
            fillCoolantFromSlot();
            if (coolant <= 0) pullCoolantFromCondensers(COOLANT_PER_BUCKET);
        }
        if (stage == 0) {
            ItemStack input = inventory.getItem(SLOT_INPUT);
            if (!input.isEmpty() && loadInput(input)) {
                Item container = inputContainer(input);
                consumeSlot(SLOT_INPUT);
                returnContainer(container);
            }
        }
        if (stage == 1) tryAdditive();
        if (isReady()) bottleResult();
    }

    private void fillCoolantFromSlot() {
        ItemStack additive = inventory.getItem(SLOT_ADDITIVE);
        if (!additive.is(Items.WATER_BUCKET) || coolant >= MAX_COOLANT) return;
        consumeSlot(SLOT_ADDITIVE);
        coolant = Math.min(MAX_COOLANT, coolant + COOLANT_PER_BUCKET);
        returnContainer(Items.BUCKET);
    }

    /**
     * Draws coolant from any condenser block touching this still. Only as much
     * as asked for is taken so several stills can share one reservoir. Returns
     * true when at least some water was moved.
     */
    private boolean pullCoolantFromCondensers(int maxAmount) {
        if (level == null || coolant >= MAX_COOLANT) return false;
        int want = Math.min(maxAmount, MAX_COOLANT - coolant);
        boolean pulled = false;
        for (Direction side : Direction.values()) {
            if (want <= 0) break;
            if (level.getBlockEntity(worldPosition.relative(side)) instanceof CondenserBlockEntity condenser) {
                int drawn = condenser.drain(want);
                if (drawn > 0) {
                    coolant += drawn;
                    want -= drawn;
                    pulled = true;
                }
            }
        }
        return pulled;
    }

    private boolean loadInput(ItemStack stack) {
        switch (kind()) {
            case MASHING -> {
                String origin = null;
                int bonus = 0;
                if (stack.is(ModItems.MALT.get())) {
                    origin = "grain";
                    bonus = 4;
                } else if (stack.is(Items.WHEAT)) {
                    origin = "grain";
                } else if (stack.is(Items.SUGAR)) {
                    origin = "sugar";
                    bonus = -4;
                } else if (stack.is(Items.SWEET_BERRIES)) {
                    origin = "berry";
                    bonus = 2;
                } else if (stack.is(Items.HONEY_BOTTLE)) {
                    origin = "honey";
                    bonus = 6;
                }
                if (origin == null) return false;
                batch = Simulation.mash(origin, bonus);
                stage = 3;
                progress = 0;
                duration = MASH_TICKS;
                settle = 0;
                active = false;
                return true;
            }
            case FERMENTATION -> {
                Batch wort = readBatch(stack);
                if (wort == null || wort.abv() > 0.5) return false;
                batch = wort;
                stage = 1;
                progress = 0;
                duration = 0;
                settle = 0;
                sieve = false;
                agingKind = "";
                active = false;
                return true;
            }
            case DISTILLER -> {
                Batch wash = readBatch(stack);
                if (still != null || wash == null || wash.abv() < 1 || wash.volume() < 100) return false;
                batch = null;
                stage = 3;
                progress = 0;
                duration = 0;
                settle = 0;
                announcedDone = false;
                receiver = Fraction.HEARTS;
                still = new StillRun(wash);
                active = false;
                return true;
            }
            case RECTIFIER -> {
                Batch feed = readBatch(stack);
                if (feed == null || feed.abv() < 1 || feed.volume() < 10) return false;
                batch = feed;
                stage = 1;
                progress = 0;
                duration = 0;
                settle = 0;
                sieve = false;
                active = false;
                return true;
            }
            case FILTER -> {
                Batch feed = readBatch(stack);
                if (feed == null || feed.abv() < 1 || feed.filtered()) return false;
                batch = feed;
                stage = 1;
                progress = 0;
                duration = 0;
                active = false;
                return true;
            }
            case BLENDING -> {
                Batch feed = readBatch(stack);
                if (feed == null || feed.abv() < 20 || feed.abv() > 60) return false;
                batch = feed;
                stage = 1;
                progress = 0;
                duration = 0;
                agingKind = "";
                blendComposition = "";
                active = false;
                return true;
            }
            case DILUTION -> {
                Batch feed = readBatch(stack);
                if (feed == null || feed.abv() <= 40 || !fitsDilutionBasin(feed)) return false;
                batch = feed;
                stage = 1;
                progress = 0;
                duration = 0;
                agingKind = "";
                active = false;
                return true;
            }
            case AGING -> {
                Batch feed = readBatch(stack);
                if (feed == null || feed.abv() < 20 || feed.abv() > 60) return false;
                batch = feed;
                stage = 1;
                progress = 0;
                duration = 0;
                active = false;
                return true;
            }
        }
        return false;
    }

    private void tryAdditive() {
        ItemStack additive = inventory.getItem(SLOT_ADDITIVE);
        if (additive.isEmpty()) return;
        Item container = null;
        switch (kind()) {
            case FERMENTATION -> {
                if (batch == null || !additive.is(ModItems.YEAST.get())) return;
                consumeSlot(SLOT_ADDITIVE);
                stage = 2;
                progress = 0;
                duration = FERMENT_TICKS;
                deviationSum = 0;
                deviationSamples = 0;
                ambientSum = 0;
                ambientSamples = 0;
            }
            case RECTIFIER -> {
                // One sieve is enough. Without the sieve guard a stack left in
                // the additive slot was eaten one item per tick while the
                // column was heating up.
                if (sieve || batch == null || !additive.is(ModItems.MOLECULAR_SIEVE.get())) return;
                consumeSlot(SLOT_ADDITIVE);
                sieve = true;
            }
            case FILTER -> {
                if (batch == null || !additive.is(ModItems.ACTIVATED_CHARCOAL.get())) return;
                consumeSlot(SLOT_ADDITIVE);
                stage = 3;
                progress = 0;
                duration = FILTER_TICKS;
            }
            case BLENDING -> {
                // The five-slot tray is filled deliberately. Automation must
                // never start after the first ingredient, or the remaining
                // slots could not be used. Starting is handled by the GUI
                // button, Shift+empty-hand, or ProcessBlockEntity#startBlending.
                return;
            }
            case DILUTION -> {
                if (batch == null || !additive.is(Items.WATER_BUCKET)) return;
                consumeSlot(SLOT_ADDITIVE);
                container = Items.BUCKET;
                stage = 3;
                progress = 0;
                duration = DILUTION_TICKS;
            }
            case AGING -> {
                if (batch == null || !additive.is(ModItems.OAK_CHIPS.get())) return;
                consumeSlot(SLOT_ADDITIVE);
                stage = 3;
                progress = 0;
                duration = AGE_TICKS;
            }
            case MASHING, DISTILLER -> {
                return;
            }
        }
        returnContainer(container);
    }

    private static Item inputContainer(ItemStack stack) {
        // Both a honey bottle and any filled batch bottle leave an empty glass
        // bottle behind when their contents are poured into a machine.
        return stack.is(Items.HONEY_BOTTLE) || stack.getItem() instanceof ru.samogon.item.BatchItem
                ? Items.GLASS_BOTTLE : null;
    }

    private static Batch readBatch(ItemStack stack) {
        if (!(stack.getItem() instanceof BatchItem)) return null;
        try {
            return BatchData.read(stack);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private void consumeSlot(int slot) {
        ItemStack stack = inventory.getItem(slot);
        if (stack.isEmpty()) return;
        stack.shrink(1);
        inventory.setItem(slot, stack.isEmpty() ? ItemStack.EMPTY : stack);
    }

    /**
     * Empty buckets and bottles are useful byproducts. If the output slot is
     * already occupied (usually by the bottle that is waiting to be filled),
     * the container is dropped at the machine instead of vanishing.
     */
    private void returnContainer(Item container) {
        if (container == null) return;
        ItemStack returned = new ItemStack(container);
        // The output slot doubles as the machine's empty-bottle feeder, so a
        // glass bottle belongs there. An empty bucket would just block
        // bottling, so it is dropped at the machine instead.
        if (container == Items.GLASS_BOTTLE && inventory.getItem(SLOT_OUTPUT).isEmpty()) {
            inventory.setItem(SLOT_OUTPUT, returned);
        } else if (level != null) {
            Block.popResource(level, worldPosition, returned);
        }
    }

    private void bottleResult() {
        ItemStack output = inventory.getItem(SLOT_OUTPUT);
        if (!output.is(Items.GLASS_BOTTLE) || output.getCount() != 1) return;

        if (kind() == ProcessBlock.Kind.DISTILLER) {
            if (still == null || !still.finished()) return;
            Fraction chosen = receiver;
            double available = still.cutVolume(chosen);
            if (available <= 0.0001) return;
            int amount = (int) Math.min(BOTTLE, Math.ceil(available));
            Batch portion = still.drain(chosen, amount);
            if (portion == null) return;
            inventory.setItem(SLOT_OUTPUT, BatchData.create(cutItem(chosen), portion));
            playBottleSound();
            return;
        }

        if (!canBottleBatch()) return;
        Item item = outputItem();
        if (item == null) return;
        Batch portion = takePortion(kind() == ProcessBlock.Kind.MASHING
                || kind() == ProcessBlock.Kind.FERMENTATION ? JAR : BOTTLE);
        inventory.setItem(SLOT_OUTPUT, BatchData.create(item, portion));
        playBottleSound();
    }

    private boolean canBottleBatch() {
        return batch != null && (stage == 4
                || (kind() == ProcessBlock.Kind.FERMENTATION && stage == 3));
    }

    private Item outputItem() {
        return switch (kind()) {
            case MASHING -> ModItems.SWEET_WORT.get();
            case FERMENTATION -> ModItems.FERMENTED_MASH.get();
            case RECTIFIER -> batch != null && batch.abv() >= 99
                    ? ModItems.ANHYDROUS_SPIRIT.get() : ModItems.NEUTRAL_SPIRIT.get();
            case FILTER -> ModItems.FILTERED_SPIRIT.get();
            case BLENDING -> blendOutput();
            case DILUTION -> ModItems.DILUTED_SPIRIT.get();
            case AGING -> ModItems.OAK_AGED_SPIRIT.get();
            case DISTILLER -> null;
        };
    }

    /**
     * A single traditional ingredient keeps its familiar tincture bottle.
     * Every new or multi-ingredient composition becomes one artisan samogon
     * whose dynamic name, composition and aroma live in the batch data.
     */
    private Item blendOutput() {
        if ("dilute".equals(agingKind)) return ModItems.DILUTED_SPIRIT.get();
        if (batch != null && !batch.composition().isBlank()) {
            List<BlendCatalog.Additive> additives = BlendCatalog.decode(batch.composition());
            if (additives.size() == 1) {
                return switch (additives.get(0).id()) {
                    case "sweet_berries" -> ModItems.BERRY_TINCTURE.get();
                    case "honey_bottle" -> ModItems.HONEY_TINCTURE.get();
                    case "botanical_bundle" -> ModItems.BOTANICAL_TINCTURE.get();
                    case "cocoa_beans", "nether_wart" -> ModItems.SPICED_TINCTURE.get();
                    case "apple" -> ModItems.FRUIT_TINCTURE.get();
                    case "kelp", "dried_kelp" -> ModItems.MARINE_TINCTURE.get();
                    default -> ModItems.SAMOGON.get();
                };
            }
            return ModItems.SAMOGON.get();
        }
        return switch (agingKind) {
            case "berry" -> ModItems.BERRY_TINCTURE.get();
            case "honey" -> ModItems.HONEY_TINCTURE.get();
            case "botanical" -> ModItems.BOTANICAL_TINCTURE.get();
            default -> ModItems.DILUTED_SPIRIT.get();
        };
    }

    private void playBottleSound() {
        if (level == null) return;
        level.playSound(null, worldPosition, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 0.7f, 0.95f);
    }

    // === TICKS ===

    private void tickMashing() {
        if (stage != 3) return;
        if (Environment.heatStrength(level, worldPosition.below()) <= 0) {
            active = false;
            return;
        }
        active = true;
        progress++;
        if (progress >= duration) {
            progress = duration;
            stage = 4;
            active = false;
            setChanged();
            notifyNearby("message.samogon.mash.ready");
            burst(ParticleTypes.CAMPFIRE_COSY_SMOKE, 12, 0.35, 0.02);
            level.playSound(null, worldPosition, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5f, 1.2f);
        }
    }

    private void tickFermentation() {
        if (stage != 2) return;
        double temp = ambient;
        double speed = 1.0 - Math.abs(temp - 20.0) / 30.0;
        speed = Simulation.clamp(speed, 0.1, 1.5);
        if (temp < 8 || temp > 38) speed = 0;
        if (speed <= 0) {
            // Keep the block visibly idle while the temperature is outside the
            // working range, not only on the one tick out of a hundred when
            // progress is sampled.
            active = false;
            if (tickCounter % 600 == 0) notifyNearby("message.samogon.ferment.stalled", f(temp));
            return;
        }
        active = true;
        if (tickCounter % 100 != 0) return;
        progress += (int) Math.round(speed * 100);
        deviationSum += Math.abs(temp - 20.0) * 100;
        deviationSamples += 100;
        ambientSum += temp * 100;
        ambientSamples += 100;
        if (progress >= duration) finishFermentation();
    }

    private void finishFermentation() {
        if (batch == null) {
            stage = 0;
            active = false;
            progress = 0;
            return;
        }
        double averageDeviation = deviationSamples == 0 ? 0 : deviationSum / deviationSamples;
        double averageTemperature = ambientSamples == 0 ? 20 : ambientSum / ambientSamples;
        batch = Simulation.ferment(batch, averageDeviation, averageTemperature);
        stage = 3;
        active = false;
        progress = duration;
        setChanged();
        notifyNearby("message.samogon.ferment.ready");
        burst(ParticleTypes.BUBBLE_POP, 20, 0.35, 0.02);
        level.playSound(null, worldPosition, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.6f, 1.1f);
    }

    private void tickDistiller() {
        if (still == null) {
            active = false;
            return;
        }
        if (tickCounter % 20 != 0) return;
        double heat = Environment.heatStrength(level, worldPosition.below());
        boolean heated = heat > 0;
        boolean cooled = coolant > 0;
        temperature = still.temperature();
        if (!still.finished()) {
            still.tick(ambient, heat, heated, cooled, 0, level.random::nextDouble);
            temperature = still.temperature();
            if (heated && cooled) {
                coolant = Math.max(0, coolant - 6);
                if (coolant == 0 && !pullCoolantFromCondensers(COOLANT_PER_BUCKET)) {
                    notifyNearby("message.samogon.coolant_empty");
                }
            }
            if (!cooled && heated && tickCounter % 200 == 0) {
                notifyNearby("message.samogon.need_coolant");
            }
            if (still.consumePukeFlag()) {
                notifyNearby("message.samogon.puked");
                burst(ParticleTypes.LARGE_SMOKE, 16, 0.4, 0.03);
                level.playSound(null, worldPosition, SoundEvents.LAVA_EXTINGUISH,
                        SoundSource.BLOCKS, 0.9f, 0.7f);
            }
            if (still.finished() && !announcedDone) {
                announcedDone = true;
                notifyNearby("message.samogon.still_done", f(still.cutVolume(Fraction.HEARTS)));
                burst(ParticleTypes.DRIPPING_HONEY, 16, 0.25, 0.02);
                level.playSound(null, worldPosition, SoundEvents.BOTTLE_FILL,
                        SoundSource.BLOCKS, 0.7f, 0.9f);
            }
            setChanged();
        }
        active = !still.finished() && heated && cooled;

        // A slow simmer every few seconds instead of an endless loop.
        if (active && tickCounter % 100 == 0) {
            level.playSound(
                    null,
                    worldPosition,
                    ModSounds.STILL_BUBBLE.get(),
                    SoundSource.BLOCKS,
                    0.55f,
                    0.9f + level.random.nextFloat() * 0.2f);
        }
    }

    private void tickRectifier() {
        if (stage == 1) {
            if (batch == null) {
                stage = 0;
                active = false;
                progress = 0;
                duration = 0;
                settle = 0;
                sieve = false;
                return;
            }
            settle++;
            if (Environment.heatStrength(level, worldPosition.below()) > 0 && settle > 60) {
                stage = 3;
                progress = 0;
                // This was missing: a freshly loaded rectifier had duration 0
                // and finished on the first tick instead of running for a minute.
                duration = RECTIFY_TICKS;
                settle = 0;
                notifyNearby("message.samogon.rectifying");
            }
            active = false;
            return;
        }
        if (stage != 3) return;
        if (batch == null) {
            stage = 0;
            active = false;
            progress = 0;
            duration = 0;
            sieve = false;
            return;
        }
        // Old saves could contain a stage-3 rectifier with duration 0. Give
        // that run a full duration instead of completing it instantly.
        if (duration <= 0) duration = RECTIFY_TICKS;
        if (Environment.heatStrength(level, worldPosition.below()) <= 0) {
            active = false;
            return;
        }
        active = true;
        progress++;
        if (progress >= duration) {
            Batch rectified = Simulation.rectify(batch);
            if (sieve) {
                // The sieve strips water, not alcohol: keep the alcohol mass
                // and shrink the volume instead of turning the same volume into
                // a stronger spirit out of thin air.
                double alcohol = rectified.alcohol();
                double volume = Math.min(rectified.volume(), alcohol / 0.995);
                double abv = volume <= 0 ? rectified.abv()
                        : Math.min(99.5, alcohol * 100.0 / volume);
                rectified = new Batch(volume, abv, Simulation.clamp(rectified.quality() + 2),
                        Math.max(0, rectified.impurities() - 2), rectified.passes(),
                        rectified.filtered(), rectified.origin(), rectified.age(),
                        rectified.composition());
            }
            batch = rectified;
            sieve = false;
            stage = 4;
            active = false;
            progress = duration;
            setChanged();
            notifyNearby("message.samogon.rectify.ready", f(batch.abv()));
            burst(ParticleTypes.CLOUD, 16, 0.25, 0.02);
            level.playSound(null, worldPosition, SoundEvents.BREWING_STAND_BREW,
                    SoundSource.BLOCKS, 0.55f, 1.25f);
        }
    }

    private void tickFilter() {
        if (stage != 3) return;
        if (batch == null) {
            stage = 0;
            active = false;
            progress = 0;
            return;
        }
        if (duration <= 0) duration = FILTER_TICKS;
        if (batch.filtered()) {
            stage = 4;
            active = false;
            progress = duration;
            setChanged();
            notifyNearby("message.samogon.filter.ready");
            return;
        }
        active = true;
        progress++;
        if (progress >= duration) {
            batch = Simulation.filter(batch);
            stage = 4;
            active = false;
            progress = duration;
            setChanged();
            notifyNearby("message.samogon.filter.ready");
            burst(ParticleTypes.DRIPPING_WATER, 14, 0.25, 0.02);
            level.playSound(null, worldPosition, SoundEvents.BOTTLE_FILL,
                    SoundSource.BLOCKS, 0.55f, 1.15f);
        }
    }

    private void tickBlending() {
        if (stage != 3) return;
        if (batch == null) {
            stage = 0;
            active = false;
            progress = 0;
            blendComposition = "";
            return;
        }
        if (duration <= 0) duration = BLEND_TICKS;
        active = true;
        progress++;
        if (progress < duration) return;
        if (agingKind.isEmpty()) {
            stage = 1;
            active = false;
            progress = 0;
            return;
        }
        try {
            batch = switch (agingKind) {
                case "dilute" -> Simulation.dilute(batch, 40);
                case "berry" -> Simulation.infuse(batch, "berry");
                case "honey" -> Simulation.infuse(batch, "honey");
                case "botanical" -> Simulation.infuse(batch, "botanical");
                case "blend" -> BlendCatalog.blend(batch, BlendCatalog.decode(blendComposition));
                default -> throw new IllegalArgumentException("Unknown blend");
            };
            stage = 4;
            active = false;
            progress = duration;
            notifyNearby("message.samogon.blend.ready");
            burst(ParticleTypes.SPLASH, 18, 0.3, 0.03);
            level.playSound(null, worldPosition, SoundEvents.BUCKET_EMPTY,
                    SoundSource.BLOCKS, 0.5f, 1.1f);
        } catch (IllegalArgumentException exception) {
            stage = 1;
            active = false;
            progress = 0;
            agingKind = "";
            blendComposition = "";
            notifyNearby("message.samogon.blend.failed");
        }
        setChanged();
    }

    private void tickDilution() {
        if (stage != 3) return;
        if (batch == null) {
            stage = 0;
            active = false;
            progress = 0;
            return;
        }
        if (duration <= 0) duration = DILUTION_TICKS;
        active = true;
        progress++;
        if (progress < duration) return;
        try {
            batch = Simulation.dilute(batch, 40);
            stage = 4;
            active = false;
            progress = duration;
            notifyNearby("message.samogon.dilution.ready", f(batch.abv()));
            burst(ParticleTypes.SPLASH, 24, 0.35, 0.03);
            level.playSound(null, worldPosition, SoundEvents.BUCKET_EMPTY,
                    SoundSource.BLOCKS, 0.6f, 1.2f);
        } catch (IllegalArgumentException exception) {
            stage = 1;
            active = false;
            progress = 0;
            notifyNearby("message.samogon.dilution.failed");
        }
        setChanged();
    }

    private void tickAging() {
        if (stage != 3) return;
        if (batch == null) {
            stage = 0;
            active = false;
            progress = 0;
            return;
        }
        if (duration <= 0) duration = AGE_TICKS;
        active = true;
        progress++;
        if (progress < duration) return;
        try {
            batch = Simulation.mature(batch, true);
            stage = 4;
            active = false;
            progress = duration;
            notifyNearby("message.samogon.aging.ready");
            burst(ParticleTypes.END_ROD, 14, 0.25, 0.01);
            level.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.BLOCKS, 0.45f, 1.4f);
        } catch (IllegalArgumentException exception) {
            stage = 1;
            active = false;
            progress = 0;
            notifyNearby("message.samogon.dilute_first");
        }
        setChanged();
    }

    private void updateTemperature() {
        if (kind() == ProcessBlock.Kind.DISTILLER && still != null) return;
        double heat = Environment.heatStrength(level, worldPosition.below());
        double target;
        double rate;
        if (heat > 0) {
            // A vessel on a heat source warms up even before a batch is loaded.
            // Once a process is active, its working temperature is much higher.
            target = active ? 60 + heat * 30 : Math.min(60, ambient + heat * 25);
            rate = active ? 0.02 : 0.01;
        } else {
            target = ambient;
            rate = 0.01;
        }
        temperature += (target - temperature) * rate;
    }

    private void updateVisualState() {
        if (level == null) return;
        int heat = Mth.clamp((int) Math.round((temperature - 20.0) / 20.0), 0, 4);
        boolean ready = isReady();
        int band;
        if (ready) {
            band = 4;
        } else if (stage == 3) {
            if (kind() == ProcessBlock.Kind.DISTILLER && still != null) {
                // The client uses the progress band as a fraction hint: smoke
                // for heads, gold for hearts, water-blue for tails.
                band = switch (still.currentFraction()) {
                    case HEADS -> 1;
                    case HEARTS -> still.progress() < 0.5 ? 2 : 3;
                    case TAILS -> 4;
                };
            } else if (duration > 0) {
                band = 1 + (int) Math.min(3, progress * 4L / duration);
            } else {
                band = 1;
            }
        } else {
            band = stage > 0 ? 1 : 0;
        }
        BlockState state = getBlockState();
        if (state.getValue(ProcessBlock.HEAT) != heat
                || state.getValue(ProcessBlock.ACTIVE) != active
                || state.getValue(ProcessBlock.PROGRESS) != band
                || state.getValue(ProcessBlock.READY) != ready) {
            level.setBlock(worldPosition, state.setValue(ProcessBlock.HEAT, heat)
                    .setValue(ProcessBlock.ACTIVE, active)
                    .setValue(ProcessBlock.PROGRESS, band)
                    .setValue(ProcessBlock.READY, ready), Block.UPDATE_CLIENTS);
        }
    }

    private boolean isReady() {
        return stage == 4
                || (kind() == ProcessBlock.Kind.FERMENTATION && stage == 3)
                || (kind() == ProcessBlock.Kind.DISTILLER && still != null && still.finished());
    }

    /** The basin accepts only a batch that will fit after reaching 40% ABV. */
    private static boolean fitsDilutionBasin(Batch batch) {
        return batch.alcohol() * 100 / 40.0 <= 4000.0 + 1.0E-9;
    }

    /** Server-side landmarks: coloured drips, steam and a golden "ready" crown. */
    private void emitEffects() {
        if (!(level instanceof ServerLevel server)) return;
        double x = worldPosition.getX() + 0.5;
        double y = worldPosition.getY() + 0.5;
        double z = worldPosition.getZ() + 0.5;

        if (isReady() && tickCounter % 14 == 0) {
            server.sendParticles(ParticleTypes.END_ROD, x, y + 0.95, z,
                    2, 0.3, 0.12, 0.3, 0.01);
            server.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y + 0.7, z,
                    1, 0.38, 0.2, 0.38, 0.005);
        }
        if (!active || tickCounter % 8 != 0) return;

        switch (kind()) {
            case MASHING -> server.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                    x, y + 0.85, z, 1, 0.35, 0.05, 0.35, 0.008);
            case FERMENTATION -> {
                server.sendParticles(ParticleTypes.BUBBLE_POP, x, y + 0.9, z,
                        1, 0.3, 0.05, 0.3, 0.005);
                if (progress > duration / 2) {
                    server.sendParticles(ParticleTypes.BUBBLE, x, y + 0.55, z,
                            1, 0.2, 0.05, 0.2, 0.01);
                }
            }
            case DISTILLER -> {
                if (still != null && !still.finished()) {
                    ParticleOptions drip = switch (still.currentFraction()) {
                        case HEADS -> ParticleTypes.SMOKE;
                        case HEARTS -> ParticleTypes.DRIPPING_HONEY;
                        case TAILS -> ParticleTypes.DRIPPING_WATER;
                    };
                    server.sendParticles(drip, x + 0.3, y + 0.25, z + 0.3,
                            1, 0.02, 0.01, 0.02, 0.0);
                }
            }
            case RECTIFIER -> server.sendParticles(ParticleTypes.CLOUD, x, y + 0.85, z,
                    1, 0.15, 0.05, 0.15, 0.005);
            case FILTER -> server.sendParticles(ParticleTypes.DRIPPING_WATER, x, y + 0.15, z,
                    1, 0.15, 0.02, 0.15, 0.0);
            case BLENDING -> server.sendParticles(ParticleTypes.SPLASH, x, y + 0.65, z,
                    1, 0.25, 0.05, 0.25, 0.01);
            case DILUTION -> server.sendParticles(ParticleTypes.SPLASH, x, y + 0.55, z,
                    1, 0.3, 0.05, 0.3, 0.015);
            case AGING -> server.sendParticles(ParticleTypes.FALLING_HONEY, x, y + 0.9, z,
                    1, 0.2, 0.02, 0.2, 0.0);
        }
    }

    private void burst(ParticleOptions particle, int count, double spread, double speed) {
        if (!(level instanceof ServerLevel server)) return;
        server.sendParticles(particle, worldPosition.getX() + 0.5, worldPosition.getY() + 0.9,
                worldPosition.getZ() + 0.5, count, spread, 0.18, spread, speed);
    }

    // === INTERACTIONS ===

    public InteractionResult interact(Player player, InteractionHand hand, ItemStack stack) {
        if (level == null || level.isClientSide) return InteractionResult.SUCCESS;
        if (stack.is(ModItems.THERMOMETER.get())) {
            reportTemperature(player);
            return InteractionResult.SUCCESS;
        }
        if (stack.is(ModItems.HYDROMETER.get())) {
            reportStrength(player);
            return InteractionResult.SUCCESS;
        }
        if (stack.is(ModItems.GUIDE.get())) {
            // The client opens the illustrated book; the server only acknowledges use.
            return InteractionResult.SUCCESS;
        }
        InteractionResult result = switch (kind()) {
            case MASHING -> useMashing(player, hand, stack);
            case FERMENTATION -> useFermentation(player, hand, stack);
            case DISTILLER -> useDistiller(player, hand, stack);
            case RECTIFIER -> useRectifier(player, hand, stack);
            case FILTER -> useFilter(player, hand, stack);
            case BLENDING -> useBlending(player, hand, stack);
            case DILUTION -> useDilution(player, hand, stack);
            case AGING -> useAging(player, hand, stack);
        };
        setChanged();
        return result;
    }

    private InteractionResult useMashing(Player player, InteractionHand hand, ItemStack stack) {
        if (stage == 0 || stage == 1) {
            String origin = null;
            int bonus = 0;
            Item container = null;
            if (stack.is(ModItems.MALT.get())) {
                origin = "grain";
                bonus = 4;
            } else if (stack.is(Items.WHEAT)) {
                origin = "grain";
            } else if (stack.is(Items.SUGAR)) {
                origin = "sugar";
                bonus = -4;
            } else if (stack.is(Items.SWEET_BERRIES)) {
                origin = "berry";
                bonus = 2;
            } else if (stack.is(Items.HONEY_BOTTLE)) {
                origin = "honey";
                bonus = 6;
                container = Items.GLASS_BOTTLE;
            }
            if (origin != null) {
                consume(player, stack, container);
                batch = Simulation.mash(origin, bonus);
                stage = 3;
                progress = 0;
                duration = MASH_TICKS;
                settle = 0;
                player.displayClientMessage(Component.translatable("message.samogon.mashing"), false);
                if (Environment.heatStrength(level, worldPosition.below()) <= 0) {
                    player.displayClientMessage(Component.translatable("message.samogon.need_heat"), false);
                }
                return InteractionResult.SUCCESS;
            }
            player.displayClientMessage(Component.translatable("message.samogon.mashing_hint"), false);
            return InteractionResult.SUCCESS;
        }
        if (stage == 3) {
            showBusy(player);
            return InteractionResult.SUCCESS;
        }
        return bottleJar(player, stack, ModItems.SWEET_WORT.get());
    }

    private InteractionResult useFermentation(Player player, InteractionHand hand, ItemStack stack) {
        if (stage == 0) {
            if (stack.getItem() instanceof ru.samogon.item.BatchItem) {
                Batch wort = BatchData.read(stack);
                if (wort.abv() > 0.5) {
                    player.displayClientMessage(Component.translatable("message.samogon.need_wort"), false);
                    return InteractionResult.SUCCESS;
                }
                batch = wort;
                stage = 1;
                consume(player, stack, Items.GLASS_BOTTLE);
                player.displayClientMessage(Component.translatable("message.samogon.fermentation_hint"), false);
                return InteractionResult.SUCCESS;
            }
            player.displayClientMessage(Component.translatable("message.samogon.fermentation_hint"), false);
            return InteractionResult.SUCCESS;
        }
        if (stage == 1) {
            if (stack.is(ModItems.YEAST.get())) {
                consume(player, stack, null);
                stage = 2;
                progress = 0;
                duration = FERMENT_TICKS;
                deviationSum = 0;
                deviationSamples = 0;
                ambientSum = 0;
                ambientSamples = 0;
                player.displayClientMessage(Component.translatable("message.samogon.fermenting"), false);
                return InteractionResult.SUCCESS;
            }
            player.displayClientMessage(Component.translatable("message.samogon.add_yeast"), false);
            return InteractionResult.SUCCESS;
        }
        if (stage == 2) {
            showBusy(player);
            return InteractionResult.SUCCESS;
        }
        return bottleJar(player, stack, ModItems.FERMENTED_MASH.get());
    }

    private InteractionResult useDistiller(Player player, InteractionHand hand, ItemStack stack) {
        // Allow filling the condenser before loading a wash; the coolant is kept
        // when the pot is charged.
        if (stack.is(Items.WATER_BUCKET)) {
            if (coolant >= MAX_COOLANT) {
                player.displayClientMessage(Component.translatable("message.samogon.coolant_full"), true);
                return InteractionResult.SUCCESS;
            }
            coolant = Math.min(MAX_COOLANT, coolant + COOLANT_PER_BUCKET);
            consume(player, stack, Items.BUCKET);
            player.displayClientMessage(Component.translatable("message.samogon.coolant_added",
                    (int) Math.round(coolant / 360.0)), false);
            return InteractionResult.SUCCESS;
        }
        if (still == null) {
            if (stack.getItem() instanceof ru.samogon.item.BatchItem) {
                Batch wash = BatchData.read(stack);
                if (wash.abv() < 1 || wash.volume() < 100) {
                    player.displayClientMessage(Component.translatable("message.samogon.not_wash"), false);
                    return InteractionResult.SUCCESS;
                }
                consume(player, stack, Items.GLASS_BOTTLE);
                batch = null;
                stage = 3;
                progress = 0;
                duration = 0;
                announcedDone = false;
                receiver = Fraction.HEARTS;
                still = new StillRun(wash);
                player.displayClientMessage(Component.translatable("message.samogon.distilling"), false);
                if (coolant <= 0) {
                    player.displayClientMessage(Component.translatable("message.samogon.need_coolant"), false);
                }
                if (Environment.heatStrength(level, worldPosition.below()) <= 0) {
                    player.displayClientMessage(Component.translatable("message.samogon.need_heat"), false);
                }
                return InteractionResult.SUCCESS;
            }
            player.displayClientMessage(Component.translatable("message.samogon.distiller_hint"), false);
            return InteractionResult.SUCCESS;
        }
        if (stack.is(Items.GLASS_BOTTLE)) {
            Fraction chosen = receiver;
            double available = still.cutVolume(chosen);
            if (available <= 0.0001) {
                player.displayClientMessage(Component.translatable("message.samogon.no_cut",
                        Component.translatable("fraction.samogon." + chosen.id())), true);
                return InteractionResult.SUCCESS;
            }
            int amount = (int) Math.min(BOTTLE, Math.ceil(available));
            Batch portion = still.drain(chosen, amount);
            if (portion == null) {
                player.displayClientMessage(Component.translatable("message.samogon.no_cut",
                        Component.translatable("fraction.samogon." + chosen.id())), true);
                return InteractionResult.SUCCESS;
            }
            consume(player, stack, null);
            giveOrDrop(player, BatchData.create(cutItem(chosen), portion));
            level.playSound(null, worldPosition, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 0.6f, 1.0f);
            player.displayClientMessage(Component.translatable("message.samogon.drained",
                    Component.translatable("fraction.samogon." + chosen.id()),
                    f(portion.abv())), true);
            if (still.finished() && totalCuts() <= 0.0001) {
                notifyNearby("message.samogon.still_empty");
            }
            return InteractionResult.SUCCESS;
        }
        if (stack.isEmpty() && player.isShiftKeyDown()) {
            // A finished pot can only be cleared after every cut has been bottled.
            // Otherwise sneak-right-click must still cycle the receiver, or the
            // cuts that are not selected would become unreachable.
            if (still.finished() && totalCuts() <= 0.0001) {
                still = null;
                stage = 0;
                coolant = 0;
                player.displayClientMessage(Component.translatable("message.samogon.still_cleared"), false);
                return InteractionResult.SUCCESS;
            }
            receiver = receiver == Fraction.HEADS ? Fraction.HEARTS
                    : receiver == Fraction.HEARTS ? Fraction.TAILS : Fraction.HEADS;
            player.displayClientMessage(Component.translatable("message.samogon.receiver",
                    Component.translatable("fraction.samogon." + receiver.id())), true);
            return InteractionResult.SUCCESS;
        }
        showStatus(player);
        return InteractionResult.SUCCESS;
    }

    private InteractionResult useRectifier(Player player, InteractionHand hand, ItemStack stack) {
        if (stage == 0) {
            if (stack.getItem() instanceof ru.samogon.item.BatchItem) {
                Batch feed = BatchData.read(stack);
                if (feed.abv() < 1 || feed.volume() < 10) {
                    player.displayClientMessage(Component.translatable("message.samogon.not_wash"), false);
                    return InteractionResult.SUCCESS;
                }
                consume(player, stack, Items.GLASS_BOTTLE);
                batch = feed;
                stage = 1;
                settle = 0;
                sieve = false;
                player.displayClientMessage(Component.translatable("message.samogon.rectifying_hint"), false);
                return InteractionResult.SUCCESS;
            }
            player.displayClientMessage(Component.translatable("message.samogon.rectifier_hint"), false);
            return InteractionResult.SUCCESS;
        }
        if (stage == 1 && !sieve && stack.is(ModItems.MOLECULAR_SIEVE.get())) {
            if (batch == null) {
                stage = 0;
                settle = 0;
                player.displayClientMessage(Component.translatable("message.samogon.status.empty"), false);
                return InteractionResult.SUCCESS;
            }
            consume(player, stack, null);
            sieve = true;
            player.displayClientMessage(Component.translatable("message.samogon.sieve_added"), false);
            return InteractionResult.SUCCESS;
        }
        if (stage == 1) {
            showStatus(player);
            return InteractionResult.SUCCESS;
        }
        if (stage == 3) {
            showBusy(player);
            return InteractionResult.SUCCESS;
        }
        if (stage == 4) {
            if (batch == null) {
                player.displayClientMessage(Component.translatable("message.samogon.status.empty"), false);
                return InteractionResult.SUCCESS;
            }
            Item output = batch.abv() >= 99 ? ModItems.ANHYDROUS_SPIRIT.get() : ModItems.NEUTRAL_SPIRIT.get();
            return bottleSmall(player, stack, output);
        }
        return InteractionResult.SUCCESS;
    }

    private InteractionResult useFilter(Player player, InteractionHand hand, ItemStack stack) {
        if (stage == 0) {
            if (stack.getItem() instanceof ru.samogon.item.BatchItem) {
                Batch feed = BatchData.read(stack);
                if (feed.abv() < 1) {
                    player.displayClientMessage(Component.translatable("message.samogon.not_wash"), false);
                    return InteractionResult.SUCCESS;
                }
                if (feed.filtered()) {
                    player.displayClientMessage(Component.translatable("message.samogon.already_filtered"), false);
                    return InteractionResult.SUCCESS;
                }
                batch = feed;
                stage = 1;
                consume(player, stack, Items.GLASS_BOTTLE);
                player.displayClientMessage(Component.translatable("message.samogon.filter_hint"), false);
                return InteractionResult.SUCCESS;
            }
            player.displayClientMessage(Component.translatable("message.samogon.filter_hint"), false);
            return InteractionResult.SUCCESS;
        }
        if (stage == 1 && stack.is(ModItems.ACTIVATED_CHARCOAL.get())) {
            if (batch == null) {
                stage = 0;
                player.displayClientMessage(Component.translatable("message.samogon.status.empty"), false);
                return InteractionResult.SUCCESS;
            }
            consume(player, stack, null);
            stage = 3;
            progress = 0;
            duration = FILTER_TICKS;
            player.displayClientMessage(Component.translatable("message.samogon.filtering"), false);
            return InteractionResult.SUCCESS;
        }
        if (stage == 2 || stage == 1) {
            player.displayClientMessage(Component.translatable("message.samogon.add_charcoal"), false);
            return InteractionResult.SUCCESS;
        }
        if (stage == 3) {
            showBusy(player);
            return InteractionResult.SUCCESS;
        }
        return bottleSmall(player, stack, ModItems.FILTERED_SPIRIT.get());
    }

    private InteractionResult useBlending(Player player, InteractionHand hand, ItemStack stack) {
        if (stage == 0) {
            if (stack.getItem() instanceof ru.samogon.item.BatchItem) {
                Batch feed = BatchData.read(stack);
                if (feed.abv() < 1) {
                    player.displayClientMessage(Component.translatable("message.samogon.not_wash"), false);
                    return InteractionResult.SUCCESS;
                }
                if (feed.abv() < 20 || feed.abv() > 60) {
                    player.displayClientMessage(Component.translatable("message.samogon.strength_range"), false);
                    return InteractionResult.SUCCESS;
                }
                batch = feed;
                stage = 1;
                blendComposition = "";
                consume(player, stack, Items.GLASS_BOTTLE);
                player.displayClientMessage(Component.translatable("message.samogon.blend_hint"), false);
                return InteractionResult.SUCCESS;
            }
            player.displayClientMessage(Component.translatable("message.samogon.blend_hint"), false);
            return InteractionResult.SUCCESS;
        }
        if (stage == 1) {
            if (batch == null) {
                stage = 0;
                progress = 0;
                player.displayClientMessage(Component.translatable("message.samogon.status.empty"), false);
                return InteractionResult.SUCCESS;
            }
            if (stack.isEmpty()) {
                if (player.isShiftKeyDown()) {
                    startBlending(player);
                } else {
                    showStatus(player);
                }
                return InteractionResult.SUCCESS;
            }
            if (addBlendAdditive(player, stack)) {
                return InteractionResult.SUCCESS;
            }
            player.displayClientMessage(Component.translatable("message.samogon.blend_hint"), false);
            return InteractionResult.SUCCESS;
        }
        if (stage == 3) {
            showBusy(player);
            return InteractionResult.SUCCESS;
        }
        return bottleSmall(player, stack, blendOutput());
    }

    private InteractionResult useDilution(Player player, InteractionHand hand, ItemStack stack) {
        if (stage == 0) {
            if (stack.getItem() instanceof ru.samogon.item.BatchItem) {
                Batch feed = BatchData.read(stack);
                if (feed.abv() <= 40) {
                    player.displayClientMessage(Component.translatable("message.samogon.dilute_needed"), false);
                    return InteractionResult.SUCCESS;
                }
                if (!fitsDilutionBasin(feed)) {
                    player.displayClientMessage(Component.translatable("message.samogon.dilution_failed"), false);
                    return InteractionResult.SUCCESS;
                }
                batch = feed;
                stage = 1;
                consume(player, stack, Items.GLASS_BOTTLE);
                player.displayClientMessage(Component.translatable("message.samogon.dilution_hint"), false);
                return InteractionResult.SUCCESS;
            }
            player.displayClientMessage(Component.translatable("message.samogon.dilution_basin_hint"), false);
            return InteractionResult.SUCCESS;
        }
        if (stage == 1) {
            if (stack.is(Items.WATER_BUCKET)) {
                if (batch == null) {
                    stage = 0;
                    player.displayClientMessage(Component.translatable("message.samogon.status.empty"), false);
                    return InteractionResult.SUCCESS;
                }
                consume(player, stack, Items.BUCKET);
                stage = 3;
                progress = 0;
                duration = DILUTION_TICKS;
                player.displayClientMessage(Component.translatable("message.samogon.diluting"), false);
                return InteractionResult.SUCCESS;
            }
            showStatus(player);
            return InteractionResult.SUCCESS;
        }
        if (stage == 3) {
            showBusy(player);
            return InteractionResult.SUCCESS;
        }
        return bottleSmall(player, stack, ModItems.DILUTED_SPIRIT.get());
    }

    private InteractionResult useAging(Player player, InteractionHand hand, ItemStack stack) {
        if (stage == 0) {
            if (stack.getItem() instanceof ru.samogon.item.BatchItem) {
                Batch feed = BatchData.read(stack);
                if (feed.abv() < 20 || feed.abv() > 60) {
                    player.displayClientMessage(Component.translatable("message.samogon.strength_range"), false);
                    return InteractionResult.SUCCESS;
                }
                batch = feed;
                stage = 1;
                consume(player, stack, Items.GLASS_BOTTLE);
                player.displayClientMessage(Component.translatable("message.samogon.cask_hint"), false);
                return InteractionResult.SUCCESS;
            }
            player.displayClientMessage(Component.translatable("message.samogon.cask_hint"), false);
            return InteractionResult.SUCCESS;
        }
        if (stage == 1) {
            if (batch == null) {
                stage = 0;
                progress = 0;
                player.displayClientMessage(Component.translatable("message.samogon.status.empty"), false);
                return InteractionResult.SUCCESS;
            }
            if (stack.is(ModItems.OAK_CHIPS.get())) {
                consume(player, stack, null);
                stage = 3;
                progress = 0;
                duration = AGE_TICKS;
                player.displayClientMessage(Component.translatable("message.samogon.aging"), false);
                return InteractionResult.SUCCESS;
            }
            player.displayClientMessage(Component.translatable("message.samogon.add_oak"), false);
            return InteractionResult.SUCCESS;
        }
        if (stage == 3) {
            showBusy(player);
            return InteractionResult.SUCCESS;
        }
        return bottleSmall(player, stack, ModItems.OAK_AGED_SPIRIT.get());
    }

    private InteractionResult bottleJar(Player player, ItemStack stack, Item item) {
        if (!stack.is(Items.GLASS_BOTTLE)) {
            player.displayClientMessage(Component.translatable("message.samogon.ready_bottle"), false);
            return InteractionResult.SUCCESS;
        }
        if (batch == null) {
            player.displayClientMessage(Component.translatable("message.samogon.status.empty"), false);
            return InteractionResult.SUCCESS;
        }
        Batch portion = takePortion(JAR);
        consume(player, stack, null);
        giveOrDrop(player, BatchData.create(item, portion));
        level.playSound(null, worldPosition, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 0.7f, 0.9f);
        player.displayClientMessage(Component.translatable("message.samogon.filled_jar", f(portion.volume())), true);
        return InteractionResult.SUCCESS;
    }

    private InteractionResult bottleSmall(Player player, ItemStack stack, Item item) {
        if (!stack.is(Items.GLASS_BOTTLE)) {
            showStatus(player);
            return InteractionResult.SUCCESS;
        }
        if (batch == null) {
            player.displayClientMessage(Component.translatable("message.samogon.status.empty"), false);
            return InteractionResult.SUCCESS;
        }
        Batch portion = takePortion(BOTTLE);
        consume(player, stack, null);
        giveOrDrop(player, BatchData.create(item, portion));
        level.playSound(null, worldPosition, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 0.7f, 1.0f);
        player.displayClientMessage(Component.translatable("message.samogon.filled_jar", f(portion.volume())), true);
        return InteractionResult.SUCCESS;
    }

    private Batch takePortion(double amount) {
        double volume = Math.min(amount, batch.volume());
        Batch portion = new Batch(volume, batch.abv(), batch.quality(), batch.impurities(), batch.passes(),
                batch.filtered(), batch.origin(), batch.age(), batch.composition());
        double remaining = batch.volume() - volume;
        // Batch requires a strictly positive volume. Calling withVolume(0) on
        // the last bottle crashed the interaction instead of emptying the
        // machine. Any positive remainder stays in the machine and can still
        // be bottled, including the final few drops.
        if (remaining <= 1.0E-9) {
            batch = null;
            stage = 0;
            progress = 0;
            duration = 0;
            active = false;
            sieve = false;
            agingKind = "";
            blendComposition = "";
        } else {
            batch = batch.withVolume(remaining);
        }
        return portion;
    }

    // === HELPERS ===

    private void showBusy(Player player) {
        int percent = progressPercent();
        player.displayClientMessage(Component.translatable("message.samogon.busy", percent), true);
    }

    private void showStatus(Player player) {
        player.displayClientMessage(Component.translatable("message.samogon.status.header",
                getBlockState().getBlock().getName()), false);
        if (batch == null && still == null) {
            player.displayClientMessage(Component.translatable("message.samogon.status.empty"), false);
            return;
        }
        switch (kind()) {
            case MASHING -> player.displayClientMessage(Component.translatable("message.samogon.status.mash",
                    progressPercent(), batch == null ? 0 : (int) batch.volume(),
                    batch == null ? 0 : batch.quality()), false);
            case FERMENTATION -> player.displayClientMessage(Component.translatable("message.samogon.status.ferment",
                    progressPercent(), f(batch == null ? 0 : batch.volume()),
                    f(batch == null ? 0 : batch.abv()), f(ambient)), false);
            case DISTILLER -> {
                if (still == null) {
                    player.displayClientMessage(Component.translatable("message.samogon.status.empty"), false);
                    return;
                }
                player.displayClientMessage(Component.translatable("message.samogon.status.still",
                        f(still.temperature()), f(still.abv()), f(still.pressure()),
                        Component.translatable("fraction.samogon." + receiver.id()),
                        f(still.cutVolume(Fraction.HEADS)),
                        f(still.cutVolume(Fraction.HEARTS)), f(still.cutVolume(Fraction.TAILS))), false);
                player.displayClientMessage(Component.translatable("message.samogon.status.coolant",
                        (int) Math.round(coolant / 360.0)), false);
            }
            case RECTIFIER -> player.displayClientMessage(Component.translatable("message.samogon.status.rectify",
                    progressPercent(), batch == null ? 0 : (int) batch.volume(),
                    f(batch == null ? 0 : batch.abv()), sieve ? Component.translatable("message.samogon.yes")
                            : Component.translatable("message.samogon.no")), false);
            case FILTER -> player.displayClientMessage(Component.translatable("message.samogon.status.filter",
                    progressPercent(), f(batch == null ? 0 : batch.volume()),
                    f(batch == null ? 0 : batch.impurities())), false);
            case BLENDING -> player.displayClientMessage(Component.translatable("message.samogon.status.blend",
                    progressPercent(), f(batch == null ? 0 : batch.abv()),
                    Component.translatable("blend.samogon." + (agingKind.isEmpty() ? "none" : agingKind))), false);
            case DILUTION -> player.displayClientMessage(Component.translatable("message.samogon.status.dilute",
                    progressPercent(), f(batch == null ? 0 : batch.abv()),
                    f(batch == null ? 0 : batch.volume())), false);
            case AGING -> player.displayClientMessage(Component.translatable("message.samogon.status.aging",
                    progressPercent(), f(batch == null ? 0 : batch.abv()),
                    batch == null ? 0 : batch.age()), false);
        }
    }

    private void reportTemperature(Player player) {
        double heat = Environment.heatStrength(level, worldPosition.below());
        player.displayClientMessage(Component.translatable("message.samogon.thermometer",
                f(temperature), f(ambient), f(heat * 100)), false);
        if (still != null) {
            player.displayClientMessage(Component.translatable("message.samogon.still_temperature",
                    f(still.temperature()), f(Simulation.boilingPoint(still.abv()))), false);
        }
    }

    private void reportStrength(Player player) {
        if (still != null) {
            player.displayClientMessage(Component.translatable("message.samogon.hydrometer.still",
                    f(still.abv()), f(still.lastVapourAbv()), progressPercent()), false);
            player.displayClientMessage(Component.translatable("message.samogon.hydrometer.cuts",
                    f(still.cutAbv(Fraction.HEADS)), f(still.cutAbv(Fraction.HEARTS)),
                    f(still.cutAbv(Fraction.TAILS))), false);
            return;
        }
        if (batch == null) {
            player.displayClientMessage(Component.translatable("message.samogon.hydrometer.empty"), false);
            return;
        }
        player.displayClientMessage(Component.translatable("message.samogon.hydrometer.batch",
                f(batch.abv()), f(batch.volume()), batch.quality(), batch.impurities()), false);
    }

    private int progressPercent() {
        if (kind() == ProcessBlock.Kind.DISTILLER && still != null) {
            return (int) Math.round(still.progress() * 100.0);
        }
        return duration <= 0 ? 0 : (int) Math.min(100, progress * 100L / duration);
    }

    private double totalCuts() {
        if (still == null) return 0;
        return still.cutVolume(Fraction.HEADS) + still.cutVolume(Fraction.HEARTS) + still.cutVolume(Fraction.TAILS);
    }

    private static Item cutItem(Fraction fraction) {
        return switch (fraction) {
            case HEADS -> ModItems.HEADS_FRACTION.get();
            case HEARTS -> ModItems.HEARTS_FRACTION.get();
            case TAILS -> ModItems.TAILS_FRACTION.get();
        };
    }

    private void consume(Player player, ItemStack stack, Item container) {
        if (player.getAbilities().instabuild) return;
        stack.shrink(1);
        if (container != null) giveOrDrop(player, new ItemStack(container));
    }

    private void giveOrDrop(Player player, ItemStack stack) {
        if (!player.addItem(stack)) player.drop(stack, false);
    }

    private void notifyNearby(String key, Object... args) {
        if (level == null) return;
        Component message = Component.translatable(key, args);
        for (Player player : level.players()) {
            if (player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
                    worldPosition.getZ() + 0.5) < 256) {
                player.displayClientMessage(message, false);
            }
        }
    }

    private static String f(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    // === NBT ===

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Stage", stage);
        tag.putInt("Progress", progress);
        tag.putInt("Duration", duration);
        tag.putBoolean("Active", active);
        tag.putInt("Coolant", coolant);
        tag.putInt("Settle", settle);
        tag.putDouble("Temperature", temperature);
        tag.putDouble("Ambient", ambient);
        tag.putDouble("DeviationSum", deviationSum);
        tag.putInt("DeviationSamples", deviationSamples);
        tag.putDouble("AmbientSum", ambientSum);
        tag.putInt("AmbientSamples", ambientSamples);
        tag.putBoolean("Sieve", sieve);
        tag.putBoolean("AnnouncedDone", announcedDone);
        tag.putString("AgingKind", agingKind);
        tag.putString("BlendComposition", blendComposition);
        tag.putString("Receiver", receiver.id());
        tag.putInt("InventoryLayout", INVENTORY_LAYOUT);
        ContainerHelper.saveAllItems(tag, inventory.getItems(), registries);
        if (batch != null) tag.put("Batch", BatchNbt.save(batch));
        if (still != null) {
            CompoundTag stillTag = new CompoundTag();
            StillNbt.save(still, stillTag);
            tag.put("Still", stillTag);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        stage = tag.getInt("Stage");
        progress = tag.getInt("Progress");
        duration = tag.getInt("Duration");
        active = tag.getBoolean("Active");
        coolant = tag.getInt("Coolant");
        settle = tag.getInt("Settle");
        temperature = tag.contains("Temperature") ? tag.getDouble("Temperature") : 20;
        ambient = tag.contains("Ambient") ? tag.getDouble("Ambient") : 20;
        deviationSum = tag.getDouble("DeviationSum");
        deviationSamples = tag.getInt("DeviationSamples");
        ambientSum = tag.getDouble("AmbientSum");
        ambientSamples = tag.getInt("AmbientSamples");
        sieve = tag.getBoolean("Sieve");
        announcedDone = tag.getBoolean("AnnouncedDone");
        agingKind = tag.getString("AgingKind");
        blendComposition = tag.getString("BlendComposition");
        boolean legacyInventory = tag.getInt("InventoryLayout") < INVENTORY_LAYOUT;
        ContainerHelper.loadAllItems(tag, inventory.getItems(), registries);
        if (legacyInventory) {
            // The output used to be slot 2. Move a machine product or bottle
            // from there so old worlds keep the contents of their machines.
            ItemStack legacyOutput = inventory.getItem(2);
            if (!legacyOutput.isEmpty() && inventory.getItem(SLOT_OUTPUT).isEmpty()) {
                inventory.setItem(2, ItemStack.EMPTY);
                inventory.setItem(SLOT_OUTPUT, legacyOutput);
            }
        }
        batch = tag.contains("Batch") ? BatchNbt.load(tag.getCompound("Batch")) : null;
        still = tag.contains("Still") ? StillNbt.load(tag.getCompound("Still")) : null;
        try {
            receiver = Fraction.valueOf(tag.getString("Receiver").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            receiver = Fraction.HEARTS;
        }
    }
}
