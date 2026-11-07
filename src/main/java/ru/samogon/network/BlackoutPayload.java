package ru.samogon.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import ru.samogon.SamogonMod;

/**
 * Tells a client that the drinker blacked out: fade the screen to black, hold it
 * while showing the warning, then fade back in.
 *
 * @param fadeTicks ticks spent fading in and, later, fading out
 * @param holdTicks ticks held fully black between the two fades
 */
public record BlackoutPayload(int fadeTicks, int holdTicks) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BlackoutPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(SamogonMod.MOD_ID, "blackout"));

    public static final StreamCodec<FriendlyByteBuf, BlackoutPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, BlackoutPayload::fadeTicks,
            ByteBufCodecs.VAR_INT, BlackoutPayload::holdTicks,
            BlackoutPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Runs only for clientbound deliveries, so the client-only overlay class is
     * never touched on a dedicated server.
     */
    public static void handle(BlackoutPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ru.samogon.client.BlackoutOverlay.begin(payload.fadeTicks(), payload.holdTicks()));
    }
}
