package ru.samogon.effect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/**
 * The short, bright clarity after a glass of brine. Nausea and the hollow
 * feeling of a hangover are pushed away, and a slow regeneration pulse runs
 * for as long as the effect lasts.
 */
public final class ClearHeadEffect extends MobEffect {
    public ClearHeadEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xA9E3D8);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        // Tick on a fixed 100-tick cadence measured in remaining duration. The
        // old check combined a 20-tick duration gate with `tickCount % 100`,
        // which only lined up when the effect happened to be applied on a
        // multiple of 20 and therefore almost never granted regeneration.
        return duration % 100 == 0;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (!(entity.level() instanceof ServerLevel)) {
            return true;
        }

        entity.removeEffect(MobEffects.CONFUSION);
        entity.removeEffect(MobEffects.HUNGER);

        if (entity instanceof ServerPlayer player) {
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0, true, false, true));
        }
        return true;
    }
}