package io.github.confidesconch.villagelife.building.scan;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Finds a fenced open-air area (field, pasture – M21d, owner's wish "like TekTopia") around a start column
 * by flood-filling columns (Minecraft-free, unit tested).
 *
 * <p>A column belongs to the area unless it is a border (fence, wall, gate, hedge, door) or its ground
 * lies more than {@value #MAX_STEP} block higher or lower than its neighbour's (a house wall, a cliff).
 * The area is valid when the fill stays within {@code maxArea} columns and loaded terrain.
 */
public final class AreaScanner {
    private AreaScanner() {}

    /** Ground may rise or fall this much from one column to the next inside an area. */
    public static final int MAX_STEP = 1;

    /** Read-only view of the world used by the scanner. */
    public interface View {
        boolean isLoaded(int x, int z);

        /** Height of the first free block above the ground of the column (water counts as ground). */
        int surface(int x, int z);

        /** True if the column is part of the border: a fence, wall, gate, hedge or door stands on it. */
        boolean isBorder(int x, int z);
    }

    /** One column of the area; {@code y} is its surface (the block above the ground). */
    public record Column(int x, int z, int y) {}

    public enum Failure {
        /** The area is open somewhere or larger than allowed. */
        NOT_ENCLOSED,
        /** The area reaches into terrain that is not loaded. */
        UNLOADED,
        /** The start column is itself part of the border. */
        ON_BORDER
    }

    /** Either a failure or the columns of the area with their bounds. */
    public record Result(Failure failure, List<Column> columns, int minX, int minZ, int maxX, int maxZ, int minY, int maxY) {
        public boolean isSuccess() {
            return failure == null;
        }

        static Result failed(Failure failure) {
            return new Result(failure, List.of(), 0, 0, 0, 0, 0, 0);
        }
    }

    private static final int[][] DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    public static Result scan(View view, int startX, int startZ, int maxArea) {
        if (!view.isLoaded(startX, startZ)) {
            return Result.failed(Failure.UNLOADED);
        }
        if (view.isBorder(startX, startZ)) {
            return Result.failed(Failure.ON_BORDER);
        }
        List<Column> columns = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        ArrayDeque<Column> queue = new ArrayDeque<>();
        Column start = new Column(startX, startZ, view.surface(startX, startZ));
        seen.add(key(startX, startZ));
        queue.add(start);
        int minX = startX, maxX = startX, minZ = startZ, maxZ = startZ, minY = start.y(), maxY = start.y();

        while (!queue.isEmpty()) {
            Column column = queue.poll();
            columns.add(column);
            minX = Math.min(minX, column.x());
            maxX = Math.max(maxX, column.x());
            minZ = Math.min(minZ, column.z());
            maxZ = Math.max(maxZ, column.z());
            minY = Math.min(minY, column.y());
            maxY = Math.max(maxY, column.y());
            for (int[] d : DIRECTIONS) {
                int x = column.x() + d[0];
                int z = column.z() + d[1];
                if (seen.contains(key(x, z))) {
                    continue;
                }
                if (!view.isLoaded(x, z)) {
                    return Result.failed(Failure.UNLOADED);
                }
                if (view.isBorder(x, z)) {
                    continue; // not marked as seen: the same fence may be reached from another side
                }
                int y = view.surface(x, z);
                if (Math.abs(y - column.y()) > MAX_STEP) {
                    continue; // a wall or a step too high – may still be reached gently from elsewhere
                }
                if (seen.size() >= maxArea) {
                    return Result.failed(Failure.NOT_ENCLOSED);
                }
                seen.add(key(x, z));
                queue.add(new Column(x, z, y));
            }
        }
        return new Result(null, List.copyOf(columns), minX, minZ, maxX, maxZ, minY, maxY);
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }
}
