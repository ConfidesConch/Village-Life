package io.github.confidesconch.villagelife.resident.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Opens a closed fence gate that lies on the resident's path when it gets close, and closes it again
 * once the resident has walked through (M13c), so animals stay in their pasture – but not while an
 * animal it leads on a leash is still in the gateway (M13d). Runs alongside
 * other goals (no flags), like the vanilla door goal.
 */
public class OpenFenceGateGoal extends Goal {
    private static final double OPEN_DISTANCE_SQR = 2.25;
    private static final double CLOSE_DISTANCE_SQR = 4.0;
    /** Path nodes ahead that are checked for a gate. */
    private static final int LOOK_AHEAD = 2;

    private final Mob mob;
    /** The gate this resident opened and still has to close, or null. */
    private BlockPos opened;

    public OpenFenceGateGoal(Mob mob) {
        this.mob = mob;
    }

    @Override
    public boolean canUse() {
        return opened != null || gateAhead() != null;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        Level level = mob.level();
        if (opened == null) {
            BlockPos gate = gateAhead();
            if (gate != null && mob.distanceToSqr(Vec3.atCenterOf(gate)) < OPEN_DISTANCE_SQR) {
                setOpen(level, gate, true);
                opened = gate;
            }
        } else if (mob.distanceToSqr(Vec3.atCenterOf(opened)) > CLOSE_DISTANCE_SQR
                && !mob.blockPosition().equals(opened) && !leashedAnimalAt(opened)) {
            setOpen(level, opened, false);
            opened = null;
        }
    }

    @Override
    public void stop() {
        if (opened != null) {
            setOpen(mob.level(), opened, false);
            opened = null;
        }
    }

    /** True while an animal this resident leads on a leash is still near the gate (M13d): wait for it. */
    private boolean leashedAnimalAt(BlockPos gate) {
        return !mob.level().getEntitiesOfClass(Entity.class, new AABB(gate).inflate(2.0),
                entity -> entity instanceof Leashable leashable && leashable.getLeashHolder() == mob).isEmpty();
    }

    /** A closed fence gate on one of the next path nodes, or null. */
    private BlockPos gateAhead() {
        Path path = mob.getNavigation().getPath();
        if (path == null || path.isDone()) {
            return null;
        }
        for (int i = path.getNextNodeIndex(); i < Math.min(path.getNodeCount(), path.getNextNodeIndex() + LOOK_AHEAD); i++) {
            BlockPos pos = path.getNode(i).asBlockPos();
            BlockState state = mob.level().getBlockState(pos);
            if (state.getBlock() instanceof FenceGateBlock && !state.getValue(FenceGateBlock.OPEN)) {
                return pos;
            }
        }
        return null;
    }

    private void setOpen(Level level, BlockPos pos, boolean open) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof FenceGateBlock) || state.getValue(FenceGateBlock.OPEN) == open) {
            return;
        }
        level.setBlock(pos, state.setValue(FenceGateBlock.OPEN, open), 10);
        level.playSound(null, pos, open ? SoundEvents.FENCE_GATE_OPEN : SoundEvents.FENCE_GATE_CLOSE, SoundSource.BLOCKS,
                1.0F, 0.9F + level.getRandom().nextFloat() * 0.1F);
        level.gameEvent(mob, open ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, pos);
    }
}
