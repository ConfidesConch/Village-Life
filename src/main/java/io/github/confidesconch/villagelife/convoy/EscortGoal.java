package io.github.confidesconch.villagelife.convoy;

import java.util.EnumSet;

import io.github.confidesconch.villagelife.resident.ResidentEntity;
import io.github.confidesconch.villagelife.resident.ai.ResidentHooks;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * An escort of a convoy (M20c): walks out of the village towards the road, or – on the road, while a
 * player can see the convoy – stays beside its wagon. Fighting is {@code defense.DefendGoal}, which
 * comes first.
 */
public class EscortGoal extends Goal {
    private static final int REPATH_TICKS = 20;
    private static final double NEAR_WAGON_SQR = 4.0 * 4.0;

    private final ResidentEntity resident;
    private final double speedModifier;
    private Entity wagon;
    private BlockPos exit;
    private int repath;

    public EscortGoal(ResidentEntity resident, double speedModifier) {
        this.resident = resident;
        this.speedModifier = speedModifier;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!(resident.level() instanceof ServerLevel level) || !ResidentHooks.isDefender(resident) // M21: cheap test first
                || resident.isKnockedOut() || !ResidentHooks.isEscorting(resident)) {
            return false;
        }
        wagon = ConvoyWagons.wagonOf(resident);
        exit = wagon == null ? EscortTrips.exitOf(level.getServer(), resident) : null;
        return wagon != null || exit != null;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        if (resident.isSleeping()) {
            resident.stopSleeping();
        }
        repath = 0;
    }

    @Override
    public void stop() {
        resident.getNavigation().stop();
        wagon = null;
        exit = null;
    }

    @Override
    public void tick() {
        if (--repath > 0) {
            return;
        }
        repath = REPATH_TICKS;
        if (wagon != null) {
            if (resident.distanceToSqr(wagon) > NEAR_WAGON_SQR) {
                resident.getNavigation().moveTo(wagon, speedModifier);
            } else {
                resident.getNavigation().stop();
            }
        } else if (exit != null) {
            resident.getNavigation().moveTo(exit.getX() + 0.5, exit.getY(), exit.getZ() + 0.5, speedModifier);
        }
    }
}
