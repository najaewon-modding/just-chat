package njw.net.justchat.data;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

public enum ChatFilter {
    ALL,
    GLOBAL,
    DIRECT,
    WHISPER;

    public static final StreamCodec<ByteBuf, ChatFilter> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ChatFilter decode(ByteBuf buffer) {
            int ordinal = ByteBufCodecs.VAR_INT.decode(buffer);
            ChatFilter[] values = ChatFilter.values();
            return ordinal >= 0 && ordinal < values.length ? values[ordinal] : ALL;
        }

        @Override
        public void encode(ByteBuf buffer, ChatFilter value) {
            ByteBufCodecs.VAR_INT.encode(buffer, value.ordinal());
        }
    };

    public boolean matches(ChatEntry entry, UUID viewerUuid) {
        if (viewerUuid == null || !entry.isVisibleTo(viewerUuid)) return false;
        return switch (this) {
            case ALL -> true;
            case GLOBAL -> entry.audience().isGlobal();
            case DIRECT -> !entry.isWhisper() && !entry.audience().isGlobal()
                    && entry.audience().players().size() == 1
                    && entry.audience().players().contains(viewerUuid);
            case WHISPER -> entry.isWhisper();
        };
    }
}
