package njw.net.justchat.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record RequestChatHistoryPayload(
        long requestId,
        long beforeId,
        int limit
) implements CustomPacketPayload {
    public static final int DEFAULT_LIMIT = 100;
    public static final Type<RequestChatHistoryPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("njw_just_chat", "request_chat_history"));
    public static final StreamCodec<ByteBuf, RequestChatHistoryPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, RequestChatHistoryPayload::requestId,
            ByteBufCodecs.VAR_LONG, RequestChatHistoryPayload::beforeId,
            ByteBufCodecs.VAR_INT, RequestChatHistoryPayload::limit,
            RequestChatHistoryPayload::new
    );

    public static RequestChatHistoryPayload latest(long requestId) {
        return new RequestChatHistoryPayload(requestId, Long.MAX_VALUE, DEFAULT_LIMIT);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
