package io.github.confidesconch.villagelife.convoy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.confidesconch.villagelife.VillageLife;
import io.github.confidesconch.villagelife.core.util.LenientCodecs;
import io.github.confidesconch.villagelife.market.contract.Shipment;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Convoy data of the server (M20c), saved once in the overworld as {@code data/villagelife/convoys.dat}:
 * how many escorts each village sends with each contract, the escorts away right now (with the saved
 * guards themselves while they are off the map) and the last raids of every village. Server thread only.
 *
 * <p>Data versions: 1 = escort settings, escort trips, raid history (M20c).
 */
public final class ConvoyStore extends SavedData {
    public static final int DATA_VERSION = 1;
    /** Raids remembered per village. */
    static final int HISTORY = 5;

    /**
     * Guards of {@code village} escorting one shipment. {@code members}: everybody of the trip that is not
     * back yet; {@code stashed}: those already off the map (saved entity data). Members not stashed yet walk
     * out of the village towards {@code exit} until {@code leaveUntil}.
     */
    public record Trip(UUID village, int contractId, boolean payment, long departedAt, long leaveUntil, long returnAt,
                       BlockPos exit, List<UUID> members, Map<UUID, CompoundTag> stashed) {
        static final Codec<Trip> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                UUIDUtil.CODEC.fieldOf("village").forGetter(Trip::village),
                Codec.INT.fieldOf("contract").forGetter(Trip::contractId),
                Codec.BOOL.fieldOf("payment").forGetter(Trip::payment),
                Codec.LONG.fieldOf("departed_at").forGetter(Trip::departedAt),
                Codec.LONG.fieldOf("leave_until").forGetter(Trip::leaveUntil),
                Codec.LONG.fieldOf("return_at").forGetter(Trip::returnAt),
                BlockPos.CODEC.fieldOf("exit").forGetter(Trip::exit),
                UUIDUtil.CODEC.listOf().fieldOf("members").forGetter(Trip::members),
                Codec.unboundedMap(UUIDUtil.STRING_CODEC, CompoundTag.CODEC).optionalFieldOf("stashed", Map.of())
                        .forGetter(Trip::stashed)
        ).apply(instance, Trip::new));

        public Trip {
            members = List.copyOf(members);
            stashed = Map.copyOf(stashed);
        }

        /** The trip goes with this shipment. */
        public boolean escorts(Shipment shipment) {
            return shipment.from().equals(village) && shipment.contractId() == contractId
                    && shipment.departedAt() == departedAt && shipment.payment() == payment;
        }

        Trip withStashed(UUID member, CompoundTag data) {
            Map<UUID, CompoundTag> map = new HashMap<>(stashed);
            map.put(member, data);
            return new Trip(village, contractId, payment, departedAt, leaveUntil, returnAt, exit, members, map);
        }

        /** The member is back home (or stays home): no longer part of the trip. */
        Trip without(UUID member) {
            List<UUID> rest = new ArrayList<>(members);
            rest.remove(member);
            Map<UUID, CompoundTag> map = new HashMap<>(stashed);
            map.remove(member);
            return new Trip(village, contractId, payment, departedAt, leaveUntil, returnAt, exit, rest, map);
        }
    }

    /** A raid on a convoy of or to the village: what was lost of how much, or fought off. */
    public record RaidRecord(long at, UUID partner, String item, int lost, int total, boolean defended) {
        static final Codec<RaidRecord> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.fieldOf("at").forGetter(RaidRecord::at),
                UUIDUtil.CODEC.fieldOf("partner").forGetter(RaidRecord::partner),
                Codec.STRING.fieldOf("item").forGetter(RaidRecord::item),
                Codec.INT.fieldOf("lost").forGetter(RaidRecord::lost),
                Codec.INT.fieldOf("total").forGetter(RaidRecord::total),
                Codec.BOOL.fieldOf("defended").forGetter(RaidRecord::defended)
        ).apply(instance, RaidRecord::new));
    }

    private static final Codec<ConvoyStore> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("data_version", 1).forGetter(store -> DATA_VERSION),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("escorts", Map.of()).forGetter(store -> Map.copyOf(store.escorts)),
            LenientCodecs.list(Trip.CODEC, "escort trip").optionalFieldOf("trips", List.of()).forGetter(store -> List.copyOf(store.trips)),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, LenientCodecs.list(RaidRecord.CODEC, "raid")).optionalFieldOf("history", Map.of())
                    .forGetter(store -> Map.copyOf(store.history))
    ).apply(instance, ConvoyStore::new));

    public static final SavedDataType<ConvoyStore> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(VillageLife.MOD_ID, "convoys"), ConvoyStore::new, CODEC);

    private final Map<String, Integer> escorts = new HashMap<>();
    private final List<Trip> trips = new ArrayList<>();
    private final Map<UUID, List<RaidRecord>> history = new HashMap<>();
    /** Everybody away with a convoy (members of all trips) – asked often by the residents' goals. */
    private final Set<UUID> away = new HashSet<>();

    public ConvoyStore() {}

    private ConvoyStore(int loadedDataVersion, Map<String, Integer> escorts, List<Trip> trips,
                        Map<UUID, List<RaidRecord>> history) {
        this.escorts.putAll(escorts);
        this.trips.addAll(trips);
        this.history.putAll(history);
        refreshAway();
        if (loadedDataVersion < DATA_VERSION) {
            setDirty();
        }
    }

    public static ConvoyStore get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    // ---- escort settings -----------------------------------------------------------------------

    private static String key(int contractId, UUID village) {
        return contractId + ":" + village;
    }

    /** Escorts {@code village} sends with its shipments of the contract (0–3). */
    public int escorts(int contractId, UUID village) {
        return escorts.getOrDefault(key(contractId, village), 0);
    }

    public void setEscorts(int contractId, UUID village, int count) {
        int clamped = Math.max(0, Math.min(ConvoyRules.MAX_ESCORTS, count));
        Integer old = clamped == 0 ? escorts.remove(key(contractId, village)) : escorts.put(key(contractId, village), clamped);
        if (old == null || old != clamped) {
            setDirty();
        }
    }

    /** Forgets the settings of contracts that no longer exist. */
    void keepEscortsOf(Set<Integer> contracts) {
        if (escorts.keySet().removeIf(k -> !contracts.contains(Integer.parseInt(k.substring(0, k.indexOf(':')))))) {
            setDirty();
        }
    }

    // ---- trips ---------------------------------------------------------------------------------

    public List<Trip> trips() {
        return List.copyOf(trips);
    }

    public boolean isAway(UUID resident) {
        return away.contains(resident);
    }

    void addTrip(Trip trip) {
        trips.add(trip);
        refreshAway();
        setDirty();
    }

    /** Replaces {@code old} by {@code replacement}; a trip without members is removed. */
    void replaceTrip(Trip old, Trip replacement) {
        int index = trips.indexOf(old);
        if (index < 0) {
            return;
        }
        if (replacement == null || replacement.members().isEmpty()) {
            trips.remove(index);
        } else {
            trips.set(index, replacement);
        }
        refreshAway();
        setDirty();
    }

    private void refreshAway() {
        away.clear();
        trips.forEach(trip -> away.addAll(trip.members()));
    }

    // ---- raid history --------------------------------------------------------------------------

    public List<RaidRecord> history(UUID village) {
        return history.getOrDefault(village, List.of());
    }

    void addRaid(UUID village, RaidRecord raid) {
        List<RaidRecord> list = new ArrayList<>(history.getOrDefault(village, List.of()));
        list.add(0, raid); // newest first
        history.put(village, List.copyOf(list.subList(0, Math.min(HISTORY, list.size()))));
        setDirty();
    }
}
