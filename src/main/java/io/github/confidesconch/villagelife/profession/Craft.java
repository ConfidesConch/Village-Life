package io.github.confidesconch.villagelife.profession;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * One recipe of a crafting profession (M14a), part of a profession file:
 * {@code {"inputs": [{"item": "minecraft:wheat", "count": 3}], "output": {"item": "minecraft:bread"},
 * "stock": 64, "ticks": 40}}. An input {@code item} starting with {@code #} is an item tag (e.g.
 * {@code #minecraft:planks}). The worker only crafts while the village holds fewer than {@code stock}
 * of the output (owner's choice "1a"); {@code ticks} is the time one craft takes.
 */
public record Craft(List<Stack> inputs, Stack output, int stock, int ticks) {

    /** An item id (or {@code #tag}) and a count. */
    public record Stack(String item, int count) {
        public static final Codec<Stack> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("item").forGetter(Stack::item),
                Codec.intRange(1, 64).optionalFieldOf("count", 1).forGetter(Stack::count)
        ).apply(instance, Stack::new));
    }

    public static final Codec<Craft> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Stack.CODEC.listOf().fieldOf("inputs").forGetter(Craft::inputs),
            Stack.CODEC.fieldOf("output").forGetter(Craft::output),
            Codec.intRange(1, 4096).optionalFieldOf("stock", 64).forGetter(Craft::stock),
            Codec.intRange(1, 1200).optionalFieldOf("ticks", 40).forGetter(Craft::ticks)
    ).apply(instance, Craft::new));

    public Craft {
        inputs = List.copyOf(inputs);
    }
}
