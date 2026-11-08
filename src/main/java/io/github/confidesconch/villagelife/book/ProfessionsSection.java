package io.github.confidesconch.villagelife.book;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.github.confidesconch.villagelife.building.Building;
import io.github.confidesconch.villagelife.profession.JobAllocator.Workplace;
import io.github.confidesconch.villagelife.profession.JobService;
import io.github.confidesconch.villagelife.profession.Profession;
import io.github.confidesconch.villagelife.profession.Professions;
import io.github.confidesconch.villagelife.profession.work.HerdGoal;
import io.github.confidesconch.villagelife.resident.ResidentEntity;
import io.github.confidesconch.villagelife.resident.ResidentRecord;
import io.github.confidesconch.villagelife.resident.ResidentService;
import io.github.confidesconch.villagelife.village.Village;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Data of the "Professions" chapter (M13b): every profession of the data packs with what it needs
 * and delivers (for the guide pages), and this village's workplaces of it with their workers and
 * work problems.
 */
final class ProfessionsSection {
    private ProfessionsSection() {}

    static void write(ServerPlayer player, Village village, CompoundTag out) {
        ServerLevel level = player.level();
        Map<UUID, String> issues = new HashMap<>();
        for (ResidentEntity resident : ResidentService.loadedResidents(level, village)) {
            if (resident.brain().workIssue() != null) {
                issues.put(resident.getUUID(), resident.brain().workIssue());
            }
        }
        List<Workplace> workplaces = JobService.places(level, village); // M20b2: one entry per place for a worker
        ListTag list = new ListTag();
        for (Map.Entry<Identifier, Profession> entry : Professions.all()) {
            String id = entry.getKey().toString();
            CompoundTag p = describe(id, entry.getValue());
            ListTag places = new ListTag();
            Map<UUID, Integer> seen = new HashMap<>();
            for (Workplace workplace : workplaces) {
                if (workplace.profession().equals(id)) {
                    int nth = seen.merge(workplace.buildingId(), 1, Integer::sum) - 1;
                    places.add(workplace(village, workplace.buildingId(), id, entry.getValue(), issues, nth));
                }
            }
            p.put("workplaces", places);
            list.add(p);
        }
        out.put("list", list);
        out.putInt("unemployed", (int) village.residents().stream().filter(r -> r.job().isEmpty()).count());
    }

    private static CompoundTag describe(String id, Profession profession) {
        CompoundTag p = new CompoundTag();
        p.putString("id", id);
        p.put("workstations", ids(profession.workstations()));
        profession.tool().ifPresent(tool -> p.putString("tool", tool.toString()));
        p.put("materials", ids(profession.materials()));
        p.put("products", ids(profession.products()));
        p.put("nearby", ids(profession.nearby()));
        p.putBoolean("work_area", profession.workArea());
        p.putInt("radius", profession.workRadius());
        return p;
    }

    /** One place for a worker: the {@code nth} worker of the building works here (or nobody yet). */
    private static CompoundTag workplace(Village village, UUID buildingId, String profession, Profession data,
                                         Map<UUID, String> issues, int nth) {
        CompoundTag w = new CompoundTag();
        w.putString("building", buildingId.toString());
        village.buildings().stream().filter(b -> b.id().equals(buildingId)).findFirst()
                .map(Building::markerPos)
                .ifPresent(pos -> w.putString("pos", pos.toShortString()));
        int skip = nth;
        for (ResidentRecord r : village.residents()) {
            if (r.job().filter(job -> job.workplace().equals(buildingId) && job.profession().equals(profession)).isPresent()
                    && skip-- == 0) {
                w.putString("worker_id", r.id().toString());
                w.putString("worker_name", r.fullName());
                String issue = issues.get(r.id());
                if (issue != null) {
                    w.putString("issue", issue);
                }
                break;
            }
        }
        boolean pasture = village.buildings().stream().filter(b -> b.id().equals(buildingId)).findFirst()
                .flatMap(b -> HerdGoal.autoPasture(village, b, profession)).isPresent(); // M21d
        if (data.workArea() && village.workAreaOf(buildingId).isEmpty() && !pasture && !w.contains("issue")) {
            w.putString("issue", Profession.ISSUE_NO_WORK_AREA); // also shown while nobody works there (M13c)
        }
        return w;
    }

    private static ListTag ids(List<Identifier> ids) {
        ListTag list = new ListTag();
        ids.forEach(id -> list.add(StringTag.valueOf(id.toString())));
        return list;
    }
}
