package njw.net.justchat.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import njw.net.justchat.data.SystemChatMessage;

@EventBusSubscriber(modid = "njw_just_chat", value = Dist.CLIENT)
public final class VanillaChatCapture {
    private static int suppressionDepth;

    private VanillaChatCapture() {}

    @SubscribeEvent
    public static void onSystemMessage(ClientChatReceivedEvent.System event) {
        if (suppressionDepth > 0 || event.isOverlay()) return;
        long receivedAt = System.currentTimeMillis();
        SystemChatMessage system = ChatClientState.findRecentSystem(event.getMessage(), receivedAt);
        long createdAt = system == null ? receivedAt : system.createdAt();
        if (system == null) ChatClientState.addVanilla(event.getMessage(), receivedAt);
        handleDisplay(event, createdAt);
    }

    @SubscribeEvent
    public static void onPlayerMessage(ClientChatReceivedEvent.Player event) {
        if (suppressionDepth > 0) return;
        long receivedAt = System.currentTimeMillis();
        ChatClientState.addVanilla(event.getMessage(), receivedAt);
        handleDisplay(event, receivedAt);
    }

    private static void handleDisplay(ClientChatReceivedEvent event, long createdAt) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof CustomChatScreen) {
            event.setCanceled(true);
            return;
        }
        String time = ChatTimeFormatter.formatTime(createdAt);
        event.setMessage(Component.literal("[" + time + "] ").append(event.getMessage().copy()));
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