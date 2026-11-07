package ru.samogon.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import ru.samogon.SamogonMod;
import ru.samogon.effect.ClearHeadEffect;
import ru.samogon.effect.HangoverEffect;
import ru.samogon.effect.IntoxicationEffect;

public final class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, SamogonMod.MOD_ID);

    /** Five-step drunkenness: amplifier 0 is level I, amplifier 4 is level V. */
    public static final DeferredHolder<MobEffect, MobEffect> INTOXICATION =
            EFFECTS.register("intoxication", IntoxicationEffect::new);

    /** Per-tincture bonus effects, activated while intoxication is level III or higher. */
    public static final DeferredHolder<MobEffect, MobEffect> BERRY_FURY =
            EFFECTS.register("berry_fury", () -> new SimpleEffect(MobEffectCategory.BENEFICIAL, 0xB43A5E));
    public static final DeferredHolder<MobEffect, MobEffect> HONEY_GUARD =
            EFFECTS.register("honey_guard", () -> new SimpleEffect(MobEffectCategory.BENEFICIAL, 0xD99A2B));
    public static final DeferredHolder<MobEffect, MobEffect> BOTANICAL_CLARITY =
            EFFECTS.register("botanical_clarity", () -> new SimpleEffect(MobEffectCategory.BENEFICIAL, 0x6F8B4A));
    public static final DeferredHolder<MobEffect, MobEffect> OAK_GRIT =
            EFFECTS.register("oak_grit", () -> new SimpleEffect(MobEffectCategory.BENEFICIAL, 0xA4722C));
    /** Spiced tinctures: a hot ember that sets struck foes alight. */
    public static final DeferredHolder<MobEffect, MobEffect> SPICED_EMBER =
            EFFECTS.register("spiced_ember", () -> new SimpleEffect(MobEffectCategory.BENEFICIAL, 0xC4551F));
    /** Fruit tinctures: a slow, orchard-fed recovery. */
    public static final DeferredHolder<MobEffect, MobEffect> ORCHARD_BLOOM =
            EFFECTS.register("orchard_bloom", () -> new SimpleEffect(MobEffectCategory.BENEFICIAL, 0xD98BB0));
    /** Marine tinctures: you breathe and swim like something from the sea. */
    public static final DeferredHolder<MobEffect, MobEffect> TIDAL_BREATH =
            EFFECTS.register("tidal_breath", () -> new SimpleEffect(MobEffectCategory.BENEFICIAL, 0x2F7FA8));

    /** The morning after: a queasy, aching debuff left by a heavy session. */
    public static final DeferredHolder<MobEffect, MobEffect> HANGOVER =
            EFFECTS.register("hangover", HangoverEffect::new);
    /** The short clarity that follows a glass of brine. */
    public static final DeferredHolder<MobEffect, MobEffect> CLEAR_HEAD =
            EFFECTS.register("clear_head", ClearHeadEffect::new);

    /** Hidden marker that remembers which tincture the player last drank. */
    public static final DeferredHolder<MobEffect, MobEffect> TINCTURE_MARK =
            EFFECTS.register("tincture_mark", () -> new SimpleEffect(MobEffectCategory.NEUTRAL, 0x7A5A32));

    private static final class SimpleEffect extends MobEffect {
        private SimpleEffect(MobEffectCategory category, int color) {
            super(category, color);
        }
    }

    private ModEffects() {}
}