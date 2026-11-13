package io.github.confidesconch.villagelife.resident.ai;

import java.util.Objects;

import io.github.confidesconch.villagelife.building.Building;
import io.github.confidesconch.villagelife.core.config.SyncedConfig;
import io.github.confidesconch.villagelife.resident.EducationRules;
import io.github.confidesconch.villagelife.resident.Job;
import io.github.confidesconch.villagelife.resident.ResidentEntity;
import io.github.confidesconch.villagelife.resident.ResidentRecord;
import io.github.confidesconch.villagelife.resident.schedule.Activity;
import io.github.confidesconch.villagelife.resident.schedule.DailySchedule;
import io.github.confidesconch.villagelife.resident.schedule.LawRules;
import io.github.confidesconch.villagelife.resident.schedule.ShiftRules;
import io.github.confidesconch.villagelife.resident.schedule.ScheduleClock;
import io.github.confidesconch.villagelife.resident.schedule.Schedules;
import io.github.confidesconch.villagelife.village.LawSet;
import io.github.confidesconch.villagelife.village.Village;
import io.github.confidesconch.villagelife.village.VillageArea;
import io.github.confidesconch.villagelife.village.VillageManager;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;

/**
 * The resident's "thinking" layer above Minecraft goals: holds the current {@link Activity} (from the
 * daily schedule; the same clock for everybody) and the village data the goals need.
 * Re-plans only every {@code aiThinkIntervalTicks} (staggered per resident).
 * Not saved: after loading, the resident simply plans again.
 */
public final class ResidentBrain {
    /** Children go to bed at 20:00 (M18b). */
    private static final int CHILD_BEDTIME_MINUTE = 20 * 60;
    private Activity activity = Activity.LEISURE;
    private VillageArea area;
    private BlockPos villageCenter;
    private BlockPos bedPos;
    private Job job;
    private Building workplace;
    private Village village;
    private String workIssue;
    private long lastThinkGameTime = -1;
    private boolean child;
    private boolean schoolTime;
    private int education;
    private int lawMood;

    public Activity activity() {
        return activity;
    }

    /** Area of the resident's village, or null if it has none (yet). */
    public VillageArea area() {
        return area;
    }

    public BlockPos villageCenter() {
        return villageCenter;
    }

    /** Head position of the resident's bed, or null while homeless. */
    public BlockPos bedPos() {
        return bedPos;
    }

    /** The resident's job, or null while unemployed. */
    public Job job() {
        return job;
    }

    /** The building the resident works in (valid workplace), or null. */
    public Building workplace() {
        return workplace;
    }

    /** The resident's village as of the last think, or null. */
    public Village village() {
        return village;
    }

    /**
     * Why the resident cannot do its work right now, e.g. {@code fisherman.no_water} (shown as
     * {@code villagelife.problem.work.<issue>} in the book, M13a), or null. Set by the work goals.
     */
    public String workIssue() {
        return workIssue;
    }

    public void setWorkIssue(String issue) {
        workIssue = issue;
    }

    /**
     * True if the resident should walk around the village now: during work (until professions
     * exist) and free time, and in the morning if it has no home to stay at.
     */
    public boolean wantsToWander() {
        return activity == Activity.WORK || activity == Activity.LEISURE
                || (activity == Activity.HOME && bedPos == null);
    }

    /**
     * The resident's daily plan: its profession's schedule, but the village's own working hours
     * (M11c) replace the default plan when the owner changed them.
     */
    private DailySchedule scheduleFor(Village village) {
        Identifier id = ResidentHooks.scheduleFor(job);
        if (village != null && id.equals(Schedules.DEFAULT_ID) && !village.settings().usesDefaultHours()) {
            return DailySchedule.workAndSleep(village.settings().workStart(), village.settings().workEnd());
        }
        return Schedules.get(id);
    }

    /**
     * Called every server tick; does real work only when it is this resident's turn. Returns true if the
     * resident re-planned (village, job, workplace and activity may have changed).
     */
    public boolean tick(ResidentEntity resident, ServerLevel level) {
        int interval = SyncedConfig.AI_THINK_INTERVAL_TICKS.getAsInt();
        if (!ThinkTimer.shouldThink(level.getGameTime(), resident.getId(), interval)) {
            return false;
        }
        long now = level.getGameTime();
        long elapsed = lastThinkGameTime < 0 ? 0 : now - lastThinkGameTime;
        lastThinkGameTime = now;
        resident.needs().update(elapsed, SyncedConfig.FOOD_PER_DAY.getAsInt(), resident.isSleeping(),
                level.getOverworldClockTime(), now);
        VillageManager manager = VillageManager.get(level);
        village = resident.getVillageId().flatMap(manager::byId).orElse(null);
        area = village == null ? null : village.area();
        if (village != null) {
            resident.fitPathRangeTo(village.radius());
        }
        villageCenter = village == null ? null : village.center();
        ResidentRecord record = village == null ? null : village.residentById(resident.getUUID()).orElse(null);
        bedPos = record == null ? null : record.bed().orElse(null);
        Job previousJob = job;
        job = record == null ? null : record.job().orElse(null);
        workplace = job == null ? null : village.buildings().stream()
                .filter(b -> b.id().equals(job.workplace()) && b.isValid()).findFirst().orElse(null);
        if (workplace == null || !Objects.equals(previousJob, job)) {
            workIssue = null;
        }
        if (workplace != null && !workplace.beds().isEmpty()) { // M20b2: beds in a workplace (guardhouse) are its workers'
            int index = 0;
            for (ResidentRecord other : village.residents()) {
                if (other.id().equals(resident.getUUID())) {
                    break;
                }
                if (other.job().filter(j -> j.workplace().equals(job.workplace())).isPresent()) {
                    index++;
                }
            }
            if (index < workplace.beds().size()) {
                bedPos = workplace.beds().get(index);
            }
        }
        long clock = level.getOverworldClockTime();
        int minute = ScheduleClock.minuteOfDay(clock);
        activity = scheduleFor(village).activityAt(minute);
        lawMood = 0;
        if (village != null) { // M19b: rest day, long evenings, curfew
            long day = LawRules.day(clock);
            LawSet laws = village.settings().laws().effective(day);
            boolean restDay = LawRules.isRestDay(laws, day);
            activity = LawRules.adjust(activity, minute, laws, village.settings().workEnd(), restDay);
            lawMood = LawRules.mood(laws, restDay) + ResidentHooks.eventMood(village.id()); // M20b: e.g. a won defense
            if (ResidentHooks.isAlarm(village.id()) && activity != Activity.SLEEP && !ResidentHooks.isDefender(resident)) {
                activity = Activity.HOME; // M20b: alarm – everybody but the defenders goes home
            }
            if (ResidentHooks.isNightShift(resident)) { // M20b2: defenders take turns, day and night
                activity = ShiftRules.nightShift(minute, village.settings().workStart(), village.settings().workEnd());
            }
        }
        if (resident.isKnockedOut()) {
            activity = Activity.SLEEP; // M20b2: knocked-out defenders rest in their bed (in the guardhouse)
        }
        child = record != null && record.isChild(now);
        education = record == null ? 0 : record.education();
        schoolTime = EducationRules.isSchoolTime(child, activity == Activity.WORK, minute); // M18c
        if (child) { // M18b: children play instead of working and go to bed at 20:00
            if (activity == Activity.WORK) {
                activity = Activity.LEISURE;
            } else if ((activity == Activity.LEISURE || activity == Activity.HOME) && minute >= CHILD_BEDTIME_MINUTE) {
                activity = Activity.SLEEP;
            }
        }
        resident.setChildSize(child);
        return true;
    }

    /** True while a child should be in school (M18c): mornings of the work time. */
    public boolean isSchoolTime() {
        return schoolTime;
    }

    /** Education 0–100 from school (M18c). */
    public int education() {
        return education;
    }

    /** Mood from the village laws (M19b), part of the satisfaction. */
    public int lawMood() {
        return lawMood;
    }

    /** True while the resident is a child (M18b). */
    public boolean isChild() {
        return child;
    }
}
