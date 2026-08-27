package njw.net.justchat.client;

import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import njw.net.justchat.data.ChatMessage;
import njw.net.justchat.data.PlayerPresence;
import njw.net.justchat.data.PlayerTag;
import njw.net.justchat.network.RequestPlayerPresencePayload;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PlayerPresenceClientState {
    private static final int REQUEST_BATCH_SIZE = 128;
    private static final Map<UUID, PlayerPresence> PRESENCES = new HashMap<>();
    private static final Set<UUID> REQUESTED = new HashSet<>();

    private PlayerPresenceClientState() {}

    public static PlayerPresence get(UUID uuid) {
        return PRESENCES.get(uuid);
    }

    public static void updateAll(List<PlayerPresence> players) {
        for (PlayerPresence player : players) {
            PRESENCES.put(player.uuid(), player);
            REQUESTED.remove(player.uuid());
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
        REQUESTED.clear();
    }

    private static void request(Collection<UUID> uuids) {
        List<UUID> batch = new ArrayList<>(REQUEST_BATCH_SIZE);
        for (UUID uuid : uuids) {
            if (PRESENCES.containsKey(uuid) || !REQUESTED.add(uuid)) continue;
            batch.add(uuid);
            if (batch.size() < REQUEST_BATCH_SIZE) continue;
            send(batch);
            batch = new ArrayList<>(REQUEST_BATCH_SIZE);
        }
        if (!batch.isEmpty()) send(batch);
    }

    private static void send(List<UUID> uuids) {
        ClientPacketDistributor.sendToServer(new RequestPlayerPresencePayload(List.copyOf(uuids)));
    }
}