package njw.net.justchat.network;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import njw.net.justchat.ChatRules;
import njw.net.justchat.data.ChatSavedData;
import njw.net.justchat.data.ItemTag;
import njw.net.justchat.data.PlayerChatReadSavedData;
import njw.net.justchat.data.PlayerPresence;
import njw.net.justchat.data.PlayerPresenceSavedData;
import njw.net.justchat.data.PlayerTag;
import njw.net.justchat.server.ChatRateLimiter;
import njw.net.justchat.server.ChatService;
import njw.net.justchat.server.PendingItemTagManager;
import njw.net.justchat.server.PlayerTagResolver;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@EventBusSubscriber(modid = "njw_just_chat")
public final class ChatNetwork {
    private ChatNetwork() {}

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("6");
        registrar.playToServer(SendChatPayload.TYPE, SendChatPayload.STREAM_CODEC, ChatNetwork::handleSendChat);
        registrar.playToServer(DeleteChatPayload.TYPE, DeleteChatPayload.STREAM_CODEC, ChatNetwork::handleDeleteChat);
        registrar.playToServer(RequestChatHistoryPayload.TYPE, RequestChatHistoryPayload.STREAM_CODEC,
                ChatNetwork::handleHistoryRequest);
        registrar.playToServer(RequestNewerChatHistoryPayload.TYPE, RequestNewerChatHistoryPayload.STREAM_CODEC,
                ChatNetwork::handleNewerHistoryRequest);
        registrar.playToServer(ChatReadStateRequestPayload.TYPE, ChatReadStateRequestPayload.STREAM_CODEC,
                ChatNetwork::handleChatReadStateRequest);
        registrar.playToServer(RequestPlayerSuggestionsPayload.TYPE, RequestPlayerSuggestionsPayload.STREAM_CODEC,
                ChatNetwork::handlePlayerSuggestions);
        registrar.playToServer(RequestWhisperTargetsPayload.TYPE, RequestWhisperTargetsPayload.STREAM_CODEC,
                ChatNetwork::handleWhisperTargets);
        registrar.playToServer(RequestPlayerPresencePayload.TYPE, RequestPlayerPresencePayload.STREAM_CODEC,
                ChatNetwork::handlePlayerPresence);
        registrar.playToServer(CreateItemTagPayload.TYPE, CreateItemTagPayload.STREAM_CODEC,
                ChatNetwork::handleCreateItemTag);
        registrar.playToClient(NewChatPayload.TYPE, NewChatPayload.STREAM_CODEC);
        registrar.playToClient(ChatHistoryPayload.TYPE, ChatHistoryPayload.STREAM_CODEC);
        registrar.playToClient(ChatReadStatePayload.TYPE, ChatReadStatePayload.STREAM_CODEC);
        registrar.playToClient(PlayerSuggestionsPayload.TYPE, PlayerSuggestionsPayload.STREAM_CODEC);
        registrar.playToClient(WhisperTargetsPayload.TYPE, WhisperTargetsPayload.STREAM_CODEC);
        registrar.playToClient(PlayerPresencePayload.TYPE, PlayerPresencePayload.STREAM_CODEC);
        registrar.playToClient(ItemTagCreatedPayload.TYPE, ItemTagCreatedPayload.STREAM_CODEC);
    }

    private static void handleSendChat(SendChatPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (!ChatRateLimiter.allow(player, ChatRateLimiter.Action.CHAT)) {
            if (ChatRateLimiter.allowChatWarning(player)) {
                player.sendSystemMessage(Component.translatable("message.njw_just_chat.rate_limited"));
            }
            return;
        }

        String content = payload.content();
        if (content.isBlank() || content.length() > ChatRules.MAX_MESSAGE_LENGTH) return;
        if (content.stripLeading().startsWith("/")) return;
        if (payload.itemTags().size() > ChatRules.MAX_ITEM_TAGS_PER_MESSAGE) return;

        MinecraftServer server = player.level().getServer();
        List<ItemTag> itemTags = PendingItemTagManager.resolve(player, content, payload.itemTags());
        List<PlayerTag> playerTags = PlayerTagResolver.resolve(server, content, itemTags);
        long createdAt = System.currentTimeMillis();

        if (payload.targetUuid().isBlank()) {
            ChatService.of(server).appendPlayer(player.getUUID(), player.getName().getString(), content, createdAt,
                    playerTags, itemTags);
            return;
        }

        UUID targetUuid;
        try {
            targetUuid = UUID.fromString(payload.targetUuid());
        } catch (IllegalArgumentException ignored) {
            return;
        }
        if (targetUuid.equals(player.getUUID())) return;

        ServerPlayer target = server.getPlayerList().getPlayer(targetUuid);
        if (target == null) {
            player.sendSystemMessage(Component.translatable("message.njw_just_chat.whisper_target_offline"));
            return;
        }

        ChatService.of(server).appendWhisper(player.getUUID(), player.getName().getString(), target.getUUID(),
                target.getName().getString(), content, createdAt, playerTags, itemTags);
    }

    private static void handleCreateItemTag(CreateItemTagPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        PendingItemTagManager.CreatedItem created = PendingItemTagManager.create(player, payload.inventorySlot());
        if (created == null) return;
        PacketDistributor.sendToPlayer(player,
                new ItemTagCreatedPayload(payload.requestId(), created.token(), created.item()));
    }

    private static void handleDeleteChat(DeleteChatPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (!ChatRateLimiter.allow(player, ChatRateLimiter.Action.DELETE)) return;
        ChatService.of(player.level().getServer()).delete(
                payload.messageId(), player.getUUID(), System.currentTimeMillis());
    }

    private static void handleHistoryRequest(RequestChatHistoryPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (!ChatRateLimiter.allow(player, ChatRateLimiter.Action.HISTORY)) return;
        ChatSavedData.HistoryBatch history = ChatService.of(player.level().getServer()).historyBefore(
                payload.beforeId(), payload.limit(), player.getUUID());
        PacketDistributor.sendToPlayer(player,
                new ChatHistoryPayload(payload.requestId(), history.entries(), history.hasMore()));
    }

    private static void handleNewerHistoryRequest(RequestNewerChatHistoryPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (!ChatRateLimiter.allow(player, ChatRateLimiter.Action.HISTORY)) return;
        ChatSavedData.HistoryBatch history = ChatService.of(player.level().getServer()).historyAfter(
                payload.afterId(), payload.limit(), player.getUUID());
        PacketDistributor.sendToPlayer(player,
                new ChatHistoryPayload(payload.requestId(), history.entries(), history.hasMore()));
    }

    private static void handleChatReadStateRequest(ChatReadStateRequestPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        MinecraftServer server = player.level().getServer();
        ChatService chatService = ChatService.of(server);
        PlayerChatReadSavedData readData = PlayerChatReadSavedData.get(server);
        long latestMessageId = Math.max(0L, chatService.latestPersistentId(player.getUUID()));
        if (payload.shouldMarkRead()) {
            long requestedId = Math.max(0L, payload.lastReadMessageId());
            readData.markRead(player.getUUID(), Math.min(requestedId, latestMessageId));
            return;
        }
        long lastReadMessageId = readData.getOrInitialize(player.getUUID(), latestMessageId);
        PacketDistributor.sendToPlayer(player, new ChatReadStatePayload(lastReadMessageId));
    }

    private static void handlePlayerSuggestions(RequestPlayerSuggestionsPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (!ChatRateLimiter.allow(player, ChatRateLimiter.Action.SUGGESTIONS)) return;
        MinecraftServer server = player.level().getServer();
        List<PlayerSuggestionsPayload.Suggestion> suggestions = PlayerTagResolver.suggest(server, payload.query())
                .stream().map(suggestion -> new PlayerSuggestionsPayload.Suggestion(suggestion.name(),
                        suggestion.online())).toList();
        PacketDistributor.sendToPlayer(player, new PlayerSuggestionsPayload(payload.query(), suggestions));
    }

    private static void handleWhisperTargets(RequestWhisperTargetsPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (!ChatRateLimiter.allow(player, ChatRateLimiter.Action.SUGGESTIONS)) return;
        List<WhisperTargetsPayload.Target> targets = player.level().getServer().getPlayerList().getPlayers().stream()
                .filter(target -> !target.getUUID().equals(player.getUUID()))
                .sorted(Comparator.comparing(target -> target.getName().getString(), String.CASE_INSENSITIVE_ORDER))
                .map(target -> new WhisperTargetsPayload.Target(target.getUUID().toString(),
                        target.getName().getString()))
                .toList();
        PacketDistributor.sendToPlayer(player, new WhisperTargetsPayload(targets));
    }

    private static void handlePlayerPresence(RequestPlayerPresencePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (!ChatRateLimiter.allow(player, ChatRateLimiter.Action.PRESENCE)) return;
        MinecraftServer server = player.level().getServer();
        PlayerPresenceSavedData data = PlayerPresenceSavedData.get(server);
        List<PlayerPresence> result = new ArrayList<>(payload.playerUuids().size());
        for (UUID uuid : payload.playerUuids()) {
            boolean online = server.getPlayerList().getPlayer(uuid) != null;
            result.add(new PlayerPresence(uuid, data.getLastSeen(uuid), online));
        }
        PacketDistributor.sendToPlayer(player,
                new PlayerPresencePayload(List.copyOf(result), System.currentTimeMillis()));
    }
}
