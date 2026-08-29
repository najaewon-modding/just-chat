package njw.net.justchat.mixin;

import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import njw.net.justchat.server.GlobalSystemMessageRouter;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Function;

@Mixin(PlayerList.class)
public abstract class PlayerListMixin {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Inject(
            method = "broadcastSystemMessage"
                    + "(Lnet/minecraft/network/chat/Component;Ljava/util/function/Function;Z)V",
            at = @At("HEAD")
    )
    private void njwJustChat$beginGlobal(
            Component message,
            Function<ServerPlayer, Component> playerMessages,
            boolean overlay,
            CallbackInfo ci
    ) {
        PlayerList playerList = (PlayerList) (Object) this;

        LOGGER.info(
                "[JCDBG][PL_BEGIN] overlay={} players={} text={}",
                overlay,
                playerList.getPlayers().size(),
                message.getString()
        );

        GlobalSystemMessageRouter.beginGlobal(playerList.getServer(), message, overlay);
    }

    @Inject(
            method = "broadcastSystemMessage"
                    + "(Lnet/minecraft/network/chat/Component;Ljava/util/function/Function;Z)V",
            at = @At("RETURN")
    )
    private void njwJustChat$endGlobal(
            Component message,
            Function<ServerPlayer, Component> playerMessages,
            boolean overlay,
            CallbackInfo ci
    ) {
        LOGGER.info(
                "[JCDBG][PL_END] overlay={} text={}",
                overlay,
                message.getString()
        );

        GlobalSystemMessageRouter.endGlobal(overlay);
    }
}