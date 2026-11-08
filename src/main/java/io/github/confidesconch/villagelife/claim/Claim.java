package io.github.confidesconch.villagelife.claim;

import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;

/** One claimed chunk (M11f): chunk coordinates and its owner. */
public record Claim(int chunkX, int chunkZ, UUID owner, String ownerName) {
    public static final Codec<Claim> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("x").forGetter(Claim::chunkX),
            Codec.INT.fieldOf("z").forGetter(Claim::chunkZ),
            UUIDUtil.CODEC.fieldOf("owner").forGetter(Claim::owner),
            Codec.STRING.optionalFieldOf("owner_name", "?").forGetter(Claim::ownerName)
    ).apply(instance, Claim::new));

    /** One number for both chunk coordinates (same packing as Minecraft's {@code ChunkPos}). */
    public static long key(int chunkX, int chunkZ) {
        return (chunkX & 0xFFFFFFFFL) | ((chunkZ & 0xFFFFFFFFL) << 32);
    }

    public long key() {
        return key(chunkX, chunkZ);
    }
}
