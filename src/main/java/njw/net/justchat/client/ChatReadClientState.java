package njw.net.justchat.client;

import njw.net.justchat.data.ChatFilter;
import njw.net.justchat.network.ChatFilterSelection;

public final class ChatReadClientState {
    private static boolean initialized;
    private static boolean boundaryPending;
    private static long serverLastReadMessageId;
    private static long sessionReadMessageId;
    private static long readBoundaryMessageId;

    private ChatReadClientState() {}

    public static void beginSession() {
        initialized = false;
        boundaryPending = true;
        serverLastReadMessageId = 0L;
        sessionReadMessageId = 0L;
        readBoundaryMessageId = 0L;
    }

    public static void update(long lastReadMessageId) {
        long safeId = Math.max(0L, lastReadMessageId);
        serverLastReadMessageId = safeId;
        sessionReadMessageId = Math.max(sessionReadMessageId, safeId);
        if (boundaryPending) {
            readBoundaryMessageId = safeId;
            boundaryPending = false;
        }
        initialized = true;
    }

    public static void markSeen(long messageId) {
        if (ChatFilterSelection.current() != ChatFilter.ALL || messageId < 0L) return;
        sessionReadMessageId = Math.max(sessionReadMessageId, messageId);
    }

    public static boolean initialized() {
        return initialized;
    }

    public static boolean readBoundaryVisible() {
        return ChatFilterSelection.current() == ChatFilter.ALL && initialized && !boundaryPending;
    }

    public static long readBoundaryMessageId() {
        return readBoundaryMessageId;
    }

    public static long readThroughMessageId() {
        return Math.max(serverLastReadMessageId, sessionReadMessageId);
    }

    public static void clear() {
        beginSession();
    }
}
