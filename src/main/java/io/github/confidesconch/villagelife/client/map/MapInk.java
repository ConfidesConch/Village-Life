package io.github.confidesconch.villagelife.client.map;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

/**
 * Colours of the parchment map in the village window (M21b8): Minecraft's map colours, faded into
 * parchment and less saturated (so grass and leaves no longer flicker), soft light from the north-west,
 * brown contour lines every few blocks of height, pale blue water with dark ink shores.
 */
final class MapInk {
    private MapInk() {}

    private static final int PAPER = 0xFFE6D5AC;
    private static final int INK = 0xFF5A4630;
    private static final int WATER = 0xFF9DB9BC;
    private static final int SHORE = 0xFF4F6E73;
    /** Height difference between two contour lines. */
    private static final int CONTOUR = 5;

    static int color(ClientLevel level, BlockPos.MutableBlockPos pos, int x, int z, int step, int unknown) {
        if (!level.hasChunkAt(x, z)) {
            return unknown;
        }
        int height = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        MapColor color = surface(level, pos, x, height, z);
        if (color == MapColor.NONE) {
            return PAPER;
        }
        boolean water = color == MapColor.WATER;
        int northHeight = level.hasChunkAt(x, z - step) ? level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z - step) : height;
        int westHeight = level.hasChunkAt(x - step, z) ? level.getHeight(Heightmap.Types.WORLD_SURFACE, x - step, z) : height;
        if (water) {
            boolean shore = isLand(level, pos, x, z - step, northHeight) || isLand(level, pos, x - step, z, westHeight);
            return shore ? SHORE : WATER;
        }
        int base = 0xFF000000 | color.calculateARGBColor(MapColor.Brightness.NORMAL);
        int tone = mix(desaturate(base, 0.4F), PAPER, 0.5F);
        int slope = (height - northHeight) + (height - westHeight);
        tone = scale(tone, Math.max(0.86F, Math.min(1.1F, 1 + slope * 0.03F)));
        if (Math.floorDiv(height, CONTOUR) != Math.floorDiv(northHeight, CONTOUR)
                || Math.floorDiv(height, CONTOUR) != Math.floorDiv(westHeight, CONTOUR)) {
            tone = mix(tone, INK, 0.35F);
        }
        return tone;
    }

    private static MapColor surface(ClientLevel level, BlockPos.MutableBlockPos pos, int x, int height, int z) {
        int y = height - 1;
        BlockState state = level.getBlockState(pos.set(x, y, z));
        MapColor color = state.getMapColor(level, pos);
        if (color == MapColor.NONE && y > level.getMinY()) {
            state = level.getBlockState(pos.set(x, y - 1, z));
            color = state.getMapColor(level, pos);
        }
        return color;
    }

    private static boolean isLand(ClientLevel level, BlockPos.MutableBlockPos pos, int x, int z, int height) {
        if (!level.hasChunkAt(x, z)) {
            return false;
        }
        MapColor color = surface(level, pos, x, height, z);
        return color != MapColor.WATER && color != MapColor.NONE;
    }

    private static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return 0xFF000000 | r << 16 | g << 8 | bl;
    }

    private static int desaturate(int c, float amount) {
        int r = (c >> 16) & 0xFF;
        int g = (c >> 8) & 0xFF;
        int b = c & 0xFF;
        int grey = (r * 30 + g * 59 + b * 11) / 100;
        return mix(c, 0xFF000000 | grey << 16 | grey << 8 | grey, amount);
    }

    private static int scale(int c, float factor) {
        int r = Math.min(255, Math.round(((c >> 16) & 0xFF) * factor));
        int g = Math.min(255, Math.round(((c >> 8) & 0xFF) * factor));
        int b = Math.min(255, Math.round((c & 0xFF) * factor));
        return 0xFF000000 | r << 16 | g << 8 | b;
    }
}
