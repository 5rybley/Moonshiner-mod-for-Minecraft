package ru.samogon.recipe;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import ru.samogon.SamogonMod;
import ru.samogon.item.BatchData;
import ru.samogon.item.BatchItem;
import ru.samogon.network.RecipePayload;
import ru.samogon.process.BeerStyle;
import ru.samogon.process.Simulation.Batch;
import ru.samogon.process.SpiritType;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Remembers which spirit types a player has already produced, so the recipes tab
 * of the handbook can reveal them. The set lives in the player's persistent data
 * and is scanned once a second from the inventory and the open container;
 * every time it grows the full set is pushed to the client.
 */
@EventBusSubscriber(modid = SamogonMod.MOD_ID)
public final class RecipeTracker {
    private static final String KEY = "samogon_known_recipes";
    private static final int SCAN_INTERVAL = 20;

    private RecipeTracker() {}

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            send(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.tickCount % SCAN_INTERVAL != 0) {
            return;
        }
        if (scan(player)) {
            send(player);
        }
    }

    /** Reads the discovered type ids from the player's persistent data. */
    public static Set<String> discovered(ServerPlayer player) {
        Set<String> result = new LinkedHashSet<>();
        CompoundTag data = player.getPersistentData();
        String raw = data.getString(KEY);
        if (!raw.isBlank()) {
            for (String id : raw.split(",")) {
                String trimmed = id.strip();
                if (!trimmed.isEmpty()) result.add(trimmed);
            }
        }
        return result;
    }

    private static void send(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new RecipePayload(String.join(",", discovered(player))));
    }

    /** Scans the inventory and the open container, recording any new drink type. */
    private static boolean scan(ServerPlayer player) {
        Set<String> known = discovered(player);
        boolean changed = false;
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            changed |= discover(known, inventory.getItem(slot));
        }
        // A hopper can move a finished bottle straight from a machine into a
        // crate or barrel, so it never touches the player's inventory. Scan the
        // container the player has open as well; that is where those bottles
        // become visible, and duplicate ids are ignored by the set.
        if (player.containerMenu != null) {
            for (Slot slot : player.containerMenu.slots) {
                changed |= discover(known, slot.getItem());
            }
        }
        if (changed) {
            CompoundTag data = player.getPersistentData();
            data.putString(KEY, String.join(",", known));
        }
        return changed;
    }

    private static boolean discover(Set<String> known, ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof BatchItem item)) {
            return false;
        }
        // Heads and tails are technical fractions, not drinks the player made.
        if (item.technical()) {
            return false;
        }
        Batch batch = BatchData.read(stack);
        if (batch.abv() <= 0.1) {
            return false;
        }
        SpiritType type = item.typeOf(batch);
        if (type == null || !type.isDrink()) {
            return false;
        }
        // Beer reveals its style (pale, wheat, dark, honey...) rather than the
        // generic BEER type, so the brewer's guide can unlock individual recipes.
        if (type == SpiritType.BEER) {
            return known.add(BeerStyle.of(batch.origin()).id());
        }
        return known.add(type.id());
    }
}