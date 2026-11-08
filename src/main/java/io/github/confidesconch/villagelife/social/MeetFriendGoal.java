package io.github.confidesconch.villagelife.social;

import java.util.Comparator;
import java.util.EnumSet;

import io.github.confidesconch.villagelife.resident.ResidentEntity;
import io.github.confidesconch.villagelife.resident.schedule.Activity;
import io.github.confidesconch.villagelife.social.RelationshipStore.Relation;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Free time with friends (M17, owner's choice "3a"): during leisure a resident walks to its best friend
 * who is nearby and stays with it for a while, instead of strolling around alone. Talks then happen on
 * their own ({@link SocialService}).
 */
public class MeetFriendGoal extends Goal {
    private static final double SEARCH_DISTANCE = 32.0;
    private static final double CLOSE_ENOUGH = 2.5;
    private static final int VISIT_TICKS = 400;
    private static final int COOLDOWN_TICKS = 600;

    private final ResidentEntity resident;
    private final double speedModifier;
    private ResidentEntity friend;
    private int ticks;
    private long nextTryGameTime;

    public MeetFriendGoal(ResidentEntity resident, double speedModifier) {
        this.resident = resident;
        this.speedModifier = speedModifier;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (resident.brain().activity() != Activity.LEISURE || !(resident.level() instanceof ServerLevel level)
                || level.getGameTime() < nextTryGameTime) {
            return false;
        }
        nextTryGameTime = level.getGameTime() + COOLDOWN_TICKS;
        friend = RelationshipStore.get(level).of(resident.getUUID()).stream()
                .filter(r -> RelationshipRules.stage(r.points()).isFriend())
                .sorted(Comparator.comparingInt(Relation::points).reversed())
                .map(r -> level.getEntity(r.other(resident.getUUID())))
                .filter(e -> e instanceof ResidentEntity other && other.isAlive() && !other.isSleeping()
                        && e.distanceTo(resident) < SEARCH_DISTANCE)
                .map(e -> (ResidentEntity) e)
                .findFirst().orElse(null);
        return friend != null;
    }

    @Override
    public boolean canContinueToUse() {
        return friend != null && friend.isAlive() && resident.brain().activity() == Activity.LEISURE && ticks < VISIT_TICKS;
    }

    @Override
    public void start() {
        ticks = 0;
    }

    @Override
    public void stop() {
        friend = null;
        resident.getNavigation().stop();
    }

    @Override
    public void tick() {
        ticks++;
        resident.getLookControl().setLookAt(friend, 30.0F, 30.0F);
        if (resident.distanceTo(friend) > CLOSE_ENOUGH) {
            if (ticks % 20 == 1) {
                resident.getNavigation().moveTo((Entity) friend, speedModifier);
            }
        } else {
            resident.getNavigation().stop();
        }
    }
}
