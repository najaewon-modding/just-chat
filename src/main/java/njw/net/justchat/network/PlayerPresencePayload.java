package njw.net.justchat.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import njw.net.justchat.data.PlayerPresence;

import java.util.List;

public record PlayerPresencePayload(
        List<PlayerPresence> players,
        long serverTimeMillis
) implements CustomPacketPayload {
    private static final StreamCodec<ByteBuf, List<PlayerPresence>> PLAYER_LIST_CODEC =
            PlayerPresence.STREAM_CODEC.apply(ByteBufCodecs.list(128));
    public static final Type<PlayerPresencePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("njw_just_chat", "player_presence"));
    public static final StreamCodec<ByteBuf, PlayerPresencePayload> STREAM_CODEC = StreamCodec.composite(
            PLAYER_LIST_CODEC, PlayerPresencePayload::players,
            ByteBufCodecs.VAR_LONG, PlayerPresencePayload::serverTimeMillis,
            PlayerPresencePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}