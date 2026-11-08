package io.github.confidesconch.villagelife.client.map;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

/**
 * What the map painters read about a column (1.0.1): the live world where it is loaded, otherwise the
 * {@link MapMemory} of what the player has seen before.
 */
final class MapSurface {
    private MapSurface() {}

    /** True if the column is loaded or remembered. */
    static boolean known(ClientLevel level, int x, int z) {
        return loaded(level, x, z) || MapMemory.has(level, x, z);
    }

    /**
     * True if the client really holds this chunk. {@code ClientLevel#hasChunk} always answers true (it
     * hands out an empty chunk instead), so the chunk cache is asked directly.
     */
    static boolean loaded(ClientLevel level, int x, int z) {
        return level.getChunkSource().hasChunk(x >> 4, z >> 4);
    }

    /** Height of the first free block above the ground; only for {@link #known} columns. */
    static int height(ClientLevel level, int x, int z) {
        return loaded(level, x, z) ? level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) : MapMemory.height(level, x, z);
    }

    /** Map colour of the top block (or the one below if the top has none); only for {@link #known} columns. */
    static MapColor color(ClientLevel level, BlockPos.MutableBlockPos pos, int x, int height, int z) {
        if (!loaded(level, x, z)) {
            return MapMemory.color(level, x, z);
        }
        int y = height - 1;
        BlockState state = level.getBlockState(pos.set(x, y, z));
        MapColor color = state.getMapColor(level, pos);
        if (color == MapColor.NONE && y > level.getMinY()) {
            state = level.getBlockState(pos.set(x, y - 1, z));
            color = state.getMapColor(level, pos);
        }
        return color;
    }
}
