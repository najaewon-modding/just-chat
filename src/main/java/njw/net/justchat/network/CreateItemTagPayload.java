package njw.net.justchat.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

public record CreateItemTagPayload(
        UUID requestId,
        int inventorySlot
) implements CustomPacketPayload {
    public static final Type<CreateItemTagPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("njw_just_chat", "create_item_tag")
    );

    public static final StreamCodec<ByteBuf, CreateItemTagPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, CreateItemTagPayload::requestId,
            ByteBufCodecs.VAR_INT, CreateItemTagPayload::inventorySlot,
            CreateItemTagPayload::new
    );

    @Override
    public Type<?> type() {
        return TYPE;
    }
}