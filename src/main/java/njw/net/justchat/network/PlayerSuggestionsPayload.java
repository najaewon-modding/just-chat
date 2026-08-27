package njw.net.justchat.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

public record PlayerSuggestionsPayload(
        String query,
        List<Suggestion> suggestions
) implements CustomPacketPayload {
    private static final StreamCodec<ByteBuf, Suggestion> SUGGESTION_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            Suggestion::name,
            ByteBufCodecs.BOOL,
            Suggestion::online,
            Suggestion::new
    );

    private static final StreamCodec<ByteBuf, List<Suggestion>> SUGGESTION_LIST_CODEC =
            SUGGESTION_CODEC.apply(ByteBufCodecs.list(5));

    public static final Type<PlayerSuggestionsPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("njw_just_chat", "player_suggestions")
    );

    public static final StreamCodec<ByteBuf, PlayerSuggestionsPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            PlayerSuggestionsPayload::query,
            SUGGESTION_LIST_CODEC,
            PlayerSuggestionsPayload::suggestions,
            PlayerSuggestionsPayload::new
    );

    @Override
    public Type<?> type() {
        return TYPE;
    }

    public record Suggestion(String name, boolean online) {}
}