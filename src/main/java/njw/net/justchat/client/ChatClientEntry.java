package njw.net.justchat.client;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import njw.net.justchat.data.ChatMessage;
import njw.net.justchat.data.ItemTag;
import njw.net.justchat.data.PlayerPresence;
import njw.net.justchat.data.PlayerTag;
import njw.net.justchat.data.SystemChatMessage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public record ChatClientEntry(
        Type type,
        ChatMessage chatMessage,
        SystemChatMessage systemMessage,
        Component vanillaMessage,
        long createdAt
) {
    private static final long ONE_MINUTE = 60_000L;
    private static final long FIVE_MINUTES = 5L * ONE_MINUTE;
    private static final long ONE_HOUR = 60L * ONE_MINUTE;
    private static final long ONE_DAY = 24L * ONE_HOUR;

    public static ChatClientEntry player(ChatMessage message) {
        return new ChatClientEntry(Type.PLAYER, message, null, null, message.createdAt());
    }

    public static ChatClientEntry system(SystemChatMessage message) {
        return new ChatClientEntry(Type.SYSTEM, null, message, null, message.createdAt());
    }

    public static ChatClientEntry vanilla(Component message, long createdAt) {
        return new ChatClientEntry(Type.VANILLA, null, null, message, createdAt);
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
                    .append(Component.translatable("screen.njw_just_chat.deleted_message")
                            .withStyle(ChatFormatting.GRAY));
        }

        return createPlayerMessage();
    }

    private Component createPlayerMessage() {
        String content = chatMessage.content();
        MutableComponent result = Component.literal("<" + chatMessage.senderName() + "> ");
        List<MessageSpan> spans = new ArrayList<>();

        for (PlayerTag tag : chatMessage.playerTags()) {
            if (!validSpan(tag.start(), tag.end(), content.length())) continue;

            Component component = Component.literal(content.substring(tag.start(), tag.end())).withStyle(style ->
                    style.withColor(0x55AAFF).withHoverEvent(new HoverEvent.ShowText(createPlayerTagHover(tag))));
            spans.add(new MessageSpan(tag.start(), tag.end(), component));
        }

        for (ItemTag tag : chatMessage.itemTags()) {
            if (!validSpan(tag.start(), tag.end(), content.length())) continue;

            String displayText = "[" + tag.item().create().getHoverName().getString() + "]";
            Component component = Component.literal(displayText).withStyle(style ->
                    style.withColor(0x55AAFF).withHoverEvent(new HoverEvent.ShowItem(tag.item())));
            spans.add(new MessageSpan(tag.start(), tag.end(), component));
        }

        spans.sort(Comparator.comparingInt(MessageSpan::start));
        int cursor = 0;

        for (MessageSpan span : spans) {
            if (span.start() < cursor) continue;

            if (cursor < span.start()) {
                result.append(Component.literal(content.substring(cursor, span.start())));
            }

            result.append(span.component());
            cursor = span.end();
        }

        if (cursor < content.length()) result.append(Component.literal(content.substring(cursor)));
        return result;
    }

    private Component createPlayerTagHover(PlayerTag tag) {
        MutableComponent hover = Component.literal(tag.targetName());
        PlayerPresence presence = PlayerPresenceClientState.get(tag.targetUuid());
        hover.append("\n");

        if (presence != null && presence.online()) {
            hover.append(Component.translatable("screen.njw_just_chat.player_tag_online")
                    .withStyle(ChatFormatting.GRAY));
            return hover;
        }

        Component lastSeen = createLastSeenText(presence);
        hover.append(Component.translatable("screen.njw_just_chat.player_tag_last_seen", lastSeen)
                .withStyle(ChatFormatting.GRAY));
        return hover;
    }

    private Component createLastSeenText(PlayerPresence presence) {
        if (presence == null) {
            return Component.translatable("screen.njw_just_chat.player_tag_last_seen_loading");
        }

        if (presence.lastSeenAt() <= 0L) {
            return Component.translatable("screen.njw_just_chat.player_tag_last_seen_unknown");
        }

        long elapsed = Math.max(0L, System.currentTimeMillis() - presence.lastSeenAt());

        if (elapsed < FIVE_MINUTES) {
            return Component.translatable("screen.njw_just_chat.player_tag_last_seen_now");
        }

        if (elapsed < ONE_HOUR) {
            long minutes = Math.max(5L, elapsed / ONE_MINUTE);
            return Component.translatable("screen.njw_just_chat.player_tag_last_seen_minutes", minutes);
        }

        if (elapsed < ONE_DAY) {
            long hours = Math.max(1L, elapsed / ONE_HOUR);
            return Component.translatable("screen.njw_just_chat.player_tag_last_seen_hours", hours);
        }

        return Component.literal(ChatTimeFormatter.formatDate(presence.lastSeenAt()));
    }

    private boolean validSpan(int start, int end, int length) {
        return start >= 0 && start < end && end <= length;
    }

    public enum Type {
        PLAYER,
        SYSTEM,
        VANILLA
    }

    private record MessageSpan(int start, int end, Component component) {}
}