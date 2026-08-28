package njw.net.justchat.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ChatReadStateRequestPayload(
        boolean shouldMarkRead,
        long lastReadMessageId
) implements CustomPacketPayload {
    public static final Type<ChatReadStateRequestPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("njw_just_chat", "chat_read_state_request"));
    public static final StreamCodec<ByteBuf, ChatReadStateRequestPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, ChatReadStateRequestPayload::shouldMarkRead,
            ByteBufCodecs.VAR_LONG, ChatReadStateRequestPayload::lastReadMessageId,
            ChatReadStateRequestPayload::new
    );

    public static ChatReadStateRequestPayload request() {
        return new ChatReadStateRequestPayload(false, 0L);
    }

    public static ChatReadStateRequestPayload markRead(long lastReadMessageId) {
        return new ChatReadStateRequestPayload(true, Math.max(0L, lastReadMessageId));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
