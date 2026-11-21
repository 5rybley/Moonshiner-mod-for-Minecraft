package ru.samogon.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.PacketDistributor;
import ru.samogon.SamogonMod;

/**
 * The mod's clientbound packet channel. Forge's {@link ChannelBuilder} registers
 * the channel with the network manager as soon as {@link ChannelBuilder#payloadChannel()}
 * is built, so {@link #init()} only has to force this class to load.
 */
public final class ModNetwork {
    private static final ResourceLocation CHANNEL_NAME =
            ResourceLocation.fromNamespaceAndPath(SamogonMod.MOD_ID, "main");

    private static final Channel<CustomPacketPayload> CHANNEL = ChannelBuilder
            .named(CHANNEL_NAME)
            .networkProtocolVersion(1)
            .payloadChannel()
            .play()
            .clientbound()
            .add(BlackoutPayload.TYPE, BlackoutPayload.STREAM_CODEC, BlackoutPayload::handle)
            .add(BanyaSteamPayload.TYPE, BanyaSteamPayload.STREAM_CODEC, BanyaSteamPayload::handle)
            .add(RecipePayload.TYPE, RecipePayload.STREAM_CODEC, RecipePayload::handle)
            .build();

    private ModNetwork() {}

    /** Called from the mod constructor to force the channel to be built. */
    public static void init() {
        // Class initialisation builds and registers the channel; nothing else to do.
    }

    /** Sends one clientbound payload to a single player. */
    public static void sendToPlayer(CustomPacketPayload payload, ServerPlayer player) {
        CHANNEL.send(payload, PacketDistributor.PLAYER.with(player));
    }
}
