package njw.net.justchat.data;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import njw.net.justchat.ChatRules;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class ChatSavedData {
    private final MinecraftServer server;

    private ChatSavedData(MinecraftServer server) {
        this.server = server;
    }

    public static ChatSavedData get(MinecraftServer server) {
        migrateLegacyIfNeeded(server);
        return new ChatSavedData(server);
    }

    public ChatEntry addPlayer(UUID senderUuid, String senderName, String content, long createdAt,
                               List<PlayerTag> playerTags, List<ItemTag> itemTags) {
        return addPlayerEntry(senderUuid, senderName, content, createdAt, playerTags, itemTags, null, null);
    }

    public ChatEntry addWhisper(UUID senderUuid, String senderName, UUID targetUuid, String targetName,
                                String content, long createdAt, List<PlayerTag> playerTags, List<ItemTag> itemTags) {
        return addPlayerEntry(senderUuid, senderName, content, createdAt, playerTags, itemTags, targetUuid, targetName);
    }

    private ChatEntry addPlayerEntry(UUID senderUuid, String senderName, String content, long createdAt,
                                     List<PlayerTag> playerTags, List<ItemTag> itemTags,
                                     UUID targetUuid, String targetName) {
        ChatIndexSavedData index = ChatIndexSavedData.get(server);
        ChatIndexSavedData.SegmentInfo segment = getWritableSegment(index, createdAt);
        long messageId = index.allocateMessageId();
        ChatMessage message = new ChatMessage(messageId, senderUuid, senderName, content, createdAt, false,
                playerTags, itemTags);
        ChatEntry entry = targetUuid == null ? ChatEntry.player(message)
                : ChatEntry.playerWhisper(message, targetUuid, targetName);
        ChatSegmentSavedData.get(server, segment.segmentId()).add(entry);
        index.recordEntry(segment.segmentId(), messageId);
        return entry;
    }

    public ChatEntry addSystem(Component content, long createdAt, ChatEntry.Sender sender,
                               ChatEntry.Audience audience, ChatEntry.Origin origin) {
        ChatIndexSavedData index = ChatIndexSavedData.get(server);
        ChatIndexSavedData.SegmentInfo segment = getWritableSegment(index, createdAt);
        long messageId = index.allocateMessageId();
        ChatEntry entry = ChatEntry.system(messageId, content, createdAt, sender, audience, origin);
        ChatSegmentSavedData.get(server, segment.segmentId()).add(entry);
        index.recordEntry(segment.segmentId(), messageId);
        return entry;
    }

    public ChatEntry delete(long messageId, UUID requesterUuid, long now) {
        ChatIndexSavedData index = ChatIndexSavedData.get(server);
        ChatIndexSavedData.SegmentInfo segment = index.findSegmentForMessageId(messageId);
        if (segment == null) return null;
        return ChatSegmentSavedData.get(server, segment.segmentId()).delete(messageId, requesterUuid, now);
    }

    public long latestPersistentId(UUID viewerUuid) {
        List<ChatIndexSavedData.SegmentInfo> segments = ChatIndexSavedData.get(server).segments();
        for (int i = segments.size() - 1; i >= 0; i--) {
            ChatIndexSavedData.SegmentInfo segment = segments.get(i);
            if (segment.entryCount() == 0) continue;
            long id = ChatSegmentSavedData.get(server, segment.segmentId()).latestVisibleId(viewerUuid);
            if (id > 0L) return id;
        }
        return 0L;
    }

    public HistoryBatch getHistoryBefore(long beforeId, int limit, UUID viewerUuid, ChatFilter filter) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        int requested = safeLimit + 1;
        List<ChatEntry> result = new ArrayList<>(requested);
        List<ChatIndexSavedData.SegmentInfo> segments = ChatIndexSavedData.get(server).segments();

        for (int i = segments.size() - 1; i >= 0 && result.size() < requested; i--) {
            ChatIndexSavedData.SegmentInfo info = segments.get(i);
            if (info.entryCount() == 0 || info.firstMessageId() >= beforeId) continue;
            result.addAll(ChatSegmentSavedData.get(server, info.segmentId()).getHistoryBefore(
                    beforeId, requested - result.size(), viewerUuid, filter));
        }

        result.sort(Comparator.comparingLong(ChatEntry::id));
        boolean hasMore = result.size() > safeLimit;
        while (result.size() > safeLimit) result.removeFirst();
        return new HistoryBatch(List.copyOf(result), hasMore);
    }

    public HistoryBatch getHistoryAfter(long afterId, int limit, UUID viewerUuid, ChatFilter filter) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        int requested = safeLimit + 1;
        List<ChatEntry> result = new ArrayList<>(requested);
        List<ChatIndexSavedData.SegmentInfo> segments = ChatIndexSavedData.get(server).segments();

        for (ChatIndexSavedData.SegmentInfo info : segments) {
            if (result.size() >= requested) break;
            if (info.entryCount() == 0 || info.lastMessageId() <= afterId) continue;
            result.addAll(ChatSegmentSavedData.get(server, info.segmentId()).getHistoryAfter(
                    afterId, requested - result.size(), viewerUuid, filter));
        }

        result.sort(Comparator.comparingLong(ChatEntry::id));
        boolean hasMore = result.size() > safeLimit;
        while (result.size() > safeLimit) result.removeLast();
        return new HistoryBatch(List.copyOf(result), hasMore);
    }

    private static ChatIndexSavedData.SegmentInfo getWritableSegment(ChatIndexSavedData index, long createdAt) {
        ChatIndexSavedData.SegmentInfo active = index.activeSegment();
        if (active == null || shouldRollOver(active, createdAt)) return index.createSegment(createdAt);
        return active;
    }

    private static boolean shouldRollOver(ChatIndexSavedData.SegmentInfo segment, long createdAt) {
        if (segment.entryCount() >= ChatRules.MAX_PERSISTENT_ENTRIES_PER_SEGMENT) return true;
        return createdAt - segment.startedAt() >= ChatRules.CHAT_SEGMENT_DURATION_MILLIS;
    }

    private static synchronized void migrateLegacyIfNeeded(MinecraftServer server) {
        ChatIndexSavedData index = ChatIndexSavedData.get(server);
        if (index.legacyMigrationDone()) return;
        LegacyChatSavedData legacy = LegacyChatSavedData.get(server);

        if (!legacy.isEmpty()) {
            List<LegacyEntry> entries = new ArrayList<>();
            for (ChatMessage message : legacy.messages()) entries.add(new LegacyEntry(message.id(), message.createdAt(), ChatEntry.player(message)));
            for (SystemChatMessage message : legacy.systemMessages()) entries.add(new LegacyEntry(message.id(), message.createdAt(), ChatEntry.legacySystem(message)));
            entries.sort(Comparator.comparingLong(LegacyEntry::id));

            for (LegacyEntry entry : entries) {
                if (index.containsMessageId(entry.id())) continue;
                ChatIndexSavedData.SegmentInfo segment = getWritableSegment(index, entry.createdAt());
                ChatSegmentSavedData.get(server, segment.segmentId()).add(entry.entry());
                index.recordEntry(segment.segmentId(), entry.id());
            }

            index.ensureNextMessageIdAtLeast(legacy.nextMessageId());
        }

        index.markLegacyMigrationDone();
    }

    public record HistoryBatch(List<ChatEntry> entries, boolean hasMore) {}
    private record LegacyEntry(long id, long createdAt, ChatEntry entry) {}
}
