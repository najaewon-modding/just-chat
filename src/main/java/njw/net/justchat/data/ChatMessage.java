package njw.net.justchat.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

public record ChatMessage(
        long id,
        UUID senderUuid,
        String senderName,
        String content,
        long createdAt,
        boolean deleted
) {
    public static final Codec<ChatMessage> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("id").forGetter(ChatMessage::id),
            UUIDUtil.CODEC.fieldOf("senderUuid").forGetter(ChatMessage::senderUuid),
            Codec.STRING.fieldOf("senderName").forGetter(ChatMessage::senderName),
            Codec.STRING.fieldOf("content").forGetter(ChatMessage::content),
            Codec.LONG.fieldOf("createdAt").forGetter(ChatMessage::createdAt),
            Codec.BOOL.optionalFieldOf("deleted", false).forGetter(ChatMessage::deleted)
    ).apply(instance, ChatMessage::new));

    public static final StreamCodec<ByteBuf, ChatMessage> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, ChatMessage::id,
            UUIDUtil.STREAM_CODEC, ChatMessage::senderUuid,
            ByteBufCodecs.STRING_UTF8, ChatMessage::senderName,
            ByteBufCodecs.STRING_UTF8, ChatMessage::content,
            ByteBufCodecs.VAR_LONG, ChatMessage::createdAt,
            ByteBufCodecs.BOOL, ChatMessage::deleted,
            ChatMessage::new
    );
}