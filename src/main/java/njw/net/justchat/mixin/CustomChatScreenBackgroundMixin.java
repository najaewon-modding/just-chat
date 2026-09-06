package njw.net.justchat.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import njw.net.justchat.client.CustomChatScreen;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(CustomChatScreen.class)
public abstract class CustomChatScreenBackgroundMixin {
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        ((Screen) (Object) this).extractTransparentBackground(graphics);
    }
}
