package io.github.confidesconch.villagelife.social;

import java.util.EnumSet;
import java.util.List;
import java.util.function.Predicate;

import io.github.confidesconch.villagelife.building.Building;
import io.github.confidesconch.villagelife.inventory.StorageAccess;
import io.github.confidesconch.villagelife.resident.Job;
import io.github.confidesconch.villagelife.resident.ResidentEntity;
import io.github.confidesconch.villagelife.resident.schedule.Activity;
import io.github.confidesconch.villagelife.resident.schedule.ScheduleClock;
import io.github.confidesconch.villagelife.village.Village;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.item.component.SwingAnimation;

/**
 * Work of the bard (M23, owner's choice "jukebox, square by day and tavern in the evening, +5 mood"):
 * fetches up to {@link BardRules#MAX_DISCS} different music discs from the village storage, plays on the
 * square at the village center by day and in the tavern from 17:00 (if the village has one), rests a
 * little between songs. Without discs he plays tunes on a horn. The music itself: {@link BardMusic}.
 * Lives in {@code social} (not {@code profession.work}) because it uses the tavern and the mood.
 */
public class BardGoal extends Goal {
    public static final String PROFESSION = "villagelife:bard";
    /** Shown in the village window: no disc in the storage, the bard only plays tunes. */
    public static final String ISSUE_NO_DISC = "bard.no_disc";

    private static final int FETCH_COOLDOWN_TICKS = 6000;
    private static final int CHECK_STAGE_TICKS = 100;
    private static final int GIVE_UP_TICKS = 600;
    private static final double SQUARE_RANGE = 4.0;
    private static final double PLACE_RANGE = 1.2;
    private static final double CONTAINER_RANGE = 2.5;

    private enum Phase { FETCH, WALK, PLAY, PAUSE }

    private final ResidentEntity resident;
    private final double speedModifier;
    private final BardMusic music;
    private Phase phase = Phase.WALK;
    private int phaseTicks;
    private boolean inTavern;
    private BlockPos target;
    private TavernPlaces.Place place;
    private BlockPos container;
    private long nextFetchGameTime;
    private int discIndex;

    public BardGoal(ResidentEntity resident, double speedModifier) {
        this.resident = resident;
        this.speedModifier = speedModifier;
        this.music = new BardMusic(resident);
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        Job job = resident.brain().job();
        return resident.level() instanceof ServerLevel && resident.brain().activity() == Activity.WORK && job != null
                && PROFESSION.equals(job.profession()) && resident.brain().workplace() != null
                && resident.brain().village() != null;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        ServerLevel level = (ServerLevel) resident.level();
        chooseStage(level);
        setPhase(wantsDisc(level) ? Phase.FETCH : Phase.WALK);
    }

    @Override
    public void stop() {
        if (resident.level() instanceof ServerLevel level) {
            music.stop(level);
        }
        releasePlace();
        container = null;
        resident.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (!(resident.level() instanceof ServerLevel level)) {
            return;
        }
        phaseTicks++;
        long now = level.getGameTime();
        if (place != null) {
            TavernPlaces.hold(place, resident.getUUID(), now);
        }
        if (phaseTicks % CHECK_STAGE_TICKS == 0 && phase != Phase.FETCH && wantsTavern(level) != inTavern) {
            music.stop(level); // evening: from the square to the tavern
            chooseStage(level);
            setPhase(Phase.WALK);
            return;
        }
        switch (phase) {
            case FETCH -> fetch(level, now);
            case WALK -> walk(level);
            case PLAY -> {
                music.tick(level);
                lookAround();
                if (!music.isPlaying()) {
                    setPhase(Phase.PAUSE);
                }
            }
            case PAUSE -> {
                lookAround();
                if (phaseTicks >= BardRules.PAUSE_TICKS) {
                    setPhase(wantsDisc(level) ? Phase.FETCH : Phase.WALK);
                }
            }
        }
    }

    // ---- where to play ---------------------------------------------------------------------------

    private boolean wantsTavern(ServerLevel level) {
        return BardRules.playsInTavern(ScheduleClock.minuteOfDay(level.getOverworldClockTime()),
                TavernVisitGoal.tavern(level, resident.brain().village()).isPresent());
    }

    /** The square at the village center, or a standing place in the tavern (the counter if all are taken). */
    private void chooseStage(ServerLevel level) {
        releasePlace();
        Village village = resident.brain().village();
        Building tavern = TavernVisitGoal.tavern(level, village).orElse(null);
        inTavern = BardRules.playsInTavern(ScheduleClock.minuteOfDay(level.getOverworldClockTime()), tavern != null);
        target = village.center();
        if (inTavern) {
            place = TavernPlaces.claimStanding(level, tavern, resident.getUUID(), resident.getRandom());
            target = place != null ? place.target() : TavernVisitGoal.brewingStand(level, village).orElse(tavern.markerPos());
        }
    }

    private void releasePlace() {
        if (place != null) {
            TavernPlaces.release(place, resident.getUUID());
            place = null;
        }
    }

    private boolean atStage() {
        if (place != null) {
            return place.target().closerToCenterThan(resident.position(), PLACE_RANGE);
        }
        double dx = resident.getX() - (target.getX() + 0.5);
        double dz = resident.getZ() - (target.getZ() + 0.5);
        double range = inTavern ? CONTAINER_RANGE : SQUARE_RANGE;
        return dx * dx + dz * dz <= range * range && Math.abs(resident.getY() - target.getY()) < 3;
    }

    private void walk(ServerLevel level) {
        if (atStage() || phaseTicks > GIVE_UP_TICKS) { // cannot get there: plays where he is
            resident.getNavigation().stop();
            music.start(level, nextDisc());
            setPhase(Phase.PLAY);
            return;
        }
        if (phaseTicks % 20 == 1) {
            int reach = place != null ? 0 : inTavern ? 1 : 3;
            resident.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, reach, speedModifier);
        }
    }

    private void lookAround() {
        if (phaseTicks % 60 != 0) {
            return;
        }
        List<ResidentEntity> near = resident.level().getEntitiesOfClass(ResidentEntity.class,
                resident.getBoundingBox().inflate(6.0), other -> other != resident);
        if (!near.isEmpty()) {
            resident.getLookControl().setLookAt(near.get(resident.getRandom().nextInt(near.size())), 30.0F, 30.0F);
        }
    }

    // ---- discs -----------------------------------------------------------------------------------

    private static boolean isDisc(ItemStack stack) {
        return !stack.isEmpty() && JukeboxSong.fromStack(stack).isPresent();
    }

    private List<ItemStack> discs() {
        return resident.carriedItems().stream().filter(BardGoal::isDisc).toList();
    }

    /** The next disc to play (changing between them), or empty for a tune. */
    private ItemStack nextDisc() {
        List<ItemStack> discs = discs();
        if (discs.isEmpty()) {
            return ItemStack.EMPTY;
        }
        discIndex = (discIndex + 1) % discs.size();
        return discs.get(discIndex);
    }

    private boolean wantsDisc(ServerLevel level) {
        return discs().size() < BardRules.MAX_DISCS && level.getGameTime() >= nextFetchGameTime;
    }

    /** A disc of a song he does not carry yet. */
    private Predicate<ItemStack> newDisc() {
        List<ItemStack> carried = discs();
        return stack -> isDisc(stack) && carried.stream().noneMatch(own -> own.is(stack.getItem()));
    }

    private void fetch(ServerLevel level, long now) {
        if (container == null) {
            container = findDiscs(level);
            if (container == null) {
                nextFetchGameTime = now + FETCH_COOLDOWN_TICKS;
                report(discs().isEmpty() ? ISSUE_NO_DISC : null);
                setPhase(Phase.WALK);
                return;
            }
        }
        if (container.closerToCenterThan(resident.position(), CONTAINER_RANGE)) {
            Container found = StorageAccess.containerAt(level, container);
            if (found != null) {
                resident.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
                StorageAccess.take(found, newDisc(), 1).forEach(resident::carry);
            }
            container = null;
            report(discs().isEmpty() ? ISSUE_NO_DISC : null);
            setPhase(Phase.WALK); // the next pause may fetch another one
            return;
        }
        if (phaseTicks > GIVE_UP_TICKS) {
            container = null;
            nextFetchGameTime = now + FETCH_COOLDOWN_TICKS;
            setPhase(Phase.WALK);
            return;
        }
        if (phaseTicks % 20 == 1) {
            resident.getNavigation().moveTo(container.getX() + 0.5, container.getY(), container.getZ() + 0.5, 1, speedModifier);
        }
    }

    private BlockPos findDiscs(ServerLevel level) {
        Predicate<ItemStack> wanted = newDisc();
        for (BlockPos pos : StorageAccess.containers(resident.brain().village(), resident.brain().workplace(), resident.blockPosition())) {
            Container found = StorageAccess.containerAt(level, pos);
            if (found != null && StorageAccess.contains(found, wanted)) {
                return pos;
            }
        }
        return null;
    }

    private void report(String issue) {
        String now = resident.brain().workIssue();
        if (issue == null ? ISSUE_NO_DISC.equals(now) : !issue.equals(now)) {
            resident.brain().setWorkIssue(issue);
        }
    }

    private void setPhase(Phase next) {
        phase = next;
        phaseTicks = 0;
    }
}
