package njw.net.justchat.client;

import net.minecraft.network.chat.Component;
import njw.net.justchat.data.ChatMessage;
import njw.net.justchat.data.SystemChatMessage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ChatClientState {
    private static final int MAX_ENTRIES = 1000;
    private static final long SYSTEM_MATCH_WINDOW = 10000L;
    private static final List<ChatClientEntry> ENTRIES = new ArrayList<>();

    private ChatClientState() {}

    public static void addPlayer(ChatMessage message) {
        int index = findPlayer(message.id());
        ChatClientEntry entry = ChatClientEntry.player(message);
        if (index >= 0) ENTRIES.set(index, entry);
        else ENTRIES.add(entry);
        sortAndTrim();
    }

    public static void addSystem(SystemChatMessage message) {
        int index = findSystem(message.id());
        ChatClientEntry entry = ChatClientEntry.system(message);
        if (index >= 0) ENTRIES.set(index, entry);
        else ENTRIES.add(entry);
        removeMatchingVanilla(message);
        sortAndTrim();
    }

    public static void addVanilla(Component message, long receivedAt) {
        ENTRIES.add(ChatClientEntry.vanilla(message.copy(), receivedAt));
        sortAndTrim();
    }

    public static void mergeHistory(List<ChatMessage> messages) {
        for (ChatMessage message : messages) addPlayer(message);
    }

    public static void mergeSystemHistory(List<SystemChatMessage> messages) {
        for (SystemChatMessage message : messages) addSystem(message);
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

    private static void sortAndTrim() {
        ENTRIES.sort(Comparator.comparingLong(ChatClientEntry::createdAt));
        while (ENTRIES.size() > MAX_ENTRIES) ENTRIES.removeFirst();
    }
}