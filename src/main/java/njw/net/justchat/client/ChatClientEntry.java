package njw.net.justchat.client;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import njw.net.justchat.data.ChatMessage;
import njw.net.justchat.data.PlayerPresence;
import njw.net.justchat.data.PlayerTag;
import njw.net.justchat.data.SystemChatMessage;

public record ChatClientEntry(
        Type type,
        ChatMessage chatMessage,
        SystemChatMessage systemMessage,
        Component vanillaMessage,
        long createdAt
) {
    private static final int PLAYER_TAG_COLOR = 0x55AAFF;
    private static final long ONE_HOUR = 60L * 60L * 1000L;
    private static final long ONE_DAY = 24L * ONE_HOUR;

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
            Component hover = createPlayerTagHover(tag);
            MutableComponent tagged = Component.literal(content.substring(start, end)).withStyle(style ->
                    style.withColor(PLAYER_TAG_COLOR).withHoverEvent(new HoverEvent.ShowText(hover))
            );
            result.append(tagged);
            cursor = end;
        }

        if (cursor < content.length()) result.append(Component.literal(content.substring(cursor)));
        return result;
    }

    private Component createPlayerTagHover(PlayerTag tag) {
        MutableComponent hover = Component.literal(tag.targetName());
        hover.append("\n");
        Component lastSeen = createLastSeenText(PlayerPresenceClientState.get(tag.targetUuid()));
        hover.append(
                Component.translatable("screen.njw_just_chat.player_tag_last_seen", lastSeen)
                        .withStyle(ChatFormatting.GRAY)
        );
        return hover;
    }

    private Component createLastSeenText(PlayerPresence presence) {
        if (presence == null) {
            return Component.translatable("screen.njw_just_chat.player_tag_last_seen_loading");
        }
        if (presence.online()) {
            return Component.translatable("screen.njw_just_chat.player_tag_online");
        }
        if (presence.lastSeenAt() <= 0L) {
            return Component.translatable("screen.njw_just_chat.player_tag_last_seen_unknown");
        }

        long age = Math.max(0L, System.currentTimeMillis() - presence.lastSeenAt());
        if (age < ONE_HOUR) {
            return Component.translatable("screen.njw_just_chat.player_tag_last_seen_now");
        }
        if (age < ONE_DAY) {
            long hours = Math.max(1L, age / ONE_HOUR);
            return Component.translatable("screen.njw_just_chat.player_tag_last_seen_hours", hours);
        }
        return Component.literal(ChatTimeFormatter.formatDate(presence.lastSeenAt()));
    }
}