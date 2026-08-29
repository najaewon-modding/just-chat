package njw.net.justchat.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import njw.net.justchat.server.GlobalSystemMessageRouter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Function;

@Mixin(PlayerList.class)
public abstract class PlayerListMixin {
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
        GlobalSystemMessageRouter.endGlobal(overlay);
    }
}