package njw.net.justchat.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

public record PlayerTag(int start, int end, UUID targetUuid, String targetName) {
    public static final Codec<PlayerTag> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("start").forGetter(PlayerTag::start),
            Codec.INT.fieldOf("end").forGetter(PlayerTag::end),
            UUIDUtil.CODEC.fieldOf("targetUuid").forGetter(PlayerTag::targetUuid),
            Codec.STRING.fieldOf("targetName").forGetter(PlayerTag::targetName)
    ).apply(instance, PlayerTag::new));

    public static final StreamCodec<ByteBuf, PlayerTag> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PlayerTag::start,
            ByteBufCodecs.VAR_INT, PlayerTag::end,
            UUIDUtil.STREAM_CODEC, PlayerTag::targetUuid,
            ByteBufCodecs.STRING_UTF8, PlayerTag::targetName,
            PlayerTag::new
    );
}