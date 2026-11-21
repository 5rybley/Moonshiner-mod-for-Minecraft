package ru.samogon.registry;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import ru.samogon.SamogonMod;
import ru.samogon.effect.AfterBanyaEffect;
import ru.samogon.effect.ClearHeadEffect;
import ru.samogon.effect.FuselBlindnessEffect;
import ru.samogon.effect.HangoverEffect;
import ru.samogon.effect.IntoxicationEffect;

public final class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, SamogonMod.MOD_ID);

    /** Five-step drunkenness: amplifier 0 is level I, amplifier 4 is level V. */
    public static final RegistryObject<MobEffect> INTOXICATION =
            EFFECTS.register("intoxication", IntoxicationEffect::new);

    /**
     * Per-tincture aura effects. Unlike the intoxication debuffs they apply
     * from level I, and each one's amplifier is the strength of its aura:
     * five matching additives give a level-V aura and the strongest bonus.
     */
    public static final RegistryObject<MobEffect> BERRY_FURY =
            EFFECTS.register("berry_fury", () -> new SimpleEffect(MobEffectCategory.BENEFICIAL, 0xB43A5E));
    public static final RegistryObject<MobEffect> HONEY_GUARD =
            EFFECTS.register("honey_guard", () -> new SimpleEffect(MobEffectCategory.BENEFICIAL, 0xD99A2B));
    public static final RegistryObject<MobEffect> BOTANICAL_CLARITY =
            EFFECTS.register("botanical_clarity", () -> new SimpleEffect(MobEffectCategory.BENEFICIAL, 0x6F8B4A));
    public static final RegistryObject<MobEffect> OAK_GRIT =
            EFFECTS.register("oak_grit", () -> new SimpleEffect(MobEffectCategory.BENEFICIAL, 0xA4722C));
    /** Spiced tinctures: a hot ember that sets struck foes alight. */
    public static final RegistryObject<MobEffect> SPICED_EMBER =
            EFFECTS.register("spiced_ember", () -> new SimpleEffect(MobEffectCategory.BENEFICIAL, 0xC4551F));
    /** Fruit tinctures: a slow, orchard-fed recovery. */
    public static final RegistryObject<MobEffect> ORCHARD_BLOOM =
            EFFECTS.register("orchard_bloom", () -> new SimpleEffect(MobEffectCategory.BENEFICIAL, 0xD98BB0));
    /** Marine tinctures: you breathe and swim like something from the sea. */
    public static final RegistryObject<MobEffect> TIDAL_BREATH =
            EFFECTS.register("tidal_breath", () -> new SimpleEffect(MobEffectCategory.BENEFICIAL, 0x2F7FA8));

    /** The morning after: a queasy, aching debuff left by a heavy session. */
    public static final RegistryObject<MobEffect> HANGOVER =
            EFFECTS.register("hangover", HangoverEffect::new);
    /**
     * Drinking the heads or tails fraction leaves the drinker blind for five
     * minutes. Unlike ordinary blindness, milk cannot lift it: it only passes
     * with time or with death.
     */
    public static final RegistryObject<MobEffect> FUSEL_BLINDNESS =
            EFFECTS.register("fusel_blindness", FuselBlindnessEffect::new);
    /** The short clarity that follows a glass of brine. */
    public static final RegistryObject<MobEffect> CLEAR_HEAD =
            EFFECTS.register("clear_head", ClearHeadEffect::new);

    /** The easy, warm body left behind by a proper session in the banya. */
    public static final RegistryObject<MobEffect> AFTER_BANYA =
            EFFECTS.register("after_banya", AfterBanyaEffect::new);

    /** Hidden marker that remembers which tincture the player last drank. */
    public static final RegistryObject<MobEffect> TINCTURE_MARK =
            EFFECTS.register("tincture_mark", () -> new SimpleEffect(MobEffectCategory.NEUTRAL, 0x7A5A32));

    /**
     * The registry holder of an effect. Forge's {@link RegistryObject} is only a
     * {@link java.util.function.Supplier}, while the effect APIs
     * ({@code getEffect}, {@code hasEffect}, {@code removeEffect} and the
     * {@code MobEffectInstance} constructors) all take a {@link Holder}. The
     * holder is resolved lazily, so this is only safe once the effects have been
     * registered, which is true everywhere it is called from.
     */
    public static Holder<MobEffect> holder(RegistryObject<MobEffect> effect) {
        return effect.getHolder().orElseThrow();
    }

    private static final class SimpleEffect extends MobEffect {
        private SimpleEffect(MobEffectCategory category, int color) {
            super(category, color);
        }
    }

    private ModEffects() {}
}