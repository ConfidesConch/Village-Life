package io.github.confidesconch.villagelife.core.config;

import io.github.confidesconch.villagelife.VillageLife;
import io.github.confidesconch.villagelife.core.config.ConfigDefaults.IntOption;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Gameplay balance values. Registered as {@code SYNCED}: loaded with the server (or singleplayer
 * world), overridable per world in {@code syncedconfig/}, and synced to connecting clients.
 */
public final class SyncedConfig {
    private SyncedConfig() {}

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue VILLAGE_RADIUS = define(ConfigDefaults.VILLAGE_RADIUS,
            "Radius of a village around its center, in blocks.");

    public static final ModConfigSpec.IntValue MAX_RESIDENTS_PER_VILLAGE = define(ConfigDefaults.MAX_RESIDENTS_PER_VILLAGE,
            "Maximum number of residents a single village can have.");

    public static final ModConfigSpec.IntValue MAX_BUILDING_VOLUME = define(ConfigDefaults.MAX_BUILDING_VOLUME,
            "Largest room (in air blocks) a building may have. Bigger or open rooms are rejected.");

    public static final ModConfigSpec.IntValue BUILDING_RESCAN_INTERVAL_SECONDS = define(ConfigDefaults.BUILDING_RESCAN_INTERVAL_SECONDS,
            "How often every building is checked again automatically, in seconds.");

    public static final ModConfigSpec.IntValue AI_THINK_INTERVAL_TICKS = define(ConfigDefaults.AI_THINK_INTERVAL_TICKS,
            "Ticks between two decisions of a resident (20 ticks = 1 second). Higher = less server load, slower reactions.");

    public static final ModConfigSpec.IntValue IMMIGRATION_INTERVAL_TICKS = define(ConfigDefaults.IMMIGRATION_INTERVAL_TICKS,
            "Minimum time between two newcomers in one village, in ticks (2400 = 2 minutes). Newcomers only arrive if beds are free.");

    public static final ModConfigSpec.IntValue FOOD_PER_DAY = define(ConfigDefaults.FOOD_PER_DAY,
            "How hungry residents get per Minecraft day (food points, 20 = full bar). 0 = never hungry.");

    public static final ModConfigSpec.IntValue MAX_WAYPOINTS_PER_PLAYER = define(ConfigDefaults.MAX_WAYPOINTS_PER_PLAYER,
            "Maximum number of waypoints one player can have on the map (all dimensions together).");

    public static final ModConfigSpec.IntValue MAX_ZONES_PER_VILLAGE = define(ConfigDefaults.MAX_ZONES_PER_VILLAGE,
            "Maximum number of named zones (marked areas) one village can have.");

    public static final ModConfigSpec.IntValue CLAIMS_PER_PLAYER = define(ConfigDefaults.CLAIMS_PER_PLAYER,
            "Chunks a player may claim anywhere (per dimension). Others cannot break or place blocks there.");

    public static final ModConfigSpec.IntValue CLAIMS_IN_OWN_VILLAGE = define(ConfigDefaults.CLAIMS_IN_OWN_VILLAGE,
            "Extra chunks a player may claim inside his own villages (per dimension), on top of claimsPerPlayer.");

    public static final ModConfigSpec.IntValue MAX_FRIENDS = define(ConfigDefaults.MAX_FRIENDS,
            "Maximum number of friends per player. Friends may build in each other's claims and see each other on the map.");

    public static final ModConfigSpec.IntValue PATH_SEARCHES_PER_TICK = define(ConfigDefaults.PATH_SEARCHES_PER_TICK,
            "How many new paths all residents together may search per tick. Lower = smoother server at sunrise/bedtime, residents react a little later.");

    public static final ModConfigSpec.IntValue TICK_BUDGET_MICROS = define(ConfigDefaults.TICK_BUDGET_MICROS,
            "Time budget per server tick for background work (building scans etc.), in microseconds.");

    public static final ModConfigSpec SPEC = BUILDER.build();

    private static ModConfigSpec.IntValue define(IntOption option, String comment) {
        return BUILDER.comment(comment)
                .translation(VillageLife.MOD_ID + ".configuration." + option.key())
                .defineInRange(option.key(), option.defaultValue(), option.min(), option.max());
    }
}
