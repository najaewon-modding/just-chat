package njw.net.justchat.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record SystemChatMessage(long id, Component content, long createdAt) {
    public static final Codec<SystemChatMessage> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("id").forGetter(SystemChatMessage::id),
            ComponentSerialization.CODEC.fieldOf("content").forGetter(SystemChatMessage::content),
            Codec.LONG.fieldOf("createdAt").forGetter(SystemChatMessage::createdAt)
    ).apply(instance, SystemChatMessage::new));

    public static final StreamCodec<ByteBuf, SystemChatMessage> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, SystemChatMessage::id,
            ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC, SystemChatMessage::content,
            ByteBufCodecs.VAR_LONG, SystemChatMessage::createdAt,
            SystemChatMessage::new
    );
}