package io.github.confidesconch.villagelife.growth;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;

/**
 * The supply parcel a village receives once when it reaches {@code stage} (M16, owner's wish "post with
 * a generous but balanced supply"), from {@code data/<namespace>/stage_reward/*.json}.
 */
public record StageReward(int stage, List<Stack> items) {

    /** {@code count} × {@code item}. */
    public record Stack(Identifier item, int count) {
        static final Codec<Stack> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.fieldOf("item").forGetter(Stack::item),
                Codec.intRange(1, 1024).fieldOf("count").forGetter(Stack::count)
        ).apply(instance, Stack::new));
    }

    public static final Codec<StageReward> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.intRange(1, 2).fieldOf("stage").forGetter(StageReward::stage),
            Stack.CODEC.listOf().fieldOf("items").forGetter(StageReward::items)
    ).apply(instance, StageReward::new));
}
