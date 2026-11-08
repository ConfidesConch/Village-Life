package io.github.confidesconch.villagelife.profession.work;

import java.util.function.Predicate;

import io.github.confidesconch.villagelife.building.Building;
import io.github.confidesconch.villagelife.building.BuildingType;
import io.github.confidesconch.villagelife.inventory.StorageAccess;
import io.github.confidesconch.villagelife.resident.ResidentEntity;
import io.github.confidesconch.villagelife.resident.ai.EatGoal;
import io.github.confidesconch.villagelife.village.Village;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * Work of the innkeeper (M17b, owner's choice "1a 2a 3a"): keeps the tavern's chest stocked with food
 * from the storage (up to {@link #STOCK}), so guests can eat there in the evening; otherwise stands at
 * the brewing stand. The guests themselves: {@code social.TavernVisitGoal}.
 */
public class InnkeeperGoal extends WorkGoal {
    public static final String PROFESSION = "villagelife:innkeeper";

    /** Food the tavern should hold. */
    static final int STOCK = 32;
    private static final double REACH = 2.5;
    private static final int CHECK_INTERVAL_TICKS = 100;

    private enum Phase { STAND, TO_STORAGE, TO_TAVERN }

    private final Predicate<ItemStack> food = EatGoal::isFood;
    private Phase phase = Phase.STAND;
    private BlockPos target;
    private int phaseTicks;
    private long nextCheckGameTime;

    public InnkeeperGoal(ResidentEntity resident, double speedModifier) {
        super(resident, speedModifier, PROFESSION, ItemTags.SHOVELS, Items.HONEY_BOTTLE); // a mug in the hand
    }

    @Override
    public boolean canUse() {
        return isWorkTime() && resident.level() instanceof ServerLevel level && stand(level) != null;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void stop() {
        if (resident.level() instanceof ServerLevel level && resident.carriedCount(food) > 0) {
            BlockPos chest = tavernChest(level);
            Container container = chest == null ? null : StorageAccess.containerAt(level, chest);
            if (container != null) { // never walk off with the guests' food
                resident.carry(StorageAccess.insert(container, resident.takeCarried(food, 64)));
            }
        }
        phase = Phase.STAND;
        super.stop();
    }

    @Override
    public void tick() {
        if (!(resident.level() instanceof ServerLevel level)) {
            return;
        }
        phaseTicks++;
        switch (phase) {
            case STAND -> stand(level, level.getGameTime());
            case TO_STORAGE -> {
                if (arrived(target)) {
                    Container container = StorageAccess.containerAt(level, target);
                    int missing = STOCK - inTavern(level) - resident.carriedCount(food);
                    if (container != null && missing > 0) {
                        swing();
                        StorageAccess.take(container, food, missing).forEach(resident::carry);
                    }
                    setPhase(Phase.TO_TAVERN, tavernChest(level));
                }
            }
            case TO_TAVERN -> {
                if (arrived(target)) {
                    Container container = StorageAccess.containerAt(level, target);
                    if (container != null) {
                        swing();
                        resident.carry(StorageAccess.insert(container, resident.takeCarried(food, 64)));
                    }
                    setPhase(Phase.STAND, null);
                }
            }
        }
    }

    /** At the brewing stand; every few seconds checks whether the tavern needs food. */
    private void stand(ServerLevel level, long now) {
        BlockPos stand = stand(level);
        if (stand == null) {
            return;
        }
        if (!stand.closerToCenterThan(resident.position(), REACH)) {
            moveTowards(stand, 1);
        } else {
            resident.getNavigation().stop();
        }
        if (now < nextCheckGameTime) {
            return;
        }
        nextCheckGameTime = now + CHECK_INTERVAL_TICKS;
        BlockPos chest = tavernChest(level);
        if (chest != null && inTavern(level) < STOCK / 2) {
            BlockPos source = storageWithFood(level);
            if (source != null) {
                setPhase(Phase.TO_STORAGE, source);
            }
        }
    }

    /** True once next to {@code pos}; gives up (back to standing) if it takes too long. */
    private boolean arrived(BlockPos pos) {
        if (pos == null || phaseTicks > GIVE_UP_TICKS) {
            setPhase(Phase.STAND, null);
            return false;
        }
        if (pos.closerToCenterThan(resident.position(), REACH)) {
            resident.getNavigation().stop();
            return true;
        }
        moveTowards(pos, 1);
        return false;
    }

    private void setPhase(Phase next, BlockPos newTarget) {
        phase = next;
        target = newTarget;
        phaseTicks = 0;
        resetPath();
    }

    private BlockPos stand(ServerLevel level) {
        for (BlockPos pos : workplace().workstations()) {
            if (level.isLoaded(pos) && level.getBlockState(pos).is(Blocks.BREWING_STAND)) {
                return pos;
            }
        }
        return null;
    }

    private BlockPos tavernChest(ServerLevel level) {
        for (BlockPos pos : workplace().containers()) {
            if (StorageAccess.containerAt(level, pos) != null) {
                return pos;
            }
        }
        return null;
    }

    private int inTavern(ServerLevel level) {
        int count = 0;
        for (BlockPos pos : workplace().containers()) {
            Container container = StorageAccess.containerAt(level, pos);
            for (int slot = 0; container != null && slot < container.getContainerSize(); slot++) {
                ItemStack stack = container.getItem(slot);
                count += food.test(stack) ? stack.getCount() : 0;
            }
        }
        return count;
    }

    private BlockPos storageWithFood(ServerLevel level) {
        Village village = resident.brain().village();
        if (village == null) {
            return null;
        }
        for (Building building : village.buildings()) {
            if (building.type() == BuildingType.STORAGE && building.isValid()) {
                for (BlockPos pos : building.containers()) {
                    Container container = StorageAccess.containerAt(level, pos);
                    if (container != null && StorageAccess.contains(container, food)) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }
}
