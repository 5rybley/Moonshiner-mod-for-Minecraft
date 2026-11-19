package ru.samogon.effect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.EffectCure;

import java.util.Set;

/**
 * The fog left by a glass of heads or tails. It is not an ordinary potion:
 * milk, honey and every other cure leave it untouched, so the only ways out
 * are to wait it out or to die. While it lasts it keeps a hidden vanilla
 * blindness running, so the world really does go dark.
 *
 * <p>The blindness it drives is deliberately short and hidden; this effect is
 * the one badge the player sees, and removing it clears the blindness with it
 * once the refresh stops.</p>
 */
public final class FuselBlindnessEffect extends MobEffect {
    /** How often the hidden vanilla blindness is refreshed, in ticks. */
    private static final int REFRESH_TICKS = 20;
    /** Lifetime of each hidden blindness instance, a little longer than the refresh. */
    private static final int BLINDNESS_TICKS = 60;

    public FuselBlindnessEffect() {
        super(MobEffectCategory.HARMFUL, 0x241B33);
    }

    /**
     * No cure is registered at all, so {@code /effect}-free cures such as a
     * bucket of milk or a bottle of honey cannot wash the fog away.
     */
    @Override
    public void fillEffectCures(Set<EffectCure> cures, MobEffectInstance instance) {
        // Intentionally empty: this effect is only lifted by time or by death.
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % REFRESH_TICKS == 0;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.level() instanceof ServerLevel) {
            // Hidden so only this effect's own icon shows, and short so it
            // starts fading the moment this effect is gone.
            entity.addEffect(new MobEffectInstance(
                    MobEffects.BLINDNESS, BLINDNESS_TICKS, 0, false, false, false));
        }
        return true;
    }
}
