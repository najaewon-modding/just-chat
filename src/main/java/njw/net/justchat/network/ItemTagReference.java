package njw.net.justchat.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

public record ItemTagReference(UUID token, String displayText) {
    public static final StreamCodec<ByteBuf, ItemTagReference> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, ItemTagReference::token,
            ByteBufCodecs.STRING_UTF8, ItemTagReference::displayText,
            ItemTagReference::new
    );
}