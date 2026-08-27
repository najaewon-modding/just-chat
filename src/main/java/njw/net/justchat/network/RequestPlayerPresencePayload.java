package njw.net.justchat.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.UUID;

public record RequestPlayerPresencePayload(List<UUID> playerUuids) implements CustomPacketPayload {
    private static final StreamCodec<ByteBuf, List<UUID>> UUID_LIST_CODEC =
            UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs.list(128));

    public static final Type<RequestPlayerPresencePayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("njw_just_chat", "request_player_presence")
    );

    public static final StreamCodec<ByteBuf, RequestPlayerPresencePayload> STREAM_CODEC =
            UUID_LIST_CODEC.map(RequestPlayerPresencePayload::new, RequestPlayerPresencePayload::playerUuids);

    @Override
    public Type<?> type() {
        return TYPE;
    }
}