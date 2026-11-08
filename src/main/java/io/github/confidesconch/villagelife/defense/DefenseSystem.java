package io.github.confidesconch.villagelife.defense;

import io.github.confidesconch.villagelife.core.util.ModProfiler;
import io.github.confidesconch.villagelife.building.Building;
import io.github.confidesconch.villagelife.profession.Profession;
import io.github.confidesconch.villagelife.profession.Professions;
import io.github.confidesconch.villagelife.profession.work.GuardGoal;
import io.github.confidesconch.villagelife.resident.ResidentEntity;
import io.github.confidesconch.villagelife.resident.ai.ResidentHooks;
import io.github.confidesconch.villagelife.resident.schedule.ScheduleClock;
import io.github.confidesconch.villagelife.resident.schedule.ShiftRules;
import io.github.confidesconch.villagelife.village.NotificationTopic;
import io.github.confidesconch.villagelife.village.Village;
import io.github.confidesconch.villagelife.village.VillageService;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Wires up village defense (M20a): guard, archer and captain (profession files with {@code "defender": true}),
 * their work (equipping and patrolling, {@link GuardGoal}) and fighting ({@link DefendGoal}), and
 * knocked-out instead of dead (owner's choice "3a"); bandit attacks and the alarm (M20b).
 */
public final class DefenseSystem {
    private DefenseSystem() {}

    /** Fighting comes before everything else except swimming (0). */
    private static final int DEFEND_PRIORITY = 1;
    private static final int WORK_PRIORITY = 4;

    public static void init() {
        ResidentHooks.setDefenderCheck(profession -> Professions.get(profession).map(Profession::defender).orElse(false));
        ResidentHooks.setKnockoutHandler(DefenseSystem::knockOut);
        // M20b: bandit attacks, alarm, mood after a won defense
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, event -> ModProfiler.time(ModProfiler.VILLAGE_LIFE, () -> {
            AlarmService.setGameTime(event.getServer().overworld().getGameTime());
            AlarmService.tick();
            RaidService.onServerTick(event);
        }));
        NeoForge.EVENT_BUS.addListener(RaidService::onDrops);
        NeoForge.EVENT_BUS.addListener(AlarmService::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(VillageProtection::onMobGriefing); // M20b2: monsters do not damage villages
        NeoForge.EVENT_BUS.addListener(VillageProtection::onDetonate);
        NeoForge.EVENT_BUS.addListener(VillageProtection::onSpawnCheck); // no monsters spawn inside villages
        ResidentHooks.setAlarmCheck(AlarmService::isAlarm);
        ResidentHooks.setNightShiftCheck(DefenseSystem::isNightShift); // M20b2
        ResidentHooks.setEventMood(village -> RaidService.eventMood(village, AlarmService.gameTime()));
        ResidentHooks.addGoalProvider(resident -> resident.goalSelector.addGoal(DEFEND_PRIORITY, new DefendGoal(resident, 0.75)));
        ResidentHooks.addGoalProvider(resident ->
                resident.goalSelector.addGoal(WORK_PRIORITY, GuardGoal.melee(resident, 0.6, DefenseRules.GUARD)));
        ResidentHooks.addGoalProvider(resident ->
                resident.goalSelector.addGoal(WORK_PRIORITY, GuardGoal.melee(resident, 0.6, DefenseRules.CAPTAIN)));
        ResidentHooks.addGoalProvider(resident ->
                resident.goalSelector.addGoal(WORK_PRIORITY, GuardGoal.archer(resident, 0.6, DefenseRules.ARCHER)));
    }

    /** Every second defender of the village (by the order of the residents) keeps the night watch. */
    private static boolean isNightShift(ResidentEntity resident) {
        Village village = resident.brain().village();
        if (village == null || !ResidentHooks.isDefender(resident)) {
            return false;
        }
        int index = 0;
        for (var record : village.residents()) {
            if (record.id().equals(resident.getUUID())) {
                return ShiftRules.isNightShift(index);
            }
            if (record.job().map(job -> Professions.get(job.profession()).map(Profession::defender).orElse(false)).orElse(false)) {
                index++;
            }
        }
        return false;
    }

    /**
     * A defender would have died: it is carried back to its guardhouse with 1 health point and rests there
     * until the next start of work, then it is fully healed (in {@code ResidentEntity}).
     */
    private static void knockOut(ResidentEntity resident) {
        if (!(resident.level() instanceof ServerLevel level)) {
            return;
        }
        Village village = resident.brain().village();
        int workStart = village == null ? 6 : village.settings().workStart();
        resident.knockOutUntil(DefenseRules.backOnDuty(level.getGameTime(),
                ScheduleClock.minuteOfDay(level.getOverworldClockTime()), workStart));
        resident.setTarget(null);
        resident.getNavigation().stop();
        // whoever fought it (bandits, monsters) looks for somebody else
        level.getEntitiesOfClass(Mob.class, resident.getBoundingBox().inflate(32), mob -> mob.getTarget() == resident)
                .forEach(mob -> mob.setTarget(null));
        level.sendParticles(ParticleTypes.POOF, resident.getX(), resident.getY() + 1.0, resident.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
        Building guardhouse = resident.brain().workplace();
        if (guardhouse != null && !ResidentHooks.isEscorting(resident)) { // M20c: escorts lie on the road
            BlockPos center = guardhouse.center();
            resident.teleportTo(center.getX() + 0.5, center.getY(), center.getZ() + 0.5);
        }
        if (village != null) {
            VillageService.tellManagers(level.getServer(), village, NotificationTopic.DEFENSE,
                    Component.translatable("villagelife.defense.knocked_out", resident.getFullName())
                            .withStyle(ChatFormatting.GOLD));
        }
    }
}
