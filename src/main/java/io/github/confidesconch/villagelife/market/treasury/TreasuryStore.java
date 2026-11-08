package io.github.confidesconch.villagelife.market.treasury;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.confidesconch.villagelife.VillageLife;
import io.github.confidesconch.villagelife.core.util.LenientCodecs;
import io.github.confidesconch.villagelife.market.treasury.TreasuryHistory.DayCount;

import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * The treasury book-keeping of all villages (M15c), saved once in the overworld as
 * {@code data/villagelife/treasury.dat}: per village the last business events and the emeralds per day.
 * Only a record for the book – the emeralds themselves lie in the storage. Server thread only.
 *
 * <p>Data versions: 1 = treasury (M15c).
 */
public final class TreasuryStore extends SavedData {
    public static final int DATA_VERSION = 1;

    /** One business event: {@code kind} (e.g. caravan_sell), the good, how many, change of the village's emeralds. */
    public record Entry(long gameTime, String kind, String item, int count, int emeralds) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.fieldOf("game_time").forGetter(Entry::gameTime),
                Codec.STRING.fieldOf("kind").forGetter(Entry::kind),
                Codec.STRING.fieldOf("item").forGetter(Entry::item),
                Codec.INT.fieldOf("count").forGetter(Entry::count),
                Codec.INT.fieldOf("emeralds").forGetter(Entry::emeralds)
        ).apply(instance, Entry::new));
    }

    /** The book of one village. */
    public record Ledger(List<Entry> entries, List<DayCount> days) {
        static final Ledger EMPTY = new Ledger(List.of(), List.of());

        private static final Codec<DayCount> DAY_CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.fieldOf("day").forGetter(DayCount::day),
                Codec.INT.fieldOf("emeralds").forGetter(DayCount::emeralds)
        ).apply(instance, DayCount::new));

        static final Codec<Ledger> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                LenientCodecs.list(Entry.CODEC, "treasury entry").optionalFieldOf("entries", List.of()).forGetter(Ledger::entries),
                LenientCodecs.list(DAY_CODEC, "treasury day").optionalFieldOf("days", List.of()).forGetter(Ledger::days)
        ).apply(instance, Ledger::new));
    }

    private static final Codec<TreasuryStore> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("data_version", 1).forGetter(store -> DATA_VERSION),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, Ledger.CODEC).optionalFieldOf("villages", Map.of())
                    .forGetter(store -> Map.copyOf(store.ledgers))
    ).apply(instance, TreasuryStore::new));

    public static final SavedDataType<TreasuryStore> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(VillageLife.MOD_ID, "treasury"), TreasuryStore::new, CODEC);

    private final Map<UUID, Ledger> ledgers = new HashMap<>();

    public TreasuryStore() {}

    private TreasuryStore(int loadedDataVersion, Map<UUID, Ledger> loaded) {
        ledgers.putAll(loaded);
        if (loadedDataVersion < DATA_VERSION) {
            setDirty();
        }
    }

    public static TreasuryStore get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public Ledger of(UUID village) {
        return ledgers.getOrDefault(village, Ledger.EMPTY);
    }

    public void put(UUID village, Ledger ledger) {
        ledgers.put(village, ledger);
        setDirty();
    }

    /** Forgets villages that no longer exist. */
    public void retain(Set<UUID> villages) {
        if (ledgers.keySet().retainAll(villages)) {
            setDirty();
        }
    }
}
