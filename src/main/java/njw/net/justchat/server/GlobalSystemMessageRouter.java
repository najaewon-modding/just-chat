package njw.net.justchat.server;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import njw.net.justchat.data.ChatSavedData;
import njw.net.justchat.data.SystemChatMessage;
import njw.net.justchat.network.NewSystemChatPayload;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@EventBusSubscriber(modid = "njw_just_chat")
public final class GlobalSystemMessageRouter {
    private static final ThreadLocal<Deque<GlobalContext>> GLOBAL_CONTEXT =
            ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<SelectorContext> SELECTOR_CONTEXT = new ThreadLocal<>();
    private static final ThreadLocal<Integer> BYPASS_DEPTH = ThreadLocal.withInitial(() -> 0);
    private static final Map<MinecraftServer, PendingBatch> PENDING = new IdentityHashMap<>();

    private GlobalSystemMessageRouter() {}

    public static void beginGlobal(MinecraftServer server, Component message, boolean overlay) {
        if (overlay) return;
        PendingBatch batch = PENDING.computeIfAbsent(server, ignored -> new PendingBatch());
        String key = message.getString();
        MessageGroup group = batch.groups.computeIfAbsent(key, ignored -> new MessageGroup());
        group.explicitCandidate = true;

        if (group.explicitBase == null) group.explicitBase = message.copy();
        if (group.explicitExpected == null) group.explicitExpected = onlinePlayers(server);

        if (!group.markerAdded) {
            group.markerAdded = true;
            batch.entries.add(new GlobalMarker(key));
        }

        GLOBAL_CONTEXT.get().push(new GlobalContext(server, key));
    }

    public static void endGlobal(boolean overlay) {
        if (overlay) return;
        Deque<GlobalContext> contexts = GLOBAL_CONTEXT.get();
        if (!contexts.isEmpty()) contexts.pop();
        if (contexts.isEmpty()) GLOBAL_CONTEXT.remove();
    }

    public static void beginSelectorGlobal(
            MinecraftServer server,
            Collection<ServerPlayer> targets
    ) {
        Set<UUID> expected = new HashSet<>();
        for (ServerPlayer player : targets) expected.add(player.getUUID());
        if (expected.isEmpty()) return;
        SELECTOR_CONTEXT.set(new SelectorContext(server, expected, new HashSet<>()));
    }

    public static boolean capture(
            ServerPlayer player,
            Component message,
            boolean accepted
    ) {
        if (BYPASS_DEPTH.get() > 0) return false;
        MinecraftServer server = player.level().getServer();
        PendingBatch batch = PENDING.computeIfAbsent(server, ignored -> new PendingBatch());
        GlobalContext globalContext = currentGlobalContext(server);
        Component copy = message.copy();

        if (globalContext != null) {
            String key = globalContext.key();
            MessageGroup group = batch.groups.computeIfAbsent(key, ignored -> new MessageGroup());
            group.explicitAttempted.add(player.getUUID());
            if (accepted) group.explicitRecipients.add(player.getUUID());

            if (group.explicitComponent == null) {
                group.explicitComponent = copy;
            } else if (!group.explicitComponent.equals(message)) {
                group.uniformExplicit = false;
            }

            batch.entries.add(new Delivery(key, player, copy, DeliveryType.EXPLICIT, accepted));
            return true;
        }

        SelectorContext selectorContext = currentSelectorContext(server, player);

        if (selectorContext != null) {
            String key = message.getString();
            MessageGroup group = batch.groups.computeIfAbsent(key, ignored -> new MessageGroup());
            group.selectorCandidate = true;
            group.selectorAttempted.add(player.getUUID());
            if (accepted) group.selectorRecipients.add(player.getUUID());

            if (group.selectorExpected == null) {
                group.selectorExpected = new HashSet<>(selectorContext.expected());
            }

            if (group.selectorComponent == null) {
                group.selectorComponent = copy;
            } else if (!group.selectorComponent.equals(message)) {
                group.uniformSelector = false;
            }

            batch.entries.add(new Delivery(key, player, copy, DeliveryType.SELECTOR, accepted));
            selectorContext.processed().add(player.getUUID());

            if (selectorContext.processed().containsAll(selectorContext.expected())) {
                SELECTOR_CONTEXT.remove();
            }

            return true;
        }

        String key = message.getString();
        MessageGroup group = batch.groups.computeIfAbsent(key, ignored -> new MessageGroup());
        group.directAttempted.add(player.getUUID());
        if (accepted) group.directRecipients.add(player.getUUID());

        if (group.directExpected == null) group.directExpected = onlinePlayers(server);

        if (group.directComponent == null) {
            group.directComponent = copy;
        } else if (!group.directComponent.equals(message)) {
            group.uniformDirect = false;
        }

        batch.entries.add(new Delivery(key, player, copy, DeliveryType.DIRECT, accepted));
        return true;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerTick(ServerTickEvent.Post event) {
        flush(event.getServer());
    }

    private static GlobalContext currentGlobalContext(MinecraftServer server) {
        Deque<GlobalContext> contexts = GLOBAL_CONTEXT.get();
        if (contexts.isEmpty()) return null;
        GlobalContext context = contexts.peek();
        return context.server() == server ? context : null;
    }

    private static SelectorContext currentSelectorContext(
            MinecraftServer server,
            ServerPlayer player
    ) {
        SelectorContext context = SELECTOR_CONTEXT.get();
        if (context == null || context.server() != server) return null;
        if (!context.expected().contains(player.getUUID())) return null;
        return context;
    }

    private static void flush(MinecraftServer server) {
        PendingBatch batch = PENDING.remove(server);
        GLOBAL_CONTEXT.remove();
        SELECTOR_CONTEXT.remove();
        if (batch == null) return;

        Set<String> published = new HashSet<>();

        for (PendingEntry entry : batch.entries) {
            MessageGroup group = batch.groups.get(entry.key());
            if (group == null) continue;

            boolean explicitGlobal = isExplicitGlobal(group);
            boolean selectorGlobal = isSelectorGlobal(group);
            boolean directGlobal = isDirectGlobal(group);

            if (entry instanceof GlobalMarker) {
                if (explicitGlobal && published.add(entry.key())) publishPreferred(server, group);
                continue;
            }

            Delivery delivery = (Delivery) entry;

            if (delivery.type() == DeliveryType.EXPLICIT) {
                if (explicitGlobal) {
                    if (published.add(entry.key())) publishPreferred(server, group);
                } else {
                    replay(delivery);
                }
                continue;
            }

            if (delivery.type() == DeliveryType.SELECTOR) {
                if (selectorGlobal) {
                    if (published.add(entry.key())) publishPreferred(server, group);
                } else {
                    replay(delivery);
                }
                continue;
            }

            if (directGlobal) {
                if (published.add(entry.key())) publishPreferred(server, group);
            } else {
                replay(delivery);
            }
        }
    }

    private static boolean isExplicitGlobal(MessageGroup group) {
        return matches(
                group.explicitCandidate,
                group.uniformExplicit,
                group.explicitAttempted,
                group.explicitExpected,
                false
        );
    }

    private static boolean isSelectorGlobal(MessageGroup group) {
        return matches(
                group.selectorCandidate,
                group.uniformSelector,
                group.selectorAttempted,
                group.selectorExpected,
                false
        );
    }

    private static boolean isDirectGlobal(MessageGroup group) {
        return matches(
                true,
                group.uniformDirect,
                group.directAttempted,
                group.directExpected,
                true
        );
    }

    private static boolean matches(
            boolean candidate,
            boolean uniform,
            Set<UUID> actual,
            Set<UUID> expected,
            boolean requireMultiple
    ) {
        if (!candidate || !uniform || expected == null || expected.isEmpty()) return false;
        if (requireMultiple && expected.size() < 2) return false;
        return actual.size() == expected.size() && actual.containsAll(expected);
    }

    private static void publishPreferred(MinecraftServer server, MessageGroup group) {
        if (isExplicitGlobal(group)) {
            Component message = group.explicitComponent != null
                    ? group.explicitComponent
                    : group.explicitBase;
            publish(server, message, group.explicitRecipients);
            return;
        }

        if (isSelectorGlobal(group)) {
            publish(server, group.selectorComponent, group.selectorRecipients);
            return;
        }

        if (isDirectGlobal(group)) {
            publish(server, group.directComponent, group.directRecipients);
        }
    }

    private static void publish(
            MinecraftServer server,
            Component message,
            Set<UUID> recipients
    ) {
        if (message == null) return;
        ChatSavedData data = ChatSavedData.get(server);
        SystemChatMessage saved = data.addSystem(message, System.currentTimeMillis());
        NewSystemChatPayload payload = new NewSystemChatPayload(saved);

        for (UUID uuid : recipients) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) PacketDistributor.sendToPlayer(player, payload);
        }
    }

    private static void replay(Delivery delivery) {
        if (!delivery.accepted()) return;
        BYPASS_DEPTH.set(BYPASS_DEPTH.get() + 1);

        try {
            delivery.player().sendSystemMessage(delivery.message(), false);
        } finally {
            int depth = BYPASS_DEPTH.get();

            if (depth <= 1) {
                BYPASS_DEPTH.remove();
            } else {
                BYPASS_DEPTH.set(depth - 1);
            }
        }
    }

    private static Set<UUID> onlinePlayers(MinecraftServer server) {
        Set<UUID> players = new HashSet<>();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            players.add(player.getUUID());
        }

        return players;
    }

    private interface PendingEntry {
        String key();
    }

    private record Delivery(
            String key,
            ServerPlayer player,
            Component message,
            DeliveryType type,
            boolean accepted
    ) implements PendingEntry {}

    private record GlobalMarker(String key) implements PendingEntry {}

    private record GlobalContext(MinecraftServer server, String key) {}

    private record SelectorContext(
            MinecraftServer server,
            Set<UUID> expected,
            Set<UUID> processed
    ) {}

    private enum DeliveryType {
        EXPLICIT,
        SELECTOR,
        DIRECT
    }

    private static final class PendingBatch {
        private final Map<String, MessageGroup> groups = new LinkedHashMap<>();
        private final List<PendingEntry> entries = new ArrayList<>();
    }

    private static final class MessageGroup {
        private Component explicitBase;
        private Component explicitComponent;
        private Component selectorComponent;
        private Component directComponent;
        private Set<UUID> explicitExpected;
        private Set<UUID> selectorExpected;
        private Set<UUID> directExpected;
        private final Set<UUID> explicitAttempted = new HashSet<>();
        private final Set<UUID> selectorAttempted = new HashSet<>();
        private final Set<UUID> directAttempted = new HashSet<>();
        private final Set<UUID> explicitRecipients = new HashSet<>();
        private final Set<UUID> selectorRecipients = new HashSet<>();
        private final Set<UUID> directRecipients = new HashSet<>();
        private boolean explicitCandidate;
        private boolean selectorCandidate;
        private boolean markerAdded;
        private boolean uniformExplicit = true;
        private boolean uniformSelector = true;
        private boolean uniformDirect = true;
    }
}