package ru.samogon.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.network.CustomPayloadEvent;
import ru.samogon.SamogonMod;

/**
 * Tells a client standing inside a banya how hot the room is, so the screen can
 * fill with a translucent veil of steam. Purely cosmetic; the server owns the
 * temperature and the curing.
 *
 * @param temperatureTenths room temperature in tenths of a degree Celsius
 */
public record BanyaSteamPayload(int temperatureTenths) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BanyaSteamPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
                    SamogonMod.MOD_ID, "banya_steam"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BanyaSteamPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, BanyaSteamPayload::temperatureTenths,
                    BanyaSteamPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Clientbound only, so the client-only overlay never loads on a server. */
    public static void handle(BanyaSteamPayload payload, CustomPayloadEvent.Context context) {
        context.enqueueWork(() -> ru.samogon.client.BanyaSteamOverlay.update(payload.temperatureTenths()));
        context.setPacketHandled(true);
    }
}
