package njw.net.justchat.server;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.UsernameCache;
import njw.net.justchat.data.ItemTag;
import njw.net.justchat.data.PlayerTag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PlayerTagResolver {
    private static final Pattern PATTERN =
            Pattern.compile("(?<![A-Za-z0-9_])@([A-Za-z0-9_]{1,16})(?![A-Za-z0-9_])");
    private static final int MAX_SUGGESTIONS = 5;

    private PlayerTagResolver() {}

    public static List<PlayerTag> resolve(
            MinecraftServer server,
            String content,
            List<ItemTag> itemTags
    ) {
        List<PlayerTag> tags = new ArrayList<>();
        Matcher matcher = PATTERN.matcher(content);

        while (matcher.find()) {
            if (overlapsItemTag(matcher.start(), matcher.end(), itemTags)) continue;
            ResolvedPlayer target = resolvePlayer(server, matcher.group(1));
            if (target == null) continue;
            tags.add(new PlayerTag(
                    matcher.start(),
                    matcher.end(),
                    target.uuid(),
                    target.name()
            ));
        }

        return List.copyOf(tags);
    }

    public static List<Suggestion> suggest(MinecraftServer server, String query) {
        if (query.length() > 16 || !query.matches("[A-Za-z0-9_]*")) return List.of();

        String lowerQuery = query.toLowerCase(Locale.ROOT);
        Map<String, Suggestion> suggestions = new LinkedHashMap<>();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            String name = player.getName().getString();
            suggestions.put(
                    name.toLowerCase(Locale.ROOT),
                    new Suggestion(name, true)
            );
        }

        for (String name : UsernameCache.getMap().values()) {
            if (name == null) continue;
            suggestions.putIfAbsent(
                    name.toLowerCase(Locale.ROOT),
                    new Suggestion(name, false)
            );
        }

        return suggestions.values().stream()
                .filter(suggestion -> suggestion.name().toLowerCase(Locale.ROOT).startsWith(lowerQuery))
                .sorted(
                        Comparator.comparingInt((Suggestion suggestion) -> suggestion.online() ? 0 : 1)
                                .thenComparing(Suggestion::name, String.CASE_INSENSITIVE_ORDER)
                )
                .limit(MAX_SUGGESTIONS)
                .toList();
    }

    private static boolean overlapsItemTag(int start, int end, List<ItemTag> itemTags) {
        for (ItemTag tag : itemTags) {
            if (start < tag.end() && end > tag.start()) return true;
        }
        return false;
    }

    private static ResolvedPlayer resolvePlayer(MinecraftServer server, String name) {
        ServerPlayer online = server.getPlayerList().getPlayerByName(name);

        if (online != null) {
            return new ResolvedPlayer(
                    online.getUUID(),
                    online.getName().getString()
            );
        }

        for (Map.Entry<UUID, String> entry : UsernameCache.getMap().entrySet()) {
            String cachedName = entry.getValue();

            if (cachedName != null && cachedName.equalsIgnoreCase(name)) {
                return new ResolvedPlayer(
                        entry.getKey(),
                        cachedName
                );
            }
        }

        return null;
    }

    public record Suggestion(String name, boolean online) {}

    private record ResolvedPlayer(UUID uuid, String name) {}
}