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
import njw.net.justchat.network.ChatHistoryPayload;
import njw.net.justchat.network.NewChatPayload;
import njw.net.justchat.network.NewSystemChatPayload;

@EventBusSubscriber(modid = "njw_just_chat", value = Dist.CLIENT)
public final class ChatClientNetwork {
    private ChatClientNetwork() {}

    @SubscribeEvent
    public static void register(RegisterClientPayloadHandlersEvent event) {
        event.register(NewChatPayload.TYPE, ChatClientNetwork::handleNewChat);
        event.register(ChatHistoryPayload.TYPE, ChatClientNetwork::handleChatHistory);
        event.register(NewSystemChatPayload.TYPE, ChatClientNetwork::handleNewSystemChat);
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ChatClientState.clear();
    }

    private static void handleNewChat(NewChatPayload payload, IPayloadContext context) {
        ChatMessage message = payload.message();
        ChatClientState.addPlayer(message);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen instanceof CustomChatScreen) return;
        String time = ChatTimeFormatter.formatTime(message.createdAt());
        Component line = Component.literal("[" + time + "] <" + message.senderName() + "> " + message.content());
        VanillaChatCapture.runSuppressed(() -> minecraft.player.sendSystemMessage(line));
    }

    private static void handleChatHistory(ChatHistoryPayload payload, IPayloadContext context) {
        ChatClientState.completeHistory(
                payload.messages(),
                payload.systemMessages(),
                payload.hasMore()
        );
    }

    private static void handleNewSystemChat(NewSystemChatPayload payload, IPayloadContext context) {
        ChatClientState.addSystem(payload.message());
    }
}