package io.github.confidesconch.villagelife.profession.work;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.github.confidesconch.villagelife.building.AreaService;
import io.github.confidesconch.villagelife.building.Building;
import io.github.confidesconch.villagelife.building.BuildingType;
import io.github.confidesconch.villagelife.building.scan.AreaScanner;
import io.github.confidesconch.villagelife.profession.Professions;
import io.github.confidesconch.villagelife.profession.work.FieldPlanner.Cell;
import io.github.confidesconch.villagelife.profession.work.FieldPlanner.CellState;
import io.github.confidesconch.villagelife.resident.ResidentEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Work of the farmer (M8b): harvests ripe crops (wheat, carrots, potatoes, beetroots, ...) on the
 * farmland around the farm, replants them with seeds from the harvest, sows empty farmland when it has
 * seeds, and brings the harvest to the farm or a storage building (M9a). Keeps a few seeds of each
 * kind; without seeds it fetches some from the farm/storage containers first.
 * Farmland inside registered buildings is left alone. M21d: fields marked with a marker replace the
 * search around the farm.
 */
public class FarmerGoal extends WorkGoal {
    public static final String PROFESSION = "villagelife:farmer";
    /** Shown in the book as {@code villagelife.problem.work.farmer.no_field} (07.11.2026). */
    public static final String ISSUE_NO_FIELD = "farmer.no_field";
    /** Shown in the book as {@code villagelife.problem.work.farmer.no_seeds} (07.11.2026). */
    public static final String ISSUE_NO_SEEDS = "farmer.no_seeds";

    private static final int WORK_TICKS = 10;
    private static final int SEARCH_COOLDOWN_TICKS = 200;
    /** Fields may be this much higher or lower than the farm's marker. */
    private static final int MAX_HEIGHT_DIFFERENCE = 6;
    private static final double WORK_DISTANCE = 2.0;
    private static final double WORK_DISTANCE_PATH_END = 3.0;
    /** Seeds of each kind the farmer keeps for sowing. */
    private static final int SEEDS_TO_KEEP = 16;

    private enum Phase { TOOL, FETCH, TO_CELL, WORK_CELL, DELIVER }

    private final List<Cell> cells = new ArrayList<>();
    private Phase phase;
    private Cell target;
    private int phaseTicks;
    private int cooldown;
    private long nextSearchGameTime;

    public FarmerGoal(ResidentEntity resident, double speedModifier) {
        super(resident, speedModifier, PROFESSION, ItemTags.HOES, Items.WOODEN_HOE);
    }

    @Override
    public boolean canUse() {
        if (!isWorkTime()) {
            return false;
        }
        if (isFull()) {
            setPhase(Phase.DELIVER);
            return true;
        }
        if (resident.level() instanceof ServerLevel level && mayFetchTool(level)) {
            setPhase(Phase.TOOL);
            return true;
        }
        if (needsSeeds()) {
            setPhase(Phase.FETCH);
            return true;
        }
        if (findWork()) {
            return true;
        }
        if (hasSomethingToDeliver()) {
            setPhase(Phase.DELIVER);
            return true;
        }
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        return isWorkTime() && phase != null;
    }

    @Override
    public void stop() {
        phase = null;
        target = null;
        super.stop();
    }

    @Override
    public void tick() {
        if (!(resident.level() instanceof ServerLevel level) || phase == null) {
            return;
        }
        phaseTicks++;
        switch (phase) {
            case TO_CELL -> walkToCell();
            case WORK_CELL -> workCell(level);
            case TOOL -> {
                if (fetchToolStep(level) != FetchStatus.RUNNING) {
                    continueWork();
                }
            }
            case FETCH -> {
                if (fetchStep(level, "seeds", FarmerGoal::isSeed, SEEDS_TO_KEEP) != FetchStatus.RUNNING) {
                    report(hasSeeds() ? null : ISSUE_NO_SEEDS);
                    phase = findWork() ? Phase.TO_CELL : null;
                }
            }
            case DELIVER -> {
                if (deliverStep(level)) {
                    // at the containers anyway: a good moment to look for a better tool
                    if (mayFetchTool(level)) {
                        setPhase(Phase.TOOL);
                    } else {
                        continueWork();
                    }
                }
            }
        }
    }

    @Override
    protected int keepMaterial(ItemStack stack) {
        return isSeed(stack) ? SEEDS_TO_KEEP : 0;
    }

    /** Picks the next cell; rescans the fields when the known cells are used up (throttled after a failed scan). */
    private boolean findWork() {
        if (!(resident.level() instanceof ServerLevel level)) {
            return false;
        }
        Optional<Cell> next = FieldPlanner.next(cells, resident.getX(), resident.getZ(), hasSeeds());
        if (next.isEmpty()) {
            if (level.getGameTime() < nextSearchGameTime) {
                return false;
            }
            scanFields(level);
            if (cells.isEmpty()) {
                report(ISSUE_NO_FIELD);
            } else if (ISSUE_NO_FIELD.equals(resident.brain().workIssue())) {
                report(null);
            }
            next = FieldPlanner.next(cells, resident.getX(), resident.getZ(), hasSeeds());
            if (next.isEmpty()) {
                nextSearchGameTime = level.getGameTime() + SEARCH_COOLDOWN_TICKS;
                return false;
            }
        }
        target = next.get();
        cells.remove(target);
        setPhase(Phase.TO_CELL);
        return true;
    }

    /**
     * Finds the farmland to work on. M21d: if the village has fields marked with a marker, the farmer works
     * exactly those (all of them, nearest cell first). Otherwise, as before, all farmland around the farm
     * (one height-map lookup per column, never loads chunks).
     */
    private void scanFields(ServerLevel level) {
        cells.clear();
        List<Building> fields = resident.brain().village() == null ? List.of() : resident.brain().village().buildings()
                .stream().filter(b -> b.type() == BuildingType.FIELD && b.isValid()).toList();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        if (!fields.isEmpty()) {
            for (Building field : fields) {
                for (AreaScanner.Column column : AreaService.columns(level, field)) {
                    addCell(level, pos, column.x(), column.z(), false);
                }
            }
            return;
        }
        BlockPos center = workplace().center();
        int radius = Professions.get(PROFESSION).map(p -> p.workRadius()).orElse(12);
        for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
            for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
                if (level.hasChunkAt(x, z)) {
                    int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                    if (Math.abs(top - center.getY()) <= MAX_HEIGHT_DIFFERENCE) {
                        addCell(level, pos, x, z, true);
                    }
                }
            }
        }
    }

    /** Adds the column as a cell if its top is farmland or a crop on farmland; optionally not inside buildings. */
    private void addCell(ServerLevel level, BlockPos.MutableBlockPos pos, int x, int z, boolean skipBuildings) {
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        BlockState state = level.getBlockState(pos.set(x, top, z));
        Cell cell = null;
        if (state.is(Blocks.FARMLAND)) {
            cell = new Cell(x, top + 1, z, CellState.EMPTY);
        } else if (state.getBlock() instanceof CropBlock crop
                && level.getBlockState(pos.set(x, top - 1, z)).is(Blocks.FARMLAND)) {
            cell = new Cell(x, top, z, crop.isMaxAge(state) ? CellState.RIPE : CellState.GROWING);
        }
        if (cell != null && !(skipBuildings && BuildingAreas.isInside(villageBuildings(), x, cell.y(), z))) {
            cells.add(cell);
        }
    }

    private void setPhase(Phase next) {
        phase = next;
        phaseTicks = 0;
        cooldown = 0;
        resetPath();
    }

    private void walkToCell() {
        BlockPos pos = new BlockPos(target.x(), target.y(), target.z());
        boolean pathEnded = resident.getNavigation().isDone() && phaseTicks > REPATH_TICKS;
        if (pos.closerToCenterThan(resident.position(), WORK_DISTANCE)
                || (pathEnded && pos.closerToCenterThan(resident.position(), WORK_DISTANCE_PATH_END))) {
            resident.getNavigation().stop();
            setPhase(Phase.WORK_CELL);
            return;
        }
        if (phaseTicks > GIVE_UP_TICKS) {
            continueWork(); // skip this cell
            return;
        }
        moveTowards(pos, 1);
    }

    private void workCell(ServerLevel level) {
        BlockPos pos = new BlockPos(target.x(), target.y(), target.z());
        resident.getLookControl().setLookAt(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        if (++cooldown < ToolTiers.ticks(WORK_TICKS, workSpeed())) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)) {
            swing();
            wearTool(level);
            Block.getDrops(state, level, pos, null).forEach(resident::carry);
            level.destroyBlock(pos, false, resident);
            Item seed = crop.asItem();
            plant(level, pos, resident.takeCarried(stack -> stack.is(seed)));
        } else if (state.isAir() && level.getBlockState(pos.below()).is(Blocks.FARMLAND)) {
            plant(level, pos, resident.takeCarried(FarmerGoal::isSeed));
        }
        continueWork();
    }

    /** Next cell; deliver when the hands are full or nothing is left to do. */
    private void continueWork() {
        target = null;
        if (!isFull() && needsSeeds()) {
            setPhase(Phase.FETCH);
            return;
        }
        if (!isFull() && findWork()) {
            return;
        }
        if (hasSomethingToDeliver()) {
            setPhase(Phase.DELIVER);
        } else {
            phase = null;
        }
    }

    private void plant(ServerLevel level, BlockPos pos, ItemStack seed) {
        if (seed.isEmpty()) {
            return;
        }
        if (seed.getItem() instanceof BlockItem blockItem && level.getBlockState(pos).isAir()) {
            BlockState crop = blockItem.getBlock().defaultBlockState();
            if (crop.canSurvive(level, pos)) {
                level.setBlockAndUpdate(pos, crop);
                return;
            }
        }
        resident.carry(seed); // could not plant: keep the seed
    }

    /** Shows why the farmer is idle in the book; null clears only the farmer's own problems. */
    private void report(String issue) {
        String now = resident.brain().workIssue();
        if (issue != null || ISSUE_NO_FIELD.equals(now) || ISSUE_NO_SEEDS.equals(now)) {
            resident.brain().setWorkIssue(issue);
        }
    }

    /** No seeds, empty farmland known, and fetching is not paused. */
    private boolean needsSeeds() {
        return resident.level() instanceof ServerLevel level && !hasSeeds() && mayFetch(level, "seeds")
                && cells.stream().anyMatch(c -> c.state() == CellState.EMPTY);
    }

    private boolean hasSeeds() {
        return resident.carriedCount(FarmerGoal::isSeed) > 0;
    }

    /** Items that plant a crop: wheat seeds, carrots, potatoes, beetroot seeds, ... */
    private static boolean isSeed(ItemStack stack) {
        return stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof CropBlock;
    }
}
