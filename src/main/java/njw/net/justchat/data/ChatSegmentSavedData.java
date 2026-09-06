package njw.net.justchat.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ChatSegmentSavedData extends SavedData {
    private static final Codec<ChatSegmentSavedData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ChatEntry.CODEC.listOf().optionalFieldOf("entries", List.of()).forGetter(data -> data.entries),
            ChatMessage.CODEC.listOf().optionalFieldOf("messages", List.of()).forGetter(data -> List.of()),
            SystemChatMessage.CODEC.listOf().optionalFieldOf("systemMessages", List.of()).forGetter(data -> List.of())
    ).apply(instance, ChatSegmentSavedData::new));
    private static final Map<Long, SavedDataType<ChatSegmentSavedData>> TYPES = new ConcurrentHashMap<>();

    private final List<ChatEntry> entries;

    public ChatSegmentSavedData() {
        this(List.of(), List.of(), List.of());
    }

    private ChatSegmentSavedData(List<ChatEntry> entries, List<ChatMessage> messages,
                                 List<SystemChatMessage> systemMessages) {
        TreeMap<Long, ChatEntry> merged = new TreeMap<>();
        for (ChatMessage message : messages) merged.put(message.id(), ChatEntry.player(message));
        for (SystemChatMessage message : systemMessages) merged.put(message.id(), ChatEntry.legacySystem(message));
        for (ChatEntry entry : entries) merged.put(entry.id(), entry);
        this.entries = new ArrayList<>(merged.values());
    }

    public static ChatSegmentSavedData get(MinecraftServer server, long segmentId) {
        return server.getDataStorage().computeIfAbsent(type(segmentId));
    }

    public void add(ChatEntry entry) {
        int index = lowerBound(entry.id());
        if (index < entries.size() && entries.get(index).id() == entry.id()) entries.set(index, entry);
        else entries.add(index, entry);
        setDirty();
    }

    public ChatEntry delete(long messageId, UUID requesterUuid, long now) {
        int index = find(messageId);
        if (index < 0) return null;
        ChatEntry entry = entries.get(index);
        if (!entry.canDelete(requesterUuid, now)) return null;
        ChatEntry deleted = entry.asDeleted();
        entries.set(index, deleted);
        setDirty();
        return deleted;
    }

    public List<ChatEntry> getHistoryBefore(long beforeId, int limit, UUID viewerUuid, ChatFilter filter) {
        int index = lowerBound(beforeId) - 1;
        List<ChatEntry> result = new ArrayList<>(Math.max(1, limit));
        while (index >= 0 && result.size() < limit) {
            ChatEntry entry = entries.get(index--);
            if (filter.matches(entry, viewerUuid)) result.add(entry);
        }
        result.sort(Comparator.comparingLong(ChatEntry::id));
        return List.copyOf(result);
    }

    public List<ChatEntry> getHistoryAfter(long afterId, int limit, UUID viewerUuid, ChatFilter filter) {
        int index = upperBound(afterId);
        List<ChatEntry> result = new ArrayList<>(Math.max(1, limit));
        while (index < entries.size() && result.size() < limit) {
            ChatEntry entry = entries.get(index++);
            if (filter.matches(entry, viewerUuid)) result.add(entry);
        }
        return List.copyOf(result);
    }

    public long latestVisibleId(UUID viewerUuid) {
        for (int i = entries.size() - 1; i >= 0; i--) {
            ChatEntry entry = entries.get(i);
            if (entry.isVisibleTo(viewerUuid)) return entry.id();
        }
        return 0L;
    }

    public boolean containsId(long id) {
        return find(id) >= 0;
    }

    private int find(long id) {
        int index = lowerBound(id);
        return index < entries.size() && entries.get(index).id() == id ? index : -1;
    }

    private int lowerBound(long id) {
        int low = 0;
        int high = entries.size();
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (entries.get(mid).id() < id) low = mid + 1;
            else high = mid;
        }
        return low;
    }

    private int upperBound(long id) {
        int low = 0;
        int high = entries.size();
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (entries.get(mid).id() <= id) low = mid + 1;
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
}
