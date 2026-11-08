package io.github.confidesconch.villagelife.building.scan;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Finds the enclosed room around a start cell by flood-filling all passable cells
 * (Minecraft-free, unit tested).
 *
 * <p>A room is valid when the fill stays below {@code maxVolume} cells, never sees the sky and
 * never leaves loaded terrain. Solid cells touching the room are returned as its boundary
 * (walls, floor, roof, doors, beds, chests ...).
 */
public final class RoomScanner {
    private RoomScanner() {}

    /** Read-only view of the world used by the scanner. */
    public interface View {
        boolean isLoaded(int x, int y, int z);

        /** True if entities could move through this cell (air, torches, flowers, markers ...). */
        boolean isPassable(int x, int y, int z);

        boolean canSeeSky(int x, int y, int z);
    }

    public record Cell(int x, int y, int z) {}

    public enum Failure {
        /** The room is open somewhere or larger than allowed. */
        NOT_ENCLOSED,
        /** The sky is visible from inside, i.e. the roof is missing. */
        NO_ROOF,
        /** The room reaches into terrain that is not loaded. */
        UNLOADED
    }

    /** Either a failure or the scanned room. */
    public record Result(Failure failure, Set<Cell> interior, Set<Cell> boundary, Cell min, Cell max) {
        public boolean isSuccess() {
            return failure == null;
        }

        static Result failed(Failure failure) {
            return new Result(failure, Set.of(), Set.of(), null, null);
        }
    }

    private static final int[][] DIRECTIONS = {
            {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
    };

    public static Result scan(View view, Cell start, int maxVolume) {
        Set<Cell> interior = new HashSet<>();
        Set<Cell> boundary = new LinkedHashSet<>();
        ArrayDeque<Cell> queue = new ArrayDeque<>();
        interior.add(start);
        queue.add(start);

        int minX = start.x(), minY = start.y(), minZ = start.z();
        int maxX = minX, maxY = minY, maxZ = minZ;

        while (!queue.isEmpty()) {
            Cell cell = queue.poll();
            if (view.canSeeSky(cell.x(), cell.y(), cell.z())) {
                return Result.failed(Failure.NO_ROOF);
            }
            minX = Math.min(minX, cell.x());
            minY = Math.min(minY, cell.y());
            minZ = Math.min(minZ, cell.z());
            maxX = Math.max(maxX, cell.x());
            maxY = Math.max(maxY, cell.y());
            maxZ = Math.max(maxZ, cell.z());

            for (int[] d : DIRECTIONS) {
                Cell next = new Cell(cell.x() + d[0], cell.y() + d[1], cell.z() + d[2]);
                if (interior.contains(next) || boundary.contains(next)) {
                    continue;
                }
                if (!view.isLoaded(next.x(), next.y(), next.z())) {
                    return Result.failed(Failure.UNLOADED);
                }
                if (view.isPassable(next.x(), next.y(), next.z())) {
                    if (interior.size() >= maxVolume) {
                        return Result.failed(Failure.NOT_ENCLOSED);
                    }
                    interior.add(next);
                    queue.add(next);
                } else {
                    boundary.add(next);
                }
            }
        }
        return new Result(null, interior, boundary, new Cell(minX, minY, minZ), new Cell(maxX, maxY, maxZ));
    }

    /** Convenience for callers that need a stable order. */
    public static List<Cell> sorted(Set<Cell> cells) {
        List<Cell> list = new ArrayList<>(cells);
        list.sort((a, b) -> a.y() != b.y() ? Integer.compare(a.y(), b.y())
                : a.x() != b.x() ? Integer.compare(a.x(), b.x()) : Integer.compare(a.z(), b.z()));
        return list;
    }
}
