package njw.net.justchat.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import njw.net.justchat.client.CustomChatScreen;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(CustomChatScreen.class)
public abstract class CustomChatScreenBackgroundMixin {
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {}
}
