package io.github.confidesconch.villagelife.resident.ai;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import io.github.confidesconch.villagelife.VillageLife;
import io.github.confidesconch.villagelife.core.config.SyncedConfig;
import io.github.confidesconch.villagelife.core.util.ModProfiler;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.phys.Vec3;

/**
 * Ground navigation for residents.
 *
 * <p>M12c: measures every path search (detail line under "residents" in {@code /villagelife debug perf})
 * and keeps to a shared budget of searches per server tick ({@link PathBudget}, config
 * {@code pathSearchesPerTick}). A refused search returns no path; the goal tries again shortly. A target
 * that could not be reached is not searched again for 5 seconds.
 *
 * <p>M21a (owner's choice "1a 2a 3a 4a"): far targets are reached in stages of about 40 blocks (small,
 * quick searches, the next stage when one is walked); a resident that does not get on for 3 seconds
 * avoids that spot for a while and looks for another way; one that gets no closer for 30 seconds is
 * put a bit further along its way – only if no player sees it. Ladders: {@link ResidentNodeEvaluator}.
 * Jumps down at most 2 blocks, unless the target is further below ({@link #allowsDeepFall}).
 */
public class ResidentNavigation extends GroundPathNavigation {
    /** One budget for all residents; only used on the server thread. */
    private static final PathBudget BUDGET = new PathBudget();

    /** A target that could not be reached is not searched again for this long (M12c). */
    private static final int FAILED_TARGET_TICKS = 100;
    /** Targets further than this are walked to in stages, each searched within this length. */
    private static final double STAGE_DISTANCE = 48;
    private static final float STAGE_LENGTH = 40;
    private static final int MAX_STAGES = 12;
    /** A spot where the resident got stuck is avoided this long. */
    private static final long AVOID_TICKS = 600;
    /** Without a new request and without a path, the target is forgotten after this long. */
    private static final long REQUEST_TICKS = 100;
    private static final double WATCH_RANGE = 48;
    /** M21: small searches go about twice the distance plus this; at least this share of the nodes. */
    private static final float NARROW_EXTRA = 16;
    private static final float MIN_NODE_SHARE = 0.2F;

    private BlockPos failedTarget;
    private long failedUntil;
    /** M21: a target whose small search failed – searched in full next time. */
    private BlockPos wideTarget;
    /** M21: time of all path searches (to measure walking without them); server thread only. */
    private static long searchNanos;

    /** Where the current goal wants to go (M21a), or null. */
    private BlockPos goal;
    private int goalReach;
    private double goalSpeed = 1.0;
    private long requestedAt;
    private boolean staged;
    private int stages;
    private long nextStageAt;
    private boolean deepFall;
    private boolean internal;
    /** The current goal chases an entity (a fight, a wagon): never put somewhere else. */
    private boolean chasing;
    private boolean entityRequest;
    private Path lastPath;
    private final StuckTracker tracker = new StuckTracker();
    private final Map<Long, Long> avoided = new HashMap<>();

    public ResidentNavigation(Mob mob, Level level) {
        super(mob, level);
    }

    /** Residents plan paths through fence gates (M13c) and up and down ladders (M21a), see {@link ResidentNodeEvaluator}. */
    @Override
    protected PathFinder createPathFinder(int maxVisitedNodes) {
        nodeEvaluator = new ResidentNodeEvaluator(this::isAvoided);
        return new PathFinder(nodeEvaluator, maxVisitedNodes);
    }

    /** M21a: jumps of more than 2 blocks only if the target lies further below. */
    public boolean allowsDeepFall() {
        return deepFall;
    }

    private boolean isAvoided(long pos) {
        Long until = avoided.get(pos);
        return until != null && until > level.getGameTime();
    }

    /** A path that does not reach the target still helps if it leads somewhere (then the resident walks it). */
    private boolean bringsCloser(Path path) {
        return path.getEndNode() != null && mob.blockPosition().distManhattan(path.getEndNode().asBlockPos()) > 2;
    }

    @Override
    protected Path createPath(Set<BlockPos> targets, int radiusOffset, boolean above, int reachRange, float maxPathLength) {
        BlockPos single = targets.size() == 1 ? targets.iterator().next() : null;
        if (!level.isClientSide() && !internal) {
            remember(single, reachRange);
        }
        Path current = getPath();
        boolean reuse = current != null && !current.isDone() && targets.contains(getTargetPos());
        if (level.isClientSide() || reuse) {
            return super.createPath(targets, radiusOffset, above, reachRange, maxPathLength); // cheap: no new search
        }
        if (single != null && single.equals(failedTarget) && level.getGameTime() < failedUntil) {
            return null; // tried a moment ago and could not get there: do not search in vain again
        }
        if (!BUDGET.tryStart(level.getGameTime(), SyncedConfig.PATH_SEARCHES_PER_TICK.get())) {
            ModProfiler.addDetail(ModProfiler.PATHS_DEFERRED, 0, 1);
            return null;
        }
        boolean inStages = single != null && horizontalDistance(single) > STAGE_DISTANCE;
        deepFall = single != null && single.getY() < mob.getBlockY() - 2;
        // M21: a search only as big as the way needs (about twice the distance); if that fails, a full one
        boolean narrow = single != null && !inStages && !single.equals(wideTarget);
        float length = inStages ? Math.min(maxPathLength, STAGE_LENGTH)
                : narrow ? Math.min(maxPathLength, (float) (mob.position().distanceTo(Vec3.atCenterOf(single)) * 2 + NARROW_EXTRA))
                : maxPathLength;
        setMaxVisitedNodesMultiplier(Math.max(MIN_NODE_SHARE, Math.min(1.0F, length / Math.max(1.0F, maxPathLength))));
        long start = System.nanoTime();
        try {
            Path path = super.createPath(targets, radiusOffset, above, reachRange, length);
            boolean failed = path == null || (!path.canReach() && !bringsCloser(path));
            if (single != null && failed && narrow) {
                wideTarget = single; // next time: the full search, right away
            } else if (single != null && failed) {
                failedTarget = single;
                failedUntil = level.getGameTime() + FAILED_TARGET_TICKS;
                wideTarget = null;
            }
            staged = inStages && path != null && !path.canReach();
            return path;
        } finally {
            resetMaxVisitedNodesMultiplier();
            long took = System.nanoTime() - start;
            BUDGET.finished(took);
            searchNanos += took;
            ModProfiler.addDetail(ModProfiler.PATHS, took, 1);
        }
    }

    @Override
    public Path createPath(Entity target, int reachRange) {
        entityRequest = true;
        try {
            return super.createPath(target, reachRange);
        } finally {
            entityRequest = false;
        }
    }

    /** A goal asked for this target: remember it, so the walk can go on in stages and be watched. */
    private void remember(BlockPos target, int reach) {
        long now = level.getGameTime();
        if (target == null) {
            goal = null;
            return;
        }
        if (goal == null || !goal.closerThan(target, 3.0)) {
            tracker.reset(now, mob.getX(), mob.getY(), mob.getZ(), Math.sqrt(mob.distanceToSqr(Vec3.atBottomCenterOf(target))));
        }
        goal = target;
        goalReach = reach;
        chasing = entityRequest;
        requestedAt = now;
        stages = 0;
    }

    private double horizontalDistance(BlockPos target) {
        double dx = target.getX() + 0.5 - mob.getX();
        double dz = target.getZ() + 0.5 - mob.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    @Override
    public boolean moveTo(Path newPath, double speedModifier) {
        if (newPath != null) {
            goalSpeed = speedModifier;
            lastPath = newPath;
        }
        return super.moveTo(newPath, speedModifier);
    }

    /** A goal stops the resident: the target is forgotten (internal stops keep it). */
    @Override
    public void stop() {
        super.stop();
        if (!internal) {
            goal = null;
            staged = false;
        }
    }

    /** Replaced by the resident's own watch in {@link #tick} (M21a). */
    @Override
    protected void doStuckDetection(Vec3 mobPos) {
    }

    @Override
    public void tick() {
        if (level.isClientSide()) {
            super.tick();
            return;
        }
        long start = System.nanoTime();
        long searchesBefore = searchNanos;
        super.tick();
        if (goal != null && level instanceof ServerLevel serverLevel) {
            watch(serverLevel, level.getGameTime());
        }
        ModProfiler.addDetail(ModProfiler.NAVIGATION, System.nanoTime() - start - (searchNanos - searchesBefore), 1); // M21
    }

    private void watch(ServerLevel serverLevel, long now) {
        double distance = Math.sqrt(mob.distanceToSqr(Vec3.atBottomCenterOf(goal)));
        if (distance <= goalReach + 1.5 || (isDone() && !staged && now - requestedAt > REQUEST_TICKS)) {
            goal = null; // there, or nobody wants to go there any more
            return;
        }
        if (isInProgress()) {
            requestedAt = now;
        }
        switch (tracker.update(now, mob.getX(), mob.getY(), mob.getZ(), distance)) {
            case RESCUE -> rescue(serverLevel, now, distance);
            case STUCK -> {
                if (isInProgress() && goal != null) {
                    avoidNextNode(now);
                    repath();
                }
            }
            case OK -> {
                if (isDone() && staged && stages < MAX_STAGES && now >= nextStageAt) { // next stage
                    nextStageAt = now + 10;
                    stages++;
                    repath();
                }
            }
        }
    }

    /** Stuck: the spot in front is avoided for a while (unless it is the target itself). */
    private void avoidNextNode(long now) {
        Path current = getPath();
        if (current == null || current.isDone()) {
            return;
        }
        BlockPos next = current.getNextNodePos();
        if (!next.closerThan(goal, 2.0)) {
            avoided.put(next.asLong(), now + AVOID_TICKS);
        }
        if (avoided.size() > 64) {
            avoided.values().removeIf(until -> until <= now);
        }
    }

    /** A new search for the remembered target, keeping it remembered. */
    private void repath() {
        BlockPos target = goal;
        if (target == null) {
            return;
        }
        internal = true;
        try {
            super.stop();
            failedTarget = null;
            Path path = createPath(target, goalReach);
            if (path != null) {
                moveTo(path, goalSpeed);
            }
        } finally {
            internal = false;
        }
    }

    /**
     * 30 seconds without getting closer (owner's choice "1a"): put the resident a bit further along its
     * way or next to its target – not while it fights and not while a player sees it.
     */
    private void rescue(ServerLevel serverLevel, long now, double distance) {
        BlockPos target = goal;
        if (chasing || mob.getTarget() != null || isWatched(serverLevel)) {
            tracker.postponeRescue(now);
            return;
        }
        boolean moved = false;
        Path way = lastPath;
        if (way != null && way.getNodeCount() > 0) {
            BlockPos ahead = way.getNodePos(Math.min(way.getNextNodeIndex() + 4, way.getNodeCount() - 1));
            moved = ahead.distSqr(mob.blockPosition()) > 9 && teleport(ahead);
        }
        for (int dy = 1; !moved && dy >= -1; dy--) { // next to the target
            for (BlockPos spot : BlockPos.betweenClosed(target.offset(-1, dy, -1), target.offset(1, dy, 1))) {
                if (teleport(spot)) {
                    moved = true;
                    break;
                }
            }
        }
        if (moved) {
            VillageLife.LOGGER.debug("Resident {} was stuck and was put further along its way", mob.getUUID());
            internal = true;
            super.stop();
            internal = false;
            goal = target;
            tracker.reset(now, mob.getX(), mob.getY(), mob.getZ(), Math.sqrt(mob.distanceToSqr(Vec3.atBottomCenterOf(target))));
        } else {
            tracker.postponeRescue(now);
        }
    }

    /** Teleporting stops the navigation (vanilla) – an internal stop, so the target is kept. */
    private boolean teleport(BlockPos spot) {
        internal = true;
        try {
            return mob.randomTeleport(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, false, state -> false);
        } finally {
            internal = false;
        }
    }

    private boolean isWatched(ServerLevel serverLevel) {
        for (ServerPlayer player : serverLevel.players()) {
            if (!player.isSpectator() && player.distanceToSqr(mob) < WATCH_RANGE * WATCH_RANGE && player.hasLineOfSight(mob)) {
                return true;
            }
        }
        return false;
    }
}
