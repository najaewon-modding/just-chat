package njw.net.justchat.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record ChatEntry(
        long id,
        Kind kind,
        Sender sender,
        Audience audience,
        Origin origin,
        String textContent,
        Component componentContent,
        long createdAt,
        boolean deleted,
        List<PlayerTag> playerTags,
        List<ItemTag> itemTags
) {
    private static final UUID NIL_UUID = new UUID(0L, 0L);
    private static final Codec<Kind> KIND_CODEC = Codec.STRING.xmap(Kind::valueOf, Kind::name);
    private static final Codec<Origin> ORIGIN_CODEC = Codec.STRING.xmap(Origin::valueOf, Origin::name);
    private static final StreamCodec<ByteBuf, List<PlayerTag>> PLAYER_TAG_LIST_CODEC =
            PlayerTag.STREAM_CODEC.apply(ByteBufCodecs.list(128));
    private static final StreamCodec<RegistryFriendlyByteBuf, List<ItemTag>> ITEM_TAG_LIST_CODEC =
            ItemTag.STREAM_CODEC.apply(ByteBufCodecs.list(16));

    public static final Codec<ChatEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("id").forGetter(ChatEntry::id),
            KIND_CODEC.fieldOf("kind").forGetter(ChatEntry::kind),
            Sender.CODEC.fieldOf("sender").forGetter(ChatEntry::sender),
            Audience.CODEC.fieldOf("audience").forGetter(ChatEntry::audience),
            ORIGIN_CODEC.fieldOf("origin").forGetter(ChatEntry::origin),
            Codec.STRING.optionalFieldOf("textContent", "").forGetter(ChatEntry::textContent),
            ComponentSerialization.CODEC.optionalFieldOf("componentContent", Component.empty()).forGetter(ChatEntry::componentContent),
            Codec.LONG.fieldOf("createdAt").forGetter(ChatEntry::createdAt),
            Codec.BOOL.optionalFieldOf("deleted", false).forGetter(ChatEntry::deleted),
            PlayerTag.CODEC.listOf().optionalFieldOf("playerTags", List.of()).forGetter(ChatEntry::playerTags),
            ItemTag.CODEC.listOf().optionalFieldOf("itemTags", List.of()).forGetter(ChatEntry::itemTags)
    ).apply(instance, ChatEntry::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, ChatEntry> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ChatEntry decode(RegistryFriendlyByteBuf buffer) {
            long id = ByteBufCodecs.VAR_LONG.decode(buffer);
            Kind kind = Kind.valueOf(ByteBufCodecs.STRING_UTF8.decode(buffer));
            Sender sender = Sender.STREAM_CODEC.decode(buffer);
            Audience audience = Audience.STREAM_CODEC.decode(buffer);
            Origin origin = Origin.valueOf(ByteBufCodecs.STRING_UTF8.decode(buffer));
            String textContent = ByteBufCodecs.STRING_UTF8.decode(buffer);
            Component componentContent = ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC.decode(buffer);
            long createdAt = ByteBufCodecs.VAR_LONG.decode(buffer);
            boolean deleted = ByteBufCodecs.BOOL.decode(buffer);
            List<PlayerTag> playerTags = PLAYER_TAG_LIST_CODEC.decode(buffer);
            List<ItemTag> itemTags = ITEM_TAG_LIST_CODEC.decode(buffer);
            return new ChatEntry(id, kind, sender, audience, origin, textContent, componentContent, createdAt,
                    deleted, playerTags, itemTags);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, ChatEntry value) {
            ByteBufCodecs.VAR_LONG.encode(buffer, value.id());
            ByteBufCodecs.STRING_UTF8.encode(buffer, value.kind().name());
            Sender.STREAM_CODEC.encode(buffer, value.sender());
            Audience.STREAM_CODEC.encode(buffer, value.audience());
            ByteBufCodecs.STRING_UTF8.encode(buffer, value.origin().name());
            ByteBufCodecs.STRING_UTF8.encode(buffer, value.textContent());
            ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC.encode(buffer, value.componentContent());
            ByteBufCodecs.VAR_LONG.encode(buffer, value.createdAt());
            ByteBufCodecs.BOOL.encode(buffer, value.deleted());
            PLAYER_TAG_LIST_CODEC.encode(buffer, value.playerTags());
            ITEM_TAG_LIST_CODEC.encode(buffer, value.itemTags());
        }
    };

    public ChatEntry {
        playerTags = List.copyOf(playerTags);
        itemTags = List.copyOf(itemTags);
        componentContent = componentContent.copy();
    }

    public static ChatEntry player(ChatMessage message) {
        return new ChatEntry(message.id(), Kind.PLAYER, Sender.player(message.senderUuid(), message.senderName()),
                Audience.global(), Origin.PLAYER_CHAT, message.content(), Component.empty(), message.createdAt(),
                message.deleted(), message.playerTags(), message.itemTags());
    }

    public static ChatEntry system(long id, Component content, long createdAt, Sender sender, Audience audience,
                                   Origin origin) {
        return new ChatEntry(id, Kind.SYSTEM, sender, audience, origin, "", content.copy(), createdAt, false,
                List.of(), List.of());
    }

    public static ChatEntry legacySystem(SystemChatMessage message) {
        return system(message.id(), message.content(), message.createdAt(), Sender.server(), Audience.global(),
                Origin.LEGACY_SYSTEM);
    }

    public boolean isPlayer() { return kind == Kind.PLAYER; }
    public boolean isSystem() { return kind == Kind.SYSTEM; }
    public boolean isVisibleTo(UUID playerUuid) { return audience.isVisibleTo(playerUuid); }

    public ChatMessage chatMessage() {
        if (!isPlayer()) return null;
        return new ChatMessage(id, sender.uuid(), sender.name(), textContent, createdAt, deleted, playerTags, itemTags);
    }

    public Component displayContent() {
        return isSystem() ? componentContent : Component.literal(textContent);
    }

    public boolean canDelete(UUID playerUuid, long now) {
        ChatMessage message = chatMessage();
        return message != null && message.canDelete(playerUuid, now);
    }

    public ChatEntry asDeleted() {
        if (!isPlayer()) return this;
        return new ChatEntry(id, kind, sender, audience, origin, "", componentContent, createdAt, true,
                List.of(), List.of());
    }

    public enum Kind { PLAYER, SYSTEM }
    public enum Origin { PLAYER_CHAT, VANILLA_BROADCAST, TELLRAW, DIRECT_SYSTEM, LEGACY_SYSTEM }

    public record Sender(SenderType type, UUID uuid, String name) {
        private static final Codec<SenderType> TYPE_CODEC = Codec.STRING.xmap(SenderType::valueOf, SenderType::name);
        public static final Codec<Sender> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                TYPE_CODEC.fieldOf("type").forGetter(Sender::type),
                UUIDUtil.CODEC.optionalFieldOf("uuid", NIL_UUID).forGetter(Sender::uuid),
                Codec.STRING.optionalFieldOf("name", "").forGetter(Sender::name)
        ).apply(instance, Sender::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Sender> STREAM_CODEC = new StreamCodec<>() {
            @Override public Sender decode(RegistryFriendlyByteBuf buffer) {
                SenderType type = SenderType.valueOf(ByteBufCodecs.STRING_UTF8.decode(buffer));
                UUID uuid = UUIDUtil.STREAM_CODEC.decode(buffer);
                String name = ByteBufCodecs.STRING_UTF8.decode(buffer);
                return new Sender(type, uuid, name);
            }
            @Override public void encode(RegistryFriendlyByteBuf buffer, Sender value) {
                ByteBufCodecs.STRING_UTF8.encode(buffer, value.type().name());
                UUIDUtil.STREAM_CODEC.encode(buffer, value.uuid());
                ByteBufCodecs.STRING_UTF8.encode(buffer, value.name());
            }
        };
        public static Sender player(UUID uuid, String name) { return new Sender(SenderType.PLAYER, uuid, name); }
        public static Sender server() { return new Sender(SenderType.SERVER, NIL_UUID, "Server"); }
        public static Sender command(String name) { return new Sender(SenderType.COMMAND, NIL_UUID, name == null ? "" : name); }
    }

    public enum SenderType { PLAYER, SERVER, COMMAND }

    public record Audience(AudienceType type, List<UUID> players) {
        private static final Codec<AudienceType> TYPE_CODEC = Codec.STRING.xmap(AudienceType::valueOf, AudienceType::name);
        public static final Codec<Audience> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                TYPE_CODEC.fieldOf("type").forGetter(Audience::type),
                UUIDUtil.CODEC.listOf().optionalFieldOf("players", List.of()).forGetter(Audience::players)
        ).apply(instance, Audience::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Audience> STREAM_CODEC = new StreamCodec<>() {
            @Override public Audience decode(RegistryFriendlyByteBuf buffer) {
                AudienceType type = AudienceType.valueOf(ByteBufCodecs.STRING_UTF8.decode(buffer));
                int size = ByteBufCodecs.VAR_INT.decode(buffer);
                List<UUID> players = new ArrayList<>(size);
                for (int i = 0; i < size; i++) players.add(UUIDUtil.STREAM_CODEC.decode(buffer));
                return new Audience(type, players);
            }
            @Override public void encode(RegistryFriendlyByteBuf buffer, Audience value) {
                ByteBufCodecs.STRING_UTF8.encode(buffer, value.type().name());
                ByteBufCodecs.VAR_INT.encode(buffer, value.players().size());
                for (UUID uuid : value.players()) UUIDUtil.STREAM_CODEC.encode(buffer, uuid);
            }
        };
        public Audience { players = List.copyOf(players); }
        public static Audience global() { return new Audience(AudienceType.GLOBAL, List.of()); }
        public static Audience players(List<UUID> players) { return new Audience(AudienceType.PLAYERS, players); }
        public boolean isGlobal() { return type == AudienceType.GLOBAL; }
        public boolean isVisibleTo(UUID playerUuid) { return isGlobal() || players.contains(playerUuid); }
    }

    public enum AudienceType { GLOBAL, PLAYERS }
}
