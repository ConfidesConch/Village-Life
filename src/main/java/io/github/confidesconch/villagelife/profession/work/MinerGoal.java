package io.github.confidesconch.villagelife.profession.work;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.confidesconch.villagelife.building.Building;
import io.github.confidesconch.villagelife.building.BuildingType;
import io.github.confidesconch.villagelife.core.registry.ModBlocks;
import io.github.confidesconch.villagelife.inventory.StorageAccess;
import io.github.confidesconch.villagelife.resident.ResidentEntity;
import io.github.confidesconch.villagelife.village.Village;
import io.github.confidesconch.villagelife.village.VillageZone;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Work of the miner (M13e, owner's choice "1a 2a 3a"): digs an open pit in its work area, layer by
 * layer from the top, down to the village's mine depth below the workplace, keeping a staircase along
 * the edge ({@link QuarryPlan}). Ores, coal and other finds always go to storage; cobblestone only until
 * the storage holds {@link #STONE_RESERVE}; dirt, gravel, sand and the other stones are dropped.
 * Never digs next to water or lava (that block stays as a wall). Unlike the other workers his basic
 * tool is a stone pickaxe (owner's wish), so he can harvest iron and copper ore from the start; blocks
 * that need more (e.g. diamond ore) wait until he has a better pickaxe from storage.
 */
public class MinerGoal extends WorkGoal {
    public static final String PROFESSION = "villagelife:miner";
    /** Shown in the book as {@code villagelife.problem.work.miner.finished}. */
    public static final String ISSUE_FINISHED = "miner.finished";

    static final int STONE_RESERVE = 256;
    private static final int BASE_TICKS_PER_HARDNESS = 15;
    private static final int MIN_TICKS = 5;
    /** Like a player: blocks up to 4.5 blocks from the eyes can be dug. */
    private static final double REACH = 4.5;
    /** Above the ground, blocks higher than this above the miner's feet wait until he stands higher. */
    private static final int REACHABLE_ABOVE_FEET = 4;
    private static final int SEARCH_COOLDOWN_TICKS = 200;
    /** How far above the workplace a hill in the area is still cleared. */
    private static final int MAX_HEIGHT_ABOVE = 16;
    private static final int STONE_COUNT_INTERVAL_TICKS = 600;

    private enum Phase { TOOL, TO_BLOCK, MINE, DELIVER }

    private final List<BlockPos> layer = new ArrayList<>();
    private final Set<BlockPos> skipped = new HashSet<>();
    private Phase phase;
    private BlockPos target;
    private int phaseTicks;
    private long nextSearchGameTime;
    private int stoneInStorage;
    private long stoneCountedGameTime = -STONE_COUNT_INTERVAL_TICKS;

    public MinerGoal(ResidentEntity resident, double speedModifier) {
        // basic tool: a stone pickaxe that never wears out – with wood he could not harvest iron and copper ore
        super(resident, speedModifier, PROFESSION, ItemTags.PICKAXES, Items.STONE_PICKAXE);
    }

    @Override
    public boolean canUse() {
        if (!isWorkTime() || !(resident.level() instanceof ServerLevel level)) {
            return false;
        }
        if (workArea() == null) {
            resident.brain().setWorkIssue(ISSUE_NO_WORK_AREA);
            return startDeliver();
        }
        if (ISSUE_NO_WORK_AREA.equals(resident.brain().workIssue())) {
            resident.brain().setWorkIssue(null);
        }
        if (isFull()) {
            return startDeliver();
        }
        if (mayFetchTool(level)) {
            setPhase(Phase.TOOL);
            return true;
        }
        return findBlock(level) || startDeliver();
    }

    private boolean startDeliver() {
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
        layer.clear();
        skipped.clear(); // a new day: try blocks again (maybe with a better pickaxe)
        super.stop();
    }

    @Override
    public void tick() {
        if (!(resident.level() instanceof ServerLevel level) || phase == null) {
            return;
        }
        phaseTicks++;
        switch (phase) {
            case TO_BLOCK -> walkToBlock(level);
            case MINE -> mine(level);
            case TOOL -> {
                if (fetchToolStep(level) != FetchStatus.RUNNING) {
                    continueWork(level);
                }
            }
            case DELIVER -> {
                if (deliverStep(level)) {
                    continueWork(level);
                }
            }
        }
    }

    private void setPhase(Phase next) {
        phase = next;
        phaseTicks = 0;
        resetPath();
    }

    private void continueWork(ServerLevel level) {
        target = null;
        if (isFull()) {
            setPhase(Phase.DELIVER);
        } else if (!findBlock(level)) {
            phase = hasSomethingToDeliver() ? Phase.DELIVER : null;
            phaseTicks = 0;
        }
    }

    // ---- finding the next block ----------------------------------------------------------------

    private QuarryPlan plan() {
        VillageZone zone = workArea();
        Village village = resident.brain().village();
        int depth = village == null ? 16 : village.settings().mineDepth();
        return new QuarryPlan(zone.minX(), zone.minZ(), zone.maxX(), zone.maxZ(), workplace().center().getY() - 1, depth);
    }

    /**
     * Above the ground the miner takes the best block within reach (hills, M13e fix: he cannot climb a
     * steep rock); below the ground strictly layer by layer, nearest first, so the stairs stay usable.
     */
    private boolean findBlock(ServerLevel level) {
        if (workArea() == null) {
            return false;
        }
        BlockPos high = nextAboveGround(level, plan());
        if (high != null) {
            target = high;
            setPhase(Phase.TO_BLOCK);
            return true;
        }
        while (!layer.isEmpty()) {
            BlockPos next = layer.removeFirst();
            if (isMinable(level, next)) {
                target = next;
                setPhase(Phase.TO_BLOCK);
                return true;
            }
        }
        if (level.getGameTime() < nextSearchGameTime) {
            return false;
        }
        nextSearchGameTime = level.getGameTime() + SEARCH_COOLDOWN_TICKS;
        if (!scanTopLayer(level)) {
            resident.brain().setWorkIssue(ISSUE_FINISHED);
            return false;
        }
        if (ISSUE_FINISHED.equals(resident.brain().workIssue())) {
            resident.brain().setWorkIssue(null);
        }
        nextSearchGameTime = 0;
        return findBlock(level);
    }

    /**
     * The block above the ground the miner should dig next: near him horizontally, higher ones first,
     * but only up to {@link #REACHABLE_ABOVE_FEET} above his feet. Null if there is none in reach.
     */
    private BlockPos nextAboveGround(ServerLevel level, QuarryPlan plan) {
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
                    if (cost < bestCost && isMinable(level, pos.set(x, y, z))) {
                        best = pos.immutable();
                        bestCost = cost;
                    }
                }
            }
        }
        return best;
    }

    /** Fills {@link #layer} with the minable blocks of the highest layer below the ground that still has some. */
    private boolean scanTopLayer(ServerLevel level) {
        QuarryPlan plan = plan();
        int top = plan.groundY();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = top; y > plan.floorY(); y--) {
            for (int x = plan.minX(); x <= plan.maxX(); x++) {
                for (int z = plan.minZ(); z <= plan.maxZ(); z++) {
                    pos.set(x, y, z);
                    if (plan.shouldMine(x, y, z) && isMinable(level, pos)) {
                        layer.add(pos.immutable());
                    }
                }
            }
            if (!layer.isEmpty()) {
                BlockPos from = resident.blockPosition();
                layer.sort(Comparator.comparingDouble(p -> p.distSqr(from)));
                return true;
            }
        }
        return false;
    }

    private int highestBlock(ServerLevel level, QuarryPlan plan) {
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

    /** M21d: marked fields and pastures (and their fences) are never dug up. */
    private List<Building> fieldsAndPastures() {
        Village village = resident.brain().village();
        return village == null ? List.of() : village.buildings().stream().filter(b -> b.type().isArea()).toList();
    }

    /** Solid, breakable, no block entity, not in a building, not next to water or lava, not skipped. */
    private boolean isMinable(ServerLevel level, BlockPos pos) {
        if (skipped.contains(pos) || !level.isLoaded(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !state.getFluidState().isEmpty() || state.hasBlockEntity()
                || state.getDestroySpeed(level, pos) < 0
                || state.is(ModBlocks.VILLAGE_CENTER.get()) || state.is(ModBlocks.BUILDING_MARKER.get()) // bug review 08.11.
                || BuildingAreas.isInside(villageBuildings(), pos.getX(), pos.getY(), pos.getZ())
                || BuildingAreas.isInside(fieldsAndPastures(), pos.getX(), pos.getY(), pos.getZ())) {
            return false;
        }
        for (Direction side : Direction.values()) {
            if (!level.getFluidState(pos.relative(side)).isEmpty()) {
                return false; // stays as a wall: nothing floods the pit
            }
        }
        return true;
    }

    // ---- digging ---------------------------------------------------------------------------

    private void walkToBlock(ServerLevel level) {
        if (!isMinable(level, target)) {
            continueWork(level);
            return;
        }
        boolean pathEnded = resident.getNavigation().isDone() && phaseTicks > REPATH_TICKS;
        if (target.closerToCenterThan(resident.getEyePosition(), REACH)
                || (pathEnded && target.closerToCenterThan(resident.getEyePosition(), REACH + 1.0))) {
            resident.getNavigation().stop();
            setPhase(Phase.MINE);
            return;
        }
        if (phaseTicks > GIVE_UP_TICKS) {
            skipped.add(target); // cannot get there today
            continueWork(level);
            return;
        }
        moveTowards(target, 1);
    }

    private void mine(ServerLevel level) {
        BlockState state = level.getBlockState(target);
        resident.getLookControl().setLookAt(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
        if (!isMinable(level, target)) {
            continueWork(level);
            return;
        }
        ItemStack tool = resident.getMainHandItem();
        if (state.requiresCorrectToolForDrops() && !tool.isCorrectToolForDrops(state)) {
            skipped.add(target); // needs a better pickaxe
            continueWork(level);
            return;
        }
        int basic = Math.max(MIN_TICKS, Math.round(state.getDestroySpeed(level, target) * BASE_TICKS_PER_HARDNESS));
        if (phaseTicks % 5 == 0) {
            swing();
        }
        if (phaseTicks < ToolTiers.ticks(basic, workSpeed())) {
            return;
        }
        List<ItemStack> drops = Block.getDrops(state, level, target, null, resident, tool);
        level.destroyBlock(target, false, resident);
        wearTool(level);
        drops.forEach(stack -> keepOrDrop(level, stack));
        continueWork(level);
    }

    /** Rule 2a: finds always, cobblestone up to the storage reserve, dirt/gravel/sand/other stone not. */
    private void keepOrDrop(ServerLevel level, ItemStack stack) {
        if (isJunk(stack)) {
            return;
        }
        if (stack.is(ItemTags.STONE_CRAFTING_MATERIALS)) {
            int wanted = QuarryPlan.stoneToKeep(stoneInStorage(level) + resident.carriedCount(s -> s.is(ItemTags.STONE_CRAFTING_MATERIALS)),
                    STONE_RESERVE);
            if (wanted <= 0) {
                return;
            }
            stack = stack.copyWithCount(Math.min(stack.getCount(), wanted));
        }
        resident.carry(stack);
    }

    private static boolean isJunk(ItemStack stack) {
        return stack.is(ItemTags.DIRT) || stack.is(ItemTags.SAND) || stack.is(Items.GRAVEL)
                || stack.is(Items.ANDESITE) || stack.is(Items.DIORITE) || stack.is(Items.GRANITE)
                || stack.is(Items.TUFF) || stack.is(Items.CALCITE) || stack.is(Items.NETHERRACK);
    }

    /** Cobblestone (and the like) in the village's storage buildings, counted every 30 seconds. */
    private int stoneInStorage(ServerLevel level) {
        if (level.getGameTime() - stoneCountedGameTime < STONE_COUNT_INTERVAL_TICKS) {
            return stoneInStorage;
        }
        stoneCountedGameTime = level.getGameTime();
        int count = 0;
        Village village = resident.brain().village();
        for (Building building : village == null ? List.<Building>of() : village.buildings()) {
            if (building.type() != BuildingType.STORAGE || !building.isValid()) {
                continue;
            }
            for (BlockPos pos : building.containers()) {
                Container container = StorageAccess.containerAt(level, pos);
                for (int slot = 0; container != null && slot < container.getContainerSize(); slot++) {
                    ItemStack stack = container.getItem(slot);
                    count += stack.is(ItemTags.STONE_CRAFTING_MATERIALS) ? stack.getCount() : 0;
                }
            }
        }
        stoneInStorage = count;
        return count;
    }
}
