package njw.net.justchat.client;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import njw.net.justchat.data.ChatEntry;
import njw.net.justchat.data.ChatMessage;
import njw.net.justchat.data.ItemTag;
import njw.net.justchat.data.PlayerPresence;
import njw.net.justchat.data.PlayerTag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public record ChatClientEntry(
        ChatEntry persistentEntry,
        Component vanillaMessage,
        long createdAt
) {
    private static final long ONE_MINUTE = 60_000L;
    private static final long FIVE_MINUTES = 5L * ONE_MINUTE;
    private static final long ONE_HOUR = 60L * ONE_MINUTE;
    private static final long ONE_DAY = 24L * ONE_HOUR;

    public static ChatClientEntry persistent(ChatEntry entry) {
        return new ChatClientEntry(entry, null, entry.createdAt());
    }

    public static ChatClientEntry vanilla(Component message, long createdAt) {
        return new ChatClientEntry(null, message, createdAt);
    }

    public boolean isPlayer() {
        return persistentEntry != null && persistentEntry.isPlayer();
    }

    public boolean isSystem() {
        return persistentEntry != null && persistentEntry.isSystem();
    }

    public boolean isPersistent() {
        return persistentEntry != null;
    }

    public long playerMessageId() {
        return isPlayer() ? persistentEntry.id() : -1L;
    }

    public long systemMessageId() {
        return isSystem() ? persistentEntry.id() : -1L;
    }

    public long persistentMessageId() {
        return persistentEntry == null ? -1L : persistentEntry.id();
    }

    public ChatMessage chatMessage() {
        return isPlayer() ? persistentEntry.chatMessage() : null;
    }

    public Component displayMessage() {
        if (isSystem()) return persistentEntry.componentContent();
        if (!isPersistent()) return vanillaMessage;

        ChatMessage chatMessage = chatMessage();
        if (chatMessage.deleted()) {
            return Component.literal("<" + chatMessage.senderName() + "> ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.translatable("screen.njw_just_chat.deleted_message")
                            .withStyle(ChatFormatting.GRAY));
        }

        return createPlayerMessage(chatMessage);
    }

    private Component createPlayerMessage(ChatMessage chatMessage) {
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
        if (presence == null) return Component.translatable("screen.njw_just_chat.player_tag_last_seen_loading");
        if (presence.lastSeenAt() <= 0L) return Component.translatable("screen.njw_just_chat.player_tag_last_seen_unknown");

        long elapsed = Math.max(0L, System.currentTimeMillis() - presence.lastSeenAt());
        if (elapsed < FIVE_MINUTES) return Component.translatable("screen.njw_just_chat.player_tag_last_seen_now");
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

    private record MessageSpan(int start, int end, Component component) {}
}
