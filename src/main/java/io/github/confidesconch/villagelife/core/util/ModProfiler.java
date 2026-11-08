package io.github.confidesconch.villagelife.core.util;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Measures how much server time each system of the mod needs (M12a), averaged over the last 100
 * ticks (5 seconds). Systems report with {@link #add} or {@link #time}; {@link #endTick} must run once
 * at the very end of every server tick. Shown by {@code /villagelife debug perf}. Server thread only;
 * free of Minecraft classes (unit tested).
 */
public final class ModProfiler {
    private ModProfiler() {}

    /** Fixed order for the report; unknown systems are added at the end. */
    public static final String RESIDENTS = "residents";
    public static final String JOBS = "jobs";
    public static final String HOUSING = "housing";
    public static final String BUILDINGS = "buildings";
    public static final String FRIENDS = "friends";
    public static final String CLAIMS = "claims";
    public static final String BOOK = "book";
    /** M21: trade, caravans, convoys, growth, relationships, families, attacks (everything ticking per server tick). */
    public static final String VILLAGE_LIFE = "village_life";
    /** Detail inside {@link #RESIDENTS}: path searches (counted once there, not again in the total). */
    public static final String PATHS = "paths";
    /** Path searches refused by the per-tick budget (only counted). */
    public static final String PATHS_DEFERRED = "paths_deferred";
    /** M21: details of the residents – daily routine / thinking ({@code brain}) and walking ({@code navigation}). */
    public static final String BRAIN = "brain";
    public static final String NAVIGATION = "navigation";

    private static final int TICKS = 100;
    private static final Map<String, RollingStats> STATS = new LinkedHashMap<>();
    private static final Map<String, long[]> CURRENT = new LinkedHashMap<>();
    /** Details: parts of a system that are already contained in its time (not added to the total). */
    private static final Map<String, RollingStats> DETAIL_STATS = new LinkedHashMap<>();
    private static final Map<String, long[]> DETAIL_CURRENT = new LinkedHashMap<>();

    static {
        for (String system : new String[]{RESIDENTS, JOBS, HOUSING, BUILDINGS, FRIENDS, CLAIMS, BOOK, VILLAGE_LIFE}) {
            register(system);
        }
    }

    private static long[] register(String system) {
        STATS.computeIfAbsent(system, s -> new RollingStats(TICKS));
        return CURRENT.computeIfAbsent(system, s -> new long[2]);
    }

    /** Adds time (and processed units, e.g. residents) of {@code system} to the current tick. */
    public static void add(String system, long nanos, int units) {
        long[] current = CURRENT.get(system);
        if (current == null) {
            current = register(system);
        }
        current[0] += nanos;
        current[1] += units;
    }

    /** Adds time of a part of a system (e.g. path searches of the residents); shown, but not added to the total. */
    public static void addDetail(String detail, long nanos, int units) {
        long[] current = DETAIL_CURRENT.computeIfAbsent(detail, d -> {
            DETAIL_STATS.put(d, new RollingStats(TICKS));
            return new long[2];
        });
        current[0] += nanos;
        current[1] += units;
    }

    /** Statistics of the details, e.g. {@link #PATHS}. */
    public static Map<String, RollingStats> details() {
        return Collections.unmodifiableMap(DETAIL_STATS);
    }

    /** Runs {@code work} and adds its time to {@code system}. */
    public static void time(String system, Runnable work) {
        long start = System.nanoTime();
        try {
            work.run();
        } finally {
            add(system, System.nanoTime() - start, 0);
        }
    }

    /** Closes the current tick for all systems. */
    public static void endTick() {
        CURRENT.forEach((system, current) -> {
            STATS.get(system).addTick(current[0], (int) current[1]);
            current[0] = 0;
            current[1] = 0;
        });
        DETAIL_CURRENT.forEach((detail, current) -> {
            DETAIL_STATS.get(detail).addTick(current[0], (int) current[1]);
            current[0] = 0;
            current[1] = 0;
        });
    }

    /** Statistics per system, in report order. */
    public static Map<String, RollingStats> stats() {
        return Collections.unmodifiableMap(STATS);
    }

    /** Average time of all systems together, in microseconds per tick. */
    public static double totalMicrosPerTick() {
        return STATS.values().stream().mapToDouble(RollingStats::averageMicrosPerTick).sum();
    }

    /** For tests: forget everything. */
    static void reset() {
        STATS.replaceAll((k, v) -> new RollingStats(TICKS));
        DETAIL_STATS.clear();
        DETAIL_CURRENT.clear();
        CURRENT.values().forEach(c -> {
            c[0] = 0;
            c[1] = 0;
        });
    }
}
