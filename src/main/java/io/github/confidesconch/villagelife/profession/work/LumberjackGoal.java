package io.github.confidesconch.villagelife.profession.work;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.github.confidesconch.villagelife.profession.Professions;
import io.github.confidesconch.villagelife.profession.work.TreeScanner.Pos;
import io.github.confidesconch.villagelife.resident.ResidentEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Work of the lumberjack (M8a/M9a): find the nearest natural tree around the workplace, walk there,
 * chop it log by log from the top, shake off its leaves (saplings, sticks, apples), replant with a
 * sapling from the own inventory, go on to the next tree while there is room, and bring the wood to
 * the workplace or a storage building. Keeps a few saplings; fetches some from storage if it has none.
 */
public class LumberjackGoal extends WorkGoal {
    public static final String PROFESSION = "villagelife:lumberjack";

    private static final int CHOP_TICKS = 15;
    private static final int SEARCH_COOLDOWN_TICKS = 200;
    /** Start chopping when this close to the trunk, or a bit farther if the path ends there. */
    private static final double CHOP_DISTANCE = 3.0;
    private static final double CHOP_DISTANCE_PATH_END = 5.0;
    /** Unreachable trees are skipped for this long. */
    private static final int IGNORE_TREE_TICKS = 6000;

    /** Saplings the lumberjack keeps for replanting (and fetches from storage when it has none). */
    private static final int SAPLINGS_TO_KEEP = 8;

    private enum Phase { TOOL, FETCH, TO_TREE, CHOP, DELIVER }

    private final Map<Pos, Long> ignoredTrees = new HashMap<>();
    private final Deque<Pos> logsToChop = new ArrayDeque<>();
    /** All logs of the current tree, to find its leaves afterwards. */
    private final List<Pos> treeLogs = new ArrayList<>();

    private Phase phase;
    private Pos treeBase;
    /** A confirmed tree that is not chopped completely yet (work was interrupted); finished first. */
    private Pos unfinishedTree;
    private int cooldown;
    private int phaseTicks;
    private long nextSearchGameTime;
    /** Id of the last chopped log block, to pick the matching sapling. */
    private String lastChoppedLog;

    public LumberjackGoal(ResidentEntity resident, double speedModifier) {
        super(resident, speedModifier, PROFESSION, ItemTags.AXES, Items.WOODEN_AXE);
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
        return chooseWork();
    }

    /** Saplings first if there are none, then the next tree, else deliver; false if nothing to do. */
    private boolean chooseWork() {
        if (resident.level() instanceof ServerLevel level && !hasSaplings() && mayFetch(level, "saplings")) {
            setPhase(Phase.FETCH);
            return true;
        }
        if (findTree()) {
            return true;
        }
        if (hasSomethingToDeliver()) {
            setPhase(Phase.DELIVER);
            return true;
        }
        phase = null;
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        return isWorkTime() && phase != null;
    }

    @Override
    public void start() {
        super.start();
        phaseTicks = 0;
    }

    @Override
    public void stop() {
        if (treeBase != null && !logsToChop.isEmpty()) {
            unfinishedTree = treeBase;
        }
        phase = null;
        treeBase = null;
        logsToChop.clear();
        treeLogs.clear();
        super.stop();
    }

    @Override
    public void tick() {
        if (!(resident.level() instanceof ServerLevel level) || phase == null) {
            return;
        }
        phaseTicks++;
        switch (phase) {
            case TO_TREE -> walkToTree();
            case CHOP -> chop(level);
            case TOOL -> {
                if (fetchToolStep(level) != FetchStatus.RUNNING) {
                    chooseWork();
                }
            }
            case FETCH -> {
                if (fetchStep(level, "saplings", LumberjackGoal::isSapling, SAPLINGS_TO_KEEP) != FetchStatus.RUNNING) {
                    phase = findTree() ? Phase.TO_TREE : null;
                }
            }
            case DELIVER -> {
                if (deliverStep(level)) {
                    // at the containers anyway: a good moment to look for a better tool
                    if (mayFetchTool(level)) {
                        setPhase(Phase.TOOL);
                    } else {
                        chooseWork();
                    }
                }
            }
        }
    }

    /** Looks for a tree (throttled after a failed search); on success the phase becomes TO_TREE. */
    private boolean findTree() {
        if (!(resident.level() instanceof ServerLevel level) || level.getGameTime() < nextSearchGameTime) {
            return false;
        }
        long now = level.getGameTime();
        ignoredTrees.values().removeIf(until -> until < now);
        LevelTreeView view = new LevelTreeView(level, villageBuildings());
        Optional<TreeScanner.Tree> tree = Optional.empty();
        if (unfinishedTree != null) {
            tree = TreeScanner.treeAt(view, unfinishedTree, false);
            unfinishedTree = null;
        }
        if (tree.isEmpty()) {
            int radius = Professions.get(PROFESSION).map(p -> p.workRadius()).orElse(24);
            BlockPos center = workplace().center();
            tree = TreeScanner.findNearest(view, center.getX(), center.getZ(), radius, ignoredTrees.keySet());
        }
        if (tree.isEmpty()) {
            nextSearchGameTime = now + SEARCH_COOLDOWN_TICKS;
            return false;
        }
        treeBase = tree.get().base();
        logsToChop.clear();
        logsToChop.addAll(tree.get().logs());
        treeLogs.clear();
        treeLogs.addAll(tree.get().logs());
        setPhase(Phase.TO_TREE);
        return true;
    }

    private void setPhase(Phase next) {
        phase = next;
        phaseTicks = 0;
        cooldown = 0;
        resetPath();
    }

    private void walkToTree() {
        BlockPos base = toBlockPos(treeBase);
        boolean pathEnded = resident.getNavigation().isDone() && phaseTicks > REPATH_TICKS;
        if (base.closerToCenterThan(resident.position(), CHOP_DISTANCE)
                || (pathEnded && base.closerToCenterThan(resident.position(), CHOP_DISTANCE_PATH_END))) {
            resident.getNavigation().stop();
            setPhase(Phase.CHOP);
            return;
        }
        if (phaseTicks > GIVE_UP_TICKS) {
            giveUpTree();
            return;
        }
        moveTowards(base, 2);
    }

    private void chop(ServerLevel level) {
        BlockPos base = toBlockPos(treeBase);
        resident.getLookControl().setLookAt(base.getX() + 0.5, base.getY() + 1.5, base.getZ() + 0.5);
        if (++cooldown < ToolTiers.ticks(CHOP_TICKS, workSpeed())) {
            return;
        }
        cooldown = 0;
        while (!logsToChop.isEmpty()) {
            BlockPos pos = toBlockPos(logsToChop.poll());
            BlockState state = level.getBlockState(pos);
            if (state.is(BlockTags.LOGS)) {
                swing();
                lastChoppedLog = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                wearTool(level);
                Block.getDrops(state, level, pos, null).forEach(resident::carry);
                level.destroyBlock(pos, false, resident);
                if (isFull()) {
                    unfinishedTree = logsToChop.isEmpty() ? null : treeBase; // finish it after delivering
                    treeBase = null;
                    logsToChop.clear();
                    setPhase(Phase.DELIVER);
                }
                return;
            }
        }
        shakeLeaves(level);
        plantSapling(level, base);
        treeBase = null;
        treeLogs.clear();
        // with room left, go on to the next tree first; deliver when full or when no tree is left
        if (resident.carriedCount() - keptCount() < CARRY_LIMIT / 2 && findTree()) {
            return;
        }
        setPhase(Phase.DELIVER);
    }

    private void giveUpTree() {
        if (treeBase != null && resident.level() instanceof ServerLevel level) {
            ignoredTrees.put(treeBase, level.getGameTime() + IGNORE_TREE_TICKS);
        }
        treeBase = null;
        logsToChop.clear();
        treeLogs.clear();
        if (hasSomethingToDeliver()) {
            setPhase(Phase.DELIVER);
        } else {
            phase = null;
        }
    }

    /** The sapling that matches the logs that were chopped (oak_log → oak_sapling); null if unknown. */
    private Block saplingFor() {
        Identifier logId = lastChoppedLog == null ? null : Identifier.tryParse(lastChoppedLog);
        if (logId == null) {
            return null;
        }
        String wood = logId.getPath().replace("stripped_", "").replace("_log", "").replace("_wood", "");
        String sapling = "mangrove".equals(wood) ? "mangrove_propagule" : wood + "_sapling";
        return BuiltInRegistries.BLOCK.getOptional(Identifier.fromNamespaceAndPath(logId.getNamespace(), sapling))
                .orElse(null);
    }

    /** Removes the tree's natural leaves at once and keeps their drops (saplings, sticks, apples). */
    private void shakeLeaves(ServerLevel level) {
        if (treeLogs.isEmpty()) {
            return;
        }
        for (Pos leaf : TreeScanner.leavesOf(new LevelTreeView(level, villageBuildings()), treeLogs)) {
            BlockPos pos = toBlockPos(leaf);
            BlockState state = level.getBlockState(pos);
            if (state.is(BlockTags.LEAVES)) {
                Block.getDrops(state, level, pos, null).forEach(resident::carry);
                level.removeBlock(pos, false);
            }
        }
    }

    /** Replants with a matching sapling from the inventory, or any sapling that can grow there. */
    private void plantSapling(ServerLevel level, BlockPos base) {
        if (!level.getBlockState(base).isAir()) {
            return;
        }
        Block matching = saplingFor();
        ItemStack sapling = matching == null ? ItemStack.EMPTY : resident.takeCarried(stack -> stack.is(matching.asItem()));
        if (sapling.isEmpty()) {
            sapling = resident.takeCarried(stack -> isSapling(stack) && canGrow(level, base, stack));
        }
        if (sapling.getItem() instanceof BlockItem blockItem && canGrow(level, base, sapling)) {
            level.setBlockAndUpdate(base, blockItem.getBlock().defaultBlockState());
        } else {
            resident.carry(sapling); // keep it
        }
    }

    private static boolean canGrow(ServerLevel level, BlockPos pos, ItemStack sapling) {
        return sapling.getItem() instanceof BlockItem blockItem
                && blockItem.getBlock().defaultBlockState().is(BlockTags.SAPLINGS)
                && blockItem.getBlock().defaultBlockState().canSurvive(level, pos);
    }

    @Override
    protected int keepMaterial(ItemStack stack) {
        return isSapling(stack) ? SAPLINGS_TO_KEEP : 0;
    }

    private boolean hasSaplings() {
        return resident.carriedCount(LumberjackGoal::isSapling) > 0;
    }

    private static boolean isSapling(ItemStack stack) {
        return stack.is(ItemTags.SAPLINGS);
    }

    static BlockPos toBlockPos(Pos pos) {
        return new BlockPos(pos.x(), pos.y(), pos.z());
    }
}
