package njw.net.justchat.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import njw.net.justchat.client.CommandOnlyChatScreen;
import njw.net.justchat.client.CustomChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "openChatScreen", at = @At("HEAD"), cancellable = true)
    private void njwJustChat$openChatScreen(ChatComponent.ChatMethod chatMethod, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();

        if (chatMethod == ChatComponent.ChatMethod.MESSAGE) {
            minecraft.setScreen(new CustomChatScreen());
            ci.cancel();
            return;
        }

        if (chatMethod == ChatComponent.ChatMethod.COMMAND) {
            minecraft.setScreen(new CommandOnlyChatScreen());
            ci.cancel();
        }
    }
}