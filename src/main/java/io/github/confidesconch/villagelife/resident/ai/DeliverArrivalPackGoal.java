package io.github.confidesconch.villagelife.resident.ai;

import java.util.EnumSet;
import java.util.List;

import io.github.confidesconch.villagelife.inventory.StorageAccess;
import io.github.confidesconch.villagelife.resident.ResidentEntity;
import io.github.confidesconch.villagelife.resident.schedule.Activity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;

/**
 * A newcomer brings its provisions to the village storage (wish of the owner, 04.11.2026): walks to
 * the nearest storage container with room, puts everything in and is done. Without a storage building
 * (or with full ones) it keeps the provisions and tries again later. Not while sleeping.
 */
public class DeliverArrivalPackGoal extends Goal {
    private static final double CONTAINER_DISTANCE = 2.5;
    private static final int GIVE_UP_TICKS = 400;
    private static final int RETRY_TICKS = 1200;
    private static final int REPATH_TICKS = 40;

    private final ResidentEntity resident;
    private final double speedModifier;
    private BlockPos target;
    private int ticks;
    private long nextTryGameTime;

    public DeliverArrivalPackGoal(ResidentEntity resident, double speedModifier) {
        this.resident = resident;
        this.speedModifier = speedModifier;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (resident.arrivalPack().isEmpty() || resident.brain().activity() == Activity.SLEEP
                || !(resident.level() instanceof ServerLevel level) || level.getGameTime() < nextTryGameTime) {
            return false;
        }
        target = findContainer(level);
        if (target == null) {
            nextTryGameTime = level.getGameTime() + RETRY_TICKS;
            return false;
        }
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return target != null && !resident.arrivalPack().isEmpty() && resident.brain().activity() != Activity.SLEEP;
    }

    @Override
    public void start() {
        ticks = 0;
        resident.setItemSlot(EquipmentSlot.MAINHAND, resident.arrivalPack().getFirst().copyWithCount(1));
    }

    @Override
    public void stop() {
        target = null;
        resident.getNavigation().stop();
        resident.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
    }

    @Override
    public void tick() {
        if (!(resident.level() instanceof ServerLevel level) || target == null) {
            return;
        }
        if (++ticks > GIVE_UP_TICKS) {
            nextTryGameTime = level.getGameTime() + RETRY_TICKS;
            target = null;
            return;
        }
        if (!target.closerToCenterThan(resident.position(), CONTAINER_DISTANCE)) {
            if (ticks % REPATH_TICKS == 1) {
                resident.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 1, speedModifier);
            }
            return;
        }
        resident.getNavigation().stop();
        resident.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
        Container container = StorageAccess.containerAt(level, target);
        if (container != null) {
            List<ItemStack> pack = resident.arrivalPack();
            for (int i = 0; i < pack.size(); i++) {
                pack.set(i, StorageAccess.insert(container, pack.get(i)));
            }
            pack.removeIf(ItemStack::isEmpty);
        }
        if (!resident.arrivalPack().isEmpty()) {
            nextTryGameTime = level.getGameTime() + RETRY_TICKS; // full: try again later, maybe elsewhere
        }
        target = null;
    }

    /** The nearest storage container (not a workplace's) that takes at least one of the provisions. */
    private BlockPos findContainer(ServerLevel level) {
        for (BlockPos pos : StorageAccess.containers(resident.brain().village(), null, resident.blockPosition())) {
            Container container = StorageAccess.containerAt(level, pos);
            if (container != null && resident.arrivalPack().stream().anyMatch(stack -> StorageAccess.canAccept(container, stack))) {
                return pos;
            }
        }
        return null;
    }
}
