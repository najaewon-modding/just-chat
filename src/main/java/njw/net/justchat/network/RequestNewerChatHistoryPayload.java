package njw.net.justchat.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record RequestNewerChatHistoryPayload(
        long afterId,
        int limit
) implements CustomPacketPayload {
    public static final int DEFAULT_LIMIT = 100;

    public static final Type<RequestNewerChatHistoryPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("njw_just_chat", "request_newer_chat_history")
    );

    public static final StreamCodec<ByteBuf, RequestNewerChatHistoryPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_LONG, RequestNewerChatHistoryPayload::afterId,
                    ByteBufCodecs.VAR_INT, RequestNewerChatHistoryPayload::limit,
                    RequestNewerChatHistoryPayload::new
            );

    @Override
    public Type<?> type() {
        return TYPE;
    }
}