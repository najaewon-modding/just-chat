package njw.net.justchat.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;

@EventBusSubscriber(modid = "njw_just_chat")
public final class VanillaChatCapture {
    private VanillaChatCapture() {}

    @SubscribeEvent
    public static void onPlayerMessage(ClientChatReceivedEvent.Player event) {
        long now = System.currentTimeMillis();
        ChatClientState.addVanilla(event.getMessage(), now);
        handlePlayerDisplay(event, now);
    }

    private static void handlePlayerDisplay(ClientChatReceivedEvent event, long createdAt) {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.screen instanceof CustomChatScreen) {
            event.setCanceled(true);
            return;
        }

        String time = ChatTimeFormatter.formatTime(createdAt);
        Component line = Component.literal("[" + time + "] ").append(event.getMessage().copy());
        event.setMessage(line);
    }
}