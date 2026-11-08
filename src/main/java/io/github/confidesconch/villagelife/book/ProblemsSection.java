package io.github.confidesconch.villagelife.book;

import java.util.List;
import java.util.OptionalInt;

import io.github.confidesconch.villagelife.building.Building;
import io.github.confidesconch.villagelife.building.BuildingType;
import io.github.confidesconch.villagelife.inventory.StorageAccess;
import io.github.confidesconch.villagelife.resident.ResidentEntity;
import io.github.confidesconch.villagelife.resident.ResidentService;
import io.github.confidesconch.villagelife.resident.ai.EatGoal;
import io.github.confidesconch.villagelife.resident.housing.HousingService;
import io.github.confidesconch.villagelife.resident.needs.NeedsRules;
import io.github.confidesconch.villagelife.resident.schedule.LawRules;
import io.github.confidesconch.villagelife.village.EventLog;
import io.github.confidesconch.villagelife.village.Village;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;

/**
 * Data of the "Problems" chapter (M11b): what the village is missing, each with severity
 * ({@code error}, {@code warning}, {@code info}) and a translation key for the text and a tip.
 */
final class ProblemsSection {
    private ProblemsSection() {}

    static void write(ServerPlayer player, Village village, CompoundTag out) {
        ServerLevel level = player.level();
        ListTag list = new ListTag();
        List<ResidentEntity> loaded = ResidentService.loadedResidents(level, village);

        long starving = loaded.stream().filter(r -> NeedsRules.isStarving(r.needs().food())).count();
        if (starving > 0) {
            add(list, "error", "starving", starving);
        }
        long homeless = village.residents().stream().filter(r -> r.bed().isEmpty()).count();
        if (homeless > 0) {
            add(list, "warning", "homeless", homeless);
        }
        List<Building> storages = village.buildings().stream()
                .filter(b -> b.type() == BuildingType.STORAGE && b.isValid()).toList();
        if (storages.isEmpty()) {
            add(list, "warning", "no_storage");
        } else {
            checkStorage(level, storages, village.settings().foodReserve(), list);
        }
        long hungry = loaded.stream().filter(r -> NeedsRules.wantsToEat(r.needs().food())).count();
        if (hungry > 0) {
            add(list, "info", "hungry", hungry);
        }
        OptionalInt mood = ResidentService.averageSatisfaction(level, village);
        if (mood.isPresent() && NeedsRules.mood(mood.getAsInt()) == NeedsRules.Mood.UNHAPPY) {
            add(list, "warning", "unhappy", mood.getAsInt());
        }
        for (Building building : village.buildings()) {
            if (!building.isValid()) {
                add(list, "warning", "invalid_building", building.markerPos().toShortString());
            } else if (building.type() == BuildingType.WORKPLACE && building.containers().isEmpty()) {
                add(list, "info", "workplace_without_chest", building.markerPos().toShortString());
            }
        }
        for (ResidentEntity resident : loaded) {
            String issue = resident.brain().workIssue(); // e.g. a fisherman without water (M13a)
            Building workplace = resident.brain().workplace();
            if (issue != null && workplace != null) {
                add(list, "warning", "work." + issue, resident.getFullName(), workplace.markerPos().toShortString());
            }
        }
        if (HousingService.bedCounts(village)[1] == 0) {
            add(list, "info", "no_beds");
        }
        long unemployed = village.residents().stream().filter(r -> r.job().isEmpty()).count();
        if (unemployed > 0 && !village.residents().isEmpty()) {
            add(list, "info", "unemployed", unemployed);
        }
        // M21b6b: problems hidden by owner or manager stay away until the next day
        EventLog log = EventLog.get(level.getServer());
        long today = LawRules.day(level.getOverworldClockTime());
        ListTag shown = new ListTag();
        int hidden = 0;
        for (int i = 0; i < list.size(); i++) {
            CompoundTag problem = list.getCompoundOrEmpty(i);
            String pid = problemId(problem);
            problem.putString("pid", pid);
            if (log.isHidden(village.id(), pid, today)) {
                hidden++;
            } else {
                shown.add(problem);
            }
        }
        out.put("list", shown);
        out.putInt("hidden", hidden);
    }

    /** "key|arg|arg": the same problem gets the same id every time (e.g. "invalid_building|1, 64, 2"). */
    static String problemId(CompoundTag problem) {
        StringBuilder id = new StringBuilder(problem.getStringOr("key", ""));
        ListTag args = problem.getListOrEmpty("args");
        for (int i = 0; i < args.size(); i++) {
            id.append('|').append(args.getStringOr(i, ""));
        }
        return id.length() > 120 ? id.substring(0, 120) : id.toString();
    }

    /** Owner or manager hides a problem until tomorrow (M21b6b). */
    static void hide(ServerPlayer player, Village village, String problemId) {
        EventLog.get(player.level().getServer()).hideProblem(village.id(), problemId,
                LawRules.day(player.level().getOverworldClockTime()));
    }

    /** No food at all, or no free slot left in any loaded storage container. */
    private static void checkStorage(ServerLevel level, List<Building> storages, int reserve, ListTag list) {
        boolean anyLoaded = false;
        int food = 0;
        boolean hasSpace = false;
        for (Building storage : storages) {
            for (BlockPos pos : storage.containers()) {
                Container container = StorageAccess.containerAt(level, pos);
                if (container == null) {
                    continue;
                }
                anyLoaded = true;
                for (int slot = 0; slot < container.getContainerSize(); slot++) {
                    if (EatGoal.isFood(container.getItem(slot))) {
                        food += container.getItem(slot).getCount();
                    }
                }
                for (int slot = 0; slot < container.getContainerSize() && !hasSpace; slot++) {
                    hasSpace = container.getItem(slot).isEmpty();
                }
            }
        }
        if (anyLoaded && food == 0) {
            add(list, "warning", "no_food");
        } else if (anyLoaded && food < reserve) {
            add(list, "warning", "low_food", food, reserve); // owner's food reserve (M11c)
        }
        if (anyLoaded && !hasSpace) {
            add(list, "warning", "storage_full");
        }
    }

    private static void add(ListTag list, String severity, String key, Object... args) {
        CompoundTag problem = new CompoundTag();
        problem.putString("severity", severity);
        problem.putString("key", key);
        ListTag arguments = new ListTag();
        for (Object arg : args) {
            arguments.add(StringTag.valueOf(String.valueOf(arg)));
        }
        problem.put("args", arguments);
        list.add(problem);
    }
}
