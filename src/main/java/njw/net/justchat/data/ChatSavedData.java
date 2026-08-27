package njw.net.justchat.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ChatSavedData extends SavedData {
    private static final Codec<ChatSavedData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.optionalFieldOf("nextMessageId", 1L).forGetter(data -> data.nextMessageId),
            ChatMessage.CODEC.listOf().optionalFieldOf("messages", List.of()).forGetter(data -> data.messages),
            SystemChatMessage.CODEC.listOf().optionalFieldOf("systemMessages", List.of())
                    .forGetter(data -> data.systemMessages)
    ).apply(instance, ChatSavedData::new));

    public static final SavedDataType<ChatSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("njw_just_chat", "chat_history"),
            ChatSavedData::new,
            CODEC,
            null
    );

    private long nextMessageId;
    private final List<ChatMessage> messages;
    private final List<SystemChatMessage> systemMessages;

    public ChatSavedData() {
        this(1L, List.of(), List.of());
    }

    private ChatSavedData(
            long nextMessageId,
            List<ChatMessage> messages,
            List<SystemChatMessage> systemMessages
    ) {
        this.messages = new ArrayList<>(messages);
        this.systemMessages = new ArrayList<>(systemMessages);
        this.nextMessageId = Math.max(nextMessageId, findNextMessageId(messages, systemMessages));
    }

    public static ChatSavedData get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public ChatMessage add(UUID senderUuid, String senderName, String content, long createdAt) {
        ChatMessage message = new ChatMessage(nextMessageId++, senderUuid, senderName, content, createdAt, false);
        messages.add(message);
        setDirty();
        return message;
    }

    public SystemChatMessage addSystem(Component content, long createdAt) {
        SystemChatMessage message = new SystemChatMessage(nextMessageId++, content.copy(), createdAt);
        systemMessages.add(message);
        setDirty();
        return message;
    }

    public List<ChatMessage> getBefore(long beforeId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        int end = messages.size();
        while (end > 0 && messages.get(end - 1).id() >= beforeId) end--;
        int start = Math.max(0, end - safeLimit);
        return List.copyOf(messages.subList(start, end));
    }

    public List<SystemChatMessage> getSystemBefore(long beforeId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        int end = systemMessages.size();
        while (end > 0 && systemMessages.get(end - 1).id() >= beforeId) end--;
        int start = Math.max(0, end - safeLimit);
        return List.copyOf(systemMessages.subList(start, end));
    }

    private static long findNextMessageId(
            List<ChatMessage> messages,
            List<SystemChatMessage> systemMessages
    ) {
        long nextId = 1L;
        for (ChatMessage message : messages) nextId = Math.max(nextId, message.id() + 1L);
        for (SystemChatMessage message : systemMessages) nextId = Math.max(nextId, message.id() + 1L);
        return nextId;
    }
}