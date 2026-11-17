package io.github.confidesconch.villagelife.profession.work;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.confidesconch.villagelife.profession.work.TunnelPlan.Cell;

import net.minecraft.core.BlockPos;

/**
 * The way through a tunnel (hotfix 1.5.1, owner's report "he stands on top of the hole and hangs"): the
 * path finder only looks a limited distance ahead, so a target deep down made the miner walk to the spot
 * straight above it. Instead he follows the tunnel's own walking cells – first to the entrance behind
 * the sign (also around a house), then down the stairs and along the corridors – a few cells at a time.
 */
final class TunnelRoute {
    /** How many cells ahead the next waypoint lies. */
    private static final int LOOK_AHEAD = 6;
    /** Closer than this (squared) the miner walks straight to his target. */
    private static final double DIRECT_SQR = 36;

    /** Walking cells of the stairs and the main corridor, from the entrance in. */
    private final List<BlockPos> trunk = new ArrayList<>();
    /** Walking cells of each side corridor, from the main corridor out. */
    private final Map<Integer, List<BlockPos>> branches = new HashMap<>();
    /** Where on the trunk each side corridor starts. */
    private final Map<Integer, Integer> junctions = new HashMap<>();
    /** Column (x, z) → {segment, index in its list}. */
    private final Map<Long, int[]> columns = new HashMap<>();

    TunnelRoute(TunnelPlan plan) {
        for (Cell cell : plan.cells()) {
            if (!cell.floor()) {
                continue;
            }
            BlockPos pos = new BlockPos(cell.x(), cell.y(), cell.z());
            if (TunnelPlan.isTrunk(cell.segment())) {
                columns.put(key(pos), new int[] {TunnelPlan.MAIN, trunk.size()});
                trunk.add(pos);
            } else {
                List<BlockPos> branch = branches.computeIfAbsent(cell.segment(), s -> new ArrayList<>());
                if (branch.isEmpty()) {
                    junctions.put(cell.segment(), trunk.size() - 1); // it starts beside the last main cell
                }
                columns.put(key(pos), new int[] {cell.segment(), branch.size()});
                branch.add(pos);
            }
        }
    }

    /** Where the miner standing at {@code feet} should walk next on his way to {@code target}. */
    BlockPos waypoint(BlockPos feet, BlockPos target) {
        int[] at = columns.get(key(target));
        if (at == null || trunk.isEmpty() || feet.distSqr(target) <= DIRECT_SQR) {
            return target;
        }
        List<BlockPos> route = route(at);
        int here = -1;
        for (int i = 0; i < route.size(); i++) {
            BlockPos cell = route.get(i);
            if (Math.abs(cell.getX() - feet.getX()) <= 1 && Math.abs(cell.getZ() - feet.getZ()) <= 1
                    && Math.abs(cell.getY() - feet.getY()) <= 2) {
                here = i;
            }
        }
        if (here >= 0) {
            return route.get(Math.min(route.size() - 1, here + LOOK_AHEAD));
        }
        int[] standing = columns.get(key(feet));
        if (standing != null && standing[0] != TunnelPlan.MAIN) { // in another side corridor: back to the main one
            List<BlockPos> branch = branches.get(standing[0]);
            int back = standing[1] - LOOK_AHEAD;
            return back >= 0 ? branch.get(back) : trunk.get(junctions.getOrDefault(standing[0], 0));
        }
        return route.getFirst(); // not in the tunnel yet: first to the entrance, the rest follows from there
    }

    /** The walking cells from the entrance to the column {@code at}. */
    private List<BlockPos> route(int[] at) {
        if (at[0] == TunnelPlan.MAIN) {
            return trunk.subList(0, at[1] + 1);
        }
        List<BlockPos> route = new ArrayList<>(trunk.subList(0, junctions.getOrDefault(at[0], trunk.size() - 1) + 1));
        route.addAll(branches.get(at[0]).subList(0, at[1] + 1));
        return route;
    }

    private static long key(BlockPos pos) {
        return BlockPos.asLong(pos.getX(), 0, pos.getZ());
    }
}
