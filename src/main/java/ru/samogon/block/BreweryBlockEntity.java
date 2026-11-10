package ru.samogon.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import ru.samogon.item.BatchData;
import ru.samogon.item.BatchItem;
import ru.samogon.menu.BreweryMenu;
import ru.samogon.process.BatchNbt;
import ru.samogon.process.BeerStyle;
import ru.samogon.process.BreweryData;
import ru.samogon.process.Brewing;
import ru.samogon.process.Environment;
import ru.samogon.process.Simulation.Batch;
import ru.samogon.registry.ModBlocks;
import ru.samogon.registry.ModItems;

/**
 * The brewing house: one vessel that turns grain and water into sweet wort
 * and boils it with hops. Fermentation is deliberately not part of this
 * machine — hopped wort leaves here in glass bottles and is turned into beer
 * in the separate beer fermentation barrel.
 *
 * <p>Only the master part ({@link BreweryBlock#PART} 0) owns a block entity.
 * A single internal {@link Batch} carries the liquid from step to step; its
 * origin records which step produced it. The selected GUI tab chooses the step
 * the player wants to run, and the machine pulls the matching ingredients from
 * its three slots on its own:</p>
 * <ul>
 *   <li>mash — grain (malt or wheat) + a water bucket;</li>
 *   <li>boil — wort + hops, over a heat source.</li>
 * </ul>
 * A glass bottle in the output slot is filled with whatever stage the vessel
 * currently holds. Brewing never happens in the mash tun or the fermentation
 * barrel: those reject brewery batches.</p>
 */
public final class BreweryBlockEntity extends BlockEntity {
    public static final int SLOT_INPUT = 0;
    public static final int SLOT_ADDITIVE = 1;
    public static final int SLOT_OUTPUT = 2;
    public static final int SLOT_COUNT = 3;

    public static final int MASH_TICKS = 400;
    public static final int BOIL_TICKS = 600;

    private final SimpleContainer inventory = new SimpleContainer(SLOT_COUNT) {
        @Override
        public void setChanged() {
            super.setChanged();
            BreweryBlockEntity.this.setChanged();
        }
    };

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            Batch b = brew;
            return switch (index) {
                case BreweryData.TAB -> tab;
                case BreweryData.STAGE -> stage();
                case BreweryData.PROGRESS -> progressPercent();
                case BreweryData.ACTIVE -> running && isProgressing() ? 1 : 0;
                case BreweryData.READY -> brew != null && !running ? 1 : 0;
                case BreweryData.TEMPERATURE -> (int) Math.round(temperature * 10.0);
                case BreweryData.AMBIENT -> (int) Math.round(ambient * 10.0);
                case BreweryData.VOLUME -> b == null ? 0 : (int) Math.round(b.volume());
                case BreweryData.ABV -> b == null ? 0 : (int) Math.round(b.abv() * 10.0);
                case BreweryData.QUALITY -> b == null ? 0 : b.quality();
                case BreweryData.IMPURITIES -> b == null ? 0 : b.impurities();
                case BreweryData.STEP -> Brewing.stageOf(b);
                case BreweryData.DURATION -> duration;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            // The client only mirrors these values.
        }

        @Override
        public int getCount() {
            return BreweryData.COUNT;
        }
    };

    /** Selected process tab: 0 mash, 1 boil. */
    private int tab;
    /** The liquid inside the vessel, at any brewing stage. */
    private Batch brew;
    /** True while the selected step is running. */
    private boolean running;
    private int progress;
    private int duration;
    private int tickCounter;
    private double temperature = 20;
    private double ambient = 20;
    /** Grain chosen for the current mash. */
    private String mashGrain = Brewing.DEFAULT_GRAIN;

    public BreweryBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.BREWERY_ENTITY.get(), pos, state);
    }

    public SimpleContainer inventory() {
        return inventory;
    }

    public ContainerData data() {
        return data;
    }

    /** 0 empty, 1 holding a brew, 2 running a step. */
    private int stage() {
        if (running) return 2;
        return brew != null ? 1 : 0;
    }

    public void openMenu(Player player) {
        if (!(player instanceof ServerPlayer server)) return;
        server.openMenu(new SimpleMenuProvider(
                        (id, playerInventory, ignored) -> BreweryMenu.server(id, playerInventory, this),
                        getBlockState().getBlock().getName()),
                buffer -> buffer.writeBlockPos(worldPosition));
    }

    /** Switches the selected step. A running step cannot be changed. */
    public boolean setTab(int newTab) {
        if (running || newTab < 0 || newTab > 1 || newTab == tab) return false;
        tab = newTab;
        setChanged();
        return true;
    }

    // === TICKING ===

    public void tick() {
        if (level == null || level.isClientSide) return;
        if (!(getBlockState().getBlock() instanceof BreweryBlock)
                || getBlockState().getValue(BreweryBlock.PART) != 0) {
            return;
        }
        tickCounter++;
        if (tickCounter % 20 == 0) ambient = Environment.temperature(level, worldPosition);

        if (!running) {
            if (brew == null) pullIntermediate();
            tryStart();
        }
        if (running) advance();
        if (!running) tryBottle();

        updateTemperature();
        updateVisualState();
        if (tickCounter % 100 == 0) setChanged();
    }

    /**
     * Loads a bottled intermediate (sweet or hopped wort) from the input slot,
     * so a player can move a brew between steps by hand as well as by running
     * the previous step in place.
     */
    private void pullIntermediate() {
        ItemStack input = inventory.getItem(SLOT_INPUT);
        if (!(input.getItem() instanceof BatchItem)) return;
        Batch batch = readBatch(input);
        if (batch == null || !Brewing.isBrew(batch)) return;
        int step = Brewing.stageOf(batch);
        if (step != Brewing.STAGE_WORT && step != Brewing.STAGE_HOPPED) return;
        brew = batch;
        consumeSlot(SLOT_INPUT);
        returnContainer(Items.GLASS_BOTTLE);
        setChanged();
    }

    private void tryStart() {
        switch (tab) {
            case 0 -> tryStartMash();
            case 1 -> tryStartBoil();
            default -> {
            }
        }
    }

    private void tryStartMash() {
        if (brew != null) return;
        ItemStack grainStack = inventory.getItem(SLOT_INPUT);
        String grain = grainOf(grainStack);
        if (grain == null) return;
        if (!inventory.getItem(SLOT_ADDITIVE).is(Items.WATER_BUCKET)) return;
        consumeSlot(SLOT_INPUT);
        consumeSlot(SLOT_ADDITIVE);
        returnContainer(Items.BUCKET);
        mashGrain = grain;
        begin(MASH_TICKS);
    }

    private void tryStartBoil() {
        if (brew == null || Brewing.stageOf(brew) != Brewing.STAGE_WORT) return;
        if (!inventory.getItem(SLOT_ADDITIVE).is(ModItems.HOPS.get())) return;
        if (Environment.heatStrength(level, worldPosition.below()) <= 0) return;
        consumeSlot(SLOT_ADDITIVE);
        begin(BOIL_TICKS);
    }

    private void begin(int ticks) {
        running = true;
        progress = 0;
        duration = ticks;
        setChanged();
        burst(ParticleTypes.CAMPFIRE_COSY_SMOKE, 6, 0.4, 0.01);
        if (level != null) {
            level.playSound(null, worldPosition, SoundEvents.BREWING_STAND_BREW,
                    SoundSource.BLOCKS, 0.5f, 1.0f);
        }
    }

    private void advance() {
        switch (tab) {
            case 0 -> {
                progress++;
                if (progress >= duration) completeMash();
            }
            case 1 -> {
                if (Environment.heatStrength(level, worldPosition.below()) <= 0) return;
                progress++;
                if (progress >= duration) completeBoil();
            }
            default -> {
            }
        }
    }

    private void completeMash() {
        brew = Brewing.wort(mashGrain);
        finishRun();
        burst(ParticleTypes.SPORE_BLOSSOM_AIR, 12, 0.35, 0.02);
        level.playSound(null, worldPosition, SoundEvents.FIRE_EXTINGUISH,
                SoundSource.BLOCKS, 0.4f, 1.2f);
    }

    private void completeBoil() {
        if (brew == null) {
            reset();
            return;
        }
        brew = Brewing.boil(brew);
        finishRun();
        burst(ParticleTypes.CLOUD, 16, 0.3, 0.02);
        level.playSound(null, worldPosition, SoundEvents.BREWING_STAND_BREW,
                SoundSource.BLOCKS, 0.6f, 1.15f);
    }

    private void finishRun() {
        running = false;
        progress = 0;
        duration = 0;
        setChanged();
    }

    private void reset() {
        running = false;
        progress = 0;
        duration = 0;
        setChanged();
    }

    // === BOTTLING ===

    private void tryBottle() {
        if (brew == null) return;
        ItemStack output = inventory.getItem(SLOT_OUTPUT);
        int stage = Brewing.stageOf(brew);
        Item container = containerFor(stage);
        if (container == null || !output.is(container) || output.getCount() != 1) return;
        Item item = bottleItem(stage);
        if (item == null) return;
        Batch portion = takePortion(bottleVolume(item));
        if (portion == null) return;
        inventory.setItem(SLOT_OUTPUT, BatchData.create(item, portion));
        if (level != null) {
            level.playSound(null, worldPosition, SoundEvents.BOTTLE_FILL,
                    SoundSource.BLOCKS, 0.7f, 0.95f);
        }
        setChanged();
    }

    /**
     * The empty vessel required at the output slot for each stage. Wort leaves
     * in a glass bottle; the mug case only drains a batch left over from an
     * older world where the brewery still fermented beer itself.
     */
    private static Item containerFor(int stage) {
        return switch (stage) {
            case Brewing.STAGE_WORT, Brewing.STAGE_HOPPED -> Items.GLASS_BOTTLE;
            case Brewing.STAGE_BEER -> ModItems.WOODEN_MUG.get();
            default -> null;
        };
    }

    private static Item bottleItem(int stage) {
        return switch (stage) {
            case Brewing.STAGE_WORT -> ModItems.BEER_WORT.get();
            case Brewing.STAGE_HOPPED -> ModItems.HOPPED_WORT.get();
            case Brewing.STAGE_BEER -> ModItems.BEER.get();
            default -> null;
        };
    }

    private static double bottleVolume(Item item) {
        return item instanceof BatchItem batch && batch.defaultVolume() > 0 ? batch.defaultVolume() : 500;
    }

    /** Removes one bottle's worth and clears the vessel when the last drop leaves. */
    private Batch takePortion(double amount) {
        if (brew == null) return null;
        double volume = Math.min(amount, brew.volume());
        Batch portion = new Batch(volume, brew.abv(), brew.quality(), brew.impurities(),
                brew.passes(), brew.filtered(), brew.origin(), brew.age(), brew.composition(),
                brew.rectified(), brew.oak(), brew.cut(), brew.columnRuns());
        double remaining = brew.volume() - volume;
        if (remaining <= 1.0E-9) {
            brew = null;
            reset();
        } else {
            brew = brew.withVolume(remaining);
        }
        return portion;
    }

    /** Releases every slot and bottles whatever liquid is still inside. */
    public void dropContents() {
        if (level == null) return;
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty()) {
                Block.popResource(level, worldPosition, stack.copy());
                inventory.setItem(slot, ItemStack.EMPTY);
            }
        }
        Item item = bottleItem(Brewing.stageOf(brew));
        int guard = 0;
        while (brew != null && item != null && guard++ < 64) {
            Batch portion = takePortion(bottleVolume(item));
            if (portion == null) break;
            Block.popResource(level, worldPosition, BatchData.create(item, portion));
        }
        brew = null;
        reset();
    }

    // === ENVIRONMENT ===

    private boolean isProgressing() {
        return switch (tab) {
            case 0 -> true;
            case 1 -> Environment.heatStrength(level, worldPosition.below()) > 0;
            default -> false;
        };
    }

    private void updateTemperature() {
        double heat = Environment.heatStrength(level, worldPosition.below());
        double target;
        double rate;
        if (heat > 0) {
            target = running && tab == 1 ? 100 : Math.min(90, ambient + heat * 25);
            rate = running ? 0.02 : 0.01;
        } else {
            target = ambient;
            rate = 0.01;
        }
        temperature += (target - temperature) * rate;
    }

    private void updateVisualState() {
        boolean active = running && isProgressing();
        boolean ready = brew != null && !running;
        int band;
        if (running) {
            band = duration > 0 ? 1 + (int) Math.min(3, progress * 4L / duration) : 1;
        } else {
            band = ready ? 4 : 0;
        }
        BlockState state = getBlockState();
        if (state.getValue(BreweryBlock.ACTIVE) != active
                || state.getValue(BreweryBlock.PROGRESS) != band
                || state.getValue(BreweryBlock.READY) != ready) {
            level.setBlock(worldPosition, state
                    .setValue(BreweryBlock.ACTIVE, active)
                    .setValue(BreweryBlock.PROGRESS, band)
                    .setValue(BreweryBlock.READY, ready), Block.UPDATE_CLIENTS);
        }
    }

    private int progressPercent() {
        return duration <= 0 ? 0 : (int) Math.min(100, progress * 100L / duration);
    }

    private void burst(ParticleOptions particle, int count, double spread, double speed) {
        if (!(level instanceof ServerLevel server)) return;
        server.sendParticles(particle, worldPosition.getX() + 1.0, worldPosition.getY() + 0.9,
                worldPosition.getZ() + 1.0, count, spread, 0.16, spread, speed);
    }

    // === HELPERS ===

    /** Grain encoded by an input stack, or null when it is not a mash grain. */
    private static String grainOf(ItemStack stack) {
        if (stack.is(ModItems.MALT.get())) return BeerStyle.Grain.BARLEY.id();
        if (stack.is(ModItems.ROASTED_MALT.get())) return BeerStyle.Grain.ROASTED.id();
        if (stack.is(Items.WHEAT)) return BeerStyle.Grain.WHEAT.id();
        return null;
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

    /** Empty buckets and bottles are returned to the world so the slots stay free. */
    private void returnContainer(Item container) {
        if (container == null || level == null) return;
        Block.popResource(level, worldPosition, new ItemStack(container));
    }

    // === PERSISTENCE ===

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Tab", tab);
        tag.putBoolean("Running", running);
        tag.putInt("Progress", progress);
        tag.putInt("Duration", duration);
        tag.putDouble("Temperature", temperature);
        tag.putDouble("Ambient", ambient);
        tag.putString("MashGrain", mashGrain);
        ContainerHelper.saveAllItems(tag, inventory.getItems(), registries);
        if (brew != null) tag.put("Brew", BatchNbt.save(brew));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        tab = Math.max(0, Math.min(1, tag.getInt("Tab")));
        // A world saved while the brewery still ran a fermentation step may
        // hold running=true with the old tab 2; there is no such step anymore,
        // so let the vessel settle instead of boiling or mashing by mistake.
        running = tag.getBoolean("Running") && tag.getInt("Tab") <= 1;
        progress = Math.max(0, tag.getInt("Progress"));
        duration = Math.max(0, tag.getInt("Duration"));
        temperature = tag.contains("Temperature") ? tag.getDouble("Temperature") : 20;
        ambient = tag.contains("Ambient") ? tag.getDouble("Ambient") : 20;
        mashGrain = tag.contains("MashGrain") ? tag.getString("MashGrain") : Brewing.DEFAULT_GRAIN;
        NonNullList<ItemStack> items = inventory.getItems();
        for (int slot = 0; slot < items.size(); slot++) items.set(slot, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, registries);
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            ItemStack stack = items.get(slot);
            if (stack.isEmpty()) continue;
            if (slot == SLOT_OUTPUT && stack.getCount() > 1) stack.setCount(1);
        }
        brew = null;
        if (tag.contains("Brew")) {
            try {
                brew = BatchNbt.load(tag.getCompound("Brew"));
            } catch (RuntimeException ignored) {
                brew = null;
            }
        }
    }
}