package njw.net.justchat.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import njw.net.justchat.data.ChatEntry;

public record NewChatPayload(ChatEntry entry) implements CustomPacketPayload {
    public static final Type<NewChatPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("njw_just_chat", "new_chat")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, NewChatPayload> STREAM_CODEC =
            ChatEntry.STREAM_CODEC.map(NewChatPayload::new, NewChatPayload::entry);

    @Override
    public Type<?> type() {
        return TYPE;
    }
}
