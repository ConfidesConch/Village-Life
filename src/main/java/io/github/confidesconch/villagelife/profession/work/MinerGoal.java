package io.github.confidesconch.villagelife.profession.work;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.confidesconch.villagelife.building.Building;
import io.github.confidesconch.villagelife.building.BuildingType;
import io.github.confidesconch.villagelife.core.registry.ModBlocks;
import io.github.confidesconch.villagelife.resident.ResidentEntity;
import io.github.confidesconch.villagelife.village.Village;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Work of the miner (M13e, owner's choice "1a 2a 3a"): digs an open pit in the nearest marked quarry (M28b:
 * fence + sign, no map zones), layer by layer from the top, down to the village's mine depth below its ground, keeping a staircase along
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
    /** M28d: the tunnel is finished or blocked (water, lava, no cobblestone to wall up a cave). */
    public static final String ISSUE_TUNNEL_DONE = "miner.tunnel_done";

    static final int STONE_RESERVE = 256;
    private static final int BASE_TICKS_PER_HARDNESS = 15;
    private static final int MIN_TICKS = 5;
    /** Like a player: blocks up to 4.5 blocks from the eyes can be dug. */
    private static final double REACH = 4.5;
    /** Above the ground, blocks higher than this above the miner's feet wait until he stands higher. */
    private static final int SEARCH_COOLDOWN_TICKS = 200;

    private enum Phase { TOOL, TORCH, TO_BLOCK, MINE, DELIVER }

    private final List<BlockPos> layer = new ArrayList<>();
    private final Set<BlockPos> skipped = new HashSet<>();
    private Phase phase;
    private BlockPos target;
    private int phaseTicks;
    private long nextSearchGameTime;
    private final MinerStock stock = new MinerStock();
    /** M28d: work in a tunnel instead of a quarry. */
    private final TunnelDigger tunnel = new TunnelDigger();
    private boolean inTunnel;
    /** The target is an open tunnel cell to wall up, not a block to dig. */
    private boolean sealing;
    private int torchStep;

    public MinerGoal(ResidentEntity resident, double speedModifier) {
        // basic tool: a stone pickaxe that never wears out – with wood he could not harvest iron and copper ore
        super(resident, speedModifier, PROFESSION, ItemTags.PICKAXES, Items.STONE_PICKAXE);
    }

    @Override
    public boolean canUse() {
        if (!isWorkTime() || !(resident.level() instanceof ServerLevel level)) {
            return false;
        }
        Building site = site();
        if (site == null) {
            resident.brain().setWorkIssue(ISSUE_NO_WORK_AREA);
            return startDeliver();
        }
        inTunnel = site.type() == BuildingType.TUNNEL;
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
        if (inTunnel && TunnelDigger.needsTorches(resident)) {
            TunnelDigger.craftTorches(resident);
            if (TunnelDigger.needsTorches(resident) && mayFetch(level, "torch")) {
                setPhase(Phase.TORCH);
                return true;
            }
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
        tunnel.newDay(resident.level().getOverworldClockTime() / 24000L);
        torchStep = 0;
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
            case TORCH -> fetchTorches(level);
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
        Building quarry = site();
        Village village = resident.brain().village();
        int depth = village == null ? 16 : village.settings().mineDepth();
        return new QuarryPlan(quarry.min().getX(), quarry.min().getZ(), quarry.max().getX(), quarry.max().getZ(),
                quarry.min().getY() - 1, depth);
    }

    /**
     * Above the ground the miner takes the best block within reach (hills, M13e fix: he cannot climb a
     * steep rock); below the ground strictly layer by layer, nearest first, so the stairs stay usable.
     */
    private boolean findBlock(ServerLevel level) {
        sealing = false;
        Building site = site();
        if (site == null) {
            return false;
        }
        if (site.type() == BuildingType.TUNNEL) {
            return findInTunnel(level, site);
        }
        BlockPos high = QuarrySearch.nextAboveGround(level, plan(), resident, pos -> isMinable(level, pos));
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
        if (!QuarrySearch.scanTopLayer(level, plan(), resident.blockPosition(), pos -> isMinable(level, pos), layer)) {
            resident.brain().setWorkIssue(ISSUE_FINISHED);
            return false;
        }
        if (ISSUE_FINISHED.equals(resident.brain().workIssue())) {
            resident.brain().setWorkIssue(null);
        }
        nextSearchGameTime = 0;
        return findBlock(level);
    }

    /** M21d: marked fields and pastures (and their fences) are never dug up. */
    private List<Building> fieldsAndPastures() {
        Village village = resident.brain().village();
        return village == null ? List.of() : village.buildings().stream()
                .filter(b -> b.type().isArea() && b.type() != BuildingType.QUARRY).toList();
    }

    /** M28b: the nearest valid marked quarry (M28d: or tunnel), or null. */
    private Building site() {
        Village village = resident.brain().village();
        return village == null ? null : WorkAreas.of(village, workplace(), PROFESSION).orElse(null);
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

    // ---- tunnel (M28d) -------------------------------------------------------------------------

    private boolean findInTunnel(ServerLevel level, Building site) {
        if (level.getGameTime() < nextSearchGameTime || !level.isLoaded(site.markerPos())) {
            return false;
        }
        Village village = resident.brain().village();
        tunnel.use(level, site, village == null ? 16 : village.settings().mineDepth());
        TunnelDigger.Target next = tunnel.next(level, pos -> isMinable(level, pos), !TunnelDigger.needsTorches(resident));
        if (next == null) {
            if (!tunnel.waitingForChunks()) { // review 15.11.2026: an unloaded tunnel part is no "finished"
                resident.brain().setWorkIssue(ISSUE_TUNNEL_DONE);
            }
            nextSearchGameTime = level.getGameTime() + SEARCH_COOLDOWN_TICKS;
            return false;
        }
        if (ISSUE_TUNNEL_DONE.equals(resident.brain().workIssue())) {
            resident.brain().setWorkIssue(null);
        }
        target = next.pos();
        sealing = next.seal();
        setPhase(Phase.TO_BLOCK);
        return true;
    }

    /**
     * Torches from storage; if there are none, he makes them from coal (carried or from storage) and
     * sticks from storage (fix 15.11.2026: before, only with coal already in his pack).
     */
    private void fetchTorches(ServerLevel level) {
        FetchStatus status = switch (torchStep) {
            case 0 -> fetchStep(level, "torch", stack -> stack.is(Items.TORCH), TunnelDigger.TORCHES_TO_KEEP);
            case 1 -> resident.carriedCount(stack -> stack.is(ItemTags.COALS)) > 0 ? FetchStatus.DONE
                    : fetchStep(level, "coal", stack -> stack.is(ItemTags.COALS), TunnelDigger.COAL_TO_KEEP);
            default -> fetchStep(level, "stick", stack -> stack.is(Items.STICK), TunnelDigger.COAL_TO_KEEP);
        };
        if (status == FetchStatus.RUNNING) {
            return;
        }
        boolean gotTorches = torchStep == 0 && !TunnelDigger.needsTorches(resident);
        if (!gotTorches && torchStep < 2 && (torchStep == 0 || status == FetchStatus.DONE)) {
            torchStep++; // no torches in storage: coal next, then sticks
            return;
        }
        torchStep = 0;
        TunnelDigger.craftTorches(resident);
        continueWork(level);
    }

    @Override
    protected int keepMaterial(ItemStack stack) {
        if (!inTunnel) {
            return super.keepMaterial(stack);
        }
        if (stack.is(Items.TORCH)) {
            return TunnelDigger.TORCHES_TO_KEEP;
        }
        if (stack.is(Items.STICK) || stack.is(ItemTags.COALS)) {
            return TunnelDigger.COAL_TO_KEEP;
        }
        return stack.is(ItemTags.STONE_CRAFTING_MATERIALS) ? TunnelDigger.COBBLE_TO_KEEP : super.keepMaterial(stack);
    }

    // ---- digging ---------------------------------------------------------------------------

    private void walkToBlock(ServerLevel level) {
        if (!sealing && !isMinable(level, target)) {
            continueWork(level);
            return;
        }
        boolean pathEnded = resident.getNavigation().isDone() && phaseTicks > REPATH_TICKS;
        if (target.closerToCenterThan(resident.getEyePosition(), REACH)
                || (pathEnded && target.closerToCenterThan(resident.getEyePosition(), REACH + 1.0))) {
            resident.getNavigation().stop();
            if (sealing) {
                swing();
                tunnel.seal(level, target, resident); // M28d: wall up the cave, set the torch
                continueWork(level);
                return;
            }
            setPhase(Phase.MINE);
            return;
        }
        if (phaseTicks > GIVE_UP_TICKS) {
            if (sealing) {
                tunnel.giveUp(target);
            }
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
        if (MinerStock.isJunk(stack)) {
            return;
        }
        if (stack.is(ItemTags.STONE_CRAFTING_MATERIALS) && inTunnel
                && resident.carriedCount(s -> s.is(ItemTags.STONE_CRAFTING_MATERIALS)) < TunnelDigger.COBBLE_TO_KEEP) {
            resident.carry(stack); // M28d: kept for walling up caves
            return;
        }
        if (stack.is(ItemTags.STONE_CRAFTING_MATERIALS)) {
            int wanted = QuarryPlan.stoneToKeep(stock.stoneInStorage(level, resident.brain().village()) + resident.carriedCount(s -> s.is(ItemTags.STONE_CRAFTING_MATERIALS)),
                    STONE_RESERVE);
            if (wanted <= 0) {
                return;
            }
            stack = stack.copyWithCount(Math.min(stack.getCount(), wanted));
        }
        resident.carry(stack);
    }

}
