package io.github.confidesconch.villagelife.profession.work;

import java.util.List;

import io.github.confidesconch.villagelife.building.Building;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * {@link TreeScanner.View} on a real world. Never loads chunks: unloaded columns are "unknown".
 * Blocks inside registered buildings (plus one block for the walls) are off limits.
 */
final class LevelTreeView implements TreeScanner.View {
    private final ServerLevel level;
    private final List<Building> buildings;
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

    LevelTreeView(ServerLevel level, List<Building> buildings) {
        this.level = level;
        this.buildings = buildings;
    }

    @Override
    public int surfaceY(int x, int z) {
        if (!level.hasChunkAt(x, z)) {
            return Integer.MIN_VALUE;
        }
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
    }

    @Override
    public boolean isLog(int x, int y, int z) {
        return state(x, y, z).is(BlockTags.LOGS);
    }

    @Override
    public boolean isSoil(int x, int y, int z) {
        return state(x, y, z).is(BlockTags.DIRT);
    }

    @Override
    public boolean isNaturalLeaves(int x, int y, int z) {
        BlockState state = state(x, y, z);
        return state.is(BlockTags.LEAVES) && state.hasProperty(LeavesBlock.PERSISTENT)
                && !state.getValue(LeavesBlock.PERSISTENT);
    }

    @Override
    public boolean isAllowed(int x, int y, int z) {
        return !BuildingAreas.isInside(buildings, x, y, z);
    }

    private BlockState state(int x, int y, int z) {
        cursor.set(x, y, z);
        if (!level.hasChunkAt(x, z)) {
            return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        }
        return level.getBlockState(cursor);
    }
}
