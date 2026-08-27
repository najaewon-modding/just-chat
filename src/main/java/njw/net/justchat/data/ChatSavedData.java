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

    public ChatMessage add(
            UUID senderUuid,
            String senderName,
            String content,
            long createdAt,
            List<PlayerTag> playerTags,
            List<ItemTag> itemTags
    ) {
        ChatIndexSavedData index = ChatIndexSavedData.get(server);
        ChatIndexSavedData.SegmentInfo segment = getWritableSegment(index, createdAt);
        long messageId = index.allocateMessageId();

        ChatMessage message = new ChatMessage(
                messageId,
                senderUuid,
                senderName,
                content,
                createdAt,
                false,
                playerTags,
                itemTags
        );

        ChatSegmentSavedData.get(server, segment.segmentId()).add(message);
        index.recordEntry(segment.segmentId(), messageId);
        return message;
    }

    public SystemChatMessage addSystem(Component content, long createdAt) {
        ChatIndexSavedData index = ChatIndexSavedData.get(server);
        ChatIndexSavedData.SegmentInfo segment = getWritableSegment(index, createdAt);
        long messageId = index.allocateMessageId();

        SystemChatMessage message = new SystemChatMessage(
                messageId,
                content.copy(),
                createdAt
        );

        ChatSegmentSavedData.get(server, segment.segmentId()).addSystem(message);
        index.recordEntry(segment.segmentId(), messageId);
        return message;
    }

    public ChatMessage delete(long messageId, UUID requesterUuid, long now) {
        ChatIndexSavedData index = ChatIndexSavedData.get(server);
        ChatIndexSavedData.SegmentInfo segment = index.findSegmentForMessageId(messageId);
        if (segment == null) return null;

        return ChatSegmentSavedData.get(server, segment.segmentId()).delete(
                messageId,
                requesterUuid,
                now
        );
    }

    public long latestPersistentId() {
        List<ChatIndexSavedData.SegmentInfo> segments = ChatIndexSavedData.get(server).segments();

        for (int i = segments.size() - 1; i >= 0; i--) {
            ChatIndexSavedData.SegmentInfo segment = segments.get(i);
            if (segment.entryCount() > 0) return segment.lastMessageId();
        }

        return 0L;
    }

    public HistoryBatch getHistoryBefore(long beforeId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        ChatIndexSavedData index = ChatIndexSavedData.get(server);
        List<ChatIndexSavedData.SegmentInfo> segments = index.segments();

        List<ChatMessage> chats = new ArrayList<>();
        List<SystemChatMessage> systems = new ArrayList<>();
        int remaining = safeLimit;

        for (int i = segments.size() - 1; i >= 0 && remaining > 0; i--) {
            ChatIndexSavedData.SegmentInfo info = segments.get(i);

            if (info.entryCount() == 0) continue;
            if (info.firstMessageId() >= beforeId) continue;

            ChatSegmentSavedData.HistoryBatch batch = ChatSegmentSavedData.get(
                    server,
                    info.segmentId()
            ).getHistoryBefore(
                    beforeId,
                    remaining
            );

            chats.addAll(batch.messages());
            systems.addAll(batch.systemMessages());
            remaining -= batch.messages().size();
            remaining -= batch.systemMessages().size();
        }

        chats.sort(Comparator.comparingLong(ChatMessage::id));
        systems.sort(Comparator.comparingLong(SystemChatMessage::id));

        long oldestReturnedId = findOldestId(chats, systems);
        boolean hasMore = oldestReturnedId != Long.MAX_VALUE
                && index.hasEntryBefore(oldestReturnedId);

        return new HistoryBatch(
                List.copyOf(chats),
                List.copyOf(systems),
                hasMore
        );
    }

    public HistoryBatch getHistoryAfter(long afterId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        ChatIndexSavedData index = ChatIndexSavedData.get(server);
        List<ChatIndexSavedData.SegmentInfo> segments = index.segments();

        List<ChatMessage> chats = new ArrayList<>();
        List<SystemChatMessage> systems = new ArrayList<>();
        int remaining = safeLimit;

        for (ChatIndexSavedData.SegmentInfo info : segments) {
            if (remaining <= 0) break;
            if (info.entryCount() == 0) continue;
            if (info.lastMessageId() <= afterId) continue;

            ChatSegmentSavedData.HistoryBatch batch = ChatSegmentSavedData.get(
                    server,
                    info.segmentId()
            ).getHistoryAfter(
                    afterId,
                    remaining
            );

            chats.addAll(batch.messages());
            systems.addAll(batch.systemMessages());
            remaining -= batch.messages().size();
            remaining -= batch.systemMessages().size();
        }

        chats.sort(Comparator.comparingLong(ChatMessage::id));
        systems.sort(Comparator.comparingLong(SystemChatMessage::id));

        long newestReturnedId = findNewestId(chats, systems);
        boolean hasMore = newestReturnedId != Long.MIN_VALUE
                && index.hasEntryAfter(newestReturnedId);

        return new HistoryBatch(
                List.copyOf(chats),
                List.copyOf(systems),
                hasMore
        );
    }

    private static ChatIndexSavedData.SegmentInfo getWritableSegment(
            ChatIndexSavedData index,
            long createdAt
    ) {
        ChatIndexSavedData.SegmentInfo active = index.activeSegment();

        if (active == null || shouldRollOver(active, createdAt)) {
            return index.createSegment(createdAt);
        }

        return active;
    }

    private static boolean shouldRollOver(
            ChatIndexSavedData.SegmentInfo segment,
            long createdAt
    ) {
        if (segment.entryCount() >= ChatRules.MAX_PERSISTENT_ENTRIES_PER_SEGMENT) {
            return true;
        }

        return createdAt - segment.startedAt() >= ChatRules.CHAT_SEGMENT_DURATION_MILLIS;
    }

    private static long findOldestId(
            List<ChatMessage> chats,
            List<SystemChatMessage> systems
    ) {
        long oldest = Long.MAX_VALUE;

        for (ChatMessage message : chats) {
            oldest = Math.min(oldest, message.id());
        }

        for (SystemChatMessage message : systems) {
            oldest = Math.min(oldest, message.id());
        }

        return oldest;
    }

    private static long findNewestId(
            List<ChatMessage> chats,
            List<SystemChatMessage> systems
    ) {
        long newest = Long.MIN_VALUE;

        for (ChatMessage message : chats) {
            newest = Math.max(newest, message.id());
        }

        for (SystemChatMessage message : systems) {
            newest = Math.max(newest, message.id());
        }

        return newest;
    }

    private static synchronized void migrateLegacyIfNeeded(MinecraftServer server) {
        ChatIndexSavedData index = ChatIndexSavedData.get(server);
        if (index.legacyMigrationDone()) return;

        LegacyChatSavedData legacy = LegacyChatSavedData.get(server);

        if (!legacy.isEmpty()) {
            List<LegacyEntry> entries = new ArrayList<>();

            for (ChatMessage message : legacy.messages()) {
                entries.add(LegacyEntry.player(message));
            }

            for (SystemChatMessage message : legacy.systemMessages()) {
                entries.add(LegacyEntry.system(message));
            }

            entries.sort(Comparator.comparingLong(LegacyEntry::id));

            for (LegacyEntry entry : entries) {
                if (index.containsMessageId(entry.id())) continue;

                ChatIndexSavedData.SegmentInfo segment = getWritableSegment(
                        index,
                        entry.createdAt()
                );

                ChatSegmentSavedData data = ChatSegmentSavedData.get(
                        server,
                        segment.segmentId()
                );

                if (entry.chatMessage() != null) {
                    data.add(entry.chatMessage());
                } else {
                    data.addSystem(entry.systemMessage());
                }

                index.recordEntry(segment.segmentId(), entry.id());
            }

            index.ensureNextMessageIdAtLeast(legacy.nextMessageId());
        }

        index.markLegacyMigrationDone();
    }

    public record HistoryBatch(
            List<ChatMessage> messages,
            List<SystemChatMessage> systemMessages,
            boolean hasMore
    ) {}

    private record LegacyEntry(
            long id,
            long createdAt,
            ChatMessage chatMessage,
            SystemChatMessage systemMessage
    ) {
        private static LegacyEntry player(ChatMessage message) {
            return new LegacyEntry(
                    message.id(),
                    message.createdAt(),
                    message,
                    null
            );
        }

        private static LegacyEntry system(SystemChatMessage message) {
            return new LegacyEntry(
                    message.id(),
                    message.createdAt(),
                    null,
                    message
            );
        }
    }
}