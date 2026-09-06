package njw.net.justchat.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import njw.net.justchat.data.ChatEntry;

import java.util.ArrayDeque;
import java.util.Deque;

public final class VanillaChatBridge {
    private static final int MAX_PENDING_LINES = 100;
    private static final Deque<Component> PENDING_LINES = new ArrayDeque<>();

    private VanillaChatBridge() {}

    public static Component withTimestamp(Component message, long createdAt) {
        String time = ChatTimeFormatter.formatTime(createdAt);
        return Component.literal("[" + time + "] ").append(message.copy());
    }

    public static void publishPlayerEntry(ChatEntry entry, boolean defer) {
        if (!entry.isPlayer()) return;
        Component line = withTimestamp(ChatClientEntry.persistent(entry).displayMessage(), entry.createdAt());
        if (defer) {
            enqueue(line);
            return;
        }
        flushPending();
        addToVanillaChat(line);
    }

    public static void defer(Component line) {
        enqueue(line.copy());
    }

    public static void flushPending() {
        while (!PENDING_LINES.isEmpty()) addToVanillaChat(PENDING_LINES.removeFirst());
    }

    public static void clear() {
        PENDING_LINES.clear();
    }

    private static void enqueue(Component line) {
        PENDING_LINES.addLast(line.copy());
        while (PENDING_LINES.size() > MAX_PENDING_LINES) PENDING_LINES.removeFirst();
    }

    private static void addToVanillaChat(Component line) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.gui.getChat().addClientSystemMessage(line);
    }
}
