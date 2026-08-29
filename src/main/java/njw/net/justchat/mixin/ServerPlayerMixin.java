package njw.net.justchat.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import njw.net.justchat.server.GlobalSystemMessageRouter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {
    @Invoker("acceptsSystemMessages")
    protected abstract boolean njwJustChat$acceptsSystemMessages(boolean overlay);

    @Inject(
            method = "sendSystemMessage(Lnet/minecraft/network/chat/Component;Z)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void njwJustChat$captureSystemMessage(Component message, boolean overlay, CallbackInfo ci) {
        if (overlay) return;
        ServerPlayer player = (ServerPlayer) (Object) this;
        boolean accepted = njwJustChat$acceptsSystemMessages(false);
        if (GlobalSystemMessageRouter.capture(player, message, accepted)) ci.cancel();
    }
}