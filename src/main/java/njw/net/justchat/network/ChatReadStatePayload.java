package njw.net.justchat.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ChatReadStatePayload(long lastReadMessageId) implements CustomPacketPayload {
    public static final Type<ChatReadStatePayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("njw_just_chat", "chat_read_state")
    );

    public static final StreamCodec<ByteBuf, ChatReadStatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG,
            ChatReadStatePayload::lastReadMessageId,
            ChatReadStatePayload::new
    );

    @Override
    public Type<?> type() {
        return TYPE;
    }
}