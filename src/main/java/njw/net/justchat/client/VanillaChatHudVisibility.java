package njw.net.justchat.client;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

@EventBusSubscriber(modid = "njw_just_chat", value = Dist.CLIENT)
public final class VanillaChatHudVisibility {
    private VanillaChatHudVisibility() {}

    @SubscribeEvent
    public static void onRenderGuiLayer(RenderGuiLayerEvent.Pre event) {
        if (!event.getName().equals(VanillaGuiLayers.CHAT)) return;
        if (Minecraft.getInstance().screen instanceof CustomChatScreen) event.setCanceled(true);
    }
}
