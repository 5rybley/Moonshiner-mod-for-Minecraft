package ru.samogon.effect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/**
 * The warm, easy feeling that follows a proper session in the banya: the body
 * stays relaxed and knits itself back together, one regeneration pulse at a
 * time.
 *
 * <p>The stove applies this to a player who has spent at least a minute in a
 * hot room and then steps out. The regeneration itself is kept running by this
 * effect, so milk or a whole-effect cleanse ends both together.</p>
 */
public final class AfterBanyaEffect extends MobEffect {
    public AfterBanyaEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xE3B26E);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % 20 == 0;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (!(entity.level() instanceof ServerLevel)) {
            return true;
        }
        // Regeneration I, refreshed every second with a short hidden instance:
        // the badge the player sees is this effect, and removing it removes the
        // healing a moment later as well.
        entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0, true, false, false));
        return true;
    }
}