package njw.net.justchat.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import njw.net.justchat.data.ChatEntry;

import java.util.List;

public record ChatHistoryPayload(
        long requestId,
        List<ChatEntry> entries,
        boolean hasMore
) implements CustomPacketPayload {
    private static final StreamCodec<RegistryFriendlyByteBuf, List<ChatEntry>> ENTRY_LIST_CODEC =
            ChatEntry.STREAM_CODEC.apply(ByteBufCodecs.list(100));
    public static final Type<ChatHistoryPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("njw_just_chat", "chat_history"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ChatHistoryPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, ChatHistoryPayload::requestId,
            ENTRY_LIST_CODEC, ChatHistoryPayload::entries,
            ByteBufCodecs.BOOL, ChatHistoryPayload::hasMore,
            ChatHistoryPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
