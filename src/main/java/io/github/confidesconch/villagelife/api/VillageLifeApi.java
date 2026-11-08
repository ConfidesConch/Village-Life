package io.github.confidesconch.villagelife.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.github.confidesconch.villagelife.building.Building;
import io.github.confidesconch.villagelife.resident.ResidentRecord;
import io.github.confidesconch.villagelife.village.Village;
import io.github.confidesconch.villagelife.village.VillageManager;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/**
 * Read-only access to Village Life for addon mods (M22a, owner's choice "small"). Server side only, on the
 * server thread. Everything returned is a snapshot: changing it changes nothing in the game, and it does
 * not update itself – look it up again when needed. Changes are announced by the events in
 * {@code io.github.confidesconch.villagelife.api.event}.
 */
public final class VillageLifeApi {
    private VillageLifeApi() {}

    /** A village at the moment of the call. {@code stage}: 0 settlement, 1 village, 2 town. */
    public record VillageInfo(UUID id, Component name, BlockPos center, int radius, UUID owner, List<UUID> managers,
                              int stage, List<ResidentInfo> residents, List<BuildingInfo> buildings) {
        /** True if the position lies inside the village border (a circle of {@code radius} around the center). */
        public boolean contains(BlockPos pos) {
            long dx = (long) pos.getX() - center.getX();
            long dz = (long) pos.getZ() - center.getZ();
            return dx * dx + dz * dz <= (long) radius * radius;
        }
    }

    /**
     * A resident. {@code profession}: e.g. {@code "villagelife:farmer"} while it has a job; {@code workplace}
     * and {@code home}: building ids.
     */
    public record ResidentInfo(UUID id, String firstName, String lastName, Optional<String> profession,
                               Optional<UUID> workplace, Optional<UUID> home, Optional<UUID> partner) {}

    /**
     * A registered building. {@code type}: {@code house}, {@code storage}, {@code workplace}, {@code field} or
     * {@code pasture}; {@code size}: interior volume (rooms) or area in blocks (fields, pastures).
     */
    public record BuildingInfo(UUID id, String type, BlockPos marker, BlockPos min, BlockPos max, boolean valid, int size,
                               List<BlockPos> beds, List<BlockPos> containers, List<BlockPos> workstations) {}

    /** The village whose border contains {@code pos}, if any. */
    public static Optional<VillageInfo> villageAt(ServerLevel level, BlockPos pos) {
        return VillageManager.get(level).findAt(pos).map(VillageLifeApi::info);
    }

    /** The village with this id in this dimension, if it exists. */
    public static Optional<VillageInfo> village(ServerLevel level, UUID villageId) {
        return VillageManager.get(level).byId(villageId).map(VillageLifeApi::info);
    }

    /** All villages of this dimension. */
    public static List<VillageInfo> villages(ServerLevel level) {
        return VillageManager.get(level).all().stream().map(VillageLifeApi::info).toList();
    }

    /** The village a resident entity belongs to (by its id), if any. */
    public static Optional<VillageInfo> villageOfResident(ServerLevel level, UUID residentId) {
        return VillageManager.get(level).all().stream()
                .filter(v -> v.residentById(residentId).isPresent()).findFirst().map(VillageLifeApi::info);
    }

    // ---- snapshots (also used to fill the events) ----------------------------------------------

    static VillageInfo info(Village village) {
        return new VillageInfo(village.id(), village.displayName(), village.center(), village.radius(), village.owner(),
                List.copyOf(village.managers()), village.stage(),
                village.residents().stream().map(VillageLifeApi::info).toList(),
                village.buildings().stream().map(VillageLifeApi::info).toList());
    }

    static ResidentInfo info(ResidentRecord r) {
        return new ResidentInfo(r.id(), r.firstName(), r.lastName(), r.job().map(j -> j.profession()),
                r.job().map(j -> j.workplace()), r.home(), r.partner());
    }

    /** Public so the mod's own services can fill building events. */
    public static BuildingInfo info(Building b) {
        return new BuildingInfo(b.id(), b.type().getSerializedName(), b.markerPos(), b.min(), b.max(), b.isValid(),
                b.interiorVolume(), List.copyOf(b.beds()), List.copyOf(b.containers()), List.copyOf(b.workstations()));
    }
}
