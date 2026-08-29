package njw.net.justchat.mixin;

import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import njw.net.justchat.server.GlobalSystemMessageRouter;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Inject(
            method = "sendSystemMessage(Lnet/minecraft/network/chat/Component;Z)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void njwJustChat$captureSystemMessage(Component message, boolean overlay, CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer) (Object) this;

        LOGGER.info(
                "[JCDBG][SP_SEND] player={} overlay={} text={}",
                player.getPlainTextName(),
                overlay,
                message.getString()
        );

        boolean captured = GlobalSystemMessageRouter.capture(player, message, overlay);

        LOGGER.info(
                "[JCDBG][SP_RESULT] player={} captured={} text={}",
                player.getPlainTextName(),
                captured,
                message.getString()
        );

        if (captured) {
            ci.cancel();
        }
    }
}