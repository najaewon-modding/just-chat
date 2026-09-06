package njw.net.justchat.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

public record WhisperTargetsPayload(List<Target> targets) implements CustomPacketPayload {
    private static final StreamCodec<ByteBuf, Target> TARGET_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, Target::uuid,
            ByteBufCodecs.STRING_UTF8, Target::name,
            Target::new
    );
    private static final StreamCodec<ByteBuf, List<Target>> TARGET_LIST_CODEC =
            TARGET_CODEC.apply(ByteBufCodecs.list(256));

    public static final Type<WhisperTargetsPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("njw_just_chat", "whisper_targets")
    );

    public static final StreamCodec<ByteBuf, WhisperTargetsPayload> STREAM_CODEC = StreamCodec.composite(
            TARGET_LIST_CODEC, WhisperTargetsPayload::targets,
            WhisperTargetsPayload::new
    );

    public WhisperTargetsPayload {
        targets = List.copyOf(targets);
    }

    @Override
    public Type<?> type() {
        return TYPE;
    }

    public record Target(String uuid, String name) {}
}
