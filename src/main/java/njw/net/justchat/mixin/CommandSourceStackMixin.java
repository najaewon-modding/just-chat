package njw.net.justchat.mixin;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import njw.net.justchat.server.SystemMessageCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Supplier;

@Mixin(CommandSourceStack.class)
public abstract class CommandSourceStackMixin {
    @Inject(method = "sendSuccess(Ljava/util/function/Supplier;Z)V", at = @At("HEAD"))
    private void njwJustChat$beginSuccessFeedback(Supplier<Component> message, boolean broadcastToAdmins,
                                                  CallbackInfo ci) {
        SystemMessageCapture.beginPersistenceSuppression();
    }

    @Inject(method = "sendSuccess(Ljava/util/function/Supplier;Z)V", at = @At("RETURN"))
    private void njwJustChat$endSuccessFeedback(Supplier<Component> message, boolean broadcastToAdmins,
                                                CallbackInfo ci) {
        SystemMessageCapture.endPersistenceSuppression();
    }

    @Inject(method = "sendFailure(Lnet/minecraft/network/chat/Component;)V", at = @At("HEAD"))
    private void njwJustChat$beginFailureFeedback(Component message, CallbackInfo ci) {
        SystemMessageCapture.beginPersistenceSuppression();
    }

    @Inject(method = "sendFailure(Lnet/minecraft/network/chat/Component;)V", at = @At("RETURN"))
    private void njwJustChat$endFailureFeedback(Component message, CallbackInfo ci) {
        SystemMessageCapture.endPersistenceSuppression();
    }
}
