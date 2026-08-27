package njw.net.justchat.client;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import njw.net.justchat.data.ChatMessage;
import njw.net.justchat.data.PlayerTag;
import njw.net.justchat.data.SystemChatMessage;

public record ChatClientEntry(
        Type type,
        ChatMessage chatMessage,
        SystemChatMessage systemMessage,
        Component vanillaMessage,
        long createdAt
) {
    public enum Type {
        PLAYER,
        SYSTEM,
        VANILLA
    }

    public static ChatClientEntry player(ChatMessage message) {
        return new ChatClientEntry(Type.PLAYER, message, null, null, message.createdAt());
    }

    public static ChatClientEntry system(SystemChatMessage message) {
        return new ChatClientEntry(Type.SYSTEM, null, message, null, message.createdAt());
    }

    public static ChatClientEntry vanilla(Component message, long receivedAt) {
        return new ChatClientEntry(Type.VANILLA, null, null, message, receivedAt);
    }

    public boolean isPlayer() {
        return type == Type.PLAYER;
    }

    public boolean isSystem() {
        return type == Type.SYSTEM;
    }

    public long playerMessageId() {
        return chatMessage == null ? -1L : chatMessage.id();
    }

    public long systemMessageId() {
        return systemMessage == null ? -1L : systemMessage.id();
    }

    public Component displayMessage() {
        if (type == Type.SYSTEM) return systemMessage.content();
        if (type == Type.VANILLA) return vanillaMessage;
        if (chatMessage.deleted()) {
            return Component.literal("<" + chatMessage.senderName() + "> ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(
                            Component.translatable("screen.njw_just_chat.deleted_message")
                                    .withStyle(ChatFormatting.GRAY)
                    );
        }
        return createPlayerMessage();
    }

    private Component createPlayerMessage() {
        String content = chatMessage.content();
        MutableComponent result = Component.literal("<" + chatMessage.senderName() + "> ");
        int cursor = 0;

        for (PlayerTag tag : chatMessage.playerTags()) {
            int start = tag.start();
            int end = tag.end();
            if (start < cursor || start < 0 || end > content.length() || start >= end) continue;
            if (cursor < start) result.append(Component.literal(content.substring(cursor, start)));
            result.append(Component.literal(content.substring(start, end)).withStyle(style -> style.withColor(0x55AAFF)));
            cursor = end;
        }

        if (cursor < content.length()) result.append(Component.literal(content.substring(cursor)));
        return result;
    }
}