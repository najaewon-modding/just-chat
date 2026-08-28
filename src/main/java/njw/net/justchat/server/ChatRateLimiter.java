package njw.net.justchat.server;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = "njw_just_chat")
public final class ChatRateLimiter {
    private static final Map<UUID, PlayerLimits> LIMITS = new ConcurrentHashMap<>();

    private ChatRateLimiter() {}

    public static boolean allow(ServerPlayer player, Action action) {
        PlayerLimits limits = LIMITS.computeIfAbsent(player.getUUID(), uuid -> new PlayerLimits());
        return limits.allow(action, System.nanoTime());
    }

    public static boolean allowChatWarning(ServerPlayer player) {
        PlayerLimits limits = LIMITS.computeIfAbsent(player.getUUID(), uuid -> new PlayerLimits());
        return limits.allowChatWarning(System.nanoTime());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) LIMITS.remove(player.getUUID());
    }

    public enum Action {
        CHAT(10, 2.0), DELETE(16, 4.0), HISTORY(8, 4.0), SUGGESTIONS(16, 16.0), PRESENCE(8, 4.0);

        private final int capacity;
        private final double refillPerSecond;

        Action(int capacity, double refillPerSecond) {
            this.capacity = capacity;
            this.refillPerSecond = refillPerSecond;
        }
    }

    private static final class PlayerLimits {
        private final Map<Action, Bucket> buckets = new EnumMap<>(Action.class);
        private final Bucket chatWarning = new Bucket(1, 0.5);

        private PlayerLimits() {
            for (Action action : Action.values()) {
                buckets.put(action, new Bucket(action.capacity, action.refillPerSecond));
            }
        }

        private boolean allow(Action action, long now) {
            return buckets.get(action).allow(now);
        }

        private boolean allowChatWarning(long now) {
            return chatWarning.allow(now);
        }
    }

    private static final class Bucket {
        private final int capacity;
        private final double refillPerSecond;
        private double tokens;
        private long lastRefillNanos;

        private Bucket(int capacity, double refillPerSecond) {
            this.capacity = capacity;
            this.refillPerSecond = refillPerSecond;
            this.tokens = capacity;
            this.lastRefillNanos = System.nanoTime();
        }

        private synchronized boolean allow(long now) {
            long elapsedNanos = Math.max(0L, now - lastRefillNanos);
            double refill = elapsedNanos / 1_000_000_000.0 * refillPerSecond;
            tokens = Math.min(capacity, tokens + refill);
            lastRefillNanos = now;
            if (tokens < 1.0) return false;
            tokens -= 1.0;
            return true;
        }
    }
}