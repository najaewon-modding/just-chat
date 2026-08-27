package njw.net.justchat.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStackTemplate;

public record ItemTag(int start, int end, ItemStackTemplate item) {
    public static final Codec<ItemTag> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("start").forGetter(ItemTag::start),
            Codec.INT.fieldOf("end").forGetter(ItemTag::end),
            ItemStackTemplate.CODEC.fieldOf("item").forGetter(ItemTag::item)
    ).apply(instance, ItemTag::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, ItemTag> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ItemTag::start,
            ByteBufCodecs.VAR_INT, ItemTag::end,
            ItemStackTemplate.STREAM_CODEC, ItemTag::item,
            ItemTag::new
    );
}