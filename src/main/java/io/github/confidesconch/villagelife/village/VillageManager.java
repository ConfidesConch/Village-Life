package io.github.confidesconch.villagelife.village;

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
import io.github.confidesconch.villagelife.building.Building;
import io.github.confidesconch.villagelife.core.util.LenientCodecs;
import io.github.confidesconch.villagelife.resident.ResidentRecord;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * All villages of one dimension. Saved with the world as {@code data/villagelife/villages.dat}
 * inside that dimension's folder. Only accessed on the server thread.
 *
 * <p>Data versions: 1 = villages (M2); 2 = + buildings (M3a); 3 = + residents (M4); 4 = + homes/beds and immigration time (M6); 5 = + jobs (M8); 6 = + settings (M11c); 7 = + zones (M11e); 8 = + managers (M11g2); 9 = + work areas (M13c); 10 = + stage (M16); 11 = + partner, parents, grown-up time per resident (M18); 12 = + education (M18c); 13 = + job priorities (M19a); 14 = + laws in the settings (M19b); 15 = + attacks setting (M20b). Older saves load unchanged.
 */
public final class VillageManager extends SavedData {
    /** Increase when the stored format changes, and add a migration. */
    public static final int DATA_VERSION = 15;

    private static final Codec<VillageManager> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("data_version", 1).forGetter(manager -> DATA_VERSION),
            LenientCodecs.list(Village.CODEC, "village").fieldOf("villages").forGetter(manager -> List.copyOf(manager.villages.values()))
    ).apply(instance, VillageManager::new));

    public static final SavedDataType<VillageManager> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(VillageLife.MOD_ID, "villages"), VillageManager::new, CODEC);

    private final Map<UUID, Village> villages = new LinkedHashMap<>();

    public VillageManager() {}

    private VillageManager(int loadedDataVersion, List<Village> loaded) {
        loaded.forEach(village -> villages.put(village.id(), village));
        if (loadedDataVersion < DATA_VERSION) {
            VillageLife.LOGGER.info("Upgrading village data from version {} to {}", loadedDataVersion, DATA_VERSION);
            setDirty();
        }
    }

    /** Village data of the given dimension, created empty on first use. */
    public static VillageManager get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public Collection<Village> all() {
        return Collections.unmodifiableCollection(villages.values());
    }

    /** The village whose area contains the given position, if any. */
    public Optional<Village> findAt(BlockPos pos) {
        return villages.values().stream()
                .filter(village -> village.area().contains(pos.getX(), pos.getZ()))
                .findFirst();
    }

    /** An existing village that would overlap a new village at {@code center} with {@code radius}. */
    public Optional<Village> findOverlapping(BlockPos center, int radius) {
        VillageArea candidate = new VillageArea(center.getX(), center.getZ(), radius);
        return villages.values().stream()
                .filter(village -> village.area().overlaps(candidate))
                .findFirst();
    }

    public Village create(BlockPos center, int radius, UUID owner, String ownerName, long gameTime) {
        Village village = new Village(UUID.randomUUID(), Optional.empty(), center.immutable(), radius, gameTime,
                owner, ownerName, List.of(), List.of(), gameTime, VillageSettings.DEFAULT, List.of(), List.of(), Map.of(), 0, List.of());
        villages.put(village.id(), village);
        setDirty();
        return village;
    }

    /** Removes the village whose center block is at {@code center}. */
    public Optional<Village> removeByCenter(BlockPos center) {
        Optional<Village> found = villages.values().stream()
                .filter(village -> village.center().equals(center))
                .findFirst();
        found.ifPresent(village -> {
            villages.remove(village.id());
            setDirty();
        });
        return found;
    }

    /** Stores the building in the given village (replacing one with the same marker). */
    public void putBuilding(Village village, Building building) {
        Village current = villages.get(village.id());
        if (current != null) {
            villages.put(current.id(), current.withBuilding(building));
            setDirty();
        }
    }

    public Optional<Village> byId(UUID villageId) {
        return Optional.ofNullable(villages.get(villageId));
    }

    public void addResident(Village village, ResidentRecord resident) {
        Village current = villages.get(village.id());
        if (current != null) {
            villages.put(current.id(), current.withResident(resident));
            setDirty();
        }
    }

    /** Replaces a village by an updated version of itself (same id). */
    public void update(Village updated) {
        if (villages.containsKey(updated.id()) && !villages.get(updated.id()).equals(updated)) {
            villages.put(updated.id(), updated);
            setDirty();
        }
    }

    public void removeResident(Village village, UUID residentId) {
        Village current = villages.get(village.id());
        if (current != null && current.residentById(residentId).isPresent()) {
            villages.put(current.id(), current.withoutResident(residentId));
            setDirty();
        }
    }

    /** A building together with the village it belongs to. */
    public record Located(Village village, Building building) {}

    public Optional<Located> findBuilding(BlockPos markerPos) {
        for (Village village : villages.values()) {
            Optional<Building> building = village.buildingAt(markerPos);
            if (building.isPresent()) {
                return Optional.of(new Located(village, building.get()));
            }
        }
        return Optional.empty();
    }

    /** All buildings of this dimension (snapshot). */
    public List<Located> allBuildings() {
        return villages.values().stream()
                .flatMap(village -> village.buildings().stream().map(b -> new Located(village, b)))
                .toList();
    }

    /** Removes the building registered by the marker at {@code markerPos}, in whichever village it is. */
    public Optional<Building> removeBuildingAt(BlockPos markerPos) {
        for (Village village : List.copyOf(villages.values())) {
            Optional<Building> building = village.buildingAt(markerPos);
            if (building.isPresent()) {
                villages.put(village.id(), village.withoutBuildingAt(markerPos));
                setDirty();
                return building;
            }
        }
        return Optional.empty();
    }
}
