package io.github.confidesconch.villagelife.book;

import java.util.List;

import io.github.confidesconch.villagelife.building.Building;
import io.github.confidesconch.villagelife.building.BuildingType;
import io.github.confidesconch.villagelife.defense.AlarmService;
import io.github.confidesconch.villagelife.defense.RaidService;
import io.github.confidesconch.villagelife.profession.JobService;
import io.github.confidesconch.villagelife.resident.ResidentEntity;
import io.github.confidesconch.villagelife.resident.ResidentService;
import io.github.confidesconch.villagelife.resident.housing.HousingService;
import io.github.confidesconch.villagelife.resident.needs.NeedsRules;
import io.github.confidesconch.villagelife.village.Village;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/** Data of the "Overview" chapter: the village at a glance. */
final class OverviewSection {
    private OverviewSection() {}

    static void write(ServerPlayer player, Village village, CompoundTag out) {
        var level = player.level();
        out.putString("owner", village.ownerName());
        out.putInt("center_x", village.center().getX());
        out.putInt("center_y", village.center().getY());
        out.putInt("center_z", village.center().getZ());
        out.putInt("radius", village.radius());
        out.putLong("founded_day", village.createdGameTime() / 24000L + 1);
        out.putLong("day", level.getGameTime() / 24000L + 1);

        out.putInt("residents", village.residents().size());
        int[] beds = HousingService.bedCounts(village);
        out.putInt("beds_free", beds[0]);
        out.putInt("beds_total", beds[1]);
        out.putInt("homeless", (int) village.residents().stream().filter(r -> r.bed().isEmpty()).count());
        int[] jobs = JobService.workplaceCounts(level, village);
        out.putInt("workplaces", jobs[0]);
        out.putInt("workplaces_taken", jobs[1]);
        out.putInt("unemployed", (int) village.residents().stream().filter(r -> r.job().isEmpty()).count());

        List<Building> buildings = village.buildings();
        out.putInt("houses", count(buildings, BuildingType.HOUSE));
        out.putInt("storages", count(buildings, BuildingType.STORAGE));
        out.putInt("workshops", count(buildings, BuildingType.WORKPLACE));
        out.putInt("invalid_buildings", (int) buildings.stream().filter(b -> !b.isValid()).count());

        List<ResidentEntity> loaded = ResidentService.loadedResidents(level, village);
        out.putInt("loaded", loaded.size());
        out.putInt("satisfaction", ResidentService.averageSatisfaction(level, village).orElse(-1));
        out.putInt("hungry", (int) loaded.stream().filter(r -> NeedsRules.wantsToEat(r.needs().food())).count());
        out.putBoolean("is_owner", village.owner().equals(player.getUUID()));
        out.putBoolean("can_manage", village.mayManage(player.getUUID()));
        out.putBoolean("under_attack", RaidService.isUnderAttack(village.id())); // M21b3
        out.putBoolean("manual_alarm", AlarmService.isManual(village.id()));
    }

    private static int count(List<Building> buildings, BuildingType type) {
        return (int) buildings.stream().filter(b -> b.type() == type && b.isValid()).count();
    }
}
