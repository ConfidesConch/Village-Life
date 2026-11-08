package io.github.confidesconch.villagelife.claim;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.confidesconch.villagelife.VillageLife;
import io.github.confidesconch.villagelife.core.util.LenientCodecs;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * All claimed chunks of one dimension (M11f), saved as {@code data/villagelife/claims.dat} in that
 * dimension's folder. Only accessed on the server thread.
 *
 * <p>Data versions: 1 = claims (M11f).
 */
public final class ClaimStore extends SavedData {
    public static final int DATA_VERSION = 1;

    private static final Codec<ClaimStore> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("data_version", 1).forGetter(store -> DATA_VERSION),
            LenientCodecs.list(Claim.CODEC, "claim").fieldOf("claims").forGetter(store -> List.copyOf(store.claims.values()))
    ).apply(instance, ClaimStore::new));

    public static final SavedDataType<ClaimStore> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(VillageLife.MOD_ID, "claims"), ClaimStore::new, CODEC);

    private final Map<Long, Claim> claims = new LinkedHashMap<>();

    public ClaimStore() {}

    private ClaimStore(int loadedDataVersion, List<Claim> loaded) {
        loaded.forEach(claim -> claims.put(claim.key(), claim));
        if (loadedDataVersion < DATA_VERSION) {
            setDirty();
        }
    }

    public static ClaimStore get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public Optional<Claim> at(int chunkX, int chunkZ) {
        return Optional.ofNullable(claims.get(Claim.key(chunkX, chunkZ)));
    }

    public Collection<Claim> all() {
        return Collections.unmodifiableCollection(claims.values());
    }

    public List<Claim> ownedBy(UUID player) {
        return claims.values().stream().filter(c -> c.owner().equals(player)).toList();
    }

    public void put(Claim claim) {
        claims.put(claim.key(), claim);
        setDirty();
    }

    public void remove(int chunkX, int chunkZ) {
        if (claims.remove(Claim.key(chunkX, chunkZ)) != null) {
            setDirty();
        }
    }
}
