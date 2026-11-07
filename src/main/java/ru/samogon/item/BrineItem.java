package ru.samogon.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import ru.samogon.effect.Intoxication;
import ru.samogon.registry.ModEffects;
import ru.samogon.registry.ModSounds;

import java.util.List;

/**
 * A glass of brine, the classic morning-after remedy. It knocks the drinker
 * down one intoxication level, wipes away a hangover and leaves a short spell
 * of clarity. Emptying the glass returns the bottle.
 */
public final class BrineItem extends Item {
    private static final int USE_TICKS = 24;

    public BrineItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return USE_TICKS;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide && entity instanceof ServerPlayer player) {
            boolean hangover = player.hasEffect(ModEffects.HANGOVER);
            int before = Intoxication.level(player);

            Intoxication.cure(player);
            player.addEffect(new MobEffectInstance(MobEffects.SATURATION, 20, 0, true, false, true));

            level.playSound(
                    null,
                    player.blockPosition(),
                    ModSounds.CURE_GLUG.get(),
                    SoundSource.PLAYERS,
                    1.0F,
                    0.95F + player.getRandom().nextFloat() * 0.1F);

            String key = hangover
                    ? "message.samogon.brine.cured"
                    : before > 0 ? "message.samogon.brine.sober" : "message.samogon.brine.fresh";
            player.displayClientMessage(Component.translatable(key), true);
        }

        if (entity instanceof Player player && player.getAbilities().instabuild) {
            return stack;
        }
        stack.shrink(1);
        return new ItemStack(Items.GLASS_BOTTLE);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.samogon.brine").withStyle(ChatFormatting.GREEN));
        lines.add(Component.translatable("tooltip.samogon.brine.hint").withStyle(ChatFormatting.GRAY));
    }
}