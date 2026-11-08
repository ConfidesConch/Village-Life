package io.github.confidesconch.villagelife.profession.work;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Decides which field cell a farmer works on next (Minecraft-free, unit tested):
 * ripe crops first (nearest first), then empty farmland if the farmer has seeds.
 */
public final class FieldPlanner {
    private FieldPlanner() {}

    public enum CellState { EMPTY, GROWING, RIPE }

    /** One farmland block; {@code y} is the height of the crop (one above the farmland). */
    public record Cell(int x, int y, int z, CellState state) {
        double distanceSqr(double fromX, double fromZ) {
            double dx = x + 0.5 - fromX;
            double dz = z + 0.5 - fromZ;
            return dx * dx + dz * dz;
        }
    }

    public static Optional<Cell> next(List<Cell> cells, double fromX, double fromZ, boolean hasSeeds) {
        Comparator<Cell> nearest = Comparator.comparingDouble(c -> c.distanceSqr(fromX, fromZ));
        Optional<Cell> ripe = cells.stream().filter(c -> c.state() == CellState.RIPE).min(nearest);
        if (ripe.isPresent() || !hasSeeds) {
            return ripe;
        }
        return cells.stream().filter(c -> c.state() == CellState.EMPTY).min(nearest);
    }
}
