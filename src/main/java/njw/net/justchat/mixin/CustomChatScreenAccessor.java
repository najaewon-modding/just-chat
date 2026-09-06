package njw.net.justchat.mixin;

import net.minecraft.client.gui.components.EditBox;
import njw.net.justchat.client.CustomChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CustomChatScreen.class)
public interface CustomChatScreenAccessor {
    @Accessor("messageInput")
    EditBox njwJustChat$messageInput();

    @Accessor("scrollOffset")
    int njwJustChat$scrollOffset();
}
