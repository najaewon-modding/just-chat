package njw.net.justchat.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import njw.net.justchat.data.ChatMessage;

public record ChatDeletedPayload(ChatMessage message) implements CustomPacketPayload {
    public static final Type<ChatDeletedPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("njw_just_chat", "chat_deleted")
    );
    public static final StreamCodec<ByteBuf, ChatDeletedPayload> STREAM_CODEC =
            ChatMessage.STREAM_CODEC.map(ChatDeletedPayload::new, ChatDeletedPayload::message);

    @Override
    public Type<?> type() {
        return TYPE;
    }
}