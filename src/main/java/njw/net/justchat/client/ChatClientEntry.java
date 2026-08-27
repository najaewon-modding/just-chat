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
        List<MessageSpan> spans = new ArrayList<>();

        for (PlayerTag tag : chatMessage.playerTags()) {
            if (!validSpan(tag.start(), tag.end(), content.length())) continue;
            Component text = Component.literal(content.substring(tag.start(), tag.end())).withStyle(style ->
                    style.withColor(ChatStyle.TAG_COLOR)
                            .withHoverEvent(new HoverEvent.ShowText(createPlayerTagHover(tag)))
            );
            spans.add(new MessageSpan(tag.start(), tag.end(), text));
        }

        for (ItemTag tag : chatMessage.itemTags()) {
            if (!validSpan(tag.start(), tag.end(), content.length())) continue;
            Component text = Component.literal(content.substring(tag.start(), tag.end())).withStyle(style ->
                    style.withColor(ChatStyle.TAG_COLOR)
                            .withHoverEvent(new HoverEvent.ShowItem(tag.item()))
            );
            spans.add(new MessageSpan(tag.start(), tag.end(), text));
        }

        spans.sort(Comparator.comparingInt(MessageSpan::start));
        int cursor = 0;

        for (MessageSpan span : spans) {
            if (span.start() < cursor) continue;
            if (cursor < span.start()) result.append(Component.literal(content.substring(cursor, span.start())));
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
            hover.append(
                    Component.translatable("screen.njw_just_chat.player_tag_online")
                            .withStyle(ChatFormatting.GRAY)
            );
            return hover;
        }

        Component lastSeen = createLastSeenText(presence);
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

    private boolean validSpan(int start, int end, int contentLength) {
        return start >= 0 && start < end && end <= contentLength;
    }

    private record MessageSpan(int start, int end, Component component) {}
}