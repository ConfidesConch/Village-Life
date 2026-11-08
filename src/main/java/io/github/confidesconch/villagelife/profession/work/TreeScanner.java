package io.github.confidesconch.villagelife.profession.work;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Finds natural trees around a point and lists their logs (Minecraft-free, unit tested).
 * A tree is a group of connected logs standing on soil with at least one natural (not
 * player-placed) leaf block next to it. This protects log houses and decorations: they have no
 * natural leaves, and logs inside registered buildings are excluded by the caller's filter.
 */
public final class TreeScanner {
    private TreeScanner() {}

    /** Most logs one tree may have (big jungle/dark oak trees). */
    static final int MAX_LOGS = 128;
    /** How far (horizontally) logs may be away from the trunk base. */
    static final int MAX_SPREAD = 5;
    private static final int UNKNOWN = Integer.MIN_VALUE;

    /** What the scanner needs to know about the world. */
    public interface View {
        /** Y of the first free block above the highest non-leaf block, or {@link Integer#MIN_VALUE} if unknown (not loaded). */
        int surfaceY(int x, int z);

        boolean isLog(int x, int y, int z);

        boolean isSoil(int x, int y, int z);

        boolean isNaturalLeaves(int x, int y, int z);

        /** False for positions the worker must not touch (e.g. inside registered buildings). */
        boolean isAllowed(int x, int y, int z);
    }

    public record Pos(int x, int y, int z) {
        long distanceSqr(int ox, int oz) {
            long dx = x - ox;
            long dz = z - oz;
            return dx * dx + dz * dz;
        }
    }

    /** A tree: its lowest trunk log and all logs, highest first (the order to chop them in). */
    public record Tree(Pos base, List<Pos> logs) {}

    /**
     * The natural tree closest to (centerX, centerZ) within {@code radius}, skipping trees whose base
     * is in {@code ignoredBases}.
     */
    public static Optional<Tree> findNearest(View view, int centerX, int centerZ, int radius, Set<Pos> ignoredBases) {
        Set<Pos> checkedBases = new HashSet<>();
        Tree best = null;
        long bestDistance = Long.MAX_VALUE;
        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                int surface = view.surfaceY(x, z);
                if (surface == UNKNOWN || !view.isLog(x, surface - 1, z)) {
                    continue;
                }
                Pos base = trunkBase(view, x, surface - 1, z);
                if (base == null || ignoredBases.contains(base) || !checkedBases.add(base)) {
                    continue;
                }
                long distance = base.distanceSqr(centerX, centerZ);
                if (distance >= bestDistance) {
                    continue;
                }
                Optional<Tree> tree = treeAt(view, base, true);
                if (tree.isPresent()) {
                    best = tree.get();
                    bestDistance = distance;
                }
            }
        }
        return Optional.ofNullable(best);
    }

    /** How far (in steps through leaves) leaves may be from the chopped logs. */
    static final int MAX_LEAF_STEPS = 6;
    /** Most leaves collected for one tree. */
    static final int MAX_LEAVES = 400;

    /**
     * The natural leaves that belonged to a tree whose logs were just chopped: reachable from the old
     * log positions through natural leaves within {@link #MAX_LEAF_STEPS}. Leaves touching a log of
     * another tree are left alone (and not walked through), so neighbouring trees keep their crowns.
     */
    public static List<Pos> leavesOf(View view, List<Pos> choppedLogs) {
        List<Pos> result = new ArrayList<>();
        Set<Pos> seen = new HashSet<>(choppedLogs);
        Deque<Pos> queue = new ArrayDeque<>();
        java.util.Map<Pos, Integer> steps = new java.util.HashMap<>();
        for (Pos log : choppedLogs) {
            queue.add(log);
            steps.put(log, 0);
        }
        int[][] directions = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        while (!queue.isEmpty() && result.size() < MAX_LEAVES) {
            Pos pos = queue.poll();
            int step = steps.get(pos);
            if (step >= MAX_LEAF_STEPS) {
                continue;
            }
            for (int[] d : directions) {
                Pos next = new Pos(pos.x() + d[0], pos.y() + d[1], pos.z() + d[2]);
                if (!seen.add(next) || !view.isNaturalLeaves(next.x(), next.y(), next.z())
                        || !view.isAllowed(next.x(), next.y(), next.z()) || touchesLog(view, next)) {
                    continue;
                }
                result.add(next);
                steps.put(next, step + 1);
                queue.add(next);
            }
        }
        return result;
    }

    private static boolean touchesLog(View view, Pos pos) {
        return view.isLog(pos.x() + 1, pos.y(), pos.z()) || view.isLog(pos.x() - 1, pos.y(), pos.z())
                || view.isLog(pos.x(), pos.y() + 1, pos.z()) || view.isLog(pos.x(), pos.y() - 1, pos.z())
                || view.isLog(pos.x(), pos.y(), pos.z() + 1) || view.isLog(pos.x(), pos.y(), pos.z() - 1);
    }

    /** Walks down the log column to the lowest log; null if it does not stand on soil. */
    static Pos trunkBase(View view, int x, int topY, int z) {
        int y = topY;
        while (view.isLog(x, y - 1, z)) {
            y--;
        }
        return view.isSoil(x, y - 1, z) ? new Pos(x, y, z) : null;
    }

    /**
     * Collects the connected logs of the tree at {@code base}; empty if it is not a natural tree.
     * {@code requireLeaves = false} is for a tree that was already confirmed and partly chopped:
     * its leaves may have decayed in the meantime.
     */
    public static Optional<Tree> treeAt(View view, Pos base, boolean requireLeaves) {
        if (!view.isLog(base.x(), base.y(), base.z())) {
            return Optional.empty();
        }
        List<Pos> logs = new ArrayList<>();
        Set<Pos> seen = new HashSet<>();
        Deque<Pos> queue = new ArrayDeque<>();
        queue.add(base);
        seen.add(base);
        boolean hasLeaves = false;
        while (!queue.isEmpty()) {
            Pos pos = queue.poll();
            if (!view.isAllowed(pos.x(), pos.y(), pos.z())) {
                return Optional.empty();
            }
            logs.add(pos);
            if (logs.size() > MAX_LOGS) {
                return Optional.empty(); // too big: probably a structure, not a tree
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = 0; dy <= 1; dy++) { // trees grow up/sideways; never follow logs downwards
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        int nx = pos.x() + dx;
                        int ny = pos.y() + dy;
                        int nz = pos.z() + dz;
                        if (!hasLeaves && view.isNaturalLeaves(nx, ny, nz)) {
                            hasLeaves = true;
                        }
                        Pos next = new Pos(nx, ny, nz);
                        if (Math.abs(nx - base.x()) <= MAX_SPREAD && Math.abs(nz - base.z()) <= MAX_SPREAD
                                && !seen.contains(next) && view.isLog(nx, ny, nz)) {
                            seen.add(next);
                            queue.add(next);
                        }
                    }
                }
            }
        }
        if (requireLeaves && !hasLeaves) {
            return Optional.empty();
        }
        logs.sort(Comparator.comparingInt(Pos::y).reversed());
        return Optional.of(new Tree(base, List.copyOf(logs)));
    }
}
