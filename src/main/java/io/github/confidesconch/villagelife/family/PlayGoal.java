package io.github.confidesconch.villagelife.family;

import java.util.EnumSet;
import java.util.List;

import io.github.confidesconch.villagelife.resident.ResidentEntity;
import io.github.confidesconch.villagelife.resident.schedule.Activity;

import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Children play (M18b): during their free time a child runs after another child nearby – a little game
 * of tag – and then picks the next one. Without other children it just strolls around like everyone.
 */
public class PlayGoal extends Goal {
    private static final double SEARCH_DISTANCE = 16.0;
    private static final double CAUGHT = 1.5;
    private static final int GAME_TICKS = 200;
    private static final int COOLDOWN_TICKS = 100;

    private final ResidentEntity child;
    private final double speedModifier;
    private ResidentEntity target;
    private int ticks;
    private long nextTryGameTime;

    public PlayGoal(ResidentEntity child, double speedModifier) {
        this.child = child;
        this.speedModifier = speedModifier;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!child.brain().isChild() || child.brain().activity() != Activity.LEISURE
                || child.level().getGameTime() < nextTryGameTime) {
            return false;
        }
        nextTryGameTime = child.level().getGameTime() + COOLDOWN_TICKS;
        List<ResidentEntity> others = child.level().getEntitiesOfClass(ResidentEntity.class,
                child.getBoundingBox().inflate(SEARCH_DISTANCE), other -> other != child && other.brain().isChild());
        if (others.isEmpty()) {
            return false;
        }
        target = others.get(child.getRandom().nextInt(others.size()));
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return target != null && target.isAlive() && child.brain().activity() == Activity.LEISURE && ticks < GAME_TICKS;
    }

    @Override
    public void start() {
        ticks = 0;
    }

    @Override
    public void stop() {
        target = null;
        child.getNavigation().stop();
    }

    @Override
    public void tick() {
        ticks++;
        child.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (child.distanceTo(target) < CAUGHT) {
            ticks = GAME_TICKS; // caught: the game is over, next one soon
            return;
        }
        if (ticks % 10 == 1) {
            child.getNavigation().moveTo(target, speedModifier);
        }
    }
}
