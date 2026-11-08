package io.github.confidesconch.villagelife.market;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;

/**
 * One tradable good from {@code data/<namespace>/market_good/<name>.json} (M15a):
 * {@code {"item": "minecraft:bread", "count": 6, "emeralds": 1, "target": 64}} – a bundle of
 * {@code count} items costs {@code emeralds} at normal supply. The village wants to keep
 * {@code target} in storage: it sells only what is above, buys only up to it (owner's choice "3a").
 */
public record MarketGood(Identifier item, int count, int emeralds, int target) {
    public static final Codec<MarketGood> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.fieldOf("item").forGetter(MarketGood::item),
            Codec.intRange(1, 64).optionalFieldOf("count", 1).forGetter(MarketGood::count),
            Codec.intRange(1, 64).optionalFieldOf("emeralds", 1).forGetter(MarketGood::emeralds),
            Codec.intRange(0, 4096).optionalFieldOf("target", 32).forGetter(MarketGood::target)
    ).apply(instance, MarketGood::new));
}
