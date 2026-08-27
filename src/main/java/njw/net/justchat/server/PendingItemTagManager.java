package njw.net.justchat.server;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import njw.net.justchat.ChatRules;
import njw.net.justchat.data.ItemTag;
import njw.net.justchat.network.ItemTagReference;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@EventBusSubscriber(modid = "njw_just_chat")
public final class PendingItemTagManager {
    private static final long EXPIRY_MILLIS = 10L * 60L * 1000L;
    private static final Map<UUID, Map<UUID, PendingItem>> PENDING = new HashMap<>();

    private PendingItemTagManager() {}

    public static CreatedItem create(ServerPlayer player, int inventorySlot) {
        Inventory inventory = player.getInventory();
        if (inventorySlot < 0 || inventorySlot >= inventory.getContainerSize()) return null;

        ItemStack stack = inventory.getItem(inventorySlot);
        if (stack.isEmpty()) return null;

        long now = System.currentTimeMillis();
        Map<UUID, PendingItem> items = PENDING.computeIfAbsent(
                player.getUUID(),
                uuid -> new HashMap<>()
        );

        cleanup(items, now);

        while (items.size() >= ChatRules.MAX_PENDING_ITEM_TAGS) {
            removeOldest(items);
        }

        UUID token = UUID.randomUUID();
        ItemStackTemplate item = ItemStackTemplate.fromNonEmptyStack(stack);
        items.put(token, new PendingItem(item, now));
        return new CreatedItem(token, item);
    }

    public static List<ItemTag> resolve(
            ServerPlayer player,
            String content,
            List<ItemTagReference> references
    ) {
        if (references.isEmpty()) return List.of();

        if (references.size() > ChatRules.MAX_ITEM_TAGS_PER_MESSAGE) {
            return List.of();
        }

        Map<UUID, PendingItem> items = PENDING.get(player.getUUID());
        if (items == null || items.isEmpty()) return List.of();

        long now = System.currentTimeMillis();
        cleanup(items, now);

        if (items.isEmpty()) {
            PENDING.remove(player.getUUID());
            return List.of();
        }

        List<ItemTagReference> sorted = new ArrayList<>(references);
        sorted.sort(Comparator.comparingInt(ItemTagReference::start));

        List<ItemTag> tags = new ArrayList<>();
        Set<UUID> usedTokens = new HashSet<>();
        int previousEnd = -1;

        for (ItemTagReference reference : sorted) {
            if (!validReference(content, reference)) continue;
            if (reference.start() < previousEnd) continue;
            if (!usedTokens.add(reference.token())) continue;

            PendingItem pending = items.get(reference.token());
            if (pending == null) continue;

            tags.add(new ItemTag(
                    reference.start(),
                    reference.end(),
                    pending.item()
            ));

            items.remove(reference.token());
            previousEnd = reference.end();

            if (tags.size() >= ChatRules.MAX_ITEM_TAGS_PER_MESSAGE) break;
        }

        if (items.isEmpty()) PENDING.remove(player.getUUID());
        return List.copyOf(tags);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PENDING.remove(player.getUUID());
        }
    }

    private static boolean validReference(
            String content,
            ItemTagReference reference
    ) {
        int start = reference.start();
        int end = reference.end();

        if (reference.displayText().isEmpty()) return false;
        if (start < 0 || start >= end || end > content.length()) return false;

        return content.substring(start, end).equals(reference.displayText());
    }

    private static void cleanup(Map<UUID, PendingItem> items, long now) {
        items.entrySet().removeIf(
                entry -> now - entry.getValue().createdAt() > EXPIRY_MILLIS
        );
    }

    private static void removeOldest(Map<UUID, PendingItem> items) {
        UUID oldestToken = null;
        long oldestTime = Long.MAX_VALUE;

        for (Map.Entry<UUID, PendingItem> entry : items.entrySet()) {
            if (entry.getValue().createdAt() >= oldestTime) continue;
            oldestTime = entry.getValue().createdAt();
            oldestToken = entry.getKey();
        }

        if (oldestToken != null) items.remove(oldestToken);
    }

    public record CreatedItem(UUID token, ItemStackTemplate item) {}

    private record PendingItem(ItemStackTemplate item, long createdAt) {}
}