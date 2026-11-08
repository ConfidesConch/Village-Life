package io.github.confidesconch.villagelife.resident.ai;

import io.github.confidesconch.villagelife.resident.ResidentEntity;
import io.github.confidesconch.villagelife.village.VillageArea;

import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.phys.Vec3;

/**
 * Walks to random places inside the resident's village while {@link ResidentBrain#wantsToWander()}.
 * A resident outside its village walks back towards the village center first.
 * Uses land positions only, so residents do not wander into water, and only spots at about the
 * same height (M12c): targets on roofs could not be reached, and searching such a path in vain was
 * the most expensive thing residents did.
 */
public class WanderInVillageGoal extends RandomStrollGoal {
    private static final int TRIES = 6;
    /** Highest height difference of a wander target (a flat roof is 4 or more blocks up). */
    private static final int MAX_HEIGHT_DIFFERENCE = 2;
    private final ResidentEntity resident;

    public WanderInVillageGoal(ResidentEntity resident, double speedModifier) {
        super(resident, speedModifier, 60);
        this.resident = resident;
    }

    @Override
    public boolean canUse() {
        return resident.brain().wantsToWander() && super.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        return resident.brain().wantsToWander() && super.canContinueToUse();
    }

    @Override
    protected Vec3 getPosition() {
        VillageArea area = resident.brain().area();
        if (area == null) {
            return LandRandomPos.getPos(resident, 8, 5);
        }
        if (!area.contains(resident.getBlockX(), resident.getBlockZ())) {
            return LandRandomPos.getPosTowards(resident, 12, MAX_HEIGHT_DIFFERENCE, Vec3.atBottomCenterOf(resident.brain().villageCenter()));
        }
        for (int i = 0; i < TRIES; i++) {
            Vec3 candidate = LandRandomPos.getPos(resident, 10, MAX_HEIGHT_DIFFERENCE);
            if (candidate != null && area.contains((int) Math.floor(candidate.x), (int) Math.floor(candidate.z))
                    && Math.abs(candidate.y - resident.getY()) <= MAX_HEIGHT_DIFFERENCE) {
                return candidate;
            }
        }
        return null; // no good spot this time; the goal tries again later
    }
}
