package njw.net.justchat.client;

import net.minecraft.network.chat.Component;
import njw.net.justchat.data.ChatMessage;
import njw.net.justchat.data.SystemChatMessage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ChatClientState {
    private static final long SYSTEM_MATCH_WINDOW = 10000L;
    private static final int MAX_HISTORY_PAGE_SIZE = 100;
    private static final int MAX_RECENT_SYSTEM_MESSAGES = 32;

    private static final int MAX_CACHED_ENTRIES = Math.max(
            20,
            Integer.getInteger(
                    "njw_just_chat.clientHistoryWindow",
                    2000
            )
    );

    private static final int INITIAL_HISTORY_LIMIT = Math.min(
            MAX_HISTORY_PAGE_SIZE,
            MAX_CACHED_ENTRIES
    );

    private static final int PAGING_HISTORY_LIMIT = Math.min(
            MAX_HISTORY_PAGE_SIZE,
            Math.max(1, MAX_CACHED_ENTRIES / 4)
    );

    private static final List<ChatClientEntry> ENTRIES = new ArrayList<>();
    private static final List<SystemChatMessage> RECENT_SYSTEM_MESSAGES = new ArrayList<>();

    private static boolean historyInitialized;
    private static boolean historyLoading;
    private static boolean hasOlderHistory = true;
    private static boolean hasNewerHistory;
    private static boolean unseenNewerWhileLoading;
    private static HistoryDirection historyDirection = HistoryDirection.NONE;

    private ChatClientState() {}

    public static void addPlayer(ChatMessage message) {
        int existing = findPlayer(message.id());

        if (existing >= 0) {
            ENTRIES.set(existing, ChatClientEntry.player(message));
            sort();
            return;
        }

        if (!shouldAcceptNewPersistent(message.id())) return;

        ENTRIES.add(ChatClientEntry.player(message));
        sort();

        if (trimOldestToLimit()) {
            hasOlderHistory = true;
        }
    }

    public static void addSystem(SystemChatMessage message) {
        rememberSystem(message);
        int existing = findSystem(message.id());

        if (existing >= 0) {
            ENTRIES.set(existing, ChatClientEntry.system(message));
            removeMatchingVanilla(message);
            sort();
            return;
        }

        if (!shouldAcceptNewPersistent(message.id())) return;

        putSystem(message);
        sort();

        if (trimOldestToLimit()) {
            hasOlderHistory = true;
        }
    }

    public static void addVanilla(Component message, long receivedAt) {
        if (hasNewerHistory) return;

        ENTRIES.add(ChatClientEntry.vanilla(message.copy(), receivedAt));
        sort();

        if (trimOldestToLimit()) {
            hasOlderHistory = true;
        }
    }

    public static boolean beginInitialHistoryRequest() {
        if (historyInitialized || historyLoading) return false;

        historyLoading = true;
        historyDirection = HistoryDirection.INITIAL;
        unseenNewerWhileLoading = false;
        return true;
    }

    public static boolean beginOlderHistoryRequest() {
        if (!historyInitialized || historyLoading || !hasOlderHistory) return false;
        if (oldestPersistentId() == Long.MAX_VALUE) return false;

        historyLoading = true;
        historyDirection = HistoryDirection.OLDER;
        unseenNewerWhileLoading = false;
        return true;
    }

    public static boolean beginNewerHistoryRequest() {
        if (!historyInitialized || historyLoading || !hasNewerHistory) return false;
        if (newestPersistentId() == Long.MIN_VALUE) return false;

        historyLoading = true;
        historyDirection = HistoryDirection.NEWER;
        unseenNewerWhileLoading = false;
        return true;
    }

    public static void completeHistory(
            List<ChatMessage> messages,
            List<SystemChatMessage> systemMessages,
            boolean hasMore
    ) {
        HistoryDirection direction = historyDirection;

        if (direction == HistoryDirection.NONE) {
            direction = HistoryDirection.INITIAL;
        }

        for (ChatMessage message : messages) {
            putPlayer(message);
        }

        for (SystemChatMessage message : systemMessages) {
            putSystem(message);
        }

        sort();

        if (direction == HistoryDirection.INITIAL) {
            hasOlderHistory = hasMore;
            hasNewerHistory = false;

            if (trimOldestToLimit()) {
                hasOlderHistory = true;
            }
        }

        if (direction == HistoryDirection.OLDER) {
            hasOlderHistory = hasMore;

            if (trimNewestToLimit()) {
                hasNewerHistory = true;
            }

            if (unseenNewerWhileLoading) {
                hasNewerHistory = true;
            }
        }

        if (direction == HistoryDirection.NEWER) {
            hasNewerHistory = hasMore || unseenNewerWhileLoading;

            if (trimOldestToLimit()) {
                hasOlderHistory = true;
            }
        }

        historyInitialized = true;
        historyLoading = false;
        historyDirection = HistoryDirection.NONE;
        unseenNewerWhileLoading = false;
    }

    public static int initialHistoryLimit() {
        return INITIAL_HISTORY_LIMIT;
    }

    public static int pagingHistoryLimit() {
        return PAGING_HISTORY_LIMIT;
    }

    public static boolean hasOlderHistory() {
        return hasOlderHistory;
    }

    public static long oldestPersistentId() {
        long oldest = Long.MAX_VALUE;

        for (ChatClientEntry entry : ENTRIES) {
            if (entry.isPlayer()) {
                oldest = Math.min(oldest, entry.playerMessageId());
            }

            if (entry.isSystem()) {
                oldest = Math.min(oldest, entry.systemMessageId());
            }
        }

        return oldest;
    }

    public static long newestPersistentId() {
        long newest = Long.MIN_VALUE;

        for (ChatClientEntry entry : ENTRIES) {
            if (entry.isPlayer()) {
                newest = Math.max(newest, entry.playerMessageId());
            }

            if (entry.isSystem()) {
                newest = Math.max(newest, entry.systemMessageId());
            }
        }

        return newest;
    }

    public static boolean canDisplayReadBoundary(long lastReadMessageId) {
        long oldest = oldestPersistentId();
        long newest = newestPersistentId();

        if (oldest == Long.MAX_VALUE || newest == Long.MIN_VALUE) return false;
        if (newest <= lastReadMessageId) return false;

        if (lastReadMessageId == 0L) {
            return !hasOlderHistory;
        }

        return oldest <= lastReadMessageId;
    }

    public static SystemChatMessage findRecentSystem(Component message, long now) {
        cleanupRecentSystems(now);

        for (int i = RECENT_SYSTEM_MESSAGES.size() - 1; i >= 0; i--) {
            SystemChatMessage system = RECENT_SYSTEM_MESSAGES.get(i);

            if (Math.abs(now - system.createdAt()) > SYSTEM_MATCH_WINDOW) continue;
            if (sameMessage(system.content(), message)) return system;
        }

        return null;
    }

    public static int size() {
        return ENTRIES.size();
    }

    public static ChatClientEntry get(int index) {
        return ENTRIES.get(index);
    }

    public static void clear() {
        ENTRIES.clear();
        RECENT_SYSTEM_MESSAGES.clear();
        historyInitialized = false;
        historyLoading = false;
        hasOlderHistory = true;
        hasNewerHistory = false;
        unseenNewerWhileLoading = false;
        historyDirection = HistoryDirection.NONE;
    }

    private static boolean shouldAcceptNewPersistent(long id) {
        long oldest = oldestPersistentId();
        long newest = newestPersistentId();

        if (historyLoading
                && (historyDirection == HistoryDirection.OLDER
                || historyDirection == HistoryDirection.NEWER)
                && newest != Long.MIN_VALUE
                && id > newest) {
            unseenNewerWhileLoading = true;
            return false;
        }

        if (hasNewerHistory && newest != Long.MIN_VALUE && id > newest) {
            return false;
        }

        return !hasOlderHistory || oldest == Long.MAX_VALUE || id >= oldest;
    }

    private static void putPlayer(ChatMessage message) {
        int index = findPlayer(message.id());
        ChatClientEntry entry = ChatClientEntry.player(message);

        if (index >= 0) {
            ENTRIES.set(index, entry);
        } else {
            ENTRIES.add(entry);
        }
    }

    private static void putSystem(SystemChatMessage message) {
        int index = findSystem(message.id());
        ChatClientEntry entry = ChatClientEntry.system(message);

        if (index >= 0) {
            ENTRIES.set(index, entry);
        } else {
            ENTRIES.add(entry);
        }

        removeMatchingVanilla(message);
    }

    private static int findPlayer(long id) {
        for (int i = 0; i < ENTRIES.size(); i++) {
            ChatClientEntry entry = ENTRIES.get(i);

            if (entry.isPlayer() && entry.playerMessageId() == id) {
                return i;
            }
        }

        return -1;
    }

    private static int findSystem(long id) {
        for (int i = 0; i < ENTRIES.size(); i++) {
            ChatClientEntry entry = ENTRIES.get(i);

            if (entry.isSystem() && entry.systemMessageId() == id) {
                return i;
            }
        }

        return -1;
    }

    private static boolean trimOldestToLimit() {
        boolean removedPersistent = false;

        while (ENTRIES.size() > MAX_CACHED_ENTRIES) {
            ChatClientEntry removed = ENTRIES.removeFirst();

            if (removed.isPlayer() || removed.isSystem()) {
                removedPersistent = true;
            }
        }

        return removedPersistent;
    }

    private static boolean trimNewestToLimit() {
        boolean removedPersistent = false;

        while (ENTRIES.size() > MAX_CACHED_ENTRIES) {
            ChatClientEntry removed = ENTRIES.removeLast();

            if (removed.isPlayer() || removed.isSystem()) {
                removedPersistent = true;
            }
        }

        return removedPersistent;
    }

    private static void rememberSystem(SystemChatMessage message) {
        RECENT_SYSTEM_MESSAGES.add(message);

        while (RECENT_SYSTEM_MESSAGES.size() > MAX_RECENT_SYSTEM_MESSAGES) {
            RECENT_SYSTEM_MESSAGES.removeFirst();
        }

        cleanupRecentSystems(System.currentTimeMillis());
    }

    private static void cleanupRecentSystems(long now) {
        RECENT_SYSTEM_MESSAGES.removeIf(
                message -> Math.abs(now - message.createdAt()) > SYSTEM_MATCH_WINDOW
        );
    }

    private static void removeMatchingVanilla(SystemChatMessage message) {
        ENTRIES.removeIf(
                entry -> entry.type() == ChatClientEntry.Type.VANILLA
                        && Math.abs(entry.createdAt() - message.createdAt()) <= SYSTEM_MATCH_WINDOW
                        && sameMessage(entry.vanillaMessage(), message.content())
        );
    }

    private static boolean sameMessage(Component first, Component second) {
        return first.getString().equals(second.getString());
    }

    private static void sort() {
        ENTRIES.sort(
                Comparator.comparingLong(ChatClientEntry::createdAt)
                        .thenComparingLong(ChatClientState::persistentSortId)
        );
    }

    private static long persistentSortId(ChatClientEntry entry) {
        if (entry.isPlayer()) return entry.playerMessageId();
        if (entry.isSystem()) return entry.systemMessageId();
        return Long.MAX_VALUE;
    }

    private enum HistoryDirection {
        NONE,
        INITIAL,
        OLDER,
        NEWER
    }
}