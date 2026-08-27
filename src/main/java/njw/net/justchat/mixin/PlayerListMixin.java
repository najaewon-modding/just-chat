package njw.net.justchat.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.server.players.PlayerList;
import net.neoforged.neoforge.network.PacketDistributor;
import njw.net.justchat.data.ChatSavedData;
import njw.net.justchat.data.SystemChatMessage;
import njw.net.justchat.network.NewSystemChatPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerList.class)
public abstract class PlayerListMixin {
    @Inject(
            method = "broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Z)V",
            at = @At("HEAD")
    )
    private void njwJustChat$saveSystemMessage(Component message, boolean overlay, CallbackInfo ci) {
        if (overlay) return;
        PlayerList playerList = (PlayerList) (Object) this;
        ChatSavedData data = ChatSavedData.get(playerList.getServer());
        SystemChatMessage saved = data.addSystem(message, System.currentTimeMillis());
        PacketDistributor.sendToAllPlayers(new NewSystemChatPayload(saved));
    }
}