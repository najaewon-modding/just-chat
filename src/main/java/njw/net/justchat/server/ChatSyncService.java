package njw.net.justchat.server;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import njw.net.justchat.data.ChatEntry;
import njw.net.justchat.network.NewChatPayload;

import java.util.UUID;

public final class ChatSyncService {
    private ChatSyncService() {}

    public static void publish(MinecraftServer server, ChatEntry entry) {
        NewChatPayload payload = new NewChatPayload(entry);
        if (entry.audience().isGlobal()) {
            PacketDistributor.sendToAllPlayers(payload);
            return;
        }

        for (UUID uuid : entry.audience().players()) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) PacketDistributor.sendToPlayer(player, payload);
        }
    }
}
