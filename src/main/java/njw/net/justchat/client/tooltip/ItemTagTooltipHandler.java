package njw.net.justchat.client.tooltip;

import com.mojang.datafixers.util.Either;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import njw.net.justchat.JustChat;
import njw.net.justchat.client.CustomChatScreen;

@EventBusSubscriber(modid = JustChat.MOD_ID, value = Dist.CLIENT)
public final class ItemTagTooltipHandler {
    private ItemTagTooltipHandler() {}

    @SubscribeEvent
    public static void register(
            RegisterClientTooltipComponentFactoriesEvent event
    ) {
        event.register(
                ItemIconTooltip.class,
                ClientItemIconTooltip::new
        );
    }

    @SubscribeEvent
    public static void gather(RenderTooltipEvent.GatherComponents event) {
        Minecraft minecraft = Minecraft.getInstance();

        if (!(minecraft.screen instanceof CustomChatScreen)) return;
        if (event.getItemStack().isEmpty()) return;

        event.getTooltipElements().add(
                0,
                Either.right(
                        new ItemIconTooltip(
                                event.getItemStack().copy()
                        )
                )
        );
    }
}