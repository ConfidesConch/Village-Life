package io.github.confidesconch.villagelife.family;

import java.util.EnumSet;
import java.util.Optional;

import io.github.confidesconch.villagelife.profession.JobAllocator.Workplace;
import io.github.confidesconch.villagelife.profession.JobService;
import io.github.confidesconch.villagelife.resident.EducationRules;
import io.github.confidesconch.villagelife.resident.ResidentEntity;
import io.github.confidesconch.villagelife.resident.ResidentRecord;
import io.github.confidesconch.villagelife.village.Village;
import io.github.confidesconch.villagelife.village.VillageManager;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.Blocks;

/**
 * School (M18c, owner's choice "2a 3a"): on mornings of the work time, children go to the school (a
 * workplace with a bookshelf whose teacher has the job) and stay around the bookshelf. Every hour there
 * gives education points ({@link EducationRules}); educated adults work faster. Without a school the
 * children just play.
 */
public class SchoolGoal extends Goal {
    public static final String TEACHER = "villagelife:teacher";

    private static final int CHECK_INTERVAL_TICKS = 200;
    private static final double CLOSE_ENOUGH = 2.0;
    private static final int SPREAD = 2;

    private final ResidentEntity child;
    private final double speedModifier;
    private BlockPos shelf;
    private BlockPos seat;
    private long nextCheckGameTime;
    private long ticksInClass;

    public SchoolGoal(ResidentEntity child, double speedModifier) {
        this.child = child;
        this.speedModifier = speedModifier;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!child.brain().isSchoolTime() || !(child.level() instanceof ServerLevel level)
                || level.getGameTime() < nextCheckGameTime) {
            return false;
        }
        nextCheckGameTime = level.getGameTime() + CHECK_INTERVAL_TICKS;
        Village village = child.brain().village();
        shelf = village == null ? null : shelf(level, village).orElse(null);
        return shelf != null;
    }

    @Override
    public boolean canContinueToUse() {
        return child.brain().isSchoolTime() && shelf != null && child.level().isLoaded(shelf)
                && child.level().getBlockState(shelf).is(Blocks.BOOKSHELF);
    }

    @Override
    public void start() {
        int dx = child.getRandom().nextInt(SPREAD * 2 + 1) - SPREAD;
        int dz = child.getRandom().nextInt(SPREAD * 2 + 1) - SPREAD;
        BlockPos candidate = shelf.offset(dx, 0, dz);
        seat = child.level().isLoaded(candidate) && child.level().getBlockState(candidate).isAir() ? candidate : shelf;
    }

    @Override
    public void stop() {
        shelf = null;
        child.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (!(child.level() instanceof ServerLevel level)) {
            return;
        }
        if (!seat.closerToCenterThan(child.position(), CLOSE_ENOUGH)) {
            if (level.getGameTime() % 20 == 0) {
                child.getNavigation().moveTo(seat.getX() + 0.5, seat.getY(), seat.getZ() + 0.5, speedModifier);
            }
            return;
        }
        child.getNavigation().stop();
        child.getLookControl().setLookAt(shelf.getX() + 0.5, shelf.getY() + 0.5, shelf.getZ() + 0.5);
        if (++ticksInClass >= EducationRules.HOUR_TICKS) {
            ticksInClass = 0;
            learn(level);
        }
    }

    /** One more hour of school: education points into the resident's record. */
    private void learn(ServerLevel level) {
        VillageManager manager = VillageManager.get(level);
        child.getVillageId().flatMap(manager::byId).ifPresent(village -> village.residentById(child.getUUID())
                .ifPresent(record -> manager.update(village.withResident(
                        record.withEducation(EducationRules.afterLesson(record.education()))))));
    }

    /** The bookshelf of the village's school, if it has a teacher. */
    static Optional<BlockPos> shelf(ServerLevel level, Village village) {
        for (Workplace workplace : JobService.workplaces(level, village)) {
            if (!workplace.profession().equals(TEACHER)) {
                continue;
            }
            boolean staffed = village.residents().stream().map(ResidentRecord::job).flatMap(Optional::stream)
                    .anyMatch(job -> job.workplace().equals(workplace.buildingId()) && job.profession().equals(TEACHER));
            if (!staffed) {
                continue;
            }
            Optional<BlockPos> shelf = village.buildings().stream().filter(b -> b.id().equals(workplace.buildingId()))
                    .flatMap(b -> b.workstations().stream())
                    .filter(pos -> level.isLoaded(pos) && level.getBlockState(pos).is(Blocks.BOOKSHELF)).findFirst();
            if (shelf.isPresent()) {
                return shelf;
            }
        }
        return Optional.empty();
    }
}
