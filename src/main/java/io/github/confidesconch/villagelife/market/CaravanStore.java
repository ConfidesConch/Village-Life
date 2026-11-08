package io.github.confidesconch.villagelife.market;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.confidesconch.villagelife.VillageLife;

import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Trading caravans of one dimension (M15b), saved as {@code data/villagelife/caravans.dat}: per village
 * when the next one comes, the one that is there now and what the last one traded. Losing this file
 * only means a caravan comes earlier – no goods are stored here. Server thread only.
 *
 * <p>Data versions: 1 = caravans (M15b).
 */
public final class CaravanStore extends SavedData {
    public static final int DATA_VERSION = 1;

    /** Result of a visit, shown in the book. */
    public record Visit(long gameTime, int sold, int bought, int earned, int spent) {
        static final Codec<Visit> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.fieldOf("game_time").forGetter(Visit::gameTime),
                Codec.INT.fieldOf("sold").forGetter(Visit::sold),
                Codec.INT.fieldOf("bought").forGetter(Visit::bought),
                Codec.INT.fieldOf("earned").forGetter(Visit::earned),
                Codec.INT.fieldOf("spent").forGetter(Visit::spent)
        ).apply(instance, Visit::new));
    }

    /** The caravan state of one village; {@code trader} is set while a caravan is there. */
    public record State(long nextVisit, Optional<UUID> trader, long arrivedAt, boolean traded, Optional<Visit> last) {
        static final Codec<State> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.fieldOf("next_visit").forGetter(State::nextVisit),
                UUIDUtil.CODEC.optionalFieldOf("trader").forGetter(State::trader),
                Codec.LONG.optionalFieldOf("arrived_at", 0L).forGetter(State::arrivedAt),
                Codec.BOOL.optionalFieldOf("traded", false).forGetter(State::traded),
                Visit.CODEC.optionalFieldOf("last").forGetter(State::last)
        ).apply(instance, State::new));

        State arrived(UUID traderId, long gameTime, long next) {
            return new State(next, Optional.of(traderId), gameTime, false, last);
        }

        State traded(Visit visit) {
            return new State(nextVisit, trader, arrivedAt, true, Optional.of(visit));
        }

        State left() {
            return new State(nextVisit, Optional.empty(), 0, false, last);
        }

        State postponed(long next) {
            return new State(next, trader, arrivedAt, traded, last);
        }
    }

    private static final Codec<CaravanStore> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("data_version", 1).forGetter(store -> DATA_VERSION),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, State.CODEC).optionalFieldOf("villages", Map.of())
                    .forGetter(store -> Map.copyOf(store.states))
    ).apply(instance, CaravanStore::new));

    public static final SavedDataType<CaravanStore> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(VillageLife.MOD_ID, "caravans"), CaravanStore::new, CODEC);

    private final Map<UUID, State> states = new HashMap<>();

    public CaravanStore() {}

    private CaravanStore(int loadedDataVersion, Map<UUID, State> loaded) {
        states.putAll(loaded);
        if (loadedDataVersion < DATA_VERSION) {
            setDirty();
        }
    }

    public static CaravanStore get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public Optional<State> get(UUID village) {
        return Optional.ofNullable(states.get(village));
    }

    public void put(UUID village, State state) {
        states.put(village, state);
        setDirty();
    }

    /** Forgets villages that no longer exist. */
    public void retain(java.util.Set<UUID> villages) {
        if (states.keySet().retainAll(villages)) {
            setDirty();
        }
    }
}
