package njw.net.justchat.server;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import njw.net.justchat.data.PlayerPresence;
import njw.net.justchat.data.PlayerPresenceSavedData;
import njw.net.justchat.network.PlayerPresencePayload;

import java.util.List;

@EventBusSubscriber(modid = "njw_just_chat")
public final class PlayerPresenceTracker {
    private PlayerPresenceTracker() {}

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        update(player, true);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        update(player, false);
    }

    private static void update(ServerPlayer player, boolean online) {
        MinecraftServer server = player.level().getServer();
        long now = System.currentTimeMillis();
        PlayerPresenceSavedData.get(server).update(player.getUUID(), now);
        PlayerPresence presence = new PlayerPresence(player.getUUID(), now, online);
        PacketDistributor.sendToAllPlayers(new PlayerPresencePayload(List.of(presence)));
    }
}