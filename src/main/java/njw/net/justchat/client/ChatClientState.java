package njw.net.justchat.client;

import net.minecraft.network.chat.Component;
import njw.net.justchat.data.ChatEntry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;

public final class ChatClientState {
    private static final long HISTORY_REQUEST_TIMEOUT_NANOS = 5_000_000_000L;
    private static final int MAX_HISTORY_PAGE_SIZE = 100;
    private static final int MAX_CACHED_ENTRIES = Math.max(20, Integer.getInteger("njw_just_chat.clientHistoryWindow", 2000));
    private static final int MAX_VANILLA_ENTRIES = 200;
    private static final int INITIAL_HISTORY_LIMIT = Math.min(MAX_HISTORY_PAGE_SIZE, MAX_CACHED_ENTRIES);
    private static final int PAGING_HISTORY_LIMIT = Math.min(MAX_HISTORY_PAGE_SIZE, Math.max(1, MAX_CACHED_ENTRIES / 4));
    private static final NavigableMap<Long, ChatClientEntry> PERSISTENT = new TreeMap<>();
    private static final List<ChatClientEntry> VANILLA = new ArrayList<>();
    private static final List<ChatClientEntry> VIEW = new ArrayList<>();
    private static boolean historyInitialized;
    private static boolean historyLoading;
    private static boolean hasOlderHistory = true;
    private static boolean hasNewerHistory;
    private static boolean unseenNewerWhileLoading;
    private static boolean historyRequestTimedOut;
    private static long historyRequestStartedAtNanos;
    private static long nextHistoryRequestId = 1L;
    private static long activeHistoryRequestId;
    private static HistoryDirection historyDirection = HistoryDirection.NONE;

    private ChatClientState() {}

    public static boolean addPersistent(ChatEntry entry) {
        boolean existed = PERSISTENT.containsKey(entry.id());
        if (!existed && !shouldAcceptNewPersistent(entry.id())) return false;
        PERSISTENT.put(entry.id(), ChatClientEntry.persistent(entry));
        if (trimOldestToLimit()) hasOlderHistory = true;
        rebuildView();
        return !existed;
    }

    public static void addVanilla(Component message, long createdAt) {
        if (hasNewerHistory) return;
        VANILLA.add(ChatClientEntry.vanilla(message.copy(), createdAt));
        VANILLA.sort(Comparator.comparingLong(ChatClientEntry::createdAt));
        while (VANILLA.size() > MAX_VANILLA_ENTRIES) VANILLA.removeFirst();
        rebuildView();
    }

    public static boolean beginInitialHistoryRequest() {
        expireHistoryRequestIfNeeded();
        if (historyInitialized || historyLoading) return false;
        startHistoryRequest(HistoryDirection.INITIAL);
        return true;
    }

    public static boolean beginOlderHistoryRequest() {
        expireHistoryRequestIfNeeded();
        if (!historyInitialized || historyLoading || !hasOlderHistory || PERSISTENT.isEmpty()) return false;
        startHistoryRequest(HistoryDirection.OLDER);
        return true;
    }

    public static boolean beginNewerHistoryRequest() {
        expireHistoryRequestIfNeeded();
        if (!historyInitialized || historyLoading || !hasNewerHistory || PERSISTENT.isEmpty()) return false;
        startHistoryRequest(HistoryDirection.NEWER);
        return true;
    }

    public static boolean beginLatestHistoryRequest() {
        expireHistoryRequestIfNeeded();
        if (!historyInitialized || historyLoading) return false;
        startHistoryRequest(HistoryDirection.LATEST);
        return true;
    }

    public static boolean completeHistory(long requestId, List<ChatEntry> entries, boolean hasMore) {
        expireHistoryRequestIfNeeded();
        if (!historyLoading || requestId != activeHistoryRequestId) return false;
        HistoryDirection direction = historyDirection;
        if (direction == HistoryDirection.LATEST) PERSISTENT.clear();
        for (ChatEntry entry : entries) PERSISTENT.put(entry.id(), ChatClientEntry.persistent(entry));

        if (direction == HistoryDirection.INITIAL) {
            hasOlderHistory = hasMore;
            hasNewerHistory = false;
            if (trimOldestToLimit()) hasOlderHistory = true;
        } else if (direction == HistoryDirection.OLDER) {
            hasOlderHistory = hasMore;
            if (trimNewestToLimit()) hasNewerHistory = true;
            if (unseenNewerWhileLoading) hasNewerHistory = true;
        } else if (direction == HistoryDirection.NEWER) {
            hasNewerHistory = hasMore || unseenNewerWhileLoading;
            if (trimOldestToLimit()) hasOlderHistory = true;
        } else if (direction == HistoryDirection.LATEST) {
            hasOlderHistory = hasMore;
            hasNewerHistory = unseenNewerWhileLoading;
            if (trimOldestToLimit()) hasOlderHistory = true;
        }

        rebuildView();
        historyInitialized = true;
        historyLoading = false;
        historyRequestStartedAtNanos = 0L;
        historyRequestTimedOut = false;
        activeHistoryRequestId = 0L;
        historyDirection = HistoryDirection.NONE;
        unseenNewerWhileLoading = false;
        return true;
    }

    public static int initialHistoryLimit() { return INITIAL_HISTORY_LIMIT; }
    public static int pagingHistoryLimit() { return PAGING_HISTORY_LIMIT; }
    public static boolean hasOlderHistory() { return hasOlderHistory; }
    public static boolean hasNewerHistory() { return hasNewerHistory; }
    public static boolean historyLoading() { expireHistoryRequestIfNeeded(); return historyLoading; }
    public static long activeHistoryRequestId() { return activeHistoryRequestId; }

    public static boolean consumeHistoryRequestTimeout() {
        expireHistoryRequestIfNeeded();
        if (!historyRequestTimedOut) return false;
        historyRequestTimedOut = false;
        return true;
    }

    public static long oldestPersistentId() {
        return PERSISTENT.isEmpty() ? Long.MAX_VALUE : PERSISTENT.firstKey();
    }

    public static long newestPersistentId() {
        return PERSISTENT.isEmpty() ? Long.MIN_VALUE : PERSISTENT.lastKey();
    }

    public static boolean canDisplayReadBoundary(long lastReadMessageId) {
        long oldest = oldestPersistentId();
        long newest = newestPersistentId();
        if (oldest == Long.MAX_VALUE || newest == Long.MIN_VALUE || newest <= lastReadMessageId) return false;
        if (lastReadMessageId == 0L) return !hasOlderHistory;
        return oldest <= lastReadMessageId;
    }

    public static int size() { return VIEW.size(); }
    public static ChatClientEntry get(int index) { return VIEW.get(index); }

    public static void clear() {
        PERSISTENT.clear();
        VANILLA.clear();
        VIEW.clear();
        historyInitialized = false;
        historyLoading = false;
        hasOlderHistory = true;
        hasNewerHistory = false;
        unseenNewerWhileLoading = false;
        historyRequestTimedOut = false;
        historyRequestStartedAtNanos = 0L;
        nextHistoryRequestId = 1L;
        activeHistoryRequestId = 0L;
        historyDirection = HistoryDirection.NONE;
    }

    private static void startHistoryRequest(HistoryDirection direction) {
        historyLoading = true;
        historyDirection = direction;
        unseenNewerWhileLoading = false;
        historyRequestTimedOut = false;
        historyRequestStartedAtNanos = System.nanoTime();
        activeHistoryRequestId = nextHistoryRequestId++;
    }

    private static void expireHistoryRequestIfNeeded() {
        if (!historyLoading || historyRequestStartedAtNanos == 0L) return;
        if (System.nanoTime() - historyRequestStartedAtNanos < HISTORY_REQUEST_TIMEOUT_NANOS) return;
        historyLoading = false;
        historyRequestStartedAtNanos = 0L;
        if (unseenNewerWhileLoading) hasNewerHistory = true;
        unseenNewerWhileLoading = false;
        activeHistoryRequestId = 0L;
        historyDirection = HistoryDirection.NONE;
        historyRequestTimedOut = true;
    }

    private static boolean shouldAcceptNewPersistent(long id) {
        long oldest = oldestPersistentId();
        long newest = newestPersistentId();

        if (historyLoading && historyDirection == HistoryDirection.LATEST) {
            unseenNewerWhileLoading = true;
            return false;
        }

        boolean paging = historyDirection == HistoryDirection.OLDER || historyDirection == HistoryDirection.NEWER;
        if (historyLoading && paging && newest != Long.MIN_VALUE && id > newest) {
            unseenNewerWhileLoading = true;
            return false;
        }

        if (hasNewerHistory && newest != Long.MIN_VALUE && id > newest) return false;
        return !hasOlderHistory || oldest == Long.MAX_VALUE || id >= oldest;
    }

    private static boolean trimOldestToLimit() {
        boolean removed = false;
        while (PERSISTENT.size() > MAX_CACHED_ENTRIES) {
            PERSISTENT.pollFirstEntry();
            removed = true;
        }
        return removed;
    }

    private static boolean trimNewestToLimit() {
        boolean removed = false;
        while (PERSISTENT.size() > MAX_CACHED_ENTRIES) {
            PERSISTENT.pollLastEntry();
            removed = true;
        }
        return removed;
    }

    private static void rebuildView() {
        VIEW.clear();
        VIEW.addAll(PERSISTENT.values());
        for (ChatClientEntry entry : VANILLA) {
            int index = VIEW.size();
            while (index > 0 && VIEW.get(index - 1).createdAt() > entry.createdAt()) index--;
            VIEW.add(index, entry);
        }
    }

    private enum HistoryDirection { NONE, INITIAL, OLDER, NEWER, LATEST }
}
