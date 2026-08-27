package njw.net.justchat.network;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import njw.net.justchat.data.ChatMessage;
import njw.net.justchat.data.ChatSavedData;

@EventBusSubscriber(modid = "njw_just_chat")
public final class ChatNetwork {
    private static final int MAX_MESSAGE_LENGTH = 256;

    private ChatNetwork() {}

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(SendChatPayload.TYPE, SendChatPayload.STREAM_CODEC, ChatNetwork::handleSendChat);
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
        registrar.playToClient(NewChatPayload.TYPE, NewChatPayload.STREAM_CODEC);
        registrar.playToClient(ChatDeletedPayload.TYPE, ChatDeletedPayload.STREAM_CODEC);
        registrar.playToClient(ChatHistoryPayload.TYPE, ChatHistoryPayload.STREAM_CODEC);
        registrar.playToClient(NewSystemChatPayload.TYPE, NewSystemChatPayload.STREAM_CODEC);
    }

    private static void handleSendChat(SendChatPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        String content = payload.content().trim();
        if (content.isEmpty() || content.length() > MAX_MESSAGE_LENGTH || content.startsWith("/")) return;
        ChatSavedData data = ChatSavedData.get(player.level().getServer());
        ChatMessage message = data.add(
                player.getUUID(),
                player.getName().getString(),
                content,
                System.currentTimeMillis()
        );
        PacketDistributor.sendToAllPlayers(new NewChatPayload(message));
    }

    private static void handleDeleteChat(DeleteChatPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        ChatSavedData data = ChatSavedData.get(player.level().getServer());
        ChatMessage deleted = data.delete(payload.messageId(), player.getUUID(), System.currentTimeMillis());
        if (deleted == null) return;
        PacketDistributor.sendToAllPlayers(new ChatDeletedPayload(deleted));
    }

    private static void handleHistoryRequest(RequestChatHistoryPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        ChatSavedData data = ChatSavedData.get(player.level().getServer());
        ChatSavedData.HistoryBatch history = data.getHistoryBefore(payload.beforeId(), payload.limit());
        PacketDistributor.sendToPlayer(
                player,
                new ChatHistoryPayload(history.messages(), history.systemMessages(), history.hasMore())
        );
    }
}