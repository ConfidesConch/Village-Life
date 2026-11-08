package io.github.confidesconch.villagelife.resident.ai;

import java.util.EnumSet;

import io.github.confidesconch.villagelife.resident.ResidentEntity;
import io.github.confidesconch.villagelife.resident.schedule.Activity;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * While the activity is {@link Activity#SLEEP}: walk to the own bed and sleep in it; wake up when
 * the activity changes (morning) or a more important goal (e.g. fleeing) takes over.
 */
public class SleepInBedGoal extends Goal {
    /** Start sleeping when closer than this to the bed's center (same distance as vanilla villagers). */
    private static final double SLEEP_DISTANCE = 2.0;
    /** Walk to any spot within this many blocks of the bed: the bed itself is no place to stand on. */
    private static final int REACH_RANGE = 1;
    private static final int REPATH_TICKS = 40;

    private final ResidentEntity resident;
    private final double speedModifier;
    private int repathCooldown;

    public SleepInBedGoal(ResidentEntity resident, double speedModifier) {
        this.resident = resident;
        this.speedModifier = speedModifier;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        BlockPos bed = resident.brain().bedPos();
        return resident.brain().activity() == Activity.SLEEP && bed != null && isFreeBed(bed);
    }

    @Override
    public boolean canContinueToUse() {
        BlockPos bed = resident.brain().bedPos();
        return resident.brain().activity() == Activity.SLEEP && bed != null
                && (resident.isSleeping() || isFreeBed(bed));
    }

    @Override
    public void start() {
        // spread the first path search over a second: at bedtime everybody starts at once (M12a)
        repathCooldown = resident.getId() % 20;
    }

    @Override
    public void tick() {
        BlockPos bed = resident.brain().bedPos();
        if (bed == null || resident.isSleeping()) {
            return;
        }
        if (bed.closerToCenterThan(resident.position(), SLEEP_DISTANCE)) {
            resident.getNavigation().stop();
            resident.startSleeping(bed);
            return;
        }
        if (--repathCooldown <= 0) {
            repathCooldown = REPATH_TICKS;
            resident.getNavigation().moveTo(bed.getX() + 0.5, bed.getY(), bed.getZ() + 0.5, REACH_RANGE, speedModifier);
        }
    }

    @Override
    public void stop() {
        if (resident.isSleeping()) {
            resident.stopSleeping();
        }
        resident.getNavigation().stop();
    }

    /**
     * A bed exists at the position and nobody else lies in it. The bed's "occupied" flag alone is not
     * trusted: it can be left over (e.g. from an older version), so it only counts if someone really
     * sleeps there.
     */
    private boolean isFreeBed(BlockPos pos) {
        if (!resident.level().isLoaded(pos)) {
            return false; // never load a chunk just to look at a bed (bug review 08.11.2026)
        }
        BlockState state = resident.level().getBlockState(pos);
        if (!state.is(BlockTags.BEDS) || !state.hasProperty(BedBlock.OCCUPIED)) {
            return false;
        }
        if (!state.getValue(BedBlock.OCCUPIED)) {
            return true;
        }
        return resident.level().getEntitiesOfClass(LivingEntity.class, new AABB(pos).inflate(1.0),
                entity -> entity != resident && entity.getSleepingPos().filter(pos::equals).isPresent()).isEmpty();
    }
}
