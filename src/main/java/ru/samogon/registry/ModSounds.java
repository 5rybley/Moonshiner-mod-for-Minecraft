package ru.samogon.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import ru.samogon.SamogonMod;

/**
 * Original sound effects, synthesised for this mod and shipped as Ogg Vorbis.
 * The names here must match both {@code assets/samogon/sounds.json} and the
 * files in {@code assets/samogon/sounds}.
 */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, SamogonMod.MOD_ID);

    /** A bottle being emptied in several gulps. */
    public static final DeferredHolder<SoundEvent, SoundEvent> DRINK_SWIG = register("drink_swig");
    /** A simmering pot still, used as a slow ambient loop. */
    public static final DeferredHolder<SoundEvent, SoundEvent> STILL_BUBBLE = register("still_bubble");
    /** Fizzy brine running down a parched throat. */
    public static final DeferredHolder<SoundEvent, SoundEvent> CURE_GLUG = register("cure_glug");

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(SamogonMod.MOD_ID, name);
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(id));
    }

    private ModSounds() {}
}