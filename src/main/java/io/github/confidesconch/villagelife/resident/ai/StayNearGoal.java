package io.github.confidesconch.villagelife.resident.ai;

import java.util.function.Function;

import io.github.confidesconch.villagelife.resident.ResidentEntity;
import io.github.confidesconch.villagelife.resident.schedule.Activity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.phys.Vec3;

/**
 * During one activity, walks to a place (e.g. the own bed or the village center) and then strolls
 * around close to it with pauses. Used for {@link Activity#HOME} and {@link Activity#MEAL}.
 */
public class StayNearGoal extends RandomStrollGoal {
    private static final int TRIES = 4;
    /** While already close, only every few checks start a short walk, so residents stand around. */
    private static final float STROLL_CHANCE = 0.3F;

    private final ResidentEntity resident;
    private final Activity activity;
    private final Function<ResidentBrain, BlockPos> anchor;
    private final int radius;

    public StayNearGoal(ResidentEntity resident, double speedModifier, Activity activity,
                        Function<ResidentBrain, BlockPos> anchor, int radius) {
        super(resident, speedModifier, 20);
        this.resident = resident;
        this.activity = activity;
        this.anchor = anchor;
        this.radius = radius;
    }

    @Override
    public boolean canUse() {
        if (!isActive()) {
            return false;
        }
        BlockPos target = anchor.apply(resident.brain());
        if (!isNear(resident.getX(), resident.getZ(), target)) {
            trigger(); // M21: far away (e.g. the alarm sends everybody home): go now, not only by chance
        }
        return super.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        return isActive() && super.canContinueToUse();
    }

    private boolean isActive() {
        return resident.brain().activity() == activity && anchor.apply(resident.brain()) != null;
    }

    @Override
    protected Vec3 getPosition() {
        BlockPos target = anchor.apply(resident.brain());
        if (target == null) {
            return null;
        }
        if (!isNear(resident.getX(), resident.getZ(), target)) {
            // M21: straight to the place (the navigation finds the way, in stages if it is far); before, hops of
            // 12 blocks at random moments took very long in a big town
            return Vec3.atBottomCenterOf(target);
        }
        if (resident.getRandom().nextFloat() >= STROLL_CHANCE) {
            return null; // stand around for a while
        }
        for (int i = 0; i < TRIES; i++) {
            Vec3 candidate = LandRandomPos.getPos(resident, 4, 2);
            if (candidate != null && isNear(candidate.x, candidate.z, target)) {
                return candidate;
            }
        }
        return null;
    }

    private boolean isNear(double x, double z, BlockPos target) {
        double dx = x - (target.getX() + 0.5);
        double dz = z - (target.getZ() + 0.5);
        return dx * dx + dz * dz <= (double) radius * radius;
    }
}
