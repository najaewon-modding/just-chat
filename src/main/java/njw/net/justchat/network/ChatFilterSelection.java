package njw.net.justchat.network;

import njw.net.justchat.data.ChatFilter;

public final class ChatFilterSelection {
    private static ChatFilter current = ChatFilter.ALL;

    private ChatFilterSelection() {}

    public static ChatFilter current() {
        return current;
    }

    public static boolean select(ChatFilter filter) {
        ChatFilter next = filter == null ? ChatFilter.ALL : filter;
        if (current == next) return false;
        current = next;
        return true;
    }

    public static void clear() {
        current = ChatFilter.ALL;
    }
}
