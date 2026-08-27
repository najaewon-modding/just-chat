package njw.net.justchat.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.List;

public final class ChatIndexSavedData extends SavedData {
    private static final Codec<SegmentInfo> SEGMENT_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("segmentId").forGetter(SegmentInfo::segmentId),
            Codec.LONG.fieldOf("startedAt").forGetter(SegmentInfo::startedAt),
            Codec.LONG.fieldOf("firstMessageId").forGetter(SegmentInfo::firstMessageId),
            Codec.LONG.fieldOf("lastMessageId").forGetter(SegmentInfo::lastMessageId),
            Codec.INT.fieldOf("entryCount").forGetter(SegmentInfo::entryCount)
    ).apply(instance, SegmentInfo::new));

    private static final Codec<ChatIndexSavedData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.optionalFieldOf("nextMessageId", 1L)
                    .forGetter(data -> data.nextMessageId),
            Codec.LONG.optionalFieldOf("nextSegmentId", 1L)
                    .forGetter(data -> data.nextSegmentId),
            SEGMENT_CODEC.listOf().optionalFieldOf("segments", List.of())
                    .forGetter(data -> data.segments),
            Codec.BOOL.optionalFieldOf("legacyMigrationDone", false)
                    .forGetter(data -> data.legacyMigrationDone)
    ).apply(instance, ChatIndexSavedData::new));

    public static final SavedDataType<ChatIndexSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("njw_just_chat", "chat_index"),
            ChatIndexSavedData::new,
            CODEC,
            null
    );

    private long nextMessageId;
    private long nextSegmentId;
    private final List<SegmentInfo> segments;
    private boolean legacyMigrationDone;

    public ChatIndexSavedData() {
        this(1L, 1L, List.of(), false);
    }

    private ChatIndexSavedData(
            long nextMessageId,
            long nextSegmentId,
            List<SegmentInfo> segments,
            boolean legacyMigrationDone
    ) {
        this.segments = new ArrayList<>(segments);
        this.nextMessageId = Math.max(
                nextMessageId,
                findNextMessageId(this.segments)
        );
        this.nextSegmentId = Math.max(
                nextSegmentId,
                findNextSegmentId(this.segments)
        );
        this.legacyMigrationDone = legacyMigrationDone;
    }

    public static ChatIndexSavedData get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public long allocateMessageId() {
        long id = nextMessageId++;
        setDirty();
        return id;
    }

    public SegmentInfo activeSegment() {
        if (segments.isEmpty()) return null;
        return segments.getLast();
    }

    public SegmentInfo createSegment(long startedAt) {
        SegmentInfo info = new SegmentInfo(
                nextSegmentId++,
                startedAt,
                0L,
                0L,
                0
        );

        segments.add(info);
        setDirty();
        return info;
    }

    public void recordEntry(
            long segmentId,
            long messageId
    ) {
        for (int i = 0; i < segments.size(); i++) {
            SegmentInfo info = segments.get(i);
            if (info.segmentId() != segmentId) continue;

            long firstMessageId = info.entryCount() == 0
                    ? messageId
                    : Math.min(info.firstMessageId(), messageId);

            long lastMessageId = info.entryCount() == 0
                    ? messageId
                    : Math.max(info.lastMessageId(), messageId);

            segments.set(
                    i,
                    new SegmentInfo(
                            info.segmentId(),
                            info.startedAt(),
                            firstMessageId,
                            lastMessageId,
                            info.entryCount() + 1
                    )
            );

            setDirty();
            return;
        }
    }

    public SegmentInfo findSegmentForMessageId(long messageId) {
        for (int i = segments.size() - 1; i >= 0; i--) {
            SegmentInfo info = segments.get(i);

            if (info.entryCount() == 0) continue;
            if (messageId < info.firstMessageId()) continue;
            if (messageId > info.lastMessageId()) continue;

            return info;
        }

        return null;
    }

    public boolean containsMessageId(long messageId) {
        return findSegmentForMessageId(messageId) != null;
    }

    public boolean hasEntryBefore(long messageId) {
        for (SegmentInfo info : segments) {
            if (info.entryCount() == 0) continue;
            if (info.firstMessageId() < messageId) return true;
        }

        return false;
    }

    public boolean hasEntryAfter(long messageId) {
        for (int i = segments.size() - 1; i >= 0; i--) {
            SegmentInfo info = segments.get(i);

            if (info.entryCount() == 0) continue;
            if (info.lastMessageId() > messageId) return true;
        }

        return false;
    }

    public List<SegmentInfo> segments() {
        return List.copyOf(segments);
    }

    public boolean legacyMigrationDone() {
        return legacyMigrationDone;
    }

    public void markLegacyMigrationDone() {
        if (legacyMigrationDone) return;

        legacyMigrationDone = true;
        setDirty();
    }

    public void ensureNextMessageIdAtLeast(long nextId) {
        if (nextMessageId >= nextId) return;

        nextMessageId = nextId;
        setDirty();
    }

    private static long findNextMessageId(List<SegmentInfo> segments) {
        long nextId = 1L;

        for (SegmentInfo info : segments) {
            if (info.entryCount() == 0) continue;
            nextId = Math.max(nextId, info.lastMessageId() + 1L);
        }

        return nextId;
    }

    private static long findNextSegmentId(List<SegmentInfo> segments) {
        long nextId = 1L;

        for (SegmentInfo info : segments) {
            nextId = Math.max(nextId, info.segmentId() + 1L);
        }

        return nextId;
    }

    public record SegmentInfo(
            long segmentId,
            long startedAt,
            long firstMessageId,
            long lastMessageId,
            int entryCount
    ) {}
}