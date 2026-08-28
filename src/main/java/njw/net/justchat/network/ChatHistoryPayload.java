package njw.net.justchat.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import njw.net.justchat.data.ChatMessage;
import njw.net.justchat.data.SystemChatMessage;

import java.util.List;

public record ChatHistoryPayload(
        long requestId,
        List<ChatMessage> messages,
        List<SystemChatMessage> systemMessages,
        boolean hasMore
) implements CustomPacketPayload {
    private static final StreamCodec<RegistryFriendlyByteBuf, List<ChatMessage>> CHAT_LIST_CODEC =
            ChatMessage.STREAM_CODEC.apply(ByteBufCodecs.list(100));
    private static final StreamCodec<ByteBuf, List<SystemChatMessage>> SYSTEM_LIST_CODEC =
            SystemChatMessage.STREAM_CODEC.apply(ByteBufCodecs.list(100));
    public static final Type<ChatHistoryPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("njw_just_chat", "chat_history"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ChatHistoryPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, ChatHistoryPayload::requestId,
            CHAT_LIST_CODEC, ChatHistoryPayload::messages,
            SYSTEM_LIST_CODEC, ChatHistoryPayload::systemMessages,
            ByteBufCodecs.BOOL, ChatHistoryPayload::hasMore,
            ChatHistoryPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
