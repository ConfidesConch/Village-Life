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

    /** 1.6 (owner's wish): the minimap can be moved, resized, zoomed and turned (key "Adjust minimap", default U). */
    public static final int MIN_MINIMAP_SIZE = 48;
    public static final int MAX_MINIMAP_SIZE = 192;
    /** Blocks per map pixel for the zoom steps (0 = closest). */
    public static final float[] MINIMAP_ZOOMS = {0.5F, 1F, 2F, 4F};

    public static final ModConfigSpec.IntValue MINIMAP_SIZE = BUILDER
            .comment("Size of the minimap in GUI pixels.")
            .translation(VillageLife.MOD_ID + ".configuration.minimapSize")
            .defineInRange("minimapSize", 64, MIN_MINIMAP_SIZE, MAX_MINIMAP_SIZE);

    public static final ModConfigSpec.IntValue MINIMAP_RIGHT = BUILDER
            .comment("Distance of the minimap from the right edge of the screen, in GUI pixels.")
            .translation(VillageLife.MOD_ID + ".configuration.minimapRight")
            .defineInRange("minimapRight", 6, 0, 4000);

    public static final ModConfigSpec.IntValue MINIMAP_TOP = BUILDER
            .comment("Distance of the minimap from the top edge of the screen, in GUI pixels.")
            .translation(VillageLife.MOD_ID + ".configuration.minimapTop")
            .defineInRange("minimapTop", 6, 0, 4000);

    public static final ModConfigSpec.IntValue MINIMAP_ZOOM = BUILDER
            .comment("Zoom step of the minimap: 0 = closest (half a block per pixel) … 3 = farthest (4 blocks per pixel).")
            .translation(VillageLife.MOD_ID + ".configuration.minimapZoom")
            .defineInRange("minimapZoom", 1, 0, MINIMAP_ZOOMS.length - 1);

    public static final ModConfigSpec.BooleanValue MINIMAP_ROTATE = BUILDER
            .comment("Turn the minimap with the player's view (true) or keep north up (false).")
            .translation(VillageLife.MOD_ID + ".configuration.minimapRotate")
            .define("minimapRotate", false);

    public static final ModConfigSpec SPEC = BUILDER.build();
}
