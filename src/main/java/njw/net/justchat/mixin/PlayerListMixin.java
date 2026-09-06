package njw.net.justchat.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import njw.net.justchat.server.SystemMessageCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Function;

@Mixin(PlayerList.class)
public abstract class PlayerListMixin {
    @Inject(method = "broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Ljava/util/function/Function;Z)V", at = @At("HEAD"))
    private void njwJustChat$beginBroadcast(Component message, Function<ServerPlayer, Component> playerMessages,
                                            boolean overlay, CallbackInfo ci) {
        PlayerList playerList = (PlayerList) (Object) this;
        SystemMessageCapture.beginBroadcast(playerList.getServer(), message, overlay);
    }

    @Inject(method = "broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Ljava/util/function/Function;Z)V", at = @At("RETURN"))
    private void njwJustChat$endBroadcast(Component message, Function<ServerPlayer, Component> playerMessages,
                                          boolean overlay, CallbackInfo ci) {
        SystemMessageCapture.endBroadcast(overlay);
    }
}
