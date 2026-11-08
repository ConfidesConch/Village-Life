package io.github.confidesconch.villagelife.profession;

import io.github.confidesconch.villagelife.core.util.ModProfiler;
import io.github.confidesconch.villagelife.profession.work.BreederGoal;
import io.github.confidesconch.villagelife.profession.work.CookGoal;
import io.github.confidesconch.villagelife.profession.work.CraftGoal;
import io.github.confidesconch.villagelife.profession.work.FarmerGoal;
import io.github.confidesconch.villagelife.profession.work.FishermanGoal;
import io.github.confidesconch.villagelife.profession.work.LumberjackGoal;
import io.github.confidesconch.villagelife.profession.work.InnkeeperGoal;
import io.github.confidesconch.villagelife.profession.work.MerchantGoal;
import io.github.confidesconch.villagelife.profession.work.TeacherGoal;
import io.github.confidesconch.villagelife.profession.work.MinerGoal;
import io.github.confidesconch.villagelife.profession.work.ShepherdGoal;
import io.github.confidesconch.villagelife.profession.work.SmithGoal;
import io.github.confidesconch.villagelife.profession.work.StorekeeperGoal;
import io.github.confidesconch.villagelife.resident.ai.ResidentHooks;
import io.github.confidesconch.villagelife.resident.schedule.Schedules;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Wires the profession system into the game: data loading, job assignment and work goals (M8). */
public final class ProfessionSystem {
    private ProfessionSystem() {}

    /** Work goals run at this priority: below sleeping (3), above wandering (5). */
    private static final int WORK_PRIORITY = 4;

    public static void init() {
        // M18d: the marker frame of a workplace shows its profession's symbol
        // same rule as JobService: workstations that are also storage containers (barrel) count last
        io.github.confidesconch.villagelife.building.BuildingService.setProfessionResolver(blocks -> blocks.stream()
                .sorted(java.util.Comparator.comparing((net.minecraft.world.level.block.Block block) -> block.defaultBlockState()
                        .is(io.github.confidesconch.villagelife.building.BuildingService.STORAGE_CONTAINERS)))
                .map(Professions::forWorkstation).flatMap(java.util.Optional::stream).map(Object::toString).findFirst());
        NeoForge.EVENT_BUS.addListener(Professions::onAddReloadListeners);
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class,
                event -> ModProfiler.time(ModProfiler.JOBS, () -> JobService.onServerTick(event)));
        ResidentHooks.addGoalProvider(resident ->
                resident.goalSelector.addGoal(WORK_PRIORITY, new LumberjackGoal(resident, 0.6)));
        ResidentHooks.addGoalProvider(resident ->
                resident.goalSelector.addGoal(WORK_PRIORITY, new FarmerGoal(resident, 0.6)));
        ResidentHooks.addGoalProvider(resident ->
                resident.goalSelector.addGoal(WORK_PRIORITY, new FishermanGoal(resident, 0.6)));
        ResidentHooks.addGoalProvider(resident ->
                resident.goalSelector.addGoal(WORK_PRIORITY, new ShepherdGoal(resident, 0.6)));
        ResidentHooks.addGoalProvider(resident ->
                resident.goalSelector.addGoal(WORK_PRIORITY, new BreederGoal(resident, 0.6)));
        ResidentHooks.addGoalProvider(resident ->
                resident.goalSelector.addGoal(WORK_PRIORITY, new MinerGoal(resident, 0.6)));
        ResidentHooks.addGoalProvider(resident ->
                resident.goalSelector.addGoal(WORK_PRIORITY, new CookGoal(resident, 0.6)));
        ResidentHooks.addGoalProvider(resident ->
                resident.goalSelector.addGoal(WORK_PRIORITY, new SmithGoal(resident, 0.6)));
        ResidentHooks.addGoalProvider(resident ->
                resident.goalSelector.addGoal(WORK_PRIORITY, new StorekeeperGoal(resident, 0.6)));
        ResidentHooks.addGoalProvider(resident ->
                resident.goalSelector.addGoal(WORK_PRIORITY, new MerchantGoal(resident, 0.6)));
        ResidentHooks.addGoalProvider(resident ->
                resident.goalSelector.addGoal(WORK_PRIORITY, new InnkeeperGoal(resident, 0.6))); // M17b
        ResidentHooks.addGoalProvider(resident ->
                resident.goalSelector.addGoal(WORK_PRIORITY, new TeacherGoal(resident, 0.6))); // M18c
        // every profession whose file has "crafts" (baker, carpenter …) shares one goal (M14a)
        ResidentHooks.addGoalProvider(resident ->
                resident.goalSelector.addGoal(WORK_PRIORITY, new CraftGoal(resident, 0.6)));
        ResidentHooks.setScheduleForJob(job -> Professions.get(job.profession())
                .map(Profession::schedule)
                .orElse(Schedules.DEFAULT_ID));
    }
}
