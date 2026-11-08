package io.github.confidesconch.villagelife.village;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.confidesconch.villagelife.building.Building;
import io.github.confidesconch.villagelife.core.util.LenientCodecs;
import io.github.confidesconch.villagelife.resident.ResidentRecord;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;

/**
 * One village: its center block, size, owner, registered buildings and residents. Immutable; changes create
 * a new instance that {@link VillageManager} stores. The radius is set when the village is founded and grows
 * with its stage (M16). {@code jobPriorities}: the owner's order of professions (M19a; empty = default).
 */
public record Village(
        UUID id,
        Optional<String> customName,
        BlockPos center,
        int radius,
        long createdGameTime,
        UUID owner,
        String ownerName,
        List<Building> buildings,
        List<ResidentRecord> residents,
        long lastImmigrationGameTime,
        VillageSettings settings,
        List<VillageZone> zones,
        List<UUID> managers,
        Map<UUID, Integer> workAreas,
        int stage,
        List<String> jobPriorities) {

    public static final Codec<Village> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(Village::id),
            Codec.STRING.optionalFieldOf("custom_name").forGetter(Village::customName),
            BlockPos.CODEC.fieldOf("center").forGetter(Village::center),
            Codec.INT.fieldOf("radius").forGetter(Village::radius),
            Codec.LONG.fieldOf("created_game_time").forGetter(Village::createdGameTime),
            UUIDUtil.CODEC.fieldOf("owner").forGetter(Village::owner),
            Codec.STRING.fieldOf("owner_name").forGetter(Village::ownerName),
            // optional: saves from data_version 1 (M2) have no buildings yet
            LenientCodecs.list(Building.CODEC, "building").optionalFieldOf("buildings", List.of()).forGetter(Village::buildings),
            // optional: saves before data_version 3 (M4) have no residents
            LenientCodecs.list(ResidentRecord.CODEC, "resident").optionalFieldOf("residents", List.of()).forGetter(Village::residents),
            Codec.LONG.optionalFieldOf("last_immigration_game_time", 0L).forGetter(Village::lastImmigrationGameTime),
            // optional: villages saved before M11c use the default settings
            VillageSettings.CODEC.optionalFieldOf("settings", VillageSettings.DEFAULT).forGetter(Village::settings),
            // optional: villages saved before M11e have no zones
            LenientCodecs.list(VillageZone.CODEC, "zone").optionalFieldOf("zones", List.of()).forGetter(Village::zones),
            // optional: villages saved before M11g2 have no managers
            UUIDUtil.CODEC.listOf().optionalFieldOf("managers", List.of()).forGetter(Village::managers),
            // optional: villages saved before M13c have no work areas (building id -> zone id)
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.INT).optionalFieldOf("work_areas", Map.of()).forGetter(Village::workAreas),
            // optional: villages saved before M16 are a settlement (stage 0)
            Codec.intRange(0, 2).optionalFieldOf("stage", 0).forGetter(Village::stage),
            // optional: villages saved before M19a use the default order (basic professions first)
            Codec.STRING.listOf().optionalFieldOf("job_priorities", List.of()).forGetter(Village::jobPriorities)
    ).apply(instance, Village::new));

    public Village {
        buildings = List.copyOf(buildings);
        residents = List.copyOf(residents);
        zones = List.copyOf(zones);
        managers = List.copyOf(managers);
        workAreas = Map.copyOf(workAreas);
        jobPriorities = List.copyOf(jobPriorities);
    }

    /** The zone assigned to building {@code buildingId} as its work area (M13c), if it still exists. */
    public Optional<VillageZone> workAreaOf(UUID buildingId) {
        Integer zoneId = workAreas.get(buildingId);
        return zoneId == null ? Optional.empty() : zones.stream().filter(z -> z.id() == zoneId).findFirst();
    }

    /** Assigns zone {@code zoneId} as work area of a building, or removes the assignment if empty (M13c). */
    public Village withWorkArea(UUID buildingId, Optional<Integer> zoneId) {
        Map<UUID, Integer> map = new HashMap<>();
        workAreas.forEach((building, zone) -> {
            if (buildings.stream().anyMatch(b -> b.id().equals(building))) { // forget removed buildings
                map.put(building, zone);
            }
        });
        zoneId.ifPresentOrElse(zone -> map.put(buildingId, zone), () -> map.remove(buildingId));
        return new Village(id, customName, center, radius, createdGameTime, owner, ownerName, buildings, residents,
                lastImmigrationGameTime, settings, zones, managers, map, stage, jobPriorities);
    }

    /** The village one stage higher (M16): settlement 0 → village 1 → town 2, with a new radius. */
    public Village withStage(int newStage, int newRadius) {
        return new Village(id, customName, center, newRadius, createdGameTime, owner, ownerName, buildings, residents,
                lastImmigrationGameTime, settings, zones, managers, workAreas, newStage, jobPriorities);
    }

    /** The order in which professions get workers (M19a): profession ids, most important first. */
    public Village withJobPriorities(List<String> priorities) {
        return new Village(id, customName, center, radius, createdGameTime, owner, ownerName, buildings, residents,
                lastImmigrationGameTime, settings, zones, managers, workAreas, stage, priorities);
    }

    /** Owner or one of his managers (M11g2). */
    public boolean mayManage(UUID player) {
        return VillageManagers.mayManage(owner, managers, player);
    }

    public Village withManagers(List<UUID> newManagers) {
        return new Village(id, customName, center, radius, createdGameTime, owner, ownerName, buildings, residents,
                lastImmigrationGameTime, settings, zones, newManagers, workAreas, stage, jobPriorities);
    }

    public VillageArea area() {
        return new VillageArea(center.getX(), center.getZ(), radius);
    }

    /** Custom name if set, otherwise "Village of &lt;owner&gt;" in the viewer's language. */
    public Component displayName() {
        return customName.<Component>map(Component::literal)
                .orElseGet(() -> Component.translatable("villagelife.village.default_name", ownerName));
    }

    public Optional<Building> buildingAt(BlockPos markerPos) {
        return buildings.stream().filter(b -> b.markerPos().equals(markerPos)).findFirst();
    }

    /** Adds the building or replaces the one with the same marker position. */
    public Village withBuilding(Building building) {
        List<Building> list = new ArrayList<>(withoutBuildingAt(building.markerPos()).buildings);
        list.add(building);
        return new Village(id, customName, center, radius, createdGameTime, owner, ownerName, list, residents, lastImmigrationGameTime, settings, zones, managers, workAreas, stage, jobPriorities);
    }

    public Optional<ResidentRecord> residentById(UUID residentId) {
        return residents.stream().filter(r -> r.id().equals(residentId)).findFirst();
    }

    public Village withResident(ResidentRecord resident) {
        List<ResidentRecord> list = new ArrayList<>(withoutResident(resident.id()).residents);
        list.add(resident);
        return new Village(id, customName, center, radius, createdGameTime, owner, ownerName, buildings, list, lastImmigrationGameTime, settings, zones, managers, workAreas, stage, jobPriorities);
    }

    public Village withoutResident(UUID residentId) {
        List<ResidentRecord> list = residents.stream().filter(r -> !r.id().equals(residentId)).toList();
        return new Village(id, customName, center, radius, createdGameTime, owner, ownerName, buildings, list, lastImmigrationGameTime, settings, zones, managers, workAreas, stage, jobPriorities);
    }

    /** Replaces the whole resident list (used by the housing update). */
    public Village withResidents(List<ResidentRecord> newResidents) {
        return new Village(id, customName, center, radius, createdGameTime, owner, ownerName, buildings, newResidents,
                lastImmigrationGameTime, settings, zones, managers, workAreas, stage, jobPriorities);
    }

    public Village withLastImmigration(long gameTime) {
        return new Village(id, customName, center, radius, createdGameTime, owner, ownerName, buildings, residents, gameTime, settings, zones, managers, workAreas, stage, jobPriorities);
    }

    public Village withoutBuildingAt(BlockPos markerPos) {
        List<Building> list = buildings.stream().filter(b -> !b.markerPos().equals(markerPos)).toList();
        return new Village(id, customName, center, radius, createdGameTime, owner, ownerName, list, residents, lastImmigrationGameTime, settings, zones, managers, workAreas, stage, jobPriorities);
    }

    public Village withSettings(VillageSettings newSettings) {
        return new Village(id, customName, center, radius, createdGameTime, owner, ownerName, buildings, residents,
                lastImmigrationGameTime, newSettings, zones, managers, workAreas, stage, jobPriorities);
    }

    public Village withZones(List<VillageZone> newZones) {
        return new Village(id, customName, center, radius, createdGameTime, owner, ownerName, buildings, residents,
                lastImmigrationGameTime, settings, newZones, managers, workAreas, stage, jobPriorities);
    }

    /** Sets the custom name; an empty name means the default "Village of …". */
    public Village withCustomName(String name) {
        Optional<String> newName = name.isBlank() ? Optional.empty() : Optional.of(name);
        return new Village(id, newName, center, radius, createdGameTime, owner, ownerName, buildings, residents,
                lastImmigrationGameTime, settings, zones, managers, workAreas, stage, jobPriorities);
    }
}
