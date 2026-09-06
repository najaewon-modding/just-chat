package njw.net.justchat.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record RequestWhisperTargetsPayload() implements CustomPacketPayload {
    public static final Type<RequestWhisperTargetsPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("njw_just_chat", "request_whisper_targets")
    );

    public static final StreamCodec<ByteBuf, RequestWhisperTargetsPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public RequestWhisperTargetsPayload decode(ByteBuf buffer) {
            return new RequestWhisperTargetsPayload();
        }

        @Override
        public void encode(ByteBuf buffer, RequestWhisperTargetsPayload value) {}
    };

    @Override
    public Type<?> type() {
        return TYPE;
    }
}
