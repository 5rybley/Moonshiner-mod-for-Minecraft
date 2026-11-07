package ru.samogon.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import ru.samogon.process.Quality;
import ru.samogon.process.Simulation.Batch;

import java.util.Locale;

/**
 * A hand-held spirit gauge.
 *
 * <p>Right-clicking a machine still reports its process values (handled by the
 * machine itself). Right-clicking the air with a bottle in the other hand
 * reports that bottle's strength and volume, so a finished batch can be checked
 * anywhere without putting it back into a machine.</p>
 */
public final class HydrometerItem extends Item {
    public HydrometerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(held);
        }
        InteractionHand other = hand == InteractionHand.MAIN_HAND
                ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack sample = player.getItemInHand(other);
        if (!(sample.getItem() instanceof BatchItem)) {
            player.displayClientMessage(Component.translatable("message.samogon.hydrometer.nothing"), true);
            return InteractionResultHolder.success(held);
        }
        Batch batch = BatchData.read(sample);
        player.displayClientMessage(Component.translatable("message.samogon.hydrometer.bottle",
                sample.getHoverName(),
                f(batch.abv()),
                f(batch.volume()),
                Quality.label(batch.quality()),
                batch.impurities()), false);
        return InteractionResultHolder.success(held);
    }

    private static String f(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}