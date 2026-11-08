package io.github.confidesconch.villagelife.profession.work;

import java.util.List;

import io.github.confidesconch.villagelife.resident.ResidentEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * Work of the teacher (M18c, owner's choice "1a"): stands at the school's bookshelf and teaches the
 * children around her – she looks at them, gestures, and enchanting letters rise from the bookshelf.
 * The children's side: {@code family.SchoolGoal}.
 */
public class TeacherGoal extends WorkGoal {
    public static final String PROFESSION = "villagelife:teacher";

    private static final double STAND_DISTANCE = 2.5;
    private static final double CLASS_DISTANCE = 6.0;

    private int ticks;

    public TeacherGoal(ResidentEntity resident, double speedModifier) {
        super(resident, speedModifier, PROFESSION, ItemTags.SHOVELS, Items.BOOK); // a book in the hand
    }

    @Override
    public boolean canUse() {
        return isWorkTime() && resident.level() instanceof ServerLevel level && shelf(level) != null;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void tick() {
        if (!(resident.level() instanceof ServerLevel level)) {
            return;
        }
        BlockPos shelf = shelf(level);
        if (shelf == null) {
            return;
        }
        if (!shelf.closerToCenterThan(resident.position(), STAND_DISTANCE)) {
            moveTowards(shelf, 1);
            return;
        }
        resident.getNavigation().stop();
        List<ResidentEntity> pupils = level.getEntitiesOfClass(ResidentEntity.class,
                resident.getBoundingBox().inflate(CLASS_DISTANCE), other -> other.brain().isChild());
        if (pupils.isEmpty()) {
            resident.getLookControl().setLookAt(shelf.getX() + 0.5, shelf.getY() + 0.5, shelf.getZ() + 0.5);
            return;
        }
        ticks++;
        resident.getLookControl().setLookAt(pupils.get((ticks / 60) % pupils.size()));
        if (ticks % 40 == 0) {
            swing();
            level.sendParticles(ParticleTypes.ENCHANT, shelf.getX() + 0.5, shelf.getY() + 1.2, shelf.getZ() + 0.5,
                    8, 0.4, 0.3, 0.4, 0.5);
        }
    }

    /** The school's bookshelf. */
    private BlockPos shelf(ServerLevel level) {
        for (BlockPos pos : workplace().workstations()) {
            if (level.isLoaded(pos) && level.getBlockState(pos).is(Blocks.BOOKSHELF)) {
                return pos;
            }
        }
        return null;
    }
}
