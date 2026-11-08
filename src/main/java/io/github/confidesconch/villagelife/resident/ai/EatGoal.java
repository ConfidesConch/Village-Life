package io.github.confidesconch.villagelife.resident.ai;

import java.util.EnumSet;

import io.github.confidesconch.villagelife.inventory.StorageAccess;
import io.github.confidesconch.villagelife.resident.ResidentEntity;
import io.github.confidesconch.villagelife.resident.needs.NeedsRules;
import io.github.confidesconch.villagelife.resident.schedule.Activity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;

/**
 * Hungry residents eat (M10): first what they carry, otherwise they fetch food from the village's
 * storage buildings (and their workplace). Only plain food is eaten – nothing with side effects
 * (rotten flesh, golden apples, ...). If there is no food anywhere, they look again after a while.
 */
public class EatGoal extends Goal {
    private static final int EAT_TICKS = 32;
    private static final int RETRY_TICKS = 600;
    private static final int GIVE_UP_TICKS = 400;
    private static final int REPATH_TICKS = 40;
    private static final double CONTAINER_DISTANCE = 2.5;
    /** How many food items to take from storage at once. */
    private static final int TAKE_AMOUNT = 4;

    private final ResidentEntity resident;
    private final double speedModifier;
    private BlockPos source;
    private int ticks;
    private int repath;
    private long nextSearchGameTime;

    public EatGoal(ResidentEntity resident, double speedModifier) {
        this.resident = resident;
        this.speedModifier = speedModifier;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (resident.isSleeping() || resident.brain().activity() == Activity.SLEEP
                || !NeedsRules.wantsToEat(resident.needs().food())) {
            return false;
        }
        return hasFood() || resident.level().getGameTime() >= nextSearchGameTime;
    }

    @Override
    public boolean canContinueToUse() {
        return !resident.isSleeping() && resident.needs().food() < NeedsRules.FULL
                && (hasFood() || source != null);
    }

    @Override
    public void start() {
        ticks = 0;
        repath = 0;
        source = null;
    }

    @Override
    public void stop() {
        source = null;
        resident.getNavigation().stop();
        resident.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (!(resident.level() instanceof ServerLevel level)) {
            return;
        }
        ticks++;
        if (hasFood()) {
            eat(level);
        } else if (source == null) {
            findSource(level);
        } else {
            fetch(level);
        }
    }

    private void eat(ServerLevel level) {
        ItemStack shown = resident.getMainHandItem();
        if (!isFood(shown)) {
            ItemStack next = resident.carriedItems().stream().filter(EatGoal::isFood).findFirst().orElse(ItemStack.EMPTY);
            resident.setItemSlot(EquipmentSlot.MAINHAND, next.copyWithCount(1));
            ticks = 0;
            return;
        }
        if (ticks % 4 == 0) {
            resident.playSound(SoundEvents.GENERIC_EAT.value(), 0.5F, 1.0F);
        }
        if (ticks < EAT_TICKS) {
            return;
        }
        ItemStack eaten = resident.takeCarried(stack -> ItemStack.isSameItemSameComponents(stack, shown));
        FoodProperties food = eaten.get(DataComponents.FOOD);
        if (food != null) {
            resident.needs().eat(food.nutrition());
        }
        resident.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        ticks = 0;
    }

    private void findSource(ServerLevel level) {
        for (BlockPos pos : StorageAccess.containers(resident.brain().village(), resident.brain().workplace(),
                resident.blockPosition())) {
            Container container = StorageAccess.containerAt(level, pos);
            if (container != null && StorageAccess.contains(container, EatGoal::isFood)) {
                source = pos;
                ticks = 0;
                return;
            }
        }
        nextSearchGameTime = level.getGameTime() + RETRY_TICKS; // nothing to eat anywhere
    }

    private void fetch(ServerLevel level) {
        if (source.closerToCenterThan(resident.position(), CONTAINER_DISTANCE)) {
            resident.getNavigation().stop();
            Container container = StorageAccess.containerAt(level, source);
            if (container != null) {
                StorageAccess.take(container, EatGoal::isFood, TAKE_AMOUNT).forEach(resident::carry);
            }
            if (!hasFood()) {
                nextSearchGameTime = level.getGameTime() + RETRY_TICKS;
            }
            source = null;
            return;
        }
        if (ticks > GIVE_UP_TICKS) {
            nextSearchGameTime = level.getGameTime() + RETRY_TICKS;
            source = null;
            return;
        }
        if (--repath <= 0) {
            repath = REPATH_TICKS;
            resident.getNavigation().moveTo(source.getX() + 0.5, source.getY(), source.getZ() + 0.5, 1, speedModifier);
        }
    }

    private boolean hasFood() {
        return resident.carriedCount(EatGoal::isFood) > 0;
    }

    /** Plain food: has nutrition and no effects when eaten. */
    public static boolean isFood(ItemStack stack) {
        if (stack.isEmpty() || !stack.has(DataComponents.FOOD)) {
            return false;
        }
        Consumable consumable = stack.get(DataComponents.CONSUMABLE);
        return consumable == null || consumable.onConsumeEffects().isEmpty();
    }
}
