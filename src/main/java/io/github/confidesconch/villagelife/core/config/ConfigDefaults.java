package io.github.confidesconch.villagelife.core.config;

/**
 * Default values and allowed ranges for all config options, kept free of Minecraft/NeoForge
 * classes so they can be unit tested and reused (e.g. as fallbacks).
 */
public final class ConfigDefaults {
    private ConfigDefaults() {}

    /** A whole-number option with default and inclusive range. */
    public record IntOption(String key, int defaultValue, int min, int max) {
        public IntOption {
            if (min > max || defaultValue < min || defaultValue > max) {
                throw new IllegalArgumentException("Invalid range for config option " + key);
            }
        }
    }

    // --- Synced (per server/world) ---
    public static final IntOption VILLAGE_RADIUS = new IntOption("villageRadius", 64, 16, 256);
    public static final IntOption MAX_RESIDENTS_PER_VILLAGE = new IntOption("maxResidentsPerVillage", 100, 1, 500);
    public static final IntOption MAX_BUILDING_VOLUME = new IntOption("maxBuildingVolume", 4000, 64, 32768);
    public static final IntOption BUILDING_RESCAN_INTERVAL_SECONDS = new IntOption("buildingRescanIntervalSeconds", 60, 10, 3600);
    public static final IntOption AI_THINK_INTERVAL_TICKS = new IntOption("aiThinkIntervalTicks", 20, 5, 200);
    public static final IntOption IMMIGRATION_INTERVAL_TICKS = new IntOption("immigrationIntervalTicks", 2400, 200, 72000);
    public static final IntOption FOOD_PER_DAY = new IntOption("foodPerDay", 5, 0, 40);
    public static final IntOption MAX_WAYPOINTS_PER_PLAYER = new IntOption("maxWaypointsPerPlayer", 100, 0, 1000);
    public static final IntOption MAX_ZONES_PER_VILLAGE = new IntOption("maxZonesPerVillage", 32, 0, 256);
    public static final IntOption CLAIMS_PER_PLAYER = new IntOption("claimsPerPlayer", 16, 0, 1024);
    public static final IntOption CLAIMS_IN_OWN_VILLAGE = new IntOption("claimsInOwnVillage", 64, 0, 4096);
    public static final IntOption MAX_FRIENDS = new IntOption("maxFriends", 100, 1, 1000);
    public static final IntOption PATH_SEARCHES_PER_TICK = new IntOption("pathSearchesPerTick", 4, 1, 50);
    public static final IntOption TICK_BUDGET_MICROS = new IntOption("tickBudgetMicros", 1000, 100, 10000);

    // --- Client ---
    public static final String SHOW_BUILDING_OUTLINES_KEY = "showBuildingOutlines";
    public static final boolean SHOW_BUILDING_OUTLINES = true;
}
