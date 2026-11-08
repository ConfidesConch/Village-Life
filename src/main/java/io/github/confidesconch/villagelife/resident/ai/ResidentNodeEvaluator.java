package io.github.confidesconch.villagelife.resident.ai;

import java.util.function.LongPredicate;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

/**
 * Path finding of residents. M13c: a closed fence gate counts like a closed wooden door, so residents
 * plan paths through gates (e.g. the shepherd into the pasture); {@link OpenFenceGateGoal} opens and
 * closes them on the way. M21a: ladders (and other climbable blocks) can be climbed up and down – a
 * climbable block is a place to stand, and from it the path goes on straight up or down the ladder;
 * places where the resident got stuck a moment ago are avoided ({@code avoided}).
 */
public class ResidentNodeEvaluator extends WalkNodeEvaluator {
    private final LongPredicate avoided;
    /** M21: one reusable position instead of a new one per looked-at cell (called very often). */
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

    public ResidentNodeEvaluator(LongPredicate avoided) {
        this.avoided = avoided;
    }

    @Override
    public PathType getPathType(PathfindingContext context, int x, int y, int z) {
        if (avoided.test(BlockPos.asLong(x, y, z))) {
            return PathType.BLOCKED;
        }
        cursor.set(x, y, z);
        BlockState state = context.getBlockState(cursor);
        if (state.is(BlockTags.CLIMBABLE) || state.getBlock() instanceof TrapDoorBlock && isClimbable(state,
                context.getBlockState(cursor.move(0, -1, 0)))) {
            return PathType.WALKABLE; // M21a: one can hold on here
        }
        PathType type = super.getPathType(context, x, y, z);
        if (type == PathType.FENCE && state.getBlock() instanceof FenceGateBlock && !state.getValue(FenceGateBlock.OPEN)) {
            return PathType.DOOR_WOOD_CLOSED;
        }
        return type;
    }

    /** On a ladder the path starts where the resident holds on, not at the bottom of the shaft. */
    @Override
    public Node getStart() {
        BlockPos at = mob.blockPosition();
        if (!mob.onGround() && isClimbable(at)) {
            return getStartNode(at);
        }
        return super.getStart();
    }

    @Override
    public int getNeighbors(Node[] neighbors, Node pos) {
        int count = super.getNeighbors(neighbors, pos);
        if (isClimbable(new BlockPos(pos.x, pos.y, pos.z))) {
            count = addClimb(neighbors, count, pos.x, pos.y + 1, pos.z, true);
            count = addClimb(neighbors, count, pos.x, pos.y - 1, pos.z, false);
        }
        return count;
    }

    /** Up: onto the next rung or off the top of the ladder; down: onto the next rung. */
    private int addClimb(Node[] neighbors, int count, int x, int y, int z, boolean up) {
        if (count >= neighbors.length) {
            return count;
        }
        BlockPos next = new BlockPos(x, y, z);
        boolean rung = isClimbable(next);
        if (!rung && (!up || getCachedPathType(x, y, z) != PathType.WALKABLE)) {
            return count;
        }
        if (avoided.test(next.asLong()) || getCachedPathType(x, y + 1, z) == PathType.BLOCKED) {
            return count; // no room for the head
        }
        Node node = getNode(x, y, z);
        if (node.closed) {
            return count;
        }
        node.type = PathType.WALKABLE;
        node.costMalus = Math.max(node.costMalus, 0.0F);
        neighbors[count] = node;
        return count + 1;
    }

    private boolean isClimbable(BlockPos pos) {
        return isClimbable(currentContext.getBlockState(pos), currentContext.getBlockState(pos.below()));
    }

    /** A ladder (or vine …), or an open hatch right above a ladder (closed hatches cannot be opened). */
    private static boolean isClimbable(BlockState state, BlockState below) {
        return state.is(BlockTags.CLIMBABLE) || state.getBlock() instanceof TrapDoorBlock
                && state.getValue(TrapDoorBlock.OPEN) && below.is(BlockTags.CLIMBABLE);
    }
}
