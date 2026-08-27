package njw.net.justchat.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PlayerChatReadSavedData extends SavedData {
    private static final Codec<Entry> ENTRY_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("uuid").forGetter(Entry::uuid),
            Codec.LONG.fieldOf("lastReadMessageId").forGetter(Entry::lastReadMessageId)
    ).apply(instance, Entry::new));

    private static final Codec<PlayerChatReadSavedData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ENTRY_CODEC.listOf().optionalFieldOf("players", List.of()).forGetter(PlayerChatReadSavedData::entries)
    ).apply(instance, PlayerChatReadSavedData::new));

    public static final SavedDataType<PlayerChatReadSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("njw_just_chat", "player_read_state"),
            PlayerChatReadSavedData::new,
            CODEC,
            null
    );

    private final Map<UUID, Long> lastReadMessageIds = new HashMap<>();

    public PlayerChatReadSavedData() {}

    private PlayerChatReadSavedData(List<Entry> entries) {
        for (Entry entry : entries) {
            lastReadMessageIds.put(entry.uuid(), Math.max(0L, entry.lastReadMessageId()));
        }
    }

    public static PlayerChatReadSavedData get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public long getOrInitialize(UUID uuid, long latestMessageId) {
        Long current = lastReadMessageIds.get(uuid);
        if (current != null) return current;

        long initial = Math.max(0L, latestMessageId);
        lastReadMessageIds.put(uuid, initial);
        setDirty();
        return initial;
    }

    public void markRead(UUID uuid, long latestMessageId) {
        long current = lastReadMessageIds.getOrDefault(uuid, 0L);
        long next = Math.max(current, Math.max(0L, latestMessageId));
        if (next == current && lastReadMessageIds.containsKey(uuid)) return;

        lastReadMessageIds.put(uuid, next);
        setDirty();
    }

    private List<Entry> entries() {
        List<Entry> entries = new ArrayList<>(lastReadMessageIds.size());
        lastReadMessageIds.forEach((uuid, id) -> entries.add(new Entry(uuid, id)));
        return entries;
    }

    private record Entry(UUID uuid, long lastReadMessageId) {}
}