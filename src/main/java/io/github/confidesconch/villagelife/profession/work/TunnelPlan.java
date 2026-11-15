package io.github.confidesconch.villagelife.profession.work;

import java.util.ArrayList;
import java.util.List;

/**
 * Shape of the miner's tunnel (M28d, owner's choice "stairs down first, then corridors", torches, length
 * from the mine depth setting), free of Minecraft so it can be unit tested.
 *
 * <p>Behind the sign the tunnel goes straight ahead: first a staircase down (one block forward and one
 * down per step, three blocks high so nobody bumps his head) to {@code targetY}, then a main corridor
 * of {@code mainLength} blocks, two high. Every {@value #BRANCH_EVERY} blocks a side corridor of
 * {@value #BRANCH_LENGTH} blocks goes off to the left and one to the right. The cells come in digging
 * order: upper block before lower block, the side corridors right after the main corridor reaches them.
 */
public final class TunnelPlan {
    public static final int BRANCH_EVERY = 3;
    public static final int BRANCH_LENGTH = 8;
    /** A torch on every this many walking cells. */
    public static final int TORCH_EVERY = 8;

    /** Segment of the stairs and of the main corridor; side corridors count up from {@link #FIRST_BRANCH}. */
    public static final int STAIRS = 0;
    public static final int MAIN = 1;
    public static final int FIRST_BRANCH = 2;

    /**
     * One block to dig. {@code floor}: the lower block one walks in (its floor is filled if it is open,
     * M28d); {@code torch}: a torch goes there once it is dug.
     */
    public record Cell(int x, int y, int z, int segment, boolean floor, boolean torch) {}

    private final List<Cell> cells = new ArrayList<>();
    private final int stairSteps;

    /**
     * @param startX/startZ the first block behind the sign
     * @param dirX/dirZ     the direction into the rock (one of them 0, the other ±1)
     * @param floorY        height of the first walking cell
     * @param targetY       height the stairs go down to (no stairs if already there)
     * @param mainLength    blocks of main corridor after the stairs
     */
    public TunnelPlan(int startX, int startZ, int dirX, int dirZ, int floorY, int targetY, int mainLength) {
        stairSteps = Math.max(0, floorY - targetY);
        int walked = 0;
        add(startX, floorY, startZ, 2, STAIRS, walked++ % TORCH_EVERY == TORCH_EVERY - 1);
        for (int step = 1; step <= stairSteps; step++) {
            add(startX + dirX * step, floorY - step, startZ + dirZ * step, 3, STAIRS, walked++ % TORCH_EVERY == TORCH_EVERY - 1);
        }
        int bottom = floorY - stairSteps;
        int branch = FIRST_BRANCH;
        for (int i = 1; i <= Math.max(0, mainLength); i++) {
            int x = startX + dirX * (stairSteps + i);
            int z = startZ + dirZ * (stairSteps + i);
            add(x, bottom, z, 2, MAIN, i % TORCH_EVERY == 0);
            if (i % BRANCH_EVERY == 0) {
                addBranch(x, z, dirZ, -dirX, bottom, branch++); // left
                addBranch(x, z, -dirZ, dirX, bottom, branch++); // right
            }
        }
    }

    private void addBranch(int x, int z, int sideX, int sideZ, int y, int segment) {
        for (int b = 1; b <= BRANCH_LENGTH; b++) {
            add(x + sideX * b, y, z + sideZ * b, 2, segment, b == BRANCH_LENGTH - 2);
        }
    }

    /** A column of {@code height} blocks, top first; the bottom one is the walking cell. */
    private void add(int x, int floorY, int z, int height, int segment, boolean torch) {
        for (int dy = height - 1; dy >= 0; dy--) {
            cells.add(new Cell(x, floorY + dy, z, segment, dy == 0, dy == 0 && torch));
        }
    }

    public List<Cell> cells() {
        return cells;
    }

    public int stairSteps() {
        return stairSteps;
    }

    /** True for the stairs and the main corridor: if they are blocked, nothing behind them can be reached. */
    public static boolean isTrunk(int segment) {
        return segment == STAIRS || segment == MAIN;
    }
}
