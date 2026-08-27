package njw.net.justchat.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import njw.net.justchat.data.ChatMessage;
import njw.net.justchat.network.ChatDeletedPayload;
import njw.net.justchat.network.ChatHistoryPayload;
import njw.net.justchat.network.ChatReadStatePayload;
import njw.net.justchat.network.ItemTagCreatedPayload;
import njw.net.justchat.network.NewChatPayload;
import njw.net.justchat.network.NewSystemChatPayload;
import njw.net.justchat.network.PlayerPresencePayload;
import njw.net.justchat.network.PlayerSuggestionsPayload;

@EventBusSubscriber(modid = "njw_just_chat", value = Dist.CLIENT)
public final class ChatClientNetwork {
    private ChatClientNetwork() {}

    @SubscribeEvent
    public static void register(RegisterClientPayloadHandlersEvent event) {
        event.register(
                NewChatPayload.TYPE,
                ChatClientNetwork::handleNewChat
        );

        event.register(
                ChatDeletedPayload.TYPE,
                ChatClientNetwork::handleChatDeleted
        );

        event.register(
                ChatHistoryPayload.TYPE,
                ChatClientNetwork::handleChatHistory
        );

        event.register(
                NewSystemChatPayload.TYPE,
                ChatClientNetwork::handleNewSystemChat
        );

        event.register(
                ChatReadStatePayload.TYPE,
                ChatClientNetwork::handleChatReadState
        );

        event.register(
                PlayerSuggestionsPayload.TYPE,
                ChatClientNetwork::handlePlayerSuggestions
        );

        event.register(
                PlayerPresencePayload.TYPE,
                ChatClientNetwork::handlePlayerPresence
        );

        event.register(
                ItemTagCreatedPayload.TYPE,
                ChatClientNetwork::handleItemTagCreated
        );
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ChatClientState.clear();
        ChatReadClientState.clear();
        PlayerPresenceClientState.clear();
    }

    private static void handleNewChat(
            NewChatPayload payload,
            IPayloadContext context
    ) {
        ChatMessage message = payload.message();

        ChatClientState.addPlayer(message);
        PlayerPresenceClientState.requestForMessage(message);
        MentionNotifier.notifyIfMentioned(message);

        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.player == null
                || minecraft.screen instanceof CustomChatScreen) {
            return;
        }

        String time = ChatTimeFormatter.formatTime(message.createdAt());
        Component content = ChatClientEntry.player(message).displayMessage();
        Component line = Component.literal("[" + time + "] ").append(content);

        VanillaChatCapture.runSuppressed(
                () -> minecraft.player.sendSystemMessage(line)
        );
    }

    private static void handleChatDeleted(
            ChatDeletedPayload payload,
            IPayloadContext context
    ) {
        ChatClientState.addPlayer(payload.message());
    }

    private static void handleChatHistory(
            ChatHistoryPayload payload,
            IPayloadContext context
    ) {
        ChatClientState.completeHistory(
                payload.messages(),
                payload.systemMessages(),
                payload.hasMore()
        );

        PlayerPresenceClientState.requestForMessages(payload.messages());

        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.screen instanceof CustomChatScreen screen) {
            screen.onHistoryUpdated();
        }
    }

    private static void handleNewSystemChat(
            NewSystemChatPayload payload,
            IPayloadContext context
    ) {
        ChatClientState.addSystem(payload.message());
    }

    private static void handleChatReadState(
            ChatReadStatePayload payload,
            IPayloadContext context
    ) {
        ChatReadClientState.update(payload.lastReadMessageId());
    }

    private static void handlePlayerSuggestions(
            PlayerSuggestionsPayload payload,
            IPayloadContext context
    ) {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.screen instanceof CustomChatScreen screen) {
            screen.updatePlayerSuggestions(
                    payload.query(),
                    payload.suggestions()
            );
        }
    }

    private static void handlePlayerPresence(
            PlayerPresencePayload payload,
            IPayloadContext context
    ) {
        PlayerPresenceClientState.updateAll(payload.players());
    }

    private static void handleItemTagCreated(
            ItemTagCreatedPayload payload,
            IPayloadContext context
    ) {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.screen instanceof CustomChatScreen screen) {
            screen.insertItemTag(
                    payload.requestId(),
                    payload.token(),
                    payload.item()
            );
        }
    }
}