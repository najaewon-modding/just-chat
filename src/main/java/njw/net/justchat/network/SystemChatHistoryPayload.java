package njw.net.justchat.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import njw.net.justchat.data.SystemChatMessage;

import java.util.List;

public record SystemChatHistoryPayload(List<SystemChatMessage> messages) implements CustomPacketPayload {
    private static final StreamCodec<ByteBuf, List<SystemChatMessage>> MESSAGE_LIST_CODEC =
            SystemChatMessage.STREAM_CODEC.apply(ByteBufCodecs.list(100));

    public static final Type<SystemChatHistoryPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("njw_just_chat", "system_chat_history")
    );
    public static final StreamCodec<ByteBuf, SystemChatHistoryPayload> STREAM_CODEC =
            MESSAGE_LIST_CODEC.map(SystemChatHistoryPayload::new, SystemChatHistoryPayload::messages);

    @Override
    public Type<?> type() {
        return TYPE;
    }
}