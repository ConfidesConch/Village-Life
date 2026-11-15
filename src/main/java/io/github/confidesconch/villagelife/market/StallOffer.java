package io.github.confidesconch.villagelife.market;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;

/**
 * One thing the daily market trader may sell (M28c), from {@code data/<namespace>/stall_offer/<name>.json}:
 * {@code {"item": "minecraft:cherry_sapling", "count": 2, "emeralds": 3, "weight": 10}} – {@code count}
 * items for {@code emeralds}; {@code weight} makes it come up more often (default 10).
 */
public record StallOffer(Identifier item, int count, int emeralds, int weight) {
    public static final Codec<StallOffer> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.fieldOf("item").forGetter(StallOffer::item),
            Codec.intRange(1, 64).optionalFieldOf("count", 1).forGetter(StallOffer::count),
            Codec.intRange(1, 64).optionalFieldOf("emeralds", 1).forGetter(StallOffer::emeralds),
            Codec.intRange(1, 1000).optionalFieldOf("weight", 10).forGetter(StallOffer::weight)
    ).apply(instance, StallOffer::new));
}
