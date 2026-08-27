package njw.net.justchat.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.List;

public final class LegacyChatSavedData extends SavedData {
    private static final Codec<LegacyChatSavedData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.optionalFieldOf("nextMessageId", 1L)
                    .forGetter(data -> data.nextMessageId),
            ChatMessage.CODEC.listOf().optionalFieldOf("messages", List.of())
                    .forGetter(data -> data.messages),
            SystemChatMessage.CODEC.listOf().optionalFieldOf("systemMessages", List.of())
                    .forGetter(data -> data.systemMessages)
    ).apply(instance, LegacyChatSavedData::new));

    public static final SavedDataType<LegacyChatSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("njw_just_chat", "chat_history"),
            LegacyChatSavedData::new,
            CODEC,
            null
    );

    private final long nextMessageId;
    private final List<ChatMessage> messages;
    private final List<SystemChatMessage> systemMessages;

    public LegacyChatSavedData() {
        this(1L, List.of(), List.of());
    }

    private LegacyChatSavedData(
            long nextMessageId,
            List<ChatMessage> messages,
            List<SystemChatMessage> systemMessages
    ) {
        this.nextMessageId = nextMessageId;
        this.messages = new ArrayList<>(messages);
        this.systemMessages = new ArrayList<>(systemMessages);
    }

    public static LegacyChatSavedData get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public long nextMessageId() {
        return nextMessageId;
    }

    public List<ChatMessage> messages() {
        return List.copyOf(messages);
    }

    public List<SystemChatMessage> systemMessages() {
        return List.copyOf(systemMessages);
    }

    public boolean isEmpty() {
        return messages.isEmpty() && systemMessages.isEmpty();
    }
}