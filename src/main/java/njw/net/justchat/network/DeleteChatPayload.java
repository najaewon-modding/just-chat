package njw.net.justchat.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record DeleteChatPayload(long messageId) implements CustomPacketPayload {
    public static final Type<DeleteChatPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("njw_just_chat", "delete_chat")
    );
    public static final StreamCodec<ByteBuf, DeleteChatPayload> STREAM_CODEC =
            ByteBufCodecs.VAR_LONG.map(DeleteChatPayload::new, DeleteChatPayload::messageId);

    @Override
    public Type<?> type() {
        return TYPE;
    }
}