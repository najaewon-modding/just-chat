package njw.net.justchat.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

public record SendChatPayload(
        String content,
        List<ItemTagReference> itemTags
) implements CustomPacketPayload {
    private static final StreamCodec<ByteBuf, List<ItemTagReference>> ITEM_TAG_LIST_CODEC =
            ItemTagReference.STREAM_CODEC.apply(ByteBufCodecs.list(16));

    public static final Type<SendChatPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("njw_just_chat", "send_chat")
    );

    public static final StreamCodec<ByteBuf, SendChatPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, SendChatPayload::content,
            ITEM_TAG_LIST_CODEC, SendChatPayload::itemTags,
            SendChatPayload::new
    );

    @Override
    public Type<?> type() {
        return TYPE;
    }
}