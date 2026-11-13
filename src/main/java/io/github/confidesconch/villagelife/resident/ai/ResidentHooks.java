package io.github.confidesconch.villagelife.resident.ai;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;

import io.github.confidesconch.villagelife.resident.Job;
import io.github.confidesconch.villagelife.resident.ResidentEntity;
import io.github.confidesconch.villagelife.resident.schedule.Schedules;

import net.minecraft.resources.Identifier;

/**
 * Lets higher systems (e.g. {@code profession}) plug into residents without the resident system
 * depending on them: extra goals, the choice of daily schedule and the defenders (M20a). Register during mod construction,
 * before any resident is created.
 */
public final class ResidentHooks {
    private ResidentHooks() {}

    private static final List<Consumer<ResidentEntity>> GOAL_PROVIDERS = new CopyOnWriteArrayList<>();
    private static volatile Function<Job, Identifier> scheduleForJob = job -> Schedules.DEFAULT_ID;
    private static volatile Predicate<String> defenderProfession = profession -> false;
    private static volatile Consumer<ResidentEntity> knockoutHandler = resident -> { };
    private static volatile Predicate<UUID> alarm = village -> false;
    private static volatile ToIntFunction<UUID> eventMood = village -> 0;
    private static volatile ToDoubleFunction<ResidentEntity> workBonus = resident -> 1.0;
    private static volatile Predicate<ResidentEntity> nightShift = resident -> false;
    private static volatile Predicate<ResidentEntity> escorting = resident -> false;

    /** M20c: which residents are away escorting a trade convoy. */
    public static void setEscortCheck(Predicate<ResidentEntity> check) {
        escorting = check;
    }

    public static boolean isEscorting(ResidentEntity resident) {
        return escorting.test(resident);
    }

    /** M20b2: which defenders keep watch at night. */
    public static void setNightShiftCheck(Predicate<ResidentEntity> check) {
        nightShift = check;
    }

    public static boolean isNightShift(ResidentEntity resident) {
        return nightShift.test(resident);
    }

    /** M20b: whether a village has an alarm (everybody but the defenders stays at home). */
    public static void setAlarmCheck(Predicate<UUID> villageHasAlarm) {
        alarm = villageHasAlarm;
    }

    /** M20b: extra mood of a village from events (e.g. a won defense). */
    public static void setEventMood(ToIntFunction<UUID> moodOfVillage) {
        eventMood = moodOfVillage;
    }

    /** M24: an extra work speed factor (the healer's morning blessing); 1.0 = none. */
    public static void setWorkBonus(ToDoubleFunction<ResidentEntity> bonus) {
        workBonus = bonus;
    }

    public static double workBonus(ResidentEntity resident) {
        return workBonus.applyAsDouble(resident);
    }

    public static boolean isAlarm(UUID village) {
        return alarm.test(village);
    }

    public static int eventMood(UUID village) {
        return eventMood.applyAsInt(village);
    }

    /** M20a: which professions defend the village (fight monsters, are only knocked out instead of dying). */
    public static void setDefenderCheck(Predicate<String> isDefenderProfession) {
        defenderProfession = isDefenderProfession;
    }

    /** M20a: what happens when a defender would die (it is knocked out instead). */
    public static void setKnockoutHandler(Consumer<ResidentEntity> handler) {
        knockoutHandler = handler;
    }

    /** True if the resident's job defends the village (guard, archer, captain). */
    public static boolean isDefender(ResidentEntity resident) {
        Job job = resident.brain().job();
        return job != null && defenderProfession.test(job.profession());
    }

    public static void knockOut(ResidentEntity resident) {
        knockoutHandler.accept(resident);
    }

    /** Adds goals to every new resident (called from {@code registerGoals}). */
    public static void addGoalProvider(Consumer<ResidentEntity> provider) {
        GOAL_PROVIDERS.add(provider);
    }

    /** Decides which daily schedule a resident with a job follows. */
    public static void setScheduleForJob(Function<Job, Identifier> resolver) {
        scheduleForJob = resolver;
    }

    public static void applyGoals(ResidentEntity resident) {
        GOAL_PROVIDERS.forEach(provider -> provider.accept(resident));
    }

    static Identifier scheduleFor(Job job) {
        return job == null ? Schedules.DEFAULT_ID : scheduleForJob.apply(job);
    }
}
