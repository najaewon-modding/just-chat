package njw.net.justchat.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import njw.net.justchat.data.ChatMessage;

public record NewChatPayload(ChatMessage message) implements CustomPacketPayload {
    public static final Type<NewChatPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("njw_just_chat", "new_chat")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, NewChatPayload> STREAM_CODEC =
            ChatMessage.STREAM_CODEC.map(NewChatPayload::new, NewChatPayload::message);

    @Override
    public Type<?> type() {
        return TYPE;
    }
}