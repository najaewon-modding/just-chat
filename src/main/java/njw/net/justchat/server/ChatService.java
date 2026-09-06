package njw.net.justchat.server;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import njw.net.justchat.data.ChatEntry;
import njw.net.justchat.data.ChatSavedData;
import njw.net.justchat.data.ItemTag;
import njw.net.justchat.data.PlayerTag;

import java.util.List;
import java.util.UUID;

public final class ChatService {
    private final MinecraftServer server;
    private final ChatSavedData data;

    private ChatService(MinecraftServer server) {
        this.server = server;
        this.data = ChatSavedData.get(server);
    }

    public static ChatService of(MinecraftServer server) {
        return new ChatService(server);
    }

    public ChatEntry appendPlayer(UUID senderUuid, String senderName, String content, long createdAt,
                                  List<PlayerTag> playerTags, List<ItemTag> itemTags) {
        ChatEntry entry = data.addPlayer(senderUuid, senderName, content, createdAt, playerTags, itemTags);
        ChatSyncService.publish(server, entry);
        return entry;
    }

    public ChatEntry appendWhisper(UUID senderUuid, String senderName, UUID targetUuid, String targetName,
                                   String content, long createdAt, List<PlayerTag> playerTags,
                                   List<ItemTag> itemTags) {
        ChatEntry entry = data.addWhisper(senderUuid, senderName, targetUuid, targetName, content, createdAt,
                playerTags, itemTags);
        ChatSyncService.publish(server, entry);
        return entry;
    }

    public ChatEntry appendSystem(Component content, ChatEntry.Sender sender, ChatEntry.Audience audience,
                                  ChatEntry.Origin origin) {
        ChatEntry entry = data.addSystem(content, System.currentTimeMillis(), sender, audience, origin);
        ChatSyncService.publish(server, entry);
        return entry;
    }

    public ChatEntry delete(long messageId, UUID requesterUuid, long now) {
        ChatEntry deleted = data.delete(messageId, requesterUuid, now);
        if (deleted != null) ChatSyncService.publish(server, deleted);
        return deleted;
    }

    public ChatSavedData.HistoryBatch historyBefore(long beforeId, int limit, UUID viewerUuid) {
        return data.getHistoryBefore(beforeId, limit, viewerUuid);
    }

    public ChatSavedData.HistoryBatch historyAfter(long afterId, int limit, UUID viewerUuid) {
        return data.getHistoryAfter(afterId, limit, viewerUuid);
    }

    public long latestPersistentId(UUID viewerUuid) {
        return data.latestPersistentId(viewerUuid);
    }
}
