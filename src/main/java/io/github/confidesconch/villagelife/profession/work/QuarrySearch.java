package io.github.confidesconch.villagelife.profession.work;

import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

import io.github.confidesconch.villagelife.resident.ResidentEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

/** Finding the miner's next block in a quarry (M13e; split off MinerGoal in M28d). */
final class QuarrySearch {
    private QuarrySearch() {}

    /** Above the ground, blocks higher than this above the miner's feet wait until he stands higher. */
    private static final int REACHABLE_ABOVE_FEET = 4;
    /** How far above the quarry's ground a hill in the area is still cleared. */
    private static final int MAX_HEIGHT_ABOVE = 16;

    /**
     * The block above the ground the miner should dig next: near him horizontally, higher ones first,
     * but only up to {@link #REACHABLE_ABOVE_FEET} above his feet. Null if there is none in reach.
     */
    static BlockPos nextAboveGround(ServerLevel level, QuarryPlan plan, ResidentEntity resident, Predicate<BlockPos> minable) {
        int top = Math.min(plan.groundY() + MAX_HEIGHT_ABOVE, highestBlock(level, plan));
        int feet = resident.getBlockY();
        BlockPos best = null;
        double bestCost = Double.MAX_VALUE;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = plan.groundY() + 1; y <= top; y++) {
            if (y > feet + REACHABLE_ABOVE_FEET) {
                break;
            }
            for (int x = plan.minX(); x <= plan.maxX(); x++) {
                for (int z = plan.minZ(); z <= plan.maxZ(); z++) {
                    double dx = x + 0.5 - resident.getX();
                    double dz = z + 0.5 - resident.getZ();
                    double cost = dx * dx + dz * dz - y * 0.5;
                    if (cost < bestCost && minable.test(pos.set(x, y, z))) {
                        best = pos.immutable();
                        bestCost = cost;
                    }
                }
            }
        }
        return best;
    }

    /** Fills {@code layer} with the minable blocks of the highest layer below the ground that still has some. */
    static boolean scanTopLayer(ServerLevel level, QuarryPlan plan, BlockPos from, Predicate<BlockPos> minable, List<BlockPos> layer) {
        int top = plan.groundY();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = top; y > plan.floorY(); y--) {
            for (int x = plan.minX(); x <= plan.maxX(); x++) {
                for (int z = plan.minZ(); z <= plan.maxZ(); z++) {
                    pos.set(x, y, z);
                    if (plan.shouldMine(x, y, z) && minable.test(pos)) {
                        layer.add(pos.immutable());
                    }
                }
            }
            if (!layer.isEmpty()) {
                layer.sort(Comparator.comparingDouble(p -> p.distSqr(from)));
                return true;
            }
        }
        return false;
    }

    private static int highestBlock(ServerLevel level, QuarryPlan plan) {
        int top = plan.floorY();
        for (int x = plan.minX(); x <= plan.maxX(); x++) {
            for (int z = plan.minZ(); z <= plan.maxZ(); z++) {
                if (level.hasChunkAt(x, z)) {
                    top = Math.max(top, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1);
                }
            }
        }
        return top;
    }

}
