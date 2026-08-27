package njw.net.justchat.client;

public final class ChatReadClientState {
    private static boolean initialized;
    private static long lastReadMessageId;

    private ChatReadClientState() {}

    public static void beginSession() {
        initialized = false;
        lastReadMessageId = 0L;
    }

    public static void update(long messageId) {
        lastReadMessageId = Math.max(0L, messageId);
        initialized = true;
    }

    public static boolean initialized() {
        return initialized;
    }

    public static long lastReadMessageId() {
        return lastReadMessageId;
    }

    public static void clear() {
        beginSession();
    }
}