package io.github.confidesconch.villagelife.building.scan;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Connects the {@link AreaScanner} to a real world (M21d). The ground is the height map without leaves
 * (water counts as ground, tree tops do not cover a pasture). Fences, walls, gates – open ones too –,
 * doors and leaf hedges standing on the ground are borders.
 */
public final class LevelAreaView implements AreaScanner.View {
    private final ServerLevel level;
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

    public LevelAreaView(ServerLevel level) {
        this.level = level;
    }

    @Override
    public boolean isLoaded(int x, int z) {
        return level.hasChunkAt(x, z);
    }

    @Override
    public int surface(int x, int z) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
    }

    @Override
    public boolean isBorder(int x, int z) {
        int y = surface(x, z);
        // a closed fence is the top block itself; an open gate or a hedge stands on the ground above it
        return isBorderBlock(level.getBlockState(cursor.set(x, y - 1, z)))
                || isBorderBlock(level.getBlockState(cursor.set(x, y, z)));
    }

    private static boolean isBorderBlock(BlockState state) {
        return state.is(BlockTags.FENCES) || state.is(BlockTags.WALLS) || state.is(BlockTags.FENCE_GATES)
                || state.is(BlockTags.DOORS) || state.is(BlockTags.LEAVES);
    }
}
