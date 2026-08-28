package njw.net.justchat.client;

import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import njw.net.justchat.data.ChatMessage;
import njw.net.justchat.data.PlayerPresence;
import njw.net.justchat.data.PlayerTag;
import njw.net.justchat.network.RequestPlayerPresencePayload;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PlayerPresenceClientState {
    private static final int REQUEST_BATCH_SIZE = 128;
    private static final long REQUEST_RETRY_NANOS = 5_000_000_000L;
    private static final Map<UUID, PlayerPresence> PRESENCES = new HashMap<>();
    private static final Map<UUID, Long> REQUESTED_AT = new HashMap<>();

    private PlayerPresenceClientState() {}

    public static PlayerPresence get(UUID uuid) {
        PlayerPresence presence = PRESENCES.get(uuid);
        if (presence == null) request(List.of(uuid));
        return presence;
    }

    public static void updateAll(List<PlayerPresence> presences, long serverTimeMillis) {
        long clientTimeMillis = System.currentTimeMillis();
        for (PlayerPresence presence : presences) {
            PlayerPresence adjusted = adjustToClientClock(presence, serverTimeMillis, clientTimeMillis);
            PRESENCES.put(adjusted.uuid(), adjusted);
            REQUESTED_AT.remove(adjusted.uuid());
        }
    }

    public static void requestForMessage(ChatMessage message) {
        Set<UUID> uuids = new LinkedHashSet<>();
        for (PlayerTag tag : message.playerTags()) uuids.add(tag.targetUuid());
        request(uuids);
    }

    public static void requestForMessages(List<ChatMessage> messages) {
        Set<UUID> uuids = new LinkedHashSet<>();
        for (ChatMessage message : messages) {
            for (PlayerTag tag : message.playerTags()) uuids.add(tag.targetUuid());
        }
        request(uuids);
    }

    public static void clear() {
        PRESENCES.clear();
        REQUESTED_AT.clear();
    }

    private static PlayerPresence adjustToClientClock(PlayerPresence presence, long serverTimeMillis,
                                                      long clientTimeMillis) {
        if (presence.lastSeenAt() <= 0L) return presence;
        long ageMillis = serverTimeMillis >= presence.lastSeenAt() ? serverTimeMillis - presence.lastSeenAt() : 0L;
        long adjustedLastSeenAt = ageMillis >= clientTimeMillis ? 1L : clientTimeMillis - ageMillis;
        return new PlayerPresence(presence.uuid(), adjustedLastSeenAt, presence.online());
    }

    private static void request(Collection<UUID> uuids) {
        long now = System.nanoTime();
        List<UUID> batch = new ArrayList<>(REQUEST_BATCH_SIZE);
        for (UUID uuid : uuids) {
            if (PRESENCES.containsKey(uuid) || recentlyRequested(uuid, now)) continue;
            REQUESTED_AT.put(uuid, now);
            batch.add(uuid);
            if (batch.size() < REQUEST_BATCH_SIZE) continue;
            send(batch);
            batch = new ArrayList<>(REQUEST_BATCH_SIZE);
        }
        if (!batch.isEmpty()) send(batch);
    }

    private static boolean recentlyRequested(UUID uuid, long now) {
        Long requestedAt = REQUESTED_AT.get(uuid);
        return requestedAt != null && now - requestedAt < REQUEST_RETRY_NANOS;
    }

    private static void send(List<UUID> uuids) {
        ClientPacketDistributor.sendToServer(new RequestPlayerPresencePayload(List.copyOf(uuids)));
    }
}