package io.github.confidesconch.villagelife.building.scan;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Connects the {@link RoomScanner} to a real world. A cell is passable when its block has no
 * collision (air, torches, flowers, carpets excluded, building markers included). Walls of glass
 * panes, fences, stairs, slabs, doors etc. therefore seal a room. M21a: ladders (climbable blocks) and a
 * trapdoor right above a ladder are passable too, so floors joined by a ladder are one building.
 */
public final class LevelScanView implements RoomScanner.View {
    private final ServerLevel level;
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

    public LevelScanView(ServerLevel level) {
        this.level = level;
    }

    @Override
    public boolean isLoaded(int x, int y, int z) {
        return level.isLoaded(cursor.set(x, y, z));
    }

    @Override
    public boolean isPassable(int x, int y, int z) {
        cursor.set(x, y, z);
        BlockState state = level.getBlockState(cursor);
        if (state.is(BlockTags.CLIMBABLE)) {
            return true; // M21a: a ladder leads to the next floor
        }
        if (state.getBlock() instanceof TrapDoorBlock) { // a hatch over a ladder, too
            return level.getBlockState(cursor.below()).is(BlockTags.CLIMBABLE);
        }
        return state.getCollisionShape(level, cursor).isEmpty();
    }

    /**
     * Uses the height map (highest non-air block per column), which is updated immediately when a
     * block changes. {@code Level#canSeeSky} relies on sky light, which is recalculated with a delay
     * and wrongly reported freshly built rooms as roofless.
     */
    @Override
    public boolean canSeeSky(int x, int y, int z) {
        return y >= level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
    }
}
