package njw.net.justchat.data;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

public record PlayerPresence(UUID uuid, long lastSeenAt, boolean online) {
    public static final StreamCodec<ByteBuf, PlayerPresence> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, PlayerPresence::uuid,
            ByteBufCodecs.VAR_LONG, PlayerPresence::lastSeenAt,
            ByteBufCodecs.BOOL, PlayerPresence::online,
            PlayerPresence::new
    );
}