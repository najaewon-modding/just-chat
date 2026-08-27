package njw.net.justchat.network;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import njw.net.justchat.ChatRules;
import njw.net.justchat.data.ChatMessage;
import njw.net.justchat.data.ChatSavedData;
import njw.net.justchat.data.ItemTag;
import njw.net.justchat.data.PlayerChatReadSavedData;
import njw.net.justchat.data.PlayerPresence;
import njw.net.justchat.data.PlayerPresenceSavedData;
import njw.net.justchat.data.PlayerTag;
import njw.net.justchat.server.PendingItemTagManager;
import njw.net.justchat.server.PlayerTagResolver;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@EventBusSubscriber(modid = "njw_just_chat")
public final class ChatNetwork {
    private ChatNetwork() {}

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        registrar.playToServer(
                SendChatPayload.TYPE,
                SendChatPayload.STREAM_CODEC,
                ChatNetwork::handleSendChat
        );

        registrar.playToServer(
                DeleteChatPayload.TYPE,
                DeleteChatPayload.STREAM_CODEC,
                ChatNetwork::handleDeleteChat
        );

        registrar.playToServer(
                RequestChatHistoryPayload.TYPE,
                RequestChatHistoryPayload.STREAM_CODEC,
                ChatNetwork::handleHistoryRequest
        );

        registrar.playToServer(
                RequestNewerChatHistoryPayload.TYPE,
                RequestNewerChatHistoryPayload.STREAM_CODEC,
                ChatNetwork::handleNewerHistoryRequest
        );

        registrar.playToServer(
                ChatReadStateRequestPayload.TYPE,
                ChatReadStateRequestPayload.STREAM_CODEC,
                ChatNetwork::handleChatReadStateRequest
        );

        registrar.playToServer(
                RequestPlayerSuggestionsPayload.TYPE,
                RequestPlayerSuggestionsPayload.STREAM_CODEC,
                ChatNetwork::handlePlayerSuggestions
        );

        registrar.playToServer(
                RequestPlayerPresencePayload.TYPE,
                RequestPlayerPresencePayload.STREAM_CODEC,
                ChatNetwork::handlePlayerPresence
        );

        registrar.playToServer(
                CreateItemTagPayload.TYPE,
                CreateItemTagPayload.STREAM_CODEC,
                ChatNetwork::handleCreateItemTag
        );

        registrar.playToClient(
                NewChatPayload.TYPE,
                NewChatPayload.STREAM_CODEC
        );

        registrar.playToClient(
                ChatDeletedPayload.TYPE,
                ChatDeletedPayload.STREAM_CODEC
        );

        registrar.playToClient(
                ChatHistoryPayload.TYPE,
                ChatHistoryPayload.STREAM_CODEC
        );

        registrar.playToClient(
                NewSystemChatPayload.TYPE,
                NewSystemChatPayload.STREAM_CODEC
        );

        registrar.playToClient(
                ChatReadStatePayload.TYPE,
                ChatReadStatePayload.STREAM_CODEC
        );

        registrar.playToClient(
                PlayerSuggestionsPayload.TYPE,
                PlayerSuggestionsPayload.STREAM_CODEC
        );

        registrar.playToClient(
                PlayerPresencePayload.TYPE,
                PlayerPresencePayload.STREAM_CODEC
        );

        registrar.playToClient(
                ItemTagCreatedPayload.TYPE,
                ItemTagCreatedPayload.STREAM_CODEC
        );
    }

    private static void handleSendChat(
            SendChatPayload payload,
            IPayloadContext context
    ) {
        if (!(context.player() instanceof ServerPlayer player)) return;

        String content = payload.content();

        if (content.isBlank()) return;
        if (content.length() > ChatRules.MAX_MESSAGE_LENGTH) return;
        if (content.stripLeading().startsWith("/")) return;

        if (payload.itemTags().size() > ChatRules.MAX_ITEM_TAGS_PER_MESSAGE) {
            return;
        }

        MinecraftServer server = player.level().getServer();

        List<ItemTag> itemTags = PendingItemTagManager.resolve(
                player,
                content,
                payload.itemTags()
        );

        List<PlayerTag> playerTags = PlayerTagResolver.resolve(
                server,
                content,
                itemTags
        );

        ChatMessage message = ChatSavedData.get(server).add(
                player.getUUID(),
                player.getName().getString(),
                content,
                System.currentTimeMillis(),
                playerTags,
                itemTags
        );

        PacketDistributor.sendToAllPlayers(
                new NewChatPayload(message)
        );
    }

    private static void handleCreateItemTag(
            CreateItemTagPayload payload,
            IPayloadContext context
    ) {
        if (!(context.player() instanceof ServerPlayer player)) return;

        PendingItemTagManager.CreatedItem created = PendingItemTagManager.create(
                player,
                payload.inventorySlot()
        );

        if (created == null) return;

        PacketDistributor.sendToPlayer(
                player,
                new ItemTagCreatedPayload(
                        payload.requestId(),
                        created.token(),
                        created.item()
                )
        );
    }

    private static void handleDeleteChat(
            DeleteChatPayload payload,
            IPayloadContext context
    ) {
        if (!(context.player() instanceof ServerPlayer player)) return;

        ChatSavedData data = ChatSavedData.get(
                player.level().getServer()
        );

        ChatMessage deleted = data.delete(
                payload.messageId(),
                player.getUUID(),
                System.currentTimeMillis()
        );

        if (deleted == null) return;

        PacketDistributor.sendToAllPlayers(
                new ChatDeletedPayload(deleted)
        );
    }

    private static void handleHistoryRequest(
            RequestChatHistoryPayload payload,
            IPayloadContext context
    ) {
        if (!(context.player() instanceof ServerPlayer player)) return;

        ChatSavedData data = ChatSavedData.get(
                player.level().getServer()
        );

        ChatSavedData.HistoryBatch history = data.getHistoryBefore(
                payload.beforeId(),
                payload.limit()
        );

        PacketDistributor.sendToPlayer(
                player,
                new ChatHistoryPayload(
                        history.messages(),
                        history.systemMessages(),
                        history.hasMore()
                )
        );
    }

    private static void handleNewerHistoryRequest(
            RequestNewerChatHistoryPayload payload,
            IPayloadContext context
    ) {
        if (!(context.player() instanceof ServerPlayer player)) return;

        ChatSavedData data = ChatSavedData.get(
                player.level().getServer()
        );

        ChatSavedData.HistoryBatch history = data.getHistoryAfter(
                payload.afterId(),
                payload.limit()
        );

        PacketDistributor.sendToPlayer(
                player,
                new ChatHistoryPayload(
                        history.messages(),
                        history.systemMessages(),
                        history.hasMore()
                )
        );
    }

    private static void handleChatReadStateRequest(
            ChatReadStateRequestPayload payload,
            IPayloadContext context
    ) {
        if (!(context.player() instanceof ServerPlayer player)) return;

        MinecraftServer server = player.level().getServer();
        ChatSavedData chatData = ChatSavedData.get(server);
        PlayerChatReadSavedData readData = PlayerChatReadSavedData.get(server);
        long latestMessageId = chatData.latestPersistentId();

        if (payload.shouldMarkRead()) {
            readData.markRead(
                    player.getUUID(),
                    latestMessageId
            );
            return;
        }

        long lastReadMessageId = readData.getOrInitialize(
                player.getUUID(),
                latestMessageId
        );

        PacketDistributor.sendToPlayer(
                player,
                new ChatReadStatePayload(lastReadMessageId)
        );
    }

    private static void handlePlayerSuggestions(
            RequestPlayerSuggestionsPayload payload,
            IPayloadContext context
    ) {
        if (!(context.player() instanceof ServerPlayer player)) return;

        MinecraftServer server = player.level().getServer();

        List<String> names = PlayerTagResolver.suggest(
                server,
                payload.query()
        );

        PacketDistributor.sendToPlayer(
                player,
                new PlayerSuggestionsPayload(
                        payload.query(),
                        names
                )
        );
    }

    private static void handlePlayerPresence(
            RequestPlayerPresencePayload payload,
            IPayloadContext context
    ) {
        if (!(context.player() instanceof ServerPlayer player)) return;

        MinecraftServer server = player.level().getServer();
        PlayerPresenceSavedData data = PlayerPresenceSavedData.get(server);

        List<PlayerPresence> result = new ArrayList<>(
                payload.playerUuids().size()
        );

        for (UUID uuid : payload.playerUuids()) {
            boolean online = server.getPlayerList().getPlayer(uuid) != null;

            result.add(
                    new PlayerPresence(
                            uuid,
                            data.getLastSeen(uuid),
                            online
                    )
            );
        }

        PacketDistributor.sendToPlayer(
                player,
                new PlayerPresencePayload(
                        List.copyOf(result)
                )
        );
    }
}