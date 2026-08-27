package njw.net.justchat.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import njw.net.justchat.data.SystemChatMessage;

public record NewSystemChatPayload(SystemChatMessage message) implements CustomPacketPayload {
    public static final Type<NewSystemChatPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("njw_just_chat", "new_system_chat")
    );
    public static final StreamCodec<ByteBuf, NewSystemChatPayload> STREAM_CODEC =
            SystemChatMessage.STREAM_CODEC.map(NewSystemChatPayload::new, NewSystemChatPayload::message);

    @Override
    public Type<?> type() {
        return TYPE;
    }
}