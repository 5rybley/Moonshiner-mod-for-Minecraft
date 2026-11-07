package ru.samogon.effect;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * The visible five-level intoxication effect. Most of the work is delegated to
 * {@link Intoxication}, because the same calculations are used by item use and
 * by the damage events.
 */
public final class IntoxicationEffect extends MobEffect {
    public IntoxicationEffect() {
        super(MobEffectCategory.HARMFUL, 0xB56A32);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity instanceof ServerPlayer player) {
            Intoxication.tick(player, Math.min(Intoxication.MAX_LEVEL, amplifier + 1));
        }
        return true;
    }
}
