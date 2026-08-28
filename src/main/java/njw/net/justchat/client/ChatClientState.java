package njw.net.justchat.client;

import net.minecraft.network.chat.Component;
import njw.net.justchat.data.ChatMessage;
import njw.net.justchat.data.SystemChatMessage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ChatClientState {
    private static final long SYSTEM_MATCH_WINDOW_MILLIS = 10_000L;
    private static final long SYSTEM_MATCH_WINDOW_NANOS = 10_000_000_000L;
    private static final long HISTORY_REQUEST_TIMEOUT_NANOS = 1_000_000_000L;
    private static final int MAX_HISTORY_PAGE_SIZE = 100;
    private static final int MAX_RECENT_SYSTEM_MESSAGES = 32;
    private static final int MAX_CACHED_ENTRIES = Math.max(
            20,
            Integer.getInteger("njw_just_chat.clientHistoryWindow", 2000)
    );
    private static final int INITIAL_HISTORY_LIMIT = Math.min(MAX_HISTORY_PAGE_SIZE, MAX_CACHED_ENTRIES);
    private static final int PAGING_HISTORY_LIMIT = Math.min(
            MAX_HISTORY_PAGE_SIZE,
            Math.max(1, MAX_CACHED_ENTRIES / 4)
    );
    private static final List<ChatClientEntry> ENTRIES = new ArrayList<>();
    private static final List<RecentPersistentSystem> RECENT_PERSISTENT_SYSTEMS = new ArrayList<>();
    private static final List<RecentVanillaSystem> RECENT_VANILLA_SYSTEMS = new ArrayList<>();
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

    public static void addPlayer(ChatMessage message) {
        int index = findPlayer(message.id());

        if (index >= 0) {
            ENTRIES.set(index, ChatClientEntry.player(message));
            sort();
            return;
        }

        if (!shouldAcceptNewPersistent(message.id())) return;
        ENTRIES.add(ChatClientEntry.player(message));
        sort();
        if (trimOldestToLimit()) hasOlderHistory = true;
    }

    public static SystemAddResult addSystem(SystemChatMessage message) {
        Long vanillaReceivedAt = consumeRecentVanillaSystem(message.content());
        boolean replacedVanilla = vanillaReceivedAt != null;

        if (replacedVanilla) {
            removeMatchingVanilla(message, vanillaReceivedAt);
        }

        int index = findSystem(message.id());

        if (index >= 0) {
            ENTRIES.set(index, ChatClientEntry.system(message));
            sort();
            if (!replacedVanilla) rememberPersistentSystem(message);
            return replacedVanilla ? SystemAddResult.REPLACED_VANILLA : SystemAddResult.DUPLICATE;
        }

        if (shouldAcceptNewPersistent(message.id())) {
            putSystem(message);
            sort();
            if (trimOldestToLimit()) hasOlderHistory = true;
        }

        if (replacedVanilla) return SystemAddResult.REPLACED_VANILLA;
        rememberPersistentSystem(message);
        return SystemAddResult.NEW;
    }

    public static void addVanilla(Component message, long createdAt) {
        if (hasNewerHistory) return;
        ENTRIES.add(ChatClientEntry.vanilla(message.copy(), createdAt));
        sort();
        if (trimOldestToLimit()) hasOlderHistory = true;
    }

    public static void rememberVanillaSystem(Component message, long receivedAtMillis) {
        long now = System.nanoTime();
        RECENT_VANILLA_SYSTEMS.add(new RecentVanillaSystem(message.copy(), receivedAtMillis, now));

        while (RECENT_VANILLA_SYSTEMS.size() > MAX_RECENT_SYSTEM_MESSAGES) {
            RECENT_VANILLA_SYSTEMS.removeFirst();
        }

        cleanupRecentSystems(now);
    }

    public static boolean consumeRecentPersistentSystem(Component message) {
        long now = System.nanoTime();
        cleanupRecentSystems(now);

        for (int i = RECENT_PERSISTENT_SYSTEMS.size() - 1; i >= 0; i--) {
            RecentPersistentSystem recent = RECENT_PERSISTENT_SYSTEMS.get(i);
            if (!sameMessage(recent.message().content(), message)) continue;
            RECENT_PERSISTENT_SYSTEMS.remove(i);
            return true;
        }

        return false;
    }

    public static boolean beginInitialHistoryRequest() {
        expireHistoryRequestIfNeeded();
        if (historyInitialized || historyLoading) return false;
        startHistoryRequest(HistoryDirection.INITIAL);
        return true;
    }

    public static boolean beginOlderHistoryRequest() {
        expireHistoryRequestIfNeeded();
        if (!historyInitialized || historyLoading || !hasOlderHistory) return false;
        if (oldestPersistentId() == Long.MAX_VALUE) return false;
        startHistoryRequest(HistoryDirection.OLDER);
        return true;
    }

    public static boolean beginNewerHistoryRequest() {
        expireHistoryRequestIfNeeded();
        if (!historyInitialized || historyLoading || !hasNewerHistory) return false;
        if (newestPersistentId() == Long.MIN_VALUE) return false;
        startHistoryRequest(HistoryDirection.NEWER);
        return true;
    }

    public static boolean beginLatestHistoryRequest() {
        expireHistoryRequestIfNeeded();
        if (!historyInitialized || historyLoading) return false;
        startHistoryRequest(HistoryDirection.LATEST);
        return true;
    }

    public static boolean completeHistory(
            long requestId,
            List<ChatMessage> messages,
            List<SystemChatMessage> systemMessages,
            boolean hasMore
    ) {
        expireHistoryRequestIfNeeded();
        if (!historyLoading || requestId != activeHistoryRequestId) return false;
        HistoryDirection direction = historyDirection;
        if (direction == HistoryDirection.LATEST) ENTRIES.clear();

        for (ChatMessage message : messages) putPlayer(message);
        mergeHistorySystems(systemMessages);

        sort();

        if (direction == HistoryDirection.INITIAL) {
            hasOlderHistory = hasMore;
            hasNewerHistory = false;
            if (trimOldestToLimit()) hasOlderHistory = true;
        }

        if (direction == HistoryDirection.OLDER) {
            hasOlderHistory = hasMore;
            if (trimNewestToLimit()) hasNewerHistory = true;
            if (unseenNewerWhileLoading) hasNewerHistory = true;
        }

        if (direction == HistoryDirection.NEWER) {
            hasNewerHistory = hasMore || unseenNewerWhileLoading;
            if (trimOldestToLimit()) hasOlderHistory = true;
        }

        if (direction == HistoryDirection.LATEST) {
            hasOlderHistory = hasMore;
            hasNewerHistory = unseenNewerWhileLoading;
            if (trimOldestToLimit()) hasOlderHistory = true;
        }

        historyInitialized = true;
        historyLoading = false;
        historyRequestStartedAtNanos = 0L;
        historyRequestTimedOut = false;
        activeHistoryRequestId = 0L;
        historyDirection = HistoryDirection.NONE;
        unseenNewerWhileLoading = false;
        return true;
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

    public static boolean hasNewerHistory() {
        return hasNewerHistory;
    }

    public static boolean historyLoading() {
        expireHistoryRequestIfNeeded();
        return historyLoading;
    }

    public static long activeHistoryRequestId() {
        return activeHistoryRequestId;
    }

    public static boolean consumeHistoryRequestTimeout() {
        expireHistoryRequestIfNeeded();
        if (!historyRequestTimedOut) return false;
        historyRequestTimedOut = false;
        return true;
    }

    public static long oldestPersistentId() {
        long oldest = Long.MAX_VALUE;

        for (ChatClientEntry entry : ENTRIES) {
            if (entry.isPlayer()) oldest = Math.min(oldest, entry.playerMessageId());
            if (entry.isSystem()) oldest = Math.min(oldest, entry.systemMessageId());
        }

        return oldest;
    }

    public static long newestPersistentId() {
        long newest = Long.MIN_VALUE;

        for (ChatClientEntry entry : ENTRIES) {
            if (entry.isPlayer()) newest = Math.max(newest, entry.playerMessageId());
            if (entry.isSystem()) newest = Math.max(newest, entry.systemMessageId());
        }

        return newest;
    }

    public static boolean canDisplayReadBoundary(long lastReadMessageId) {
        long oldest = oldestPersistentId();
        long newest = newestPersistentId();
        if (oldest == Long.MAX_VALUE || newest == Long.MIN_VALUE) return false;
        if (newest <= lastReadMessageId) return false;
        if (lastReadMessageId == 0L) return !hasOlderHistory;
        return oldest <= lastReadMessageId;
    }

    public static int size() {
        return ENTRIES.size();
    }

    public static ChatClientEntry get(int index) {
        return ENTRIES.get(index);
    }

    public static void clear() {
        ENTRIES.clear();
        RECENT_PERSISTENT_SYSTEMS.clear();
        RECENT_VANILLA_SYSTEMS.clear();
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

        if (historyLoading && (historyDirection == HistoryDirection.OLDER || historyDirection == HistoryDirection.NEWER)
                && newest != Long.MIN_VALUE && id > newest) {
            unseenNewerWhileLoading = true;
            return false;
        }

        if (hasNewerHistory && newest != Long.MIN_VALUE && id > newest) return false;
        return !hasOlderHistory || oldest == Long.MAX_VALUE || id >= oldest;
    }

    private static void mergeHistorySystems(List<SystemChatMessage> systemMessages) {
        List<SystemChatMessage> newestFirst = new ArrayList<>(systemMessages);
        newestFirst.sort(Comparator.comparingLong(SystemChatMessage::createdAt).reversed());

        for (SystemChatMessage message : newestFirst) {
            Long vanillaReceivedAt = consumeRecentVanillaSystem(message.content());
            if (vanillaReceivedAt != null) removeMatchingVanilla(message, vanillaReceivedAt);
        }

        for (SystemChatMessage message : systemMessages) putSystem(message);
    }

    private static void putPlayer(ChatMessage message) {
        int index = findPlayer(message.id());
        ChatClientEntry entry = ChatClientEntry.player(message);
        if (index >= 0) ENTRIES.set(index, entry);
        else ENTRIES.add(entry);
    }

    private static void putSystem(SystemChatMessage message) {
        int index = findSystem(message.id());
        ChatClientEntry entry = ChatClientEntry.system(message);
        if (index >= 0) ENTRIES.set(index, entry);
        else ENTRIES.add(entry);
    }

    private static int findPlayer(long id) {
        for (int i = 0; i < ENTRIES.size(); i++) {
            ChatClientEntry entry = ENTRIES.get(i);
            if (entry.isPlayer() && entry.playerMessageId() == id) return i;
        }

        return -1;
    }

    private static int findSystem(long id) {
        for (int i = 0; i < ENTRIES.size(); i++) {
            ChatClientEntry entry = ENTRIES.get(i);
            if (entry.isSystem() && entry.systemMessageId() == id) return i;
        }

        return -1;
    }

    private static boolean trimOldestToLimit() {
        boolean removedPersistent = false;

        while (ENTRIES.size() > MAX_CACHED_ENTRIES) {
            ChatClientEntry removed = ENTRIES.removeFirst();
            if (removed.isPlayer() || removed.isSystem()) removedPersistent = true;
        }

        return removedPersistent;
    }

    private static boolean trimNewestToLimit() {
        boolean removedPersistent = false;

        while (ENTRIES.size() > MAX_CACHED_ENTRIES) {
            ChatClientEntry removed = ENTRIES.removeLast();
            if (removed.isPlayer() || removed.isSystem()) removedPersistent = true;
        }

        return removedPersistent;
    }

    private static void rememberPersistentSystem(SystemChatMessage message) {
        long now = System.nanoTime();
        RECENT_PERSISTENT_SYSTEMS.add(new RecentPersistentSystem(message, now));

        while (RECENT_PERSISTENT_SYSTEMS.size() > MAX_RECENT_SYSTEM_MESSAGES) {
            RECENT_PERSISTENT_SYSTEMS.removeFirst();
        }

        cleanupRecentSystems(now);
    }

    private static Long consumeRecentVanillaSystem(Component message) {
        long now = System.nanoTime();
        cleanupRecentSystems(now);

        for (int i = RECENT_VANILLA_SYSTEMS.size() - 1; i >= 0; i--) {
            RecentVanillaSystem recent = RECENT_VANILLA_SYSTEMS.get(i);
            if (!sameMessage(recent.message(), message)) continue;
            RECENT_VANILLA_SYSTEMS.remove(i);
            return recent.receivedAtMillis();
        }

        return null;
    }

    private static void cleanupRecentSystems(long now) {
        RECENT_PERSISTENT_SYSTEMS.removeIf(
                recent -> now - recent.receivedAtNanos() > SYSTEM_MATCH_WINDOW_NANOS
        );
        RECENT_VANILLA_SYSTEMS.removeIf(
                recent -> now - recent.receivedAtNanos() > SYSTEM_MATCH_WINDOW_NANOS
        );
    }

    private static void removeMatchingVanilla(SystemChatMessage message, long receivedAtMillis) {
        for (int i = ENTRIES.size() - 1; i >= 0; i--) {
            ChatClientEntry entry = ENTRIES.get(i);
            if (entry.type() != ChatClientEntry.Type.VANILLA) continue;
            if (Math.abs(entry.createdAt() - receivedAtMillis) > SYSTEM_MATCH_WINDOW_MILLIS) continue;
            if (!sameMessage(entry.vanillaMessage(), message.content())) continue;
            ENTRIES.remove(i);
            return;
        }
    }

    private static boolean sameMessage(Component first, Component second) {
        return first.getString().equals(second.getString());
    }

    private static void sort() {
        List<ChatClientEntry> persistent = new ArrayList<>();
        List<ChatClientEntry> vanilla = new ArrayList<>();

        for (ChatClientEntry entry : ENTRIES) {
            if (entry.isPlayer() || entry.isSystem()) persistent.add(entry);
            else vanilla.add(entry);
        }

        persistent.sort(Comparator.comparingLong(ChatClientState::persistentSortId));
        vanilla.sort(Comparator.comparingLong(ChatClientEntry::createdAt));
        ENTRIES.clear();
        ENTRIES.addAll(persistent);

        for (ChatClientEntry entry : vanilla) insertVanilla(entry);
    }

    private static void insertVanilla(ChatClientEntry entry) {
        int index = ENTRIES.size();
        while (index > 0 && ENTRIES.get(index - 1).createdAt() > entry.createdAt()) index--;
        ENTRIES.add(index, entry);
    }

    private static long persistentSortId(ChatClientEntry entry) {
        if (entry.isPlayer()) return entry.playerMessageId();
        if (entry.isSystem()) return entry.systemMessageId();
        return Long.MAX_VALUE;
    }

    public enum SystemAddResult {
        NEW,
        REPLACED_VANILLA,
        DUPLICATE
    }

    private enum HistoryDirection {
        NONE,
        INITIAL,
        OLDER,
        NEWER,
        LATEST
    }

    private record RecentPersistentSystem(SystemChatMessage message, long receivedAtNanos) {}

    private record RecentVanillaSystem(Component message, long receivedAtMillis, long receivedAtNanos) {}
}