package njw.net.justchat.client;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import njw.net.justchat.data.ChatEntry;
import njw.net.justchat.data.ChatMessage;
import njw.net.justchat.network.ChatHistoryPayload;
import njw.net.justchat.network.ChatReadStatePayload;
import njw.net.justchat.network.ItemTagCreatedPayload;
import njw.net.justchat.network.NewChatPayload;
import njw.net.justchat.network.PlayerPresencePayload;
import njw.net.justchat.network.PlayerSuggestionsPayload;
import njw.net.justchat.network.WhisperTargetSelection;
import njw.net.justchat.network.WhisperTargetsPayload;

import java.util.UUID;

@EventBusSubscriber(modid = "njw_just_chat", value = Dist.CLIENT)
public final class ChatClientNetwork {
    private ChatClientNetwork() {}

    @SubscribeEvent
    public static void register(RegisterClientPayloadHandlersEvent event) {
        event.register(NewChatPayload.TYPE, ChatClientNetwork::handleNewEntry);
        event.register(ChatHistoryPayload.TYPE, ChatClientNetwork::handleChatHistory);
        event.register(ChatReadStatePayload.TYPE, ChatClientNetwork::handleChatReadState);
        event.register(PlayerSuggestionsPayload.TYPE, ChatClientNetwork::handlePlayerSuggestions);
        event.register(WhisperTargetsPayload.TYPE, ChatClientNetwork::handleWhisperTargets);
        event.register(PlayerPresencePayload.TYPE, ChatClientNetwork::handlePlayerPresence);
        event.register(ItemTagCreatedPayload.TYPE, ChatClientNetwork::handleItemTagCreated);
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        VanillaChatBridge.clear();
        WhisperTargetScreenExtension.clear();
        ChatFilterScreenExtension.clear();
        ChatClientState.clear();
        ChatReadClientState.clear();
        PlayerPresenceClientState.clear();
    }

    private static void handleNewEntry(NewChatPayload payload, IPayloadContext context) {
        ChatEntry entry = payload.entry();
        Minecraft minecraft = Minecraft.getInstance();
        CustomChatScreen screen = minecraft.screen instanceof CustomChatScreen current ? current : null;
        UUID viewerUuid = minecraft.player == null ? null : minecraft.player.getUUID();
        boolean visibleInFilter = ChatClientState.matchesActiveFilter(entry, viewerUuid);
        boolean ownMessage = entry.isPlayer() && viewerUuid != null && viewerUuid.equals(entry.sender().uuid());

        if (screen != null && visibleInFilter) screen.beforeLivePersistentMessage(ownMessage);
        boolean isNew = visibleInFilter && ChatClientState.addPersistent(entry, viewerUuid);
        if (entry.isPlayer()) {
            PlayerPresenceClientState.requestForEntry(entry);
            ChatMessage message = entry.chatMessage();
            if (message != null) MentionNotifier.notifyIfMentioned(message);
        }
        if (screen != null && visibleInFilter) screen.afterLivePersistentMessage(entry.id());
        if (!entry.isPlayer() || entry.deleted()) return;
        if (!visibleInFilter || isNew) VanillaChatBridge.publishPlayerEntry(entry, screen != null);
    }

    private static void handleChatHistory(ChatHistoryPayload payload, IPayloadContext context) {
        boolean accepted = ChatClientState.completeHistory(
                payload.requestId(),
                payload.entries(),
                payload.hasMore()
        );
        if (!accepted) return;
        PlayerPresenceClientState.requestForEntries(payload.entries());
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof CustomChatScreen screen) screen.onHistoryUpdated();
    }

    private static void handleChatReadState(ChatReadStatePayload payload, IPayloadContext context) {
        ChatReadClientState.update(payload.lastReadMessageId());
    }

    private static void handlePlayerSuggestions(PlayerSuggestionsPayload payload, IPayloadContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof CustomChatScreen screen) {
            screen.updatePlayerSuggestions(payload.query(), payload.suggestions());
        }
    }

    private static void handleWhisperTargets(WhisperTargetsPayload payload, IPayloadContext context) {
        String selectedUuid = WhisperTargetSelection.targetUuid();
        if (!selectedUuid.isBlank()
                && payload.targets().stream().noneMatch(target -> target.uuid().equals(selectedUuid))) {
            WhisperTargetSelection.clear();
        }
        WhisperTargetScreenExtension.updateTargets(payload.targets());
    }

    private static void handlePlayerPresence(PlayerPresencePayload payload, IPayloadContext context) {
        PlayerPresenceClientState.updateAll(payload.players(), payload.serverTimeMillis());
    }

    private static void handleItemTagCreated(ItemTagCreatedPayload payload, IPayloadContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof CustomChatScreen screen) {
            screen.insertItemTag(payload.requestId(), payload.token(), payload.item());
        }
    }
}
