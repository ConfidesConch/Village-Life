package io.github.confidesconch.villagelife.profession.work;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import io.github.confidesconch.villagelife.building.Building;
import io.github.confidesconch.villagelife.building.BuildingMarkerBlock;
import io.github.confidesconch.villagelife.core.registry.ModBlocks;
import io.github.confidesconch.villagelife.profession.work.TunnelPlan.Cell;
import io.github.confidesconch.villagelife.resident.ResidentEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The miner's work in a tunnel (M28d): follows the {@link TunnelPlan} behind the sign, and keeps the
 * tunnel a closed tube (owner's choice): where a cave opens the floor, a wall or the ceiling, the hole
 * is walled up with cobblestone from his pack. Every few blocks he sets a torch. A blocked staircase or
 * main corridor (water, lava, bedrock) ends the tunnel; a blocked side corridor is just left.
 */
final class TunnelDigger {
    /** Cobblestone the miner keeps for walling up while he works in a tunnel. */
    static final int COBBLE_TO_KEEP = 32;
    static final int TORCHES_TO_KEEP = 16;
    /** Coal and sticks kept for making torches (one of each gives four). */
    static final int COAL_TO_KEEP = 4;

    /** What to do next: dig the block, or wall up around the (open) cell. */
    record Target(BlockPos pos, boolean seal) {}

    private Building site;
    private int length;
    private TunnelPlan plan;
    private Set<Long> inPlan = Set.of();
    private BlockPos entrance;
    /** Cells already walled up (index into the plan); reset with a new plan. */
    private final Set<Integer> sealed = new HashSet<>();
    /** Cells that could not be walled up today (no cobblestone, out of reach): their corridor waits. */
    private final Set<Integer> waiting = new HashSet<>();
    private long day = Long.MIN_VALUE;
    private TunnelRoute route;
    /** True if the last {@link #next} stopped at an unloaded part of the tunnel (then it is not "finished"). */
    private boolean waitingForChunks;
    private BlockPos start;
    private Direction into;
    private int floorY;
    /** Where the stairs should end (Y); higher while today's stairs are blocked further down. */
    private int deepest;
    /** Torch spots passed while he had no torch (the nearest to the entrance first). */
    private final java.util.TreeSet<Integer> missingTorches = new java.util.TreeSet<>();

    /** The tunnel at {@code site} (a valid tunnel sign) with a main corridor of {@code mainLength}. */
    void use(ServerLevel level, Building site, int mainLength) {
        if (plan != null && site.id().equals(this.site.id()) && site.markerPos().equals(this.site.markerPos()) && mainLength == length) {
            return;
        }
        this.site = site;
        this.length = mainLength;
        BlockPos marker = site.markerPos();
        BlockState state = level.getBlockState(marker);
        Direction facing = state.is(ModBlocks.BUILDING_MARKER.get()) ? state.getValue(BuildingMarkerBlock.FACING) : Direction.NORTH;
        Direction into = facing.getOpposite();
        floorY = groundInFront(level, marker.relative(facing));
        start = marker.relative(into);
        this.into = into;
        deepest = level.getMinY() + 8;
        entrance = marker; // the column in front of the first cell stays open
        build(deepest);
    }

    /** (Re)builds the plan with the stairs going down to {@code targetY}. */
    private void build(int targetY) {
        plan = new TunnelPlan(start.getX(), start.getZ(), into.getStepX(), into.getStepZ(), floorY, targetY, length);
        Set<Long> keys = new HashSet<>();
        plan.cells().forEach(cell -> keys.add(BlockPos.asLong(cell.x(), cell.y(), cell.z())));
        inPlan = keys;
        route = new TunnelRoute(plan, new BlockPos(entrance.getX(), floorY, entrance.getZ()));
        sealed.clear();
        waiting.clear();
        missingTorches.clear();
    }

    /**
     * Height the tunnel floor starts at: where one stands in front of the sign (fix 15.11.2026: a sign hung
     * two or three blocks up made him leave the lowest block). Up to 3 blocks below the sign.
     */
    private static int groundInFront(ServerLevel level, BlockPos front) {
        for (int down = 1; down <= 3; down++) {
            if (level.getBlockState(front.below(down)).isSolid()) {
                return front.getY() - down + 1;
            }
        }
        return front.getY() - 1;
    }

    /** On a new Minecraft day the waiting cells are tried again. */
    void newDay(long day) {
        if (day != this.day) {
            this.day = day;
            waiting.clear();
            if (plan != null && plan.stairSteps() < floorY - deepest) {
                build(deepest); // try the full stairs again
            }
        }
    }

    /** The miner could not reach the cell to wall it up: its corridor waits until tomorrow. */
    void giveUp(BlockPos pos) {
        int index = indexOf(pos);
        if (index >= 0) {
            waiting.add(index);
        }
    }

    /**
     * The next block to dig or cell to wall up, in plan order; null when the tunnel is finished or
     * blocked. {@code minable}: the miner's own rules (not next to fluids, not skipped …);
     * {@code hasTorch}: torch spots passed without a torch are visited again (fix 15.11.2026).
     */
    Target next(ServerLevel level, Predicate<BlockPos> minable, boolean hasTorch) {
        waitingForChunks = false;
        List<Cell> cells = plan.cells();
        if (hasTorch) {
            for (java.util.Iterator<Integer> it = missingTorches.iterator(); it.hasNext(); ) {
                Cell cell = cells.get(it.next());
                BlockPos spot = new BlockPos(cell.x(), cell.y(), cell.z());
                if (!level.isLoaded(spot)) {
                    continue;
                }
                if (wantsTorch(level, spot)) {
                    return new Target(spot, true);
                }
                it.remove(); // something is there now
            }
        }
        int blockedSegment = -1;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < cells.size(); i++) {
            Cell cell = cells.get(i);
            if (cell.segment() == blockedSegment) {
                continue;
            }
            pos.set(cell.x(), cell.y(), cell.z());
            if (!level.isLoaded(pos)) {
                waitingForChunks = true; // not finished, just out of reach of the loaded world
                return null;
            }
            BlockState state = level.getBlockState(pos);
            if (!state.isAir() && !state.canBeReplaced() && !state.is(Blocks.TORCH)) { // his own torches stay
                if (minable.test(pos)) {
                    return new Target(pos.immutable(), false);
                }
                if (cell.segment() == TunnelPlan.STAIRS) {
                    // fix 15.11.2026: water, lava or bedrock on the way down – the corridors start here instead
                    int step = Math.abs(cell.x() - start.getX()) + Math.abs(cell.z() - start.getZ());
                    if (step >= 2 && step - 1 < plan.stairSteps()) {
                        build(floorY - (step - 1));
                        return next(level, minable, hasTorch);
                    }
                }
                if (TunnelPlan.isTrunk(cell.segment())) {
                    return null; // stairs or main corridor blocked: nothing behind it can be reached
                }
                blockedSegment = cell.segment();
                continue;
            }
            if (waiting.contains(i)) {
                if (TunnelPlan.isTrunk(cell.segment())) {
                    return null;
                }
                blockedSegment = cell.segment();
                continue;
            }
            if (!sealed.contains(i)) {
                if (openSides(level, cell).isEmpty() && !(cell.torch() && wantsTorch(level, pos))) {
                    sealed.add(i);
                } else {
                    return new Target(pos.immutable(), true);
                }
            }
        }
        return null;
    }

    /**
     * Walls up the open sides of the cell at {@code pos} with carried cobblestone and sets its torch.
     * False if cobblestone ran out (the tunnel waits for more).
     */
    boolean seal(ServerLevel level, BlockPos pos, ResidentEntity miner) {
        int index = indexOf(pos);
        if (index < 0) {
            return true;
        }
        Cell cell = plan.cells().get(index);
        boolean complete = true;
        for (BlockPos open : openSides(level, cell)) {
            ItemStack stone = miner.takeCarried(stack -> stack.is(ItemTags.STONE_CRAFTING_MATERIALS), 1);
            if (stone.isEmpty()) {
                complete = false;
                break;
            }
            level.setBlockAndUpdate(open, Blocks.COBBLESTONE.defaultBlockState());
            level.playSound(null, open, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 0.8F, 1.0F);
        }
        if (complete && cell.torch() && wantsTorch(level, pos)) {
            if (miner.takeCarried(stack -> stack.is(Items.TORCH), 1).isEmpty()) {
                missingTorches.add(index); // set it once he has torches
            } else {
                level.setBlockAndUpdate(pos, Blocks.TORCH.defaultBlockState());
                level.playSound(null, pos, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.8F, 1.0F);
                missingTorches.remove(index);
            }
        }
        if (complete) {
            sealed.add(index);
        } else {
            waiting.add(index); // out of cobblestone: this corridor waits
        }
        return complete;
    }

    /** True if the plan has torch spots and the miner carries none. */
    static boolean needsTorches(ResidentEntity miner) {
        return miner.carriedCount(stack -> stack.is(Items.TORCH)) == 0;
    }

    /** Makes torches like a player: one coal or charcoal and one stick give four. */
    static void craftTorches(ResidentEntity miner) {
        while (miner.carriedCount(stack -> stack.is(Items.TORCH)) < TORCHES_TO_KEEP
                && miner.carriedCount(stack -> stack.is(ItemTags.COALS)) > 0
                && miner.carriedCount(stack -> stack.is(Items.STICK)) > 0) {
            miner.takeCarried(stack -> stack.is(ItemTags.COALS), 1);
            miner.takeCarried(stack -> stack.is(Items.STICK), 1);
            miner.carry(new ItemStack(Items.TORCH, 4));
        }
    }

    /** Hotfix 1.5.1: the next point on the way through the tunnel to {@code target}. */
    BlockPos waypoint(BlockPos feet, BlockPos target) {
        return route == null ? target : route.waypoint(feet, target);
    }

    boolean waitingForChunks() {
        return waitingForChunks;
    }

    /** True if the block is one of the tunnel's own (the miner never walls those up). */
    boolean contains(BlockPos pos) {
        return inPlan.contains(pos.asLong());
    }

    /** Neighbours of a dug cell that are open but not part of the tunnel: cave holes to wall up. */
    private List<BlockPos> openSides(ServerLevel level, Cell cell) {
        BlockPos pos = new BlockPos(cell.x(), cell.y(), cell.z());
        java.util.ArrayList<BlockPos> open = new java.util.ArrayList<>();
        for (Direction side : Direction.values()) {
            BlockPos next = pos.relative(side);
            if (inPlan.contains(next.asLong()) || isEntrance(next) || !level.isLoaded(next)) {
                continue;
            }
            BlockState state = level.getBlockState(next);
            if ((state.isAir() || state.canBeReplaced()) && state.getFluidState().isEmpty() && !state.is(Blocks.TORCH)) {
                open.add(next);
            }
        }
        return open;
    }

    /** The column in front of the first cell (where the sign hangs) stays open: it is the way in. */
    private boolean isEntrance(BlockPos pos) {
        return pos.getX() == entrance.getX() && pos.getZ() == entrance.getZ()
                && Math.abs(pos.getY() - entrance.getY()) <= 3;
    }

    private static boolean wantsTorch(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).isAir() && Blocks.TORCH.defaultBlockState().canSurvive(level, pos);
    }

    private int indexOf(BlockPos pos) {
        List<Cell> cells = plan.cells();
        for (int i = 0; i < cells.size(); i++) {
            Cell cell = cells.get(i);
            if (cell.x() == pos.getX() && cell.y() == pos.getY() && cell.z() == pos.getZ()) {
                return i;
            }
        }
        return -1;
    }
}
