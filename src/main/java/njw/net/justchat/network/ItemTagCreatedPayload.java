package njw.net.justchat.network;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStackTemplate;

import java.util.UUID;

public record ItemTagCreatedPayload(
        UUID requestId,
        UUID token,
        ItemStackTemplate item
) implements CustomPacketPayload {
    public static final Type<ItemTagCreatedPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("njw_just_chat", "item_tag_created")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ItemTagCreatedPayload> STREAM_CODEC =
            StreamCodec.composite(
                    UUIDUtil.STREAM_CODEC, ItemTagCreatedPayload::requestId,
                    UUIDUtil.STREAM_CODEC, ItemTagCreatedPayload::token,
                    ItemStackTemplate.STREAM_CODEC, ItemTagCreatedPayload::item,
                    ItemTagCreatedPayload::new
            );

    @Override
    public Type<?> type() {
        return TYPE;
    }
}