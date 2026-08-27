package njw.net.justchat.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import njw.net.justchat.data.ChatMessage;

import java.util.UUID;

public final class MentionNotifier {
    private static final SystemToast.SystemToastId TOAST_ID = new SystemToast.SystemToastId(5000L);

    private MentionNotifier() {}

    public static void notifyIfMentioned(ChatMessage message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        UUID playerUuid = minecraft.player.getUUID();
        if (message.senderUuid().equals(playerUuid)) return;
        boolean mentioned = message.playerTags().stream().anyMatch(tag -> tag.targetUuid().equals(playerUuid));
        if (!mentioned) return;
        Component title = Component.translatable("screen.njw_just_chat.mention_title");
        Component body = Component.translatable("screen.njw_just_chat.mention_message", message.senderName());
        SystemToast.add(minecraft.getToastManager(), TOAST_ID, title, body);
    }
}