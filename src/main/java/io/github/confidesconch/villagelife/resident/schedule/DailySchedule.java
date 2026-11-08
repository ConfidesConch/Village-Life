package io.github.confidesconch.villagelife.resident.schedule;

import java.util.Comparator;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;


/**
 * A daily plan: from which hour on a resident does what (Minecraft-free logic, unit tested).
 * Loaded from {@code data/<namespace>/schedule/<name>.json}. The day wraps around: before the
 * first entry the last entry of the previous day is still active.
 */
public record DailySchedule(List<Entry> entries) {

    public static final int MINUTES_PER_DAY = 24 * 60;

    /** From {@code hour} (0–23, full hours) on, the resident does {@code activity}. */
    public record Entry(int hour, Activity activity) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.intRange(0, 23).fieldOf("from").forGetter(Entry::hour),
                Activity.CODEC.fieldOf("activity").forGetter(Entry::activity)
        ).apply(instance, Entry::new));
    }

    public static final Codec<DailySchedule> CODEC = Entry.CODEC.listOf().fieldOf("entries").codec()
            .comapFlatMap(DailySchedule::validate, DailySchedule::entries);

    /** Bedtime of the built-in plans: free time from the end of work until 21:00 (M19b). */
    public static final int BEDTIME_HOUR = 21;

    /** Used when no data pack file exists: work while it is day, free time in the evening, then sleep. */
    public static final DailySchedule DEFAULT = new DailySchedule(List.of(
            new Entry(6, Activity.WORK),
            new Entry(18, Activity.LEISURE),
            new Entry(BEDTIME_HOUR, Activity.SLEEP)));

    private static final java.util.Map<Integer, DailySchedule> WORK_AND_SLEEP = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Work from {@code start} to {@code end} o'clock (village working hours, M11c), free time until
     * {@link #BEDTIME_HOUR} (M19b), sleep otherwise.
     */
    public static DailySchedule workAndSleep(int start, int end) {
        return WORK_AND_SLEEP.computeIfAbsent(start * 100 + end, key -> new DailySchedule(end < BEDTIME_HOUR
                ? List.of(new Entry(start, Activity.WORK), new Entry(end, Activity.LEISURE), new Entry(BEDTIME_HOUR, Activity.SLEEP))
                : List.of(new Entry(start, Activity.WORK), new Entry(end, Activity.SLEEP))));
    }

    public DailySchedule {
        entries = entries.stream().sorted(Comparator.comparingInt(Entry::hour)).toList();
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("schedule needs at least one entry");
        }
    }

    private static DataResult<DailySchedule> validate(List<Entry> entries) {
        if (entries.isEmpty()) {
            return DataResult.error(() -> "schedule needs at least one entry");
        }
        long distinctHours = entries.stream().mapToInt(Entry::hour).distinct().count();
        if (distinctHours != entries.size()) {
            return DataResult.error(() -> "two schedule entries start at the same hour");
        }
        return DataResult.success(new DailySchedule(entries));
    }

    /** Activity at the given minute of the day (0 = midnight, 360 = 6:00). */
    public Activity activityAt(int minuteOfDay) {
        int minute = Math.floorMod(minuteOfDay, MINUTES_PER_DAY);
        Activity current = entries.getLast().activity(); // still running from yesterday
        for (Entry entry : entries) {
            if (entry.hour() * 60 > minute) {
                break;
            }
            current = entry.activity();
        }
        return current;
    }
}
