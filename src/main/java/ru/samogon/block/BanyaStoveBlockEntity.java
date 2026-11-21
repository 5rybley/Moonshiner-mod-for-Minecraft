package ru.samogon.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraftforge.common.ForgeHooks;
import ru.samogon.menu.BanyaStoveMenu;
import ru.samogon.config.SamogonConfig;
import ru.samogon.network.BanyaSteamPayload;
import ru.samogon.network.ModNetwork;
import ru.samogon.process.Environment;
import ru.samogon.registry.ModBlocks;
import ru.samogon.registry.ModEffects;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The firebox of the banya stove.
 *
 * <p>The stove does not heat a single block: it heats the whole closed room it
 * stands in. {@link BanyaRoom} floods the air around the stove, and the volume
 * of that room is the heat capacity of the banya - a small washroom warms up in
 * a minute, a big steam room takes several. The lever in the interface sets the
 * target temperature and the rate of burning; coal in the firebox supplies the
 * heat. Without fuel the stones cool back to the local ambient temperature.</p>
 *
 * <p>Steam appears on its own once the room is warm enough, and the heat cures
 * a hangover. The interface only mirrors the firebox; all physics happens
 * here.</p>
 */
public final class BanyaStoveBlockEntity extends BlockEntity {
    public static final int SLOT_FUEL = 0;
    public static final int SLOT_COUNT = 1;
    public static final int MAX_POWER = 100;
    /** Highest temperature the stones can reach, in degrees Celsius. */
    public static final double MAX_TEMPERATURE = 120.0;
    /** The room starts to fill with visible steam at this temperature. */
    public static final double STEAM_TEMPERATURE = 40.0;
    /** The heat starts curing a hangover at this temperature. */
    public static final double CURE_TEMPERATURE = 55.0;
    /**
     * Steam and the cure are measured above the local ambient temperature, so
     * a desert or the Nether does not turn a cold stove into a free steam room.
     */
    public static final double STEAM_ABOVE_AMBIENT = 8.0;
    public static final double CURE_ABOVE_AMBIENT = 15.0;

    /** Continuous ticks in a hot banya that earn the after-banya glow; the live value comes from the config. */
    public static final int DEFAULT_SOAK_TICKS = 20 * 60;
    /** Default glow length after leaving; the live value comes from the config. */
    public static final int DEFAULT_GLOW_TICKS = 20 * 60 * 5;
    /** Soak is measured twice a second, in step with the steam sync. */
    private static final int SOAK_STEP = 10;

    public static final int DATA_BURN = 0;
    public static final int DATA_BURN_TOTAL = 1;
    public static final int DATA_POWER = 2;
    public static final int DATA_TEMPERATURE = 3;
    public static final int DATA_TARGET = 4;
    public static final int DATA_ROOM_VOLUME = 5;
    public static final int DATA_ROOM_VALID = 6;
    public static final int DATA_STEAM = 7;
    public static final int DATA_COUNT = 8;

    /** Every 5 seconds a warm room is rescanned; an open box is retried faster. */
    private static final int RESCAN_WARM = 100;
    private static final int RESCAN_COLD = 60;
    /** Degrees per second at full power in a room of eight blocks. */
    private static final double HEAT_RATE = 12.0;
    /** Degrees per second the stones lose in a room of eight blocks. */
    private static final double COOL_RATE = 2.5;

    private final SimpleContainer inventory = new SimpleContainer(SLOT_COUNT) {
        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return isFuel(stack);
        }

        @Override
        public void setChanged() {
            super.setChanged();
            BanyaStoveBlockEntity.this.setChanged();
        }
    };

    private final ContainerData machineData = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_BURN -> burnTime;
                case DATA_BURN_TOTAL -> burnTotal;
                case DATA_POWER -> power;
                case DATA_TEMPERATURE -> temperatureTenths();
                case DATA_TARGET -> targetTenths();
                case DATA_ROOM_VOLUME -> room.valid() ? room.volume() : 0;
                case DATA_ROOM_VALID -> room.valid() ? 1 : 0;
                case DATA_STEAM -> temperature >= steamThreshold() ? 1 : 0;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            if (index == DATA_POWER) {
                setPower(value);
            }
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    private int burnTime;
    private int burnTotal;
    /** Fractional fuel ticks, so a low lever setting really does burn less coal. */
    private double fuelFraction;
    private int power = 50;
    private double temperature = 20.0;
    private int ambient = 20;
    private BanyaRoom room = BanyaRoom.NONE;
    private int scanCooldown;
    /** Ticks each player has spent in the hot room, keyed by their id. */
    private final Map<UUID, Integer> soakTicks = new HashMap<>();

    public BanyaStoveBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.BANYA_STOVE_ENTITY.get(), pos, state);
    }

    public SimpleContainer inventory() {
        return inventory;
    }

    public ContainerData data() {
        return machineData;
    }

    public int power() {
        return power;
    }

    public double temperature() {
        return temperature;
    }

    public BanyaRoom room() {
        return room;
    }

    public double targetTemperature() {
        return ambient + power / (double) MAX_POWER * (MAX_TEMPERATURE - 5.0 - ambient);
    }

    /** Real steam threshold: hot air is not enough in an already hot climate. */
    public double steamThreshold() {
        return Math.max(STEAM_TEMPERATURE, ambient + STEAM_ABOVE_AMBIENT);
    }

    /** Real cure threshold, likewise lifted by a hot climate. */
    public double cureThreshold() {
        return Math.max(CURE_TEMPERATURE, ambient + CURE_ABOVE_AMBIENT);
    }

    public void setPower(int value) {
        int clamped = Math.max(0, Math.min(MAX_POWER, value));
        if (clamped != power) {
            power = clamped;
            setChanged();
        }
    }

    /** True when the stack can be burned in the firebox. */
    public static boolean isFuel(ItemStack stack) {
        return fuelTicks(stack) > 0;
    }

    /**
     * Burn time in ticks, or zero for a non-fuel item.
     *
     * <p>Goes through {@link ForgeHooks#getBurnTime} rather than
     * {@link ItemStack#getBurnTime} because Forge reports {@code -1} from that
     * method for every item that does not override it, including coal and
     * planks. The hook turns that {@code -1} into the vanilla furnace fuel
     * table and fires the burn-time event, which is exactly what a vanilla
     * furnace does. Reading {@code ItemStack#getBurnTime} directly and clamping
     * it with {@code Math.max(0, ...)} silently turned every ordinary fuel into
     * a non-fuel, leaving strong spirit as the only thing the firebox accepted.
     */
    public static int fuelTicks(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        return Math.max(0, ForgeHooks.getBurnTime(stack, RecipeType.SMELTING));
    }

    public void openMenu(Player player) {
        if (!(player instanceof ServerPlayer server)) {
            return;
        }
        // Show the room volume as it is right now, not as it was up to five
        // seconds ago: the player may have just finished the last wall.
        rescanNow();
        server.openMenu(new SimpleMenuProvider(
                        (id, playerInventory, ignored) -> BanyaStoveMenu.server(id, playerInventory, this),
                        getBlockState().getBlock().getName()),
                buffer -> buffer.writeBlockPos(worldPosition));
    }

    /** Forces an immediate room scan; returns the room that was found. */
    public BanyaRoom rescanNow() {
        if (level != null) {
            room = BanyaRoom.scan(level, worldPosition, BanyaRoom.DEFAULT_LIMIT);
            scanCooldown = room.valid() ? RESCAN_WARM : RESCAN_COLD;
        }
        return room;
    }

    public void dropContents() {
        if (level == null) {
            return;
        }
        ItemStack fuel = inventory.removeItemNoUpdate(SLOT_FUEL);
        if (!fuel.isEmpty()) {
            Block.popResource(level, worldPosition, fuel);
        }
    }

    public void tick() {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        if (--scanCooldown <= 0) {
            room = BanyaRoom.scan(server, worldPosition, BanyaRoom.DEFAULT_LIMIT);
            scanCooldown = room.valid() ? RESCAN_WARM : RESCAN_COLD;
        }
        ambient = (int) Math.round(Environment.temperature(server, worldPosition));

        double target = targetTemperature();
        if (power <= 0 || !room.valid()) {
            // No lever and no closed banya mean no fire: the coals die instead
            // of idling forever while the stones hold their heat.
            burnTime = 0;
            fuelFraction = 0.0;
        }
        boolean wantsHeat = power > 0 && room.valid() && temperature < target - 0.05;
        if (burnTime <= 0 && wantsHeat) {
            ignite();
        }

        if (burnTime > 0) {
            // The lever is a thermostat and a damper at once: a full blast
            // below the target, and a banked, smouldering fire that still eats
            // a little coal once the stones are at temperature.
            double powerFactor = power / (double) MAX_POWER;
            if (temperature < target - 0.05) {
                burnFuel(4.0 * powerFactor);
                double perTick = HEAT_RATE / Math.sqrt(Math.max(8.0, room.volume()))
                        * powerFactor / 20.0;
                temperature = Math.min(target, temperature + perTick);
            } else {
                burnFuel(0.25 + 0.75 * powerFactor);
                if (temperature > target + 0.05) {
                    // The lever was pulled down: bleed the excess heat down to
                    // the new setting instead of holding the old one forever.
                    double perTick = COOL_RATE / Math.sqrt(Math.max(8.0, room.volume())) / 20.0;
                    temperature = Math.max(target, temperature - perTick);
                }
            }
        } else {
            fuelFraction = 0.0;
            double perTick = COOL_RATE / Math.sqrt(Math.max(8.0, room.volume())) / 20.0;
            temperature = Math.max(ambient, temperature - perTick);
        }
        temperature = Math.max(ambient, Math.min(MAX_TEMPERATURE, temperature));

        boolean nowLit = burnTime > 0;
        BlockState state = getBlockState();
        if (state.hasProperty(BanyaStoveBlock.LIT) && state.getValue(BanyaStoveBlock.LIT) != nowLit) {
            server.setBlock(worldPosition, state.setValue(BanyaStoveBlock.LIT, nowLit), Block.UPDATE_CLIENTS);
        }

        // The stones and the fire change every tick, so nothing here marks the
        // chunk dirty on its own. Without an occasional mark the heat and the
        // remaining burn would be forgotten when the chunk unloads.
        if (server.getGameTime() % 100 == 0) {
            setChanged();
        }

        if (server.getGameTime() % SOAK_STEP == 0) {
            trackSoak(server);
        }
        if (!room.valid()) {
            return;
        }
        if (temperature >= steamThreshold()) {
            steam(server);
        }
        if (temperature >= cureThreshold()) {
            cure(server);
        }
        if (server.getGameTime() % 10 == 0) {
            syncSteam(server);
        }
    }

    /** Burns part of the current fuel; the fraction is carried to the next tick. */
    private void burnFuel(double amount) {
        fuelFraction += amount;
        int consume = (int) fuelFraction;
        if (consume <= 0) {
            return;
        }
        fuelFraction -= consume;
        burnTime = Math.max(0, burnTime - consume);
    }

    /** Pulls one item out of the fuel slot and lights it. */
    private void ignite() {
        ItemStack fuel = inventory.getItem(SLOT_FUEL);
        int ticks = fuelTicks(fuel);
        if (ticks <= 0) {
            return;
        }
        ItemStack remainder = fuel.getCraftingRemainingItem();
        fuel.shrink(1);
        if (!remainder.isEmpty()) {
            // Never park a non-fuel remainder in the only fuel slot: the empty
            // spirit bottle is not fuel, so leaving it there would stall the
            // stove until the player opened the GUI and pulled it out by hand.
            // A remainder that is itself fuel (a lava bucket, say) may still go
            // back into the slot; everything else is handed back beside the stove.
            if (level != null) {
                if (fuel.isEmpty() && fuelTicks(remainder) > 0) {
                    inventory.setItem(SLOT_FUEL, remainder);
                } else {
                    Block.popResource(level, worldPosition.above(), remainder);
                }
            } else if (fuel.isEmpty()) {
                inventory.setItem(SLOT_FUEL, remainder);
            }
        }
        burnTotal = ticks;
        burnTime = ticks;
        fuelFraction = 0.0;
        inventory.setChanged();
    }

    /** A cloud of steam that fills the whole banya, thicker the hotter it is. */
    private void steam(ServerLevel server) {
        if (server.getGameTime() % 5 != 0) {
            return;
        }
        RandomSource random = server.getRandom();
        double density = SamogonConfig.banyaSteamDensity();
        if (density <= 0.0) {
            return;
        }
        double heat = Math.min(1.0, (temperature - steamThreshold())
                / Math.max(1.0, MAX_TEMPERATURE - steamThreshold()));
        // A few packets per burst instead of one per particle: the same cloud,
        // a fraction of the traffic on a busy server. The config multiplier
        // scales both the number of emitters and the particles per emitter.
        int points = Math.max(1, (int) Math.round((Math.max(3, Math.min(7,
                (int) Math.round(room.volume() / 240.0) + 2))) * density));
        int perPoint = Math.max(1, (int) Math.round((Math.max(1, Math.min(3,
                1 + (int) Math.round(heat * 2.0)))) * Math.min(1.0, density)));
        for (int i = 0; i < points; i++) {
            double x = room.min().getX() + random.nextDouble() * (room.max().getX() - room.min().getX() + 1);
            double y = room.min().getY() + random.nextDouble() * (room.max().getY() - room.min().getY() + 1);
            double z = room.min().getZ() + random.nextDouble() * (room.max().getZ() - room.min().getZ() + 1);
            if (!server.getBlockState(BlockPos.containing(x, y, z)).isAir()) {
                continue;
            }
            server.sendParticles(ParticleTypes.CLOUD, x, y, z, perPoint, 0.4, 0.3, 0.4, 0.006);
            if (heat > 0.4 && random.nextInt(3) == 0) {
                server.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y, z, 1,
                        0.2, 0.1, 0.2, 0.002);
            }
        }
        if (random.nextInt(90) == 0) {
            server.playSound(null, worldPosition, SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS,
                    0.25f, 0.9f + random.nextFloat() * 0.15f);
        }
    }

    /** The hot air and steam drive the hangover out of everyone in the room. */
    private void cure(ServerLevel server) {
        if (server.getGameTime() % 20 != 0) {
            return;
        }
        applyCure(server);
    }

    /** Applies the cure right now, used by the ticking check and by a splash. */
    private void applyCure(ServerLevel server) {
        for (ServerPlayer player : server.getPlayers(candidate -> !candidate.isSpectator()
                && candidate.level() == server && room.contains(candidate.blockPosition()))) {
            player.removeEffect(ModEffects.holder(ModEffects.HANGOVER));
            player.addEffect(new MobEffectInstance(ModEffects.holder(ModEffects.CLEAR_HEAD), 140, 0, false, true, true));
            if (temperature >= 80.0) {
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0, true, false, false));
            }
        }
    }

    /** Tells the clients inside the room how thick the steam is right now. */
    private void syncSteam(ServerLevel server) {
        int tenths = temperatureTenths();
        for (ServerPlayer player : server.getPlayers(candidate -> !candidate.isSpectator()
                && candidate.level() == server && room.contains(candidate.blockPosition()))) {
            ModNetwork.sendToPlayer(new BanyaSteamPayload(tenths), player);
        }
    }

    /**
     * Adds up the time every player spends in the hot room and pays it out as
     * the after-banya glow the moment they step out.
     *
     * <p>The timer only runs while the stones are actually hot - sitting in a
     * cold box does not count - but a room that is merely cooling does not wipe
     * the session either. Leaving the room, or the room ceasing to be closed,
     * cashes the timer in.</p>
     */
    private void trackSoak(ServerLevel server) {
        boolean hot = temperature >= steamThreshold();
        int required = SamogonConfig.banyaSoakTicks();
        Set<UUID> present = new HashSet<>();
        for (ServerPlayer player : server.getPlayers(candidate -> !candidate.isSpectator()
                && candidate.level() == server && room.contains(candidate.blockPosition()))) {
            UUID id = player.getUUID();
            present.add(id);
            if (hot) {
                soakTicks.merge(id, SOAK_STEP,
                        (soaked, added) -> Math.min(required, soaked + added));
            }
        }

        Iterator<Map.Entry<UUID, Integer>> entries = soakTicks.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<UUID, Integer> entry = entries.next();
            if (present.contains(entry.getKey())) {
                continue;
            }
            entries.remove();
            if (entry.getValue() < required) {
                continue;
            }
            ServerPlayer player = server.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                applyAfterBanya(player);
            }
        }
    }

    /** The reward for a full minute of steam. */
    private static void applyAfterBanya(ServerPlayer player) {
        int ticks = SamogonConfig.banyaAfterTicks();
        if (ticks <= 0) {
            return;
        }
        player.addEffect(new MobEffectInstance(ModEffects.holder(ModEffects.AFTER_BANYA), ticks, 0,
                false, true, true));
        player.displayClientMessage(Component.translatable("message.samogon.banya.after"), true);
    }

    /** A bucket of water thrown on hot stones: a burst of steam and a small chill. */
    public void splash(ServerLevel server) {
        RandomSource random = server.getRandom();
        int particles = (int) Math.round(40 * SamogonConfig.banyaSteamDensity());
        for (int i = 0; i < particles; i++) {
            double x = room.min().getX() + random.nextDouble() * (room.max().getX() - room.min().getX() + 1);
            double y = room.min().getY() + random.nextDouble() * (room.max().getY() - room.min().getY() + 1);
            double z = room.min().getZ() + random.nextDouble() * (room.max().getZ() - room.min().getZ() + 1);
            if (!server.getBlockState(BlockPos.containing(x, y, z)).isAir()) {
                continue;
            }
            server.sendParticles(ParticleTypes.CLOUD, x, y, z, 1, 0.3, 0.2, 0.3, 0.02);
        }
        server.playSound(null, worldPosition, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS,
                0.9f, 1.2f);
        temperature = Math.max(ambient, temperature - 3.0);
        applyCure(server);
        syncSteam(server);
    }

    private int temperatureTenths() {
        return (int) Math.round(temperature * 10.0);
    }

    private int targetTenths() {
        return (int) Math.round(targetTemperature() * 10.0);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, inventory.getItems(), registries);
        tag.putInt("BurnTime", burnTime);
        tag.putInt("BurnTotal", burnTotal);
        tag.putInt("Power", power);
        tag.putDouble("Temperature", temperature);
        tag.putDouble("FuelFraction", fuelFraction);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        NonNullList<ItemStack> items = inventory.getItems();
        for (int slot = 0; slot < items.size(); slot++) {
            items.set(slot, ItemStack.EMPTY);
        }
        ContainerHelper.loadAllItems(tag, items, registries);
        burnTime = Math.max(0, tag.getInt("BurnTime"));
        burnTotal = Math.max(0, tag.getInt("BurnTotal"));
        power = tag.contains("Power")
                ? Math.max(0, Math.min(MAX_POWER, tag.getInt("Power")))
                : 50;
        temperature = Math.max(0.0, Math.min(MAX_TEMPERATURE,
                tag.contains("Temperature") ? tag.getDouble("Temperature") : 20.0));
        fuelFraction = Math.max(0.0, Math.min(4.0, tag.getDouble("FuelFraction")));
    }
}