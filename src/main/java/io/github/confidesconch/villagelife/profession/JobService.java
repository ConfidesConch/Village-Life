package io.github.confidesconch.villagelife.profession;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import io.github.confidesconch.villagelife.building.Building;
import io.github.confidesconch.villagelife.building.BuildingService;
import io.github.confidesconch.villagelife.building.BuildingType;
import io.github.confidesconch.villagelife.profession.JobAllocator.Workplace;
import io.github.confidesconch.villagelife.resident.Job;
import io.github.confidesconch.villagelife.resident.ResidentRecord;
import io.github.confidesconch.villagelife.resident.schedule.ScheduleClock;
import io.github.confidesconch.villagelife.village.NotificationTopic;
import io.github.confidesconch.villagelife.village.Village;
import io.github.confidesconch.villagelife.village.VillageManager;
import io.github.confidesconch.villagelife.village.VillageService;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Keeps jobs up to date (automatic job assignment, M8): every {@link #UPDATE_INTERVAL_TICKS} each
 * village finds its workplaces (valid workplace buildings whose workstation belongs to a profession)
 * and lets {@link JobAllocator} hand them to unemployed residents.
 */
public final class JobService {
    private JobService() {}

    /** 10 seconds, shifted by half an interval against the housing update. */
    static final int UPDATE_INTERVAL_TICKS = 200;
    private static final int OFFSET = UPDATE_INTERVAL_TICKS / 2;

    public static void onServerTick(ServerTickEvent.Post event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            long now = level.getGameTime();
            VillageManager manager = VillageManager.get(level);
            for (Village village : List.copyOf(manager.all())) {
                if (Math.floorMod(now + OFFSET + village.id().hashCode(), UPDATE_INTERVAL_TICKS) == 0) {
                    Village assigned = assignJobs(level, village);
                    manager.update(isMorning(level, assigned) ? rebalance(level, assigned) : assigned);
                }
            }
        }
    }

    // ---- priorities (M19a) ---------------------------------------------------------------------

    /** The day (counted from midnight) of each village's last morning moves; not saved, a restart may repeat one. */
    private static final Map<UUID, Long> LAST_MORNING = new HashMap<>();

    /** Forgets everything kept in memory for the stopped server (bug review 08.11.2026). */
    public static void clear() {
        LAST_MORNING.clear();
    }
    /** Morning moves happen in the two hours before work starts. */
    private static final int MORNING_MINUTES = 120;

    /** All professions in their default order: basic professions first, then by their book order. */
    public static List<String> defaultOrder() {
        List<String> essential = new ArrayList<>();
        List<String> others = new ArrayList<>();
        for (Map.Entry<net.minecraft.resources.Identifier, Profession> entry : Professions.all()) {
            (entry.getValue().essential() ? essential : others).add(entry.getKey().toString());
        }
        essential.addAll(others);
        return essential;
    }

    /** The village's full job priority list, most important first. */
    public static List<String> ranking(Village village) {
        return JobPriorities.ranking(village.jobPriorities(), defaultOrder());
    }

    private static boolean isMorning(ServerLevel level, Village village) {
        if (!village.settings().autoJobs()) {
            return false;
        }
        long clock = level.getOverworldClockTime();
        int minute = ScheduleClock.minuteOfDay(clock);
        int start = village.settings().workStart() * 60;
        if (minute < start - MORNING_MINUTES || minute >= start) {
            return false;
        }
        long day = Math.floorDiv(clock + 6000, (long) ScheduleClock.TICKS_PER_DAY); // 6000 ticks = 6 hours: days start at midnight
        Long last = LAST_MORNING.put(village.id(), day);
        return last == null || last != day;
    }

    /** Each morning workers of lower professions move to free workplaces of higher ones (owner's choice "2a"). */
    static Village rebalance(ServerLevel level, Village village) {
        Map<UUID, Workplace> current = new HashMap<>();
        for (ResidentRecord r : village.residents()) {
            r.job().ifPresent(job -> current.put(r.id(), new Workplace(job.workplace(), job.profession())));
        }
        Map<UUID, Workplace> moves = JobPriorities.rebalance(places(level, village), current, ranking(village));
        Village updated = village;
        for (Map.Entry<UUID, Workplace> move : moves.entrySet()) {
            Optional<ResidentRecord> record = updated.residentById(move.getKey());
            if (record.isEmpty()) {
                continue;
            }
            String from = record.get().job().map(Job::translationKey).orElse("");
            Workplace to = move.getValue();
            updated = updated.withResident(record.get().withJob(Optional.of(new Job(to.buildingId(), to.profession()))));
            VillageService.tellManagers(level.getServer(), updated, NotificationTopic.JOBS,
                    Component.translatable("villagelife.jobs.moved", record.get().fullName(), Component.translatable(from),
                            Component.translatable(Job.translationKey(to.profession()))).withStyle(ChatFormatting.AQUA));
        }
        return updated;
    }

    /** Number of workplaces and how many of them are taken (for info displays). */
    public static int[] workplaceCounts(ServerLevel level, Village village) {
        List<Workplace> workplaces = places(level, village);
        long taken = village.residents().stream().filter(r -> r.job().isPresent()).count();
        return new int[]{workplaces.size(), (int) Math.min(taken, workplaces.size())};
    }

    static Village assignJobs(ServerLevel level, Village village) {
        Map<UUID, Workplace> current = new HashMap<>();
        for (ResidentRecord r : village.residents()) {
            r.job().ifPresent(job -> current.put(r.id(), new Workplace(job.workplace(), job.profession())));
        }
        // with automatic job assignment switched off (M11c), only residents who have a job keep it
        long now = level.getGameTime();
        List<UUID> order = village.residents().stream()
                .filter(r -> !r.isChild(now)) // M18b: children do not work
                .filter(r -> village.settings().autoJobs() || r.job().isPresent())
                .map(ResidentRecord::id).toList();
        Map<UUID, java.util.Set<String>> preferred = new HashMap<>(); // M18b: grown-up children like their parents' jobs
        for (ResidentRecord r : village.residents()) {
            java.util.Set<String> jobs = new java.util.HashSet<>();
            r.parents().forEach(parent -> village.residentById(parent).flatMap(ResidentRecord::job)
                    .ifPresent(job -> jobs.add(job.profession())));
            if (!jobs.isEmpty()) {
                preferred.put(r.id(), jobs);
            }
        }
        // M19a: free workplaces of higher-ranked professions are filled first
        JobAllocator.Result result = JobAllocator.allocate(JobPriorities.sort(places(level, village), ranking(village)),
                order, current, preferred);

        List<ResidentRecord> updated = new ArrayList<>();
        for (ResidentRecord r : village.residents()) {
            Optional<Job> job = result.workplaceOf(r.id()).map(w -> new Job(w.buildingId(), w.profession()));
            updated.add(r.withJob(job));
        }
        return village.withResidents(updated);
    }

    /**
     * How many residents can work in this building (M20b2): by floor size, 1–4; for defenders (guardhouse)
     * one per bed in the building instead (owner's wish), at least one.
     */
    public static int capacity(Building building, String profession) {
        if (Professions.get(profession).map(Profession::defender).orElse(false)) {
            return Math.max(1, building.beds().size());
        }
        return WorkplaceCapacity.workers(building.interiorVolume(), building.max().getY() - building.min().getY() + 1);
    }

    /** Every place for a worker: each workplace once per {@link #capacity} (M20b2). */
    public static List<Workplace> places(ServerLevel level, Village village) {
        List<Workplace> result = new ArrayList<>();
        for (Workplace workplace : workplaces(level, village)) {
            int places = village.buildings().stream().filter(b -> b.id().equals(workplace.buildingId()))
                    .findFirst().map(b -> capacity(b, workplace.profession())).orElse(1);
            for (int i = 0; i < places; i++) {
                result.add(workplace);
            }
        }
        return result;
    }

    /**
     * Valid workplace buildings with a known profession. If a building's workstations are not
     * loaded, its profession cannot be checked: then the profession of its current worker is kept.
     */
    public static List<Workplace> workplaces(ServerLevel level, Village village) {
        List<Workplace> result = new ArrayList<>();
        for (Building building : village.buildings()) {
            if (building.type() != BuildingType.WORKPLACE || !building.isValid()) {
                continue;
            }
            boolean loaded = building.workstations().stream().anyMatch(level::isLoaded);
            Optional<String> profession = loaded ? professionOf(level, building) : currentProfession(village, building);
            profession.ifPresent(p -> result.add(new Workplace(building.id(), p)));
        }
        return result;
    }

    /**
     * The profession of the first workstation that belongs to one. Workstations that are also
     * containers (the fisherman's barrel, M13a) only count if there is no other one, so a
     * lumberjack's hut with a barrel for storage stays a lumberjack's hut.
     */
    private static Optional<String> professionOf(ServerLevel level, Building building) {
        List<Block> blocks = new ArrayList<>();
        for (BlockPos pos : building.workstations()) {
            if (level.isLoaded(pos)) {
                blocks.add(level.getBlockState(pos).getBlock());
            }
        }
        return Professions.forWorkstations(blocks, JobService::isContainer).map(Object::toString);
    }

    private static boolean isContainer(Block block) {
        return block.defaultBlockState().is(BuildingService.STORAGE_CONTAINERS);
    }

    private static Optional<String> currentProfession(Village village, Building building) {
        return village.residents().stream()
                .flatMap(r -> r.job().stream())
                .filter(job -> job.workplace().equals(building.id()))
                .map(Job::profession)
                .findFirst();
    }
}
