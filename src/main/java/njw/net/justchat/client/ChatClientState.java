package njw.net.justchat.client;

import net.minecraft.network.chat.Component;
import njw.net.justchat.data.ChatMessage;
import njw.net.justchat.data.SystemChatMessage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ChatClientState {
    private static final long SYSTEM_MATCH_WINDOW = 10000L;
    private static final List<ChatClientEntry> ENTRIES = new ArrayList<>();
    private static boolean historyInitialized;
    private static boolean historyLoading;
    private static boolean hasOlderHistory = true;

    private ChatClientState() {}

    public static void addPlayer(ChatMessage message) {
        putPlayer(message);
        sort();
    }

    public static void addSystem(SystemChatMessage message) {
        putSystem(message);
        sort();
    }

    public static void addVanilla(Component message, long receivedAt) {
        ENTRIES.add(ChatClientEntry.vanilla(message.copy(), receivedAt));
        sort();
    }

    public static boolean beginInitialHistoryRequest() {
        if (historyInitialized || historyLoading) return false;
        historyLoading = true;
        return true;
    }

    public static boolean beginOlderHistoryRequest() {
        if (!historyInitialized || historyLoading || !hasOlderHistory) return false;
        if (oldestPersistentId() == Long.MAX_VALUE) return false;
        historyLoading = true;
        return true;
    }

    public static void completeHistory(
            List<ChatMessage> messages,
            List<SystemChatMessage> systemMessages,
            boolean hasMore
    ) {
        for (ChatMessage message : messages) putPlayer(message);
        for (SystemChatMessage message : systemMessages) putSystem(message);
        historyInitialized = true;
        historyLoading = false;
        hasOlderHistory = hasMore;
        sort();
    }

    public static long oldestPersistentId() {
        long oldest = Long.MAX_VALUE;
        for (ChatClientEntry entry : ENTRIES) {
            if (entry.isPlayer()) oldest = Math.min(oldest, entry.playerMessageId());
            if (entry.isSystem()) oldest = Math.min(oldest, entry.systemMessageId());
        }
        return oldest;
    }

    public static SystemChatMessage findRecentSystem(Component message, long now) {
        for (int i = ENTRIES.size() - 1; i >= 0; i--) {
            ChatClientEntry entry = ENTRIES.get(i);
            if (!entry.isSystem()) continue;
            SystemChatMessage system = entry.systemMessage();
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
        historyInitialized = false;
        historyLoading = false;
        hasOlderHistory = true;
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
        removeMatchingVanilla(message);
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

    private static void removeMatchingVanilla(SystemChatMessage message) {
        ENTRIES.removeIf(entry -> entry.type() == ChatClientEntry.Type.VANILLA
                && Math.abs(entry.createdAt() - message.createdAt()) <= SYSTEM_MATCH_WINDOW
                && sameMessage(entry.vanillaMessage(), message.content()));
    }

    private static boolean sameMessage(Component first, Component second) {
        return first.getString().equals(second.getString());
    }

    private static void sort() {
        ENTRIES.sort(Comparator.comparingLong(ChatClientEntry::createdAt));
    }
}