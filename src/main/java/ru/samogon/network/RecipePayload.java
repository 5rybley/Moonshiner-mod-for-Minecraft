package ru.samogon.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import ru.samogon.SamogonMod;

/**
 * Sends the full set of spirit types the player has already produced. The set is
 * encoded as a comma-separated list of {@link ru.samogon.process.SpiritType#id()}
 * values; an empty string means only the basic recipes are known.
 */
public record RecipePayload(String ids) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RecipePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(SamogonMod.MOD_ID, "recipes"));

    public static final StreamCodec<FriendlyByteBuf, RecipePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, RecipePayload::ids,
            RecipePayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RecipePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ru.samogon.client.ClientRecipeBook.set(payload.ids()));
    }
}