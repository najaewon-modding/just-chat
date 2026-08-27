package njw.net.justchat.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import njw.net.justchat.data.ChatMessage;

import java.util.List;

public record ChatHistoryPayload(List<ChatMessage> messages) implements CustomPacketPayload {
    private static final StreamCodec<ByteBuf, List<ChatMessage>> MESSAGE_LIST_CODEC =
            ChatMessage.STREAM_CODEC.apply(ByteBufCodecs.list(100));

    public static final Type<ChatHistoryPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("njw_just_chat", "chat_history")
    );
    public static final StreamCodec<ByteBuf, ChatHistoryPayload> STREAM_CODEC =
            MESSAGE_LIST_CODEC.map(ChatHistoryPayload::new, ChatHistoryPayload::messages);

    @Override
    public Type<?> type() {
        return TYPE;
    }
}