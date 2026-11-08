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
import ru.samogon.effect.Intoxication;
import ru.samogon.item.BatchData;
import ru.samogon.item.BatchItem;
import ru.samogon.menu.ProcessMenu;
import ru.samogon.process.BatchNbt;
import ru.samogon.process.BlendCatalog;
import ru.samogon.process.Environment;
import ru.samogon.process.MachineData;
import ru.samogon.process.Quality;
import ru.samogon.process.Simulation;
import ru.samogon.process.Simulation.Batch;
import ru.samogon.process.Simulation.Fraction;
import ru.samogon.process.SpiritType;
import ru.samogon.process.StillNbt;
import ru.samogon.process.StillRun;
import ru.samogon.registry.ModBlocks;
import ru.samogon.registry.ModItems;
import ru.samogon.registry.ModSounds;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
            Batch b = kind() == ProcessBlock.Kind.TASTING ? tastingBatch : batch;
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
                case MachineData.STILL_FRACTIONAL ->
                        (s != null ? s.fractional() : fractional) ? 1 : 0;
                case MachineData.RECTIFY_RUNS -> columnRunsTarget;
                case MachineData.RECTIFY_RUN -> b == null ? 0 : b.columnRuns();
                case MachineData.TASTING_VALID -> tastingValid ? 1 : 0;
                case MachineData.TASTING_TYPE -> tastingType;
                case MachineData.TASTING_INTOX -> tastingIntox;
                case MachineData.TASTING_DURATION -> tastingDuration;
                case MachineData.TASTING_AURA -> tastingAura;
                case MachineData.TASTING_AURA_LEVEL -> tastingAuraLevel;
                case MachineData.TASTING_AURA_COUNT -> tastingAuraCount;
                case MachineData.TASTING_TECHNICAL -> tastingTechnical;
                case MachineData.TASTING_DRINKABLE -> tastingDrinkable;
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
    /** Pot-still mode chosen before charging: separate fractions or run whole. */
    private boolean fractional = true;
    /** Column passes requested in the GUI (1..6). */
    private int columnRunsTarget = 1;
    /** Passes still to perform for the currently loaded rectifier charge. */
    private int columnRunsLeft;
    /**
     * The tasting table never runs a process: it only reads a bottle with a
     * hydrometer. Its batch is kept apart from the process batch so breaking
     * the table cannot bottle a phantom duplicate of the sample.
     */
    private Batch tastingBatch;
    private boolean tastingValid;
    private int tastingType;
    private int tastingIntox;
    private int tastingDuration;
    private int tastingAura;
    private int tastingAuraLevel;
    private int tastingAuraCount;
    private int tastingTechnical;
    private int tastingDrinkable;
    private Map<String, Integer> tastingAuras = Map.of();

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
            // The tasting table never bottles anything: the sample stays on it.
            case TASTING -> null;
        };
    }

    public boolean cycleReceiver() {
        if (kind() != ProcessBlock.Kind.DISTILLER) return false;
        // A whole run collects everything in one receiver, so cycling is meaningless.
        if (still != null && !still.fractional()) return false;
        receiver = receiver == Fraction.HEADS ? Fraction.HEARTS
                : receiver == Fraction.HEARTS ? Fraction.TAILS : Fraction.HEADS;
        setChanged();
        return true;
    }

    /**
     * Switches the pot still between a fractional run (heads / hearts / tails)
     * and a single whole run. The choice is locked while a charge is loaded.
     */
    public boolean toggleFractional() {
        if (kind() != ProcessBlock.Kind.DISTILLER || still != null) return false;
        fractional = !fractional;
        if (!fractional) receiver = Fraction.HEARTS;
        setChanged();
        return true;
    }

    public boolean isFractional() {
        return still != null ? still.fractional() : fractional;
    }

    /** Cycles the desired number of passes through the rectification column. */
    public boolean cycleColumnRuns() {
        if (kind() != ProcessBlock.Kind.RECTIFIER || stage == 3) return false;
        int next = columnRunsTarget >= 6 ? 1 : columnRunsTarget + 1;
        // Shortening the run can leave an already loaded sieve with no usable
        // pass. Hand it back rather than destroying it at the end of the run.
        if (sieve && batch != null && batch.columnRuns() + next < 4) {
            sieve = false;
            if (level != null && !level.isClientSide) {
                Block.popResource(level, worldPosition, new ItemStack(ModItems.MOLECULAR_SIEVE.get()));
            }
        }
        columnRunsTarget = next;
        setChanged();
        return true;
    }

    /** True when the requested passes will actually reach the sieve's 4th run. */
    private boolean canUseSieve() {
        return batch != null && batch.columnRuns() + columnRunsTarget >= 4;
    }

    public int columnRunsTarget() {
        return columnRunsTarget;
    }

    /** The receiver that actually collects product for the current mode. */
    private Fraction effectiveReceiver() {
        if (still != null && !still.fractional()) return Fraction.HEARTS;
        return receiver;
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
                return false;
            }
            additives.add(additive);
        }

        if (hasWater && (additives.size() > 0 || waterSlots > 1)) {
            return false;
        }
        if (additives.isEmpty() && !hasWater) {
            return false;
        }

        if (hasWater) {
            // A single bucket is the old dilution operation. It is deliberately
            // not part of a five-ingredient maceration.
            if (batch.abv() <= 40) {
                return false;
            }
            if (!fitsDilutionBasin(batch)) {
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
            setChanged();
            return true;
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
        setChanged();
        return true;
    }

    /** Adds one manually held additive to the first free tray slot. */
    private boolean addBlendAdditive(Player player, ItemStack stack) {
        if (!isBlendAdditive(stack)) return false;
        int slot = firstEmptyAdditiveSlot();
        if (slot < 0) {
            return true;
        }
        inventory.setItem(slot, stack.copyWithCount(1));
        // Keep the container tied to the stored tray item. startBlending
        // returns the bottle/bucket when it consumes the tray, so returning it
        // here as well would duplicate it.
        consume(player, stack, null);

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
            case TASTING -> refreshTasting();
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
        // The tasting table only reads the sample. Its input must stay in place,
        // so the shared load/consume automation has nothing to do here.
        if (kind() == ProcessBlock.Kind.TASTING) return;
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
        // Leave the bucket in the slot until the whole charge fits: accepting
        // a partial amount and still consuming the bucket destroyed water.
        if (MAX_COOLANT - coolant < COOLANT_PER_BUCKET) return;
        consumeSlot(SLOT_ADDITIVE);
        coolant += COOLANT_PER_BUCKET;
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
                } else if (stack.is(Items.POTATO)) {
                    origin = "potato";
                    bonus = -2;
                } else if (stack.is(Items.BEETROOT)) {
                    origin = "beetroot";
                    bonus = 0;
                } else if (stack.is(Items.SUGAR_CANE)) {
                    origin = "cane";
                    bonus = 1;
                } else if (stack.is(Items.APPLE)) {
                    origin = "apple";
                    bonus = 3;
                } else if (stack.is(Items.COCOA_BEANS)) {
                    origin = "cocoa";
                    bonus = 0;
                } else if (stack.is(Items.MELON_SLICE)) {
                    origin = "melon";
                    bonus = 1;
                } else if (stack.is(Items.CARROT)) {
                    origin = "carrot";
                    bonus = 2;
                } else if (stack.is(Items.CACTUS)) {
                    origin = "cactus";
                    bonus = 1;
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
                still = new StillRun(wash, fractional);
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
                columnRunsLeft = 0;
                sieve = false;
                active = false;
                return true;
            }
            case FILTER -> {
                Batch feed = readBatch(stack);
                if (feed == null || feed.abv() < 1) return false;
                batch = feed;
                stage = 1;
                progress = 0;
                duration = 0;
                active = false;
                return true;
            }
            case BLENDING -> {
                Batch feed = readBatch(stack);
                if (feed == null) return false;
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
                if (feed == null || !Simulation.canAge(feed.abv())) return false;
                batch = feed;
                stage = 1;
                progress = 0;
                duration = 0;
                active = false;
                return true;
            }
            // The tasting table keeps its sample in the slot; it is never
            // consumed into the process batch.
            case TASTING -> {
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
                // The sieve only fires on the 4th run. Consuming it for a
                // shorter run wasted the item, so leave it in the slot until
                // the player asks for enough passes.
                if (!canUseSieve()) return;
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
            case MASHING, DISTILLER, TASTING -> {
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
            Fraction chosen = effectiveReceiver();
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
            case DISTILLER, TASTING -> null;
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
                if (coolant == 0) {
                    pullCoolantFromCondensers(COOLANT_PER_BUCKET);
                }
            }
            if (still.consumePukeFlag()) {
                burst(ParticleTypes.LARGE_SMOKE, 16, 0.4, 0.03);
                level.playSound(null, worldPosition, SoundEvents.LAVA_EXTINGUISH,
                        SoundSource.BLOCKS, 0.9f, 0.7f);
            }
            if (still.finished() && !announcedDone) {
                announcedDone = true;
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
                columnRunsLeft = Math.max(1, columnRunsTarget);
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
            int nextRun = batch.columnRuns() + 1;
            boolean lastRun = columnRunsLeft <= 1;
            // The molecular sieve only helps once the column has already been
            // run four times; before that the spirit is still too wet.
            boolean useSieve = sieve && lastRun && nextRun >= 4;
            batch = Simulation.rectify(batch, useSieve);
            columnRunsLeft--;
            if (columnRunsLeft > 0) {
                progress = 0;
                duration = RECTIFY_TICKS;
                setChanged();
                burst(ParticleTypes.CLOUD, 8, 0.2, 0.01);
                return;
            }
            sieve = false;
            stage = 4;
            active = false;
            progress = duration;
            setChanged();
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
        active = true;
        progress++;
        if (progress >= duration) {
            batch = Simulation.filter(batch);
            stage = 4;
            active = false;
            progress = duration;
            setChanged();
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
            burst(ParticleTypes.SPLASH, 18, 0.3, 0.03);
            level.playSound(null, worldPosition, SoundEvents.BUCKET_EMPTY,
                    SoundSource.BLOCKS, 0.5f, 1.1f);
        } catch (IllegalArgumentException exception) {
            stage = 1;
            active = false;
            progress = 0;
            agingKind = "";
            blendComposition = "";
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
            burst(ParticleTypes.SPLASH, 24, 0.35, 0.03);
            level.playSound(null, worldPosition, SoundEvents.BUCKET_EMPTY,
                    SoundSource.BLOCKS, 0.6f, 1.2f);
        } catch (IllegalArgumentException exception) {
            stage = 1;
            active = false;
            progress = 0;
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
            burst(ParticleTypes.END_ROD, 14, 0.25, 0.01);
            level.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.BLOCKS, 0.45f, 1.4f);
        } catch (IllegalArgumentException exception) {
            stage = 1;
            active = false;
            progress = 0;
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
            case TASTING -> {
            }
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
        // On the tasting table the hydrometer is a sample, not a gauge pointed at
        // a running process, so this branch comes before the generic tool checks.
        if (kind() == ProcessBlock.Kind.TASTING) {
            setChanged();
            return useTasting(player, hand, stack);
        }
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
            case TASTING -> useTasting(player, hand, stack);
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
            } else if (stack.is(Items.POTATO)) {
                origin = "potato";
                bonus = -2;
            } else if (stack.is(Items.BEETROOT)) {
                origin = "beetroot";
                bonus = 0;
            } else if (stack.is(Items.SUGAR_CANE)) {
                origin = "cane";
                bonus = 1;
            } else if (stack.is(Items.APPLE)) {
                origin = "apple";
                bonus = 3;
            } else if (stack.is(Items.COCOA_BEANS)) {
                origin = "cocoa";
                bonus = 0;
            } else if (stack.is(Items.MELON_SLICE)) {
                origin = "melon";
                bonus = 1;
            } else if (stack.is(Items.CARROT)) {
                origin = "carrot";
                bonus = 2;
            } else if (stack.is(Items.CACTUS)) {
                origin = "cactus";
                bonus = 1;
            }
            if (origin != null) {
                consume(player, stack, container);
                batch = Simulation.mash(origin, bonus);
                stage = 3;
                progress = 0;
                duration = MASH_TICKS;
                settle = 0;
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.SUCCESS;
        }
        if (stage == 3) {
            return InteractionResult.SUCCESS;
        }
        return bottleJar(player, stack, ModItems.SWEET_WORT.get());
    }

    private InteractionResult useFermentation(Player player, InteractionHand hand, ItemStack stack) {
        if (stage == 0) {
            if (stack.getItem() instanceof ru.samogon.item.BatchItem) {
                Batch wort = BatchData.read(stack);
                if (wort.abv() > 0.5) {
                    return InteractionResult.SUCCESS;
                }
                batch = wort;
                stage = 1;
                consume(player, stack, Items.GLASS_BOTTLE);
                return InteractionResult.SUCCESS;
            }
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
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.SUCCESS;
        }
        if (stage == 2) {
            return InteractionResult.SUCCESS;
        }
        return bottleJar(player, stack, ModItems.FERMENTED_MASH.get());
    }

    private InteractionResult useDistiller(Player player, InteractionHand hand, ItemStack stack) {
        // Allow filling the condenser before loading a wash; the coolant is kept
        // when the pot is charged.
        if (stack.is(Items.WATER_BUCKET)) {
            // As with the standalone condenser, refuse a bucket that only
            // partly fits instead of consuming it and losing the remainder.
            if (MAX_COOLANT - coolant < COOLANT_PER_BUCKET) {
                return InteractionResult.SUCCESS;
            }
            coolant += COOLANT_PER_BUCKET;
            consume(player, stack, Items.BUCKET);
            return InteractionResult.SUCCESS;
        }
        if (still == null) {
            if (stack.getItem() instanceof ru.samogon.item.BatchItem) {
                Batch wash = BatchData.read(stack);
                if (wash.abv() < 1 || wash.volume() < 100) {
                    return InteractionResult.SUCCESS;
                }
                consume(player, stack, Items.GLASS_BOTTLE);
                batch = null;
                stage = 3;
                progress = 0;
                duration = 0;
                announcedDone = false;
                receiver = Fraction.HEARTS;
                still = new StillRun(wash, fractional);
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.SUCCESS;
        }
        if (stack.is(Items.GLASS_BOTTLE)) {
            Fraction chosen = effectiveReceiver();
            double available = still.cutVolume(chosen);
            if (available <= 0.0001) {
                return InteractionResult.SUCCESS;
            }
            int amount = (int) Math.min(BOTTLE, Math.ceil(available));
            Batch portion = still.drain(chosen, amount);
            if (portion == null) {
                return InteractionResult.SUCCESS;
            }
            consume(player, stack, null);
            giveOrDrop(player, BatchData.create(cutItem(chosen), portion));
            level.playSound(null, worldPosition, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 0.6f, 1.0f);
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
                return InteractionResult.SUCCESS;
            }
            if (!still.fractional()) {
                return InteractionResult.SUCCESS;
            }
            receiver = receiver == Fraction.HEADS ? Fraction.HEARTS
                    : receiver == Fraction.HEARTS ? Fraction.TAILS : Fraction.HEADS;
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.SUCCESS;
    }

    private InteractionResult useRectifier(Player player, InteractionHand hand, ItemStack stack) {
        if (stage == 0) {
            if (stack.getItem() instanceof ru.samogon.item.BatchItem) {
                Batch feed = BatchData.read(stack);
                if (feed.abv() < 1 || feed.volume() < 10) {
                    return InteractionResult.SUCCESS;
                }
                consume(player, stack, Items.GLASS_BOTTLE);
                batch = feed;
                stage = 1;
                settle = 0;
                columnRunsLeft = 0;
                sieve = false;
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.SUCCESS;
        }
        if (stage == 1 && !sieve && stack.is(ModItems.MOLECULAR_SIEVE.get())) {
            if (batch == null) {
                stage = 0;
                settle = 0;
                return InteractionResult.SUCCESS;
            }
            if (!canUseSieve()) {
                return InteractionResult.SUCCESS;
            }
            consume(player, stack, null);
            sieve = true;
            return InteractionResult.SUCCESS;
        }
        if (stage == 1) {
            return InteractionResult.SUCCESS;
        }
        if (stage == 3) {
            return InteractionResult.SUCCESS;
        }
        if (stage == 4) {
            if (batch == null) {
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
                    return InteractionResult.SUCCESS;
                }
                batch = feed;
                stage = 1;
                consume(player, stack, Items.GLASS_BOTTLE);
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.SUCCESS;
        }
        if (stage == 1 && stack.is(ModItems.ACTIVATED_CHARCOAL.get())) {
            if (batch == null) {
                stage = 0;
                return InteractionResult.SUCCESS;
            }
            consume(player, stack, null);
            stage = 3;
            progress = 0;
            duration = FILTER_TICKS;
            return InteractionResult.SUCCESS;
        }
        if (stage == 2 || stage == 1) {
            return InteractionResult.SUCCESS;
        }
        if (stage == 3) {
            return InteractionResult.SUCCESS;
        }
        return bottleSmall(player, stack, ModItems.FILTERED_SPIRIT.get());
    }

    private InteractionResult useBlending(Player player, InteractionHand hand, ItemStack stack) {
        if (stage == 0) {
            if (stack.getItem() instanceof ru.samogon.item.BatchItem) {
                Batch feed = BatchData.read(stack);
                if (feed.abv() < 1) {
                    return InteractionResult.SUCCESS;
                }
                batch = feed;
                stage = 1;
                blendComposition = "";
                consume(player, stack, Items.GLASS_BOTTLE);
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.SUCCESS;
        }
        if (stage == 1) {
            if (batch == null) {
                stage = 0;
                progress = 0;
                return InteractionResult.SUCCESS;
            }
            if (stack.isEmpty()) {
                if (player.isShiftKeyDown()) {
                    startBlending(player);
                }
                return InteractionResult.SUCCESS;
            }
            if (addBlendAdditive(player, stack)) {
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.SUCCESS;
        }
        if (stage == 3) {
            return InteractionResult.SUCCESS;
        }
        return bottleSmall(player, stack, blendOutput());
    }

    private InteractionResult useDilution(Player player, InteractionHand hand, ItemStack stack) {
        if (stage == 0) {
            if (stack.getItem() instanceof ru.samogon.item.BatchItem) {
                Batch feed = BatchData.read(stack);
                if (feed.abv() <= 40) {
                    return InteractionResult.SUCCESS;
                }
                if (!fitsDilutionBasin(feed)) {
                    return InteractionResult.SUCCESS;
                }
                batch = feed;
                stage = 1;
                consume(player, stack, Items.GLASS_BOTTLE);
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.SUCCESS;
        }
        if (stage == 1) {
            if (stack.is(Items.WATER_BUCKET)) {
                if (batch == null) {
                    stage = 0;
                    return InteractionResult.SUCCESS;
                }
                consume(player, stack, Items.BUCKET);
                stage = 3;
                progress = 0;
                duration = DILUTION_TICKS;
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.SUCCESS;
        }
        if (stage == 3) {
            return InteractionResult.SUCCESS;
        }
        return bottleSmall(player, stack, ModItems.DILUTED_SPIRIT.get());
    }

    private InteractionResult useAging(Player player, InteractionHand hand, ItemStack stack) {
        if (stage == 0) {
            if (stack.getItem() instanceof ru.samogon.item.BatchItem) {
                Batch feed = BatchData.read(stack);
                if (!Simulation.canAge(feed.abv())) {
                    return InteractionResult.SUCCESS;
                }
                batch = feed;
                stage = 1;
                consume(player, stack, Items.GLASS_BOTTLE);
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.SUCCESS;
        }
        if (stage == 1) {
            if (batch == null) {
                stage = 0;
                progress = 0;
                return InteractionResult.SUCCESS;
            }
            if (stack.is(ModItems.OAK_CHIPS.get())) {
                consume(player, stack, null);
                stage = 3;
                progress = 0;
                duration = AGE_TICKS;
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.SUCCESS;
        }
        if (stage == 3) {
            return InteractionResult.SUCCESS;
        }
        return bottleSmall(player, stack, ModItems.OAK_AGED_SPIRIT.get());
    }

    /**
     * The tasting table reads a bottle with a hydrometer. The drink is copied
     * into a dedicated slot instead of being poured into the machine, so the
     * sample is never consumed, never bottled, and can be taken back at any
     * time without losing a drop.
     */
    private InteractionResult useTasting(Player player, InteractionHand hand, ItemStack stack) {
        if (stack.getItem() instanceof BatchItem) {
            if (!inventory.getItem(SLOT_INPUT).isEmpty()) {
                player.displayClientMessage(Component.translatable("message.samogon.tasting.occupied"), true);
                return InteractionResult.SUCCESS;
            }
            inventory.setItem(SLOT_INPUT, stack.copyWithCount(1));
            consume(player, stack, null);
            refreshTasting();
            player.displayClientMessage(Component.translatable("message.samogon.tasting.drink_added"), true);
            return InteractionResult.SUCCESS;
        }
        if (stack.is(ModItems.HYDROMETER.get())) {
            if (!inventory.getItem(SLOT_ADDITIVE).isEmpty()) {
                player.displayClientMessage(Component.translatable("message.samogon.tasting.gauge_present"), true);
                return InteractionResult.SUCCESS;
            }
            inventory.setItem(SLOT_ADDITIVE, stack.copyWithCount(1));
            consume(player, stack, null);
            refreshTasting();
            player.displayClientMessage(Component.translatable("message.samogon.tasting.gauge_added"), true);
            return InteractionResult.SUCCESS;
        }
        showTastingStatus(player);
        return InteractionResult.SUCCESS;
    }

    /**
     * Recomputes the tasting readout from the two loaded items. The result is
     * derived fresh every tick (and on load) instead of being stored in NBT:
     * the bottle already carries the whole production history, so persisting a
     * second copy could only drift or duplicate the sample.
     */
    private void refreshTasting() {
        ItemStack drink = inventory.getItem(SLOT_INPUT);
        ItemStack gauge = inventory.getItem(SLOT_ADDITIVE);
        Batch sample = drink.getItem() instanceof BatchItem && gauge.is(ModItems.HYDROMETER.get())
                ? readBatch(drink) : null;
        if (sample == null) {
            tastingBatch = null;
            tastingValid = false;
            tastingType = 0;
            tastingIntox = 0;
            tastingDuration = 0;
            tastingAura = 0;
            tastingAuraLevel = 0;
            tastingAuraCount = 0;
            tastingTechnical = 0;
            tastingDrinkable = 0;
            tastingAuras = Map.of();
            return;
        }
        BatchItem item = (BatchItem) drink.getItem();
        SpiritType type = item.typeOf(sample);
        BatchItem.Tincture tincture = item.effectiveTincture(sample);
        Map<String, Integer> auras = Intoxication.aurasFor(sample, tincture);
        String dominant = Intoxication.dominantAura(auras);
        int aura = Intoxication.auraIndex(dominant);
        tastingBatch = sample;
        tastingValid = true;
        tastingType = type.ordinal();
        tastingIntox = Intoxication.baseLevel(sample);
        tastingDuration = Math.round(Intoxication.durationTicks(sample) / 20.0F);
        tastingAura = aura + 1;
        tastingAuraLevel = dominant == null ? 0 : auras.getOrDefault(dominant, 0);
        tastingAuraCount = auras.size();
        tastingAuras = auras;
        tastingTechnical = item.technical() && sample.abv() > 0.1 ? 1 : 0;
        tastingDrinkable = item.canDrink(sample) ? 1 : 0;
    }

    private void showTastingStatus(Player player) {
        player.displayClientMessage(Component.translatable("message.samogon.status.header",
                getBlockState().getBlock().getName()), false);
        if (!tastingValid || tastingBatch == null) {
            player.displayClientMessage(Component.translatable("message.samogon.tasting.incomplete"), false);
            return;
        }
        SpiritType type = SpiritType.values()[Math.floorMod(tastingType, SpiritType.values().length)];
        player.displayClientMessage(Component.translatable("message.samogon.tasting.result",
                Component.translatable(type.translationKey()), f(tastingBatch.abv()),
                Quality.label(tastingBatch.quality()), tastingBatch.impurities()), false);
        player.displayClientMessage(Component.translatable("message.samogon.tasting.effects",
                roman(tastingIntox), tastingDuration,
                tastingAuraCount > 0
                        ? Intoxication.auraSummary(tastingAuras)
                        : Component.translatable("message.samogon.none")), false);
        if (tastingTechnical == 1) {
            player.displayClientMessage(Component.translatable("message.samogon.tasting.technical"), false);
        }
        if (tastingDrinkable == 0) {
            player.displayClientMessage(Component.translatable("message.samogon.tasting.not_ready"), false);
        }
    }

    private static String roman(int level) {
        return switch (Math.max(0, Math.min(5, level))) {
            case 0 -> "-";
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            default -> "V";
        };
    }

    private InteractionResult bottleJar(Player player, ItemStack stack, Item item) {
        if (!stack.is(Items.GLASS_BOTTLE)) {
            return InteractionResult.SUCCESS;
        }
        if (batch == null) {
            return InteractionResult.SUCCESS;
        }
        Batch portion = takePortion(JAR);
        consume(player, stack, null);
        giveOrDrop(player, BatchData.create(item, portion));
        level.playSound(null, worldPosition, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 0.7f, 0.9f);
        return InteractionResult.SUCCESS;
    }

    private InteractionResult bottleSmall(Player player, ItemStack stack, Item item) {
        if (!stack.is(Items.GLASS_BOTTLE)) {
            return InteractionResult.SUCCESS;
        }
        if (batch == null) {
            return InteractionResult.SUCCESS;
        }
        Batch portion = takePortion(BOTTLE);
        consume(player, stack, null);
        giveOrDrop(player, BatchData.create(item, portion));
        level.playSound(null, worldPosition, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 0.7f, 1.0f);
        return InteractionResult.SUCCESS;
    }

    private Batch takePortion(double amount) {
        double volume = Math.min(amount, batch.volume());
        Batch portion = new Batch(volume, batch.abv(), batch.quality(), batch.impurities(), batch.passes(),
                batch.filtered(), batch.origin(), batch.age(), batch.composition(),
                batch.rectified(), batch.oak(), batch.cut(), batch.columnRuns());
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
                f(batch.abv()), f(batch.volume()), Quality.label(batch.quality()), batch.impurities()), false);
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
        tag.putBoolean("Fractional", fractional);
        tag.putInt("ColumnRunsTarget", columnRunsTarget);
        tag.putInt("ColumnRunsLeft", columnRunsLeft);
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
        fractional = !tag.contains("Fractional") || tag.getBoolean("Fractional");
        columnRunsTarget = tag.contains("ColumnRunsTarget") ? tag.getInt("ColumnRunsTarget") : 1;
        columnRunsLeft = tag.getInt("ColumnRunsLeft");
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
        // Tasting values are derived, never stored; rebuild them from the two
        // slots so a reloaded world shows the same report.
        refreshTasting();
    }
}
