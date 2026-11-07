package ru.samogon.effect;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/**
 * The morning after a heavy session. A queasy, aching debuff: waves of nausea,
 * a little weakness at the stronger grades and a sickly trail of particles. It
 * fades on its own, and a glass of brine clears it.
 */
public final class HangoverEffect extends MobEffect {
    private static final int TICK_INTERVAL = 40;

    public HangoverEffect() {
        super(MobEffectCategory.HARMFUL, 0x7C8A4E);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % TICK_INTERVAL == 0;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (!(entity.level() instanceof ServerLevel server)) {
            return true;
        }

        entity.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 120, 0, true, false, true));

        // The stronger the hangover, the more often the body refuses to cooperate.
        if (amplifier >= 1) {
            if (entity.getRandom().nextFloat() < 0.35F) {
                entity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0, true, false, true));
            }
            if (entity.getRandom().nextFloat() < 0.30F) {
                entity.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 100, 0, true, false, true));
            }
        }

        server.sendParticles(
                ParticleTypes.ITEM_SLIME,
                entity.getX(),
                entity.getY() + entity.getBbHeight() * 0.85,
                entity.getZ(),
                4,
                0.25,
                0.2,
                0.25,
                0.01);
        return true;
    }
}