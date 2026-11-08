package io.github.confidesconch.villagelife.resident;

import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;

/**
 * A resident's job (M8): the workplace building and the profession id (e.g.
 * {@code villagelife:lumberjack}). Stored in the {@link ResidentRecord}; what the profession does is
 * defined in the {@code profession} system.
 */
public record Job(UUID workplace, String profession) {

    public static final Codec<Job> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("workplace").forGetter(Job::workplace),
            Codec.STRING.fieldOf("profession").forGetter(Job::profession)
    ).apply(instance, Job::new));

    /** Translation key of the profession name, e.g. {@code villagelife.profession.lumberjack}. */
    public String translationKey() {
        return translationKey(profession);
    }

    /** Translation key of a profession id: {@code villagelife:fisherman} → {@code villagelife.profession.fisherman}. */
    public static String translationKey(String profession) {
        int colon = profession.indexOf(':');
        String namespace = colon < 0 ? "minecraft" : profession.substring(0, colon);
        String path = colon < 0 ? profession : profession.substring(colon + 1);
        return namespace + ".profession." + path.replace('/', '.');
    }
}
