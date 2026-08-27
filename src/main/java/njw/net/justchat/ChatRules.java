package njw.net.justchat;

public final class ChatRules {
    public static final int MAX_MESSAGE_LENGTH = 256;
    public static final int MAX_ITEM_TAGS_PER_MESSAGE = 1;
    public static final int MAX_PENDING_ITEM_TAGS = 4;

    public static final long CHAT_SEGMENT_DURATION_MILLIS = Long.getLong(
            "njw_just_chat.segmentDurationMillis",
            180L * 24L * 60L * 60L * 1000L
    );

    public static final int MAX_PERSISTENT_ENTRIES_PER_SEGMENT = Integer.getInteger(
            "njw_just_chat.maxEntriesPerSegment",
            100_000
    );

    private ChatRules() {}
}