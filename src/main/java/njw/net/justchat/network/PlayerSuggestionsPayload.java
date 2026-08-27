package njw.net.justchat.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

public record PlayerSuggestionsPayload(String query, List<String> names) implements CustomPacketPayload {
    private static final StreamCodec<ByteBuf, List<String>> NAME_LIST_CODEC =
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(5));

    public static final Type<PlayerSuggestionsPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("njw_just_chat", "player_suggestions")
    );

    public static final StreamCodec<ByteBuf, PlayerSuggestionsPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, PlayerSuggestionsPayload::query,
            NAME_LIST_CODEC, PlayerSuggestionsPayload::names,
            PlayerSuggestionsPayload::new
    );

    @Override
    public Type<?> type() {
        return TYPE;
    }
}