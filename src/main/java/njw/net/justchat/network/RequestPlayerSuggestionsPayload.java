package njw.net.justchat.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record RequestPlayerSuggestionsPayload(String query) implements CustomPacketPayload {
    public static final Type<RequestPlayerSuggestionsPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("njw_just_chat", "request_player_suggestions")
    );

    public static final StreamCodec<ByteBuf, RequestPlayerSuggestionsPayload> STREAM_CODEC =
            ByteBufCodecs.STRING_UTF8.map(
                    RequestPlayerSuggestionsPayload::new,
                    RequestPlayerSuggestionsPayload::query
            );

    @Override
    public Type<?> type() {
        return TYPE;
    }
}