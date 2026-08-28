package njw.net.justchat.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;

@EventBusSubscriber(modid = "njw_just_chat")
public final class VanillaChatCapture {
    private static int suppressionDepth;

    private VanillaChatCapture() {}

    @SubscribeEvent
    public static void onSystemMessage(ClientChatReceivedEvent.System event) {
        if (suppressionDepth > 0 || event.isOverlay()) return;

        if (ChatClientState.consumeRecentPersistentSystem(event.getMessage())) {
            event.setCanceled(true);
            return;
        }

        long now = System.currentTimeMillis();
        ChatClientState.rememberVanillaSystem(event.getMessage(), now);
        ChatClientState.addVanilla(event.getMessage(), now);
        handleDisplay(event, now);
    }

    @SubscribeEvent
    public static void onPlayerMessage(ClientChatReceivedEvent.Player event) {
        if (suppressionDepth > 0) return;
        long now = System.currentTimeMillis();
        ChatClientState.addVanilla(event.getMessage(), now);
        handleDisplay(event, now);
    }

    private static void handleDisplay(ClientChatReceivedEvent event, long createdAt) {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.screen instanceof CustomChatScreen) {
            event.setCanceled(true);
            return;
        }

        String time = ChatTimeFormatter.formatTime(createdAt);
        Component line = Component.literal("[" + time + "] ").append(event.getMessage().copy());
        event.setMessage(line);
    }

    public static void runSuppressed(Runnable action) {
        suppressionDepth++;

        try {
            action.run();
        } finally {
            suppressionDepth--;
        }
    }
}