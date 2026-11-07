package ru.samogon.network;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import ru.samogon.SamogonMod;

/** Registers the mod's clientbound packets. */
@EventBusSubscriber(modid = SamogonMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class ModNetwork {
    private ModNetwork() {}

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(BlackoutPayload.TYPE, BlackoutPayload.STREAM_CODEC, BlackoutPayload::handle);
    }
}
