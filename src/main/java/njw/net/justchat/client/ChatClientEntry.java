package njw.net.justchat.client;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import njw.net.justchat.data.ChatMessage;
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
        return Component.literal("<" + chatMessage.senderName() + "> " + chatMessage.content());
    }
}