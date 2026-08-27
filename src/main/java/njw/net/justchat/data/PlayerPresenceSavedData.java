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

public final class PlayerPresenceSavedData extends SavedData {
    private static final Codec<Entry> ENTRY_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("uuid").forGetter(Entry::uuid),
            Codec.LONG.fieldOf("lastSeenAt").forGetter(Entry::lastSeenAt)
    ).apply(instance, Entry::new));

    private static final Codec<PlayerPresenceSavedData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ENTRY_CODEC.listOf().optionalFieldOf("players", List.of()).forGetter(PlayerPresenceSavedData::entries)
    ).apply(instance, PlayerPresenceSavedData::new));

    public static final SavedDataType<PlayerPresenceSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("njw_just_chat", "player_presence"),
            PlayerPresenceSavedData::new,
            CODEC,
            null
    );

    private final Map<UUID, Long> lastSeen = new HashMap<>();

    public PlayerPresenceSavedData() {}

    private PlayerPresenceSavedData(List<Entry> entries) {
        for (Entry entry : entries) lastSeen.put(entry.uuid(), entry.lastSeenAt());
    }

    public static PlayerPresenceSavedData get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public void update(UUID uuid, long lastSeenAt) {
        lastSeen.put(uuid, lastSeenAt);
        setDirty();
    }

    public long getLastSeen(UUID uuid) {
        return lastSeen.getOrDefault(uuid, 0L);
    }

    private List<Entry> entries() {
        List<Entry> entries = new ArrayList<>(lastSeen.size());
        lastSeen.forEach((uuid, time) -> entries.add(new Entry(uuid, time)));
        return entries;
    }

    private record Entry(UUID uuid, long lastSeenAt) {}
}