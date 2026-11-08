package io.github.confidesconch.villagelife.social;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.confidesconch.villagelife.VillageLife;
import io.github.confidesconch.villagelife.core.util.LenientCodecs;

import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Relationships between residents of one dimension (M17), saved as {@code data/villagelife/relationships.dat}:
 * points per pair of residents. Pairs at 0 points are not stored. Server thread only.
 *
 * <p>Data versions: 1 = relationships (M17a).
 */
public final class RelationshipStore extends SavedData {
    public static final int DATA_VERSION = 1;

    /** The relationship of residents {@code a} and {@code b} of village {@code village}. */
    public record Relation(UUID village, UUID a, UUID b, int points) {
        static final Codec<Relation> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                UUIDUtil.CODEC.fieldOf("village").forGetter(Relation::village),
                UUIDUtil.CODEC.fieldOf("a").forGetter(Relation::a),
                UUIDUtil.CODEC.fieldOf("b").forGetter(Relation::b),
                Codec.INT.fieldOf("points").forGetter(Relation::points)
        ).apply(instance, Relation::new));

        /** The other resident of the pair. */
        public UUID other(UUID resident) {
            return a.equals(resident) ? b : a;
        }

        public boolean involves(UUID resident) {
            return a.equals(resident) || b.equals(resident);
        }
    }

    private static final Codec<RelationshipStore> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("data_version", 1).forGetter(store -> DATA_VERSION),
            LenientCodecs.list(Relation.CODEC, "relationship").optionalFieldOf("relations", List.of())
                    .forGetter(store -> List.copyOf(store.relations.values()))
    ).apply(instance, RelationshipStore::new));

    public static final SavedDataType<RelationshipStore> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(VillageLife.MOD_ID, "relationships"), RelationshipStore::new, CODEC);

    private final Map<String, Relation> relations = new HashMap<>();

    public RelationshipStore() {}

    private RelationshipStore(int loadedDataVersion, List<Relation> loaded) {
        loaded.forEach(relation -> relations.put(key(relation.a(), relation.b()), relation));
        if (loadedDataVersion < DATA_VERSION) {
            setDirty();
        }
    }

    public static RelationshipStore get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    /** The same key for (a, b) and (b, a). */
    static String key(UUID a, UUID b) {
        return a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a;
    }

    public int points(UUID a, UUID b) {
        Relation relation = relations.get(key(a, b));
        return relation == null ? 0 : relation.points();
    }

    public void set(UUID village, UUID a, UUID b, int points) {
        if (points == 0) {
            relations.remove(key(a, b));
        } else {
            relations.put(key(a, b), new Relation(village, a, b, points));
        }
        setDirty();
    }

    /** All relationships of a resident. */
    public List<Relation> of(UUID resident) {
        List<Relation> list = new ArrayList<>();
        for (Relation relation : relations.values()) {
            if (relation.involves(resident)) {
                list.add(relation);
            }
        }
        return list;
    }

    public List<Relation> ofVillage(UUID village) {
        return relations.values().stream().filter(r -> r.village().equals(village)).toList();
    }

    /** Forgets relationships of residents who are no longer in a village (moved away, died, village gone). */
    public void retain(Set<UUID> residents) {
        if (relations.values().removeIf(r -> !residents.contains(r.a()) || !residents.contains(r.b()))) {
            setDirty();
        }
    }
}
