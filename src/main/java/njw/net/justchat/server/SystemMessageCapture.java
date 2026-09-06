package njw.net.justchat.server;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import njw.net.justchat.data.ChatEntry;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

public final class SystemMessageCapture {
    private static final ThreadLocal<Deque<BroadcastContext>> BROADCAST_CONTEXT =
            ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<TellrawContext> TELLRAW_CONTEXT = new ThreadLocal<>();
    private static final ThreadLocal<Integer> PERSISTENCE_SUPPRESSION_DEPTH = ThreadLocal.withInitial(() -> 0);

    private SystemMessageCapture() {}

    public static void runWithoutPersistence(Runnable action) {
        int depth = PERSISTENCE_SUPPRESSION_DEPTH.get();
        PERSISTENCE_SUPPRESSION_DEPTH.set(depth + 1);
        try {
            action.run();
        } finally {
            if (depth == 0) PERSISTENCE_SUPPRESSION_DEPTH.remove();
            else PERSISTENCE_SUPPRESSION_DEPTH.set(depth);
        }
    }

    public static void beginBroadcast(MinecraftServer server, Component message, boolean overlay) {
        if (overlay) return;
        BROADCAST_CONTEXT.get().push(new BroadcastContext(server, message.copy()));
    }

    public static void endBroadcast(boolean overlay) {
        if (overlay) return;
        Deque<BroadcastContext> contexts = BROADCAST_CONTEXT.get();
        if (contexts.isEmpty()) return;
        BroadcastContext context = contexts.pop();
        if (contexts.isEmpty()) BROADCAST_CONTEXT.remove();
        Component content = context.deliveries.isEmpty() ? context.baseMessage : context.deliveries.getFirst().message();
        ChatService.of(context.server).appendSystem(content, ChatEntry.Sender.server(), ChatEntry.Audience.global(),
                ChatEntry.Origin.VANILLA_BROADCAST);
    }

    public static void beginTellraw(CommandSourceStack source, Collection<ServerPlayer> targets,
                                    boolean explicitAllPlayers) {
        if (targets.isEmpty()) return;
        List<UUID> expected = targets.stream().map(ServerPlayer::getUUID).distinct().toList();
        TELLRAW_CONTEXT.set(new TellrawContext(source.getServer(), senderOf(source), expected, explicitAllPlayers));
    }

    public static void observe(ServerPlayer player, Component message, boolean overlay, boolean accepted) {
        if (overlay || PERSISTENCE_SUPPRESSION_DEPTH.get() > 0) return;
        MinecraftServer server = player.level().getServer();
        BroadcastContext broadcast = currentBroadcast(server);
        if (broadcast != null) {
            if (accepted) broadcast.deliveries.add(new Delivery(player.getUUID(), message.copy()));
            return;
        }

        TellrawContext tellraw = currentTellraw(server, player.getUUID());
        if (tellraw != null) {
            tellraw.processed.add(player.getUUID());
            if (accepted) tellraw.deliveries.add(new Delivery(player.getUUID(), message.copy()));
            if (tellraw.processed.containsAll(tellraw.expected)) finishTellraw(tellraw);
            return;
        }

        if (!accepted) return;
        ChatService.of(server).appendSystem(message, ChatEntry.Sender.server(),
                ChatEntry.Audience.players(List.of(player.getUUID())), ChatEntry.Origin.DIRECT_SYSTEM);
    }

    private static BroadcastContext currentBroadcast(MinecraftServer server) {
        Deque<BroadcastContext> contexts = BROADCAST_CONTEXT.get();
        if (contexts.isEmpty()) return null;
        BroadcastContext context = contexts.peek();
        return context.server == server ? context : null;
    }

    private static TellrawContext currentTellraw(MinecraftServer server, UUID playerUuid) {
        TellrawContext context = TELLRAW_CONTEXT.get();
        if (context == null || context.server != server || !context.expected.contains(playerUuid)) return null;
        return context;
    }

    private static void finishTellraw(TellrawContext context) {
        TELLRAW_CONTEXT.remove();
        if (context.deliveries.isEmpty()) return;
        List<MessageGroup> groups = new ArrayList<>();
        for (Delivery delivery : context.deliveries) {
            MessageGroup group = null;
            for (MessageGroup candidate : groups) {
                if (candidate.message.equals(delivery.message())) {
                    group = candidate;
                    break;
                }
            }
            if (group == null) {
                group = new MessageGroup(delivery.message().copy());
                groups.add(group);
            }
            group.players.add(delivery.playerUuid());
        }

        for (MessageGroup group : groups) {
            ChatEntry.Audience audience = context.explicitAllPlayers && groups.size() == 1
                    ? ChatEntry.Audience.global()
                    : ChatEntry.Audience.players(List.copyOf(group.players));
            ChatService.of(context.server).appendSystem(group.message, context.sender, audience, ChatEntry.Origin.TELLRAW);
        }
    }

    private static ChatEntry.Sender senderOf(CommandSourceStack source) {
        if (source.getEntity() instanceof ServerPlayer player) {
            return ChatEntry.Sender.player(player.getUUID(), player.getName().getString());
        }
        return ChatEntry.Sender.command(source.getTextName());
    }

    private record Delivery(UUID playerUuid, Component message) {}

    private static final class BroadcastContext {
        private final MinecraftServer server;
        private final Component baseMessage;
        private final List<Delivery> deliveries = new ArrayList<>();
        private BroadcastContext(MinecraftServer server, Component baseMessage) {
            this.server = server;
            this.baseMessage = baseMessage;
        }
    }

    private static final class TellrawContext {
        private final MinecraftServer server;
        private final ChatEntry.Sender sender;
        private final List<UUID> expected;
        private final boolean explicitAllPlayers;
        private final List<UUID> processed = new ArrayList<>();
        private final List<Delivery> deliveries = new ArrayList<>();
        private TellrawContext(MinecraftServer server, ChatEntry.Sender sender, List<UUID> expected,
                               boolean explicitAllPlayers) {
            this.server = server;
            this.sender = sender;
            this.expected = expected;
            this.explicitAllPlayers = explicitAllPlayers;
        }
    }

    private static final class MessageGroup {
        private final Component message;
        private final List<UUID> players = new ArrayList<>();
        private MessageGroup(Component message) { this.message = message; }
    }
}
