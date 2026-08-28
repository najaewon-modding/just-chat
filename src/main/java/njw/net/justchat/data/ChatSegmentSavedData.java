package njw.net.justchat.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ChatSegmentSavedData extends SavedData {
    private static final Codec<ChatSegmentSavedData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ChatMessage.CODEC.listOf().optionalFieldOf("messages", List.of()).forGetter(data -> data.messages),
            SystemChatMessage.CODEC.listOf().optionalFieldOf("systemMessages", List.of())
                    .forGetter(data -> data.systemMessages)
    ).apply(instance, ChatSegmentSavedData::new));
    private static final Map<Long, SavedDataType<ChatSegmentSavedData>> TYPES = new ConcurrentHashMap<>();

    private final List<ChatMessage> messages;
    private final List<SystemChatMessage> systemMessages;

    public ChatSegmentSavedData() {
        this(List.of(), List.of());
    }

    private ChatSegmentSavedData(List<ChatMessage> messages, List<SystemChatMessage> systemMessages) {
        this.messages = new ArrayList<>(messages);
        this.systemMessages = new ArrayList<>(systemMessages);
        this.messages.sort(Comparator.comparingLong(ChatMessage::id));
        this.systemMessages.sort(Comparator.comparingLong(SystemChatMessage::id));
    }

    public static ChatSegmentSavedData get(MinecraftServer server, long segmentId) {
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

    public ChatMessage delete(long messageId, UUID requesterUuid, long now) {
        int index = findChat(messageId);
        if (index < 0) return null;

        ChatMessage message = messages.get(index);
        if (!message.canDelete(requesterUuid, now)) return null;

        ChatMessage deleted = message.asDeleted();
        messages.set(index, deleted);
        setDirty();
        return deleted;
    }

    public HistoryBatch getHistoryBefore(long beforeId, int limit) {
        int safeLimit = Math.max(1, limit);
        int chatIndex = findLastChatBefore(beforeId);
        int systemIndex = findLastSystemBefore(beforeId);
        List<ChatMessage> resultMessages = new ArrayList<>();
        List<SystemChatMessage> resultSystems = new ArrayList<>();
        int count = 0;

        while (count < safeLimit && (chatIndex >= 0 || systemIndex >= 0)) {
            boolean useChat = systemIndex < 0 || chatIndex >= 0
                    && messages.get(chatIndex).id() > systemMessages.get(systemIndex).id();

            if (useChat) resultMessages.add(messages.get(chatIndex--));
            else resultSystems.add(systemMessages.get(systemIndex--));

            count++;
        }

        Collections.reverse(resultMessages);
        Collections.reverse(resultSystems);
        return new HistoryBatch(
                List.copyOf(resultMessages),
                List.copyOf(resultSystems),
                chatIndex >= 0 || systemIndex >= 0
        );
    }

    public HistoryBatch getHistoryAfter(long afterId, int limit) {
        int safeLimit = Math.max(1, limit);
        int chatIndex = findFirstChatAfter(afterId);
        int systemIndex = findFirstSystemAfter(afterId);
        List<ChatMessage> resultMessages = new ArrayList<>();
        List<SystemChatMessage> resultSystems = new ArrayList<>();
        int count = 0;

        while (count < safeLimit && (chatIndex < messages.size() || systemIndex < systemMessages.size())) {
            boolean useChat = systemIndex >= systemMessages.size() || chatIndex < messages.size()
                    && messages.get(chatIndex).id() < systemMessages.get(systemIndex).id();

            if (useChat) resultMessages.add(messages.get(chatIndex++));
            else resultSystems.add(systemMessages.get(systemIndex++));

            count++;
        }

        return new HistoryBatch(
                List.copyOf(resultMessages),
                List.copyOf(resultSystems),
                chatIndex < messages.size() || systemIndex < systemMessages.size()
        );
    }

    public boolean containsId(long id) {
        return findChat(id) >= 0 || findSystem(id) >= 0;
    }

    private int findChat(long id) {
        int low = 0;
        int high = messages.size() - 1;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            long current = messages.get(mid).id();
            if (current < id) low = mid + 1;
            else if (current > id) high = mid - 1;
            else return mid;
        }

        return -1;
    }

    private int findSystem(long id) {
        int low = 0;
        int high = systemMessages.size() - 1;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            long current = systemMessages.get(mid).id();
            if (current < id) low = mid + 1;
            else if (current > id) high = mid - 1;
            else return mid;
        }

        return -1;
    }

    private int findLastChatBefore(long id) {
        int low = 0;
        int high = messages.size();

        while (low < high) {
            int mid = (low + high) >>> 1;
            if (messages.get(mid).id() < id) low = mid + 1;
            else high = mid;
        }

        return low - 1;
    }

    private int findLastSystemBefore(long id) {
        int low = 0;
        int high = systemMessages.size();

        while (low < high) {
            int mid = (low + high) >>> 1;
            if (systemMessages.get(mid).id() < id) low = mid + 1;
            else high = mid;
        }

        return low - 1;
    }

    private int findFirstChatAfter(long id) {
        int low = 0;
        int high = messages.size();

        while (low < high) {
            int mid = (low + high) >>> 1;
            if (messages.get(mid).id() <= id) low = mid + 1;
            else high = mid;
        }

        return low;
    }

    private int findFirstSystemAfter(long id) {
        int low = 0;
        int high = systemMessages.size();

        while (low < high) {
            int mid = (low + high) >>> 1;
            if (systemMessages.get(mid).id() <= id) low = mid + 1;
            else high = mid;
        }

        return low;
    }

    private static SavedDataType<ChatSegmentSavedData> type(long segmentId) {
        return TYPES.computeIfAbsent(segmentId, id -> new SavedDataType<>(
                Identifier.fromNamespaceAndPath("njw_just_chat", "segments/segment_" + id),
                ChatSegmentSavedData::new,
                CODEC,
                null
        ));
    }

    public record HistoryBatch(
            List<ChatMessage> messages,
            List<SystemChatMessage> systemMessages,
            boolean hasMore
    ) {}
}