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
        Component line = VanillaChatBridge.withTimestamp(event.getMessage(), now);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof CustomChatScreen) {
            VanillaChatBridge.defer(line);
            event.setCanceled(true);
            return;
        }
        VanillaChatBridge.flushPending();
        event.setMessage(line);
    }

    @SubscribeEvent
    public static void onSystemMessage(ClientChatReceivedEvent.System event) {
        if (event.isOverlay()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.screen instanceof CustomChatScreen)) VanillaChatBridge.flushPending();
        event.setMessage(VanillaChatBridge.withTimestamp(event.getMessage(), System.currentTimeMillis()));
    }
}
