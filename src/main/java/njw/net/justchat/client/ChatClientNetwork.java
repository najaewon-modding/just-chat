package njw.net.justchat.client;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import njw.net.justchat.data.ChatMessage;
import njw.net.justchat.data.SystemChatMessage;
import njw.net.justchat.network.ChatDeletedPayload;
import njw.net.justchat.network.ChatHistoryPayload;
import njw.net.justchat.network.ChatReadStatePayload;
import njw.net.justchat.network.ItemTagCreatedPayload;
import njw.net.justchat.network.NewChatPayload;
import njw.net.justchat.network.NewSystemChatPayload;
import njw.net.justchat.network.PlayerPresencePayload;
import njw.net.justchat.network.PlayerSuggestionsPayload;
import org.slf4j.Logger;

@EventBusSubscriber(modid = "njw_just_chat", value = Dist.CLIENT)
public final class ChatClientNetwork {
    private static final Logger LOGGER = LogUtils.getLogger();

    private ChatClientNetwork() {}

    @SubscribeEvent
    public static void register(RegisterClientPayloadHandlersEvent event) {
        event.register(NewChatPayload.TYPE, ChatClientNetwork::handleNewChat);
        event.register(ChatDeletedPayload.TYPE, ChatClientNetwork::handleChatDeleted);
        event.register(ChatHistoryPayload.TYPE, ChatClientNetwork::handleChatHistory);
        event.register(NewSystemChatPayload.TYPE, ChatClientNetwork::handleNewSystemChat);
        event.register(ChatReadStatePayload.TYPE, ChatClientNetwork::handleChatReadState);
        event.register(PlayerSuggestionsPayload.TYPE, ChatClientNetwork::handlePlayerSuggestions);
        event.register(PlayerPresencePayload.TYPE, ChatClientNetwork::handlePlayerPresence);
        event.register(ItemTagCreatedPayload.TYPE, ChatClientNetwork::handleItemTagCreated);
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ChatClientState.clear();
        ChatReadClientState.clear();
        PlayerPresenceClientState.clear();
    }

    private static void handleNewChat(NewChatPayload payload, IPayloadContext context) {
        ChatMessage message = payload.message();
        Minecraft minecraft = Minecraft.getInstance();
        CustomChatScreen screen = minecraft.screen instanceof CustomChatScreen current ? current : null;
        boolean ownMessage = minecraft.player != null
                && minecraft.player.getUUID().equals(message.senderUuid());
        if (screen != null) screen.beforeLivePersistentMessage(ownMessage);
        ChatClientState.addPlayer(message);
        PlayerPresenceClientState.requestForMessage(message);
        MentionNotifier.notifyIfMentioned(message);
        if (screen != null) screen.afterLivePersistentMessage(message.id());
        if (minecraft.player == null || screen != null) return;
        String time = ChatTimeFormatter.formatTime(message.createdAt());
        Component content = ChatClientEntry.player(message).displayMessage();
        Component line = Component.literal("[" + time + "] ").append(content);
        VanillaChatCapture.runSuppressed(() -> minecraft.player.sendSystemMessage(line));
    }

    private static void handleChatDeleted(ChatDeletedPayload payload, IPayloadContext context) {
        ChatClientState.addPlayer(payload.message());
    }

    private static void handleChatHistory(ChatHistoryPayload payload, IPayloadContext context) {
        boolean accepted = ChatClientState.completeHistory(
                payload.requestId(),
                payload.messages(),
                payload.systemMessages(),
                payload.hasMore()
        );
        if (!accepted) return;
        PlayerPresenceClientState.requestForMessages(payload.messages());
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof CustomChatScreen screen) screen.onHistoryUpdated();
    }

    private static void handleNewSystemChat(NewSystemChatPayload payload, IPayloadContext context) {
        SystemChatMessage message = payload.message();

        LOGGER.info(
                "[JCDBG][CLIENT_PAYLOAD] id={} text={}",
                message.id(),
                message.content().getString()
        );

        Minecraft minecraft = Minecraft.getInstance();
        CustomChatScreen screen = minecraft.screen instanceof CustomChatScreen current ? current : null;
        if (screen != null) screen.beforeLivePersistentMessage(false);

        ChatClientState.SystemAddResult result = ChatClientState.addSystem(message);

        LOGGER.info(
                "[JCDBG][CLIENT_ADD_RESULT] id={} result={} screen={} text={}",
                message.id(),
                result,
                screen != null,
                message.content().getString()
        );

        if (screen != null) screen.afterLivePersistentMessage(message.id());
        if (result != ChatClientState.SystemAddResult.NEW) return;
        if (minecraft.player == null || screen != null) return;

        String time = ChatTimeFormatter.formatTime(message.createdAt());
        Component line = Component.literal("[" + time + "] ").append(message.content().copy());

        LOGGER.info(
                "[JCDBG][CLIENT_DISPLAY] id={} text={}",
                message.id(),
                line.getString()
        );

        VanillaChatCapture.runSuppressed(() -> minecraft.player.sendSystemMessage(line));
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