package njw.net.justchat.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SendChatPayload(String content) implements CustomPacketPayload {
    public static final Type<SendChatPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("njw_just_chat", "send_chat")
    );
    public static final StreamCodec<ByteBuf, SendChatPayload> STREAM_CODEC =
            ByteBufCodecs.STRING_UTF8.map(SendChatPayload::new, SendChatPayload::content);

    @Override
    public Type<?> type() {
        return TYPE;
    }
}