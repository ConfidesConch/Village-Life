package io.github.confidesconch.villagelife.core.config;

import io.github.confidesconch.villagelife.VillageLife;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Display-only options for the local player. Registered as {@code CLIENT} from the client entry
 * point, so it never exists on a dedicated server.
 */
public final class ClientConfig {
    private ClientConfig() {}

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue SHOW_BUILDING_OUTLINES = BUILDER
            .comment("Show outlines of registered buildings when holding a building marker.")
            .translation(VillageLife.MOD_ID + ".configuration." + ConfigDefaults.SHOW_BUILDING_OUTLINES_KEY)
            .define(ConfigDefaults.SHOW_BUILDING_OUTLINES_KEY, ConfigDefaults.SHOW_BUILDING_OUTLINES);

    public static final ModConfigSpec.BooleanValue SHOW_MINIMAP = BUILDER
            .comment("Show the minimap in the top right corner (toggle in game with the minimap key, default N).")
            .translation(VillageLife.MOD_ID + ".configuration.showMinimap")
            .define("showMinimap", true);

    /** M21b1 (owner's choice "2a"): size of the village window in percent of the screen. */
    public static final int MIN_UI_SIZE = 60;
    public static final int MAX_UI_SIZE = 100;

    public static final ModConfigSpec.IntValue UI_SIZE = BUILDER
            .comment("Size of the village window in percent of the screen (also adjustable in the window).")
            .translation(VillageLife.MOD_ID + ".configuration.uiSize")
            .defineInRange("uiSize", 90, MIN_UI_SIZE, MAX_UI_SIZE);

    public static final ModConfigSpec SPEC = BUILDER.build();
}
