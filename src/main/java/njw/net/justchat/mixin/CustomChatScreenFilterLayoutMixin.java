package njw.net.justchat.mixin;

import njw.net.justchat.client.CustomChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(CustomChatScreen.class)
public abstract class CustomChatScreenFilterLayoutMixin {
    @ModifyConstant(
            method = {"captureViewportAnchor", "renderMessages", "findHoveredEntry", "getMaxScrollOffset"},
            constant = @Constant(intValue = 8, ordinal = 0)
    )
    private int njwJustChat$reserveFilterRow(int original) {
        return 36;
    }
}
