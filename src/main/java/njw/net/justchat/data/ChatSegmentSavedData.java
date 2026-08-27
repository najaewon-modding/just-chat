package njw.net.justchat.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ChatSegmentSavedData extends SavedData {
    private static final Codec<ChatSegmentSavedData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ChatMessage.CODEC.listOf().optionalFieldOf("messages", List.of())
                    .forGetter(data -> data.messages),
            SystemChatMessage.CODEC.listOf().optionalFieldOf("systemMessages", List.of())
                    .forGetter(data -> data.systemMessages)
    ).apply(instance, ChatSegmentSavedData::new));

    private static final Map<Long, SavedDataType<ChatSegmentSavedData>> TYPES =
            new ConcurrentHashMap<>();

    private final List<ChatMessage> messages;
    private final List<SystemChatMessage> systemMessages;

    public ChatSegmentSavedData() {
        this(List.of(), List.of());
    }

    private ChatSegmentSavedData(
            List<ChatMessage> messages,
            List<SystemChatMessage> systemMessages
    ) {
        this.messages = new ArrayList<>(messages);
        this.systemMessages = new ArrayList<>(systemMessages);
    }

    public static ChatSegmentSavedData get(
            MinecraftServer server,
            long segmentId
    ) {
        return server.getDataStorage().computeIfAbsent(type(segmentId));
    }

    public void add(ChatMessage message) {
        messages.add(message);
        setDirty();
    }

    public void addSystem(SystemChatMessage message) {
        systemMessages.add(message);
        setDirty();
    }

    public ChatMessage delete(
            long messageId,
            UUID requesterUuid,
            long now
    ) {
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage message = messages.get(i);

            if (message.id() != messageId) continue;
            if (!message.canDelete(requesterUuid, now)) continue;

            ChatMessage deleted = message.asDeleted();
            messages.set(i, deleted);
            setDirty();
            return deleted;
        }

        return null;
    }

    public HistoryBatch getHistoryBefore(
            long beforeId,
            int limit
    ) {
        int safeLimit = Math.max(1, limit);
        int chatIndex = findLastChatBefore(beforeId);
        int systemIndex = findLastSystemBefore(beforeId);

        List<ChatMessage> chats = new ArrayList<>();
        List<SystemChatMessage> systems = new ArrayList<>();

        for (int count = 0;
             count < safeLimit && (chatIndex >= 0 || systemIndex >= 0);
             count++) {
            boolean takeChat = systemIndex < 0
                    || chatIndex >= 0
                    && messages.get(chatIndex).id()
                    > systemMessages.get(systemIndex).id();

            if (takeChat) {
                chats.add(messages.get(chatIndex--));
            } else {
                systems.add(systemMessages.get(systemIndex--));
            }
        }

        Collections.reverse(chats);
        Collections.reverse(systems);

        return new HistoryBatch(
                List.copyOf(chats),
                List.copyOf(systems),
                chatIndex >= 0 || systemIndex >= 0
        );
    }

    public HistoryBatch getHistoryAfter(
            long afterId,
            int limit
    ) {
        int safeLimit = Math.max(1, limit);
        int chatIndex = findFirstChatAfter(afterId);
        int systemIndex = findFirstSystemAfter(afterId);

        List<ChatMessage> chats = new ArrayList<>();
        List<SystemChatMessage> systems = new ArrayList<>();

        for (int count = 0;
             count < safeLimit
                     && (chatIndex < messages.size() || systemIndex < systemMessages.size());
             count++) {
            boolean takeChat = systemIndex >= systemMessages.size()
                    || chatIndex < messages.size()
                    && messages.get(chatIndex).id()
                    < systemMessages.get(systemIndex).id();

            if (takeChat) {
                chats.add(messages.get(chatIndex++));
            } else {
                systems.add(systemMessages.get(systemIndex++));
            }
        }

        return new HistoryBatch(
                List.copyOf(chats),
                List.copyOf(systems),
                chatIndex < messages.size() || systemIndex < systemMessages.size()
        );
    }

    public boolean containsId(long messageId) {
        for (ChatMessage message : messages) {
            if (message.id() == messageId) return true;
        }

        for (SystemChatMessage message : systemMessages) {
            if (message.id() == messageId) return true;
        }

        return false;
    }

    private int findLastChatBefore(long beforeId) {
        int index = messages.size() - 1;

        while (index >= 0
                && messages.get(index).id() >= beforeId) {
            index--;
        }

        return index;
    }

    private int findLastSystemBefore(long beforeId) {
        int index = systemMessages.size() - 1;

        while (index >= 0
                && systemMessages.get(index).id() >= beforeId) {
            index--;
        }

        return index;
    }

    private int findFirstChatAfter(long afterId) {
        int index = 0;

        while (index < messages.size()
                && messages.get(index).id() <= afterId) {
            index++;
        }

        return index;
    }

    private int findFirstSystemAfter(long afterId) {
        int index = 0;

        while (index < systemMessages.size()
                && systemMessages.get(index).id() <= afterId) {
            index++;
        }

        return index;
    }

    private static SavedDataType<ChatSegmentSavedData> type(long segmentId) {
        return TYPES.computeIfAbsent(
                segmentId,
                id -> new SavedDataType<>(
                        Identifier.fromNamespaceAndPath(
                                "njw_just_chat",
                                "segments/segment_" + id
                        ),
                        ChatSegmentSavedData::new,
                        CODEC,
                        null
                )
        );
    }

    public record HistoryBatch(
            List<ChatMessage> messages,
            List<SystemChatMessage> systemMessages,
            boolean hasMore
    ) {}
}