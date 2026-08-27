package njw.net.justchat.server;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import njw.net.justchat.data.ItemTag;
import njw.net.justchat.network.ItemTagReference;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
        Map<UUID, PendingItem> items = PENDING.get(player.getUUID());
        if (items == null || items.isEmpty()) return List.of();

        long now = System.currentTimeMillis();
        cleanup(items, now);
        List<ItemTag> tags = new ArrayList<>();
        int cursor = 0;

        for (ItemTagReference reference : references) {
            PendingItem pending = items.get(reference.token());
            if (pending == null || reference.displayText().isEmpty()) continue;

            int start = content.indexOf(reference.displayText(), cursor);
            if (start < 0) continue;

            int end = start + reference.displayText().length();
            tags.add(new ItemTag(start, end, pending.item()));
            items.remove(reference.token());
            cursor = end;
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

    private static void cleanup(Map<UUID, PendingItem> items, long now) {
        items.entrySet().removeIf(entry -> now - entry.getValue().createdAt() > EXPIRY_MILLIS);
    }

    public record CreatedItem(UUID token, ItemStackTemplate item) {}

    private record PendingItem(ItemStackTemplate item, long createdAt) {}
}