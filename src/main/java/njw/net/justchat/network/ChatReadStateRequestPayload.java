package njw.net.justchat.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ChatReadStateRequestPayload(boolean shouldMarkRead) implements CustomPacketPayload {
    public static final Type<ChatReadStateRequestPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("njw_just_chat", "chat_read_state_request")
    );

    public static final StreamCodec<ByteBuf, ChatReadStateRequestPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL,
            ChatReadStateRequestPayload::shouldMarkRead,
            ChatReadStateRequestPayload::new
    );

    public static ChatReadStateRequestPayload request() {
        return new ChatReadStateRequestPayload(false);
    }

    public static ChatReadStateRequestPayload markRead() {
        return new ChatReadStateRequestPayload(true);
    }

    @Override
    public Type<?> type() {
        return TYPE;
    }
}