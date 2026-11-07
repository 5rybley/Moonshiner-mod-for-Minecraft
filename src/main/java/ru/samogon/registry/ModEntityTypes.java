package ru.samogon.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import ru.samogon.SamogonMod;
import ru.samogon.entity.PukeBlockEntity;

public final class ModEntityTypes {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, SamogonMod.MOD_ID);

    /** The soul-sand lump the drunkard throws up. It renders as a full block. */
    public static final DeferredHolder<EntityType<?>, EntityType<PukeBlockEntity>> PUKE =
            ENTITY_TYPES.register("puke_block", () -> EntityType.Builder
                    .<PukeBlockEntity>of(PukeBlockEntity::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F)
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .setShouldReceiveVelocityUpdates(true)
                    .build("puke_block"));

    private ModEntityTypes() {}
}
