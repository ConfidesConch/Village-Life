package io.github.confidesconch.villagelife.profession.work;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import io.github.confidesconch.villagelife.building.AreaService;
import io.github.confidesconch.villagelife.building.Building;
import io.github.confidesconch.villagelife.building.BuildingType;
import io.github.confidesconch.villagelife.profession.work.HerdArea.Herd;
import io.github.confidesconch.villagelife.profession.work.HerdRules.Task;
import io.github.confidesconch.villagelife.resident.ResidentEntity;
import io.github.confidesconch.villagelife.village.Village;
import io.github.confidesconch.villagelife.village.VillageZone;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Shared work of herders (shepherd M13c, breeder M13d): looks after the animals inside the work area
 * the owner assigned to the workplace ({@link HerdArea}). In this order: brings escaped animals of its
 * herd back ({@link HerdLure}), picks up loose products (e.g. eggs), does the profession's own task
 * (shearing, milking), then – for each kind of animal separately – slaughters adults above the
 * village's herd limit or feeds a pair to breed ({@link HerdRules}). Picks up what falls (wool, meat,
 * leather) and delivers it like every worker. Without a work area it reports the problem in the book.
 */
public abstract class HerdGoal<T extends Animal> extends WorkGoal {
    private static final double REACH = 2.2;
    private static final int COLLECT_DELAY_TICKS = 10;
    private static final double COLLECT_RADIUS = 3.0;
    private static final int FEED_TO_KEEP = 8;
    /** After finding nothing to do, look again after this long. */
    private static final int IDLE_COOLDOWN_TICKS = 100;
    /** Animals and items that could not be reached are left alone for this long. */
    private static final int IGNORE_TICKS = 1200;

    /** What the herder does with its target animal. */
    protected enum Action { SPECIAL, CULL, FEED, RETURN }

    /** Something to fetch from storage before the own task (e.g. empty buckets). */
    protected record Supply(String key, Predicate<ItemStack> wanted, int amount) {}

    private enum Phase { DELIVER, FETCH, TO_TARGET, COLLECT, LEAD }

    private final Class<T> animalClass;
    private final Map<UUID, Long> ignored = new HashMap<>();
    private Phase phase;
    private Action action;
    /** The animal (or, if {@code action} is null, the loose item) the herder walks to. */
    private Entity target;
    /** The second animal of a breeding pair, fed right after the first. */
    private T partner;
    /** Bringing an escaped animal back (lure, or carry as fallback), or null. */
    private HerdLure lure;
    /** What is fetched in phase FETCH. */
    private Supply fetching;
    private Vec3 dropPos;
    private int phaseTicks;
    private long nextLookGameTime;

    protected HerdGoal(ResidentEntity resident, double speedModifier, String profession, TagKey<Item> toolTag,
                       Item basicTool, Class<T> animalClass) {
        super(resident, speedModifier, profession, toolTag, basicTool);
        this.animalClass = animalClass;
    }

    /** True for the animals this herder looks after (default: every animal of the class). */
    protected boolean accepts(T animal) {
        return true;
    }

    /** Feed this herder fetches from storage and keeps a few of (each kind of animal still picks its own). */
    protected abstract boolean isFeed(ItemStack stack);

    /** The animal for the profession's own task (e.g. a sheep with wool), or null. */
    protected abstract T specialTarget(List<T> adults);

    /** Does the profession's own task on the animal (the herder stands next to it). */
    protected abstract void doSpecial(ServerLevel level, T animal);

    /** True if the special task leaves drops on the ground to pick up (e.g. wool). */
    protected boolean specialDrops() {
        return true;
    }

    /** True if the special task uses up the tool a little (shears: yes; milking: no). */
    protected boolean specialWearsTool() {
        return true;
    }

    /** Which adult to slaughter first (e.g. a sheared sheep); default: the first one. */
    protected T cullCandidate(List<T> adults) {
        return adults.getFirst();
    }

    /** Items lying in the area that the herder picks up (e.g. eggs); default: none. */
    protected boolean isLooseProduct(ItemStack stack) {
        return false;
    }

    /** Something to fetch before the special task can be done (e.g. buckets for milking), or null. */
    protected Supply neededSupply(List<T> adults) {
        return null;
    }

    @Override
    public boolean canUse() {
        if (!isWorkTime() || !(resident.level() instanceof ServerLevel level)) {
            return false;
        }
        if (area() == null) {
            resident.brain().setWorkIssue(ISSUE_NO_WORK_AREA);
            return startDeliverIfNeeded();
        }
        if (ISSUE_NO_WORK_AREA.equals(resident.brain().workIssue())) {
            resident.brain().setWorkIssue(null);
        }
        if (isFull()) {
            setPhase(Phase.DELIVER);
            return true;
        }
        if (level.getGameTime() >= nextLookGameTime) {
            if (chooseWork(level)) {
                return true;
            }
            nextLookGameTime = level.getGameTime() + IDLE_COOLDOWN_TICKS;
        }
        return startDeliverIfNeeded();
    }

    private boolean startDeliverIfNeeded() {
        if (hasSomethingToDeliver()) {
            setPhase(Phase.DELIVER);
            return true;
        }
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        return isWorkTime() && phase != null;
    }

    @Override
    public void stop() {
        if (lure != null && resident.level() instanceof ServerLevel level) {
            lure.finish(level);
        }
        lure = null;
        phase = null;
        target = null;
        partner = null;
        super.stop();
    }

    @Override
    protected int keepMaterial(ItemStack stack) {
        return isFeed(stack) ? FEED_TO_KEEP : 0;
    }

    /** M21d: the marked pasture a herder of {@code profession} uses while no zone is drawn for its workplace. */
    public static java.util.Optional<Building> autoPasture(Village village, Building workplace, String profession) {
        boolean herder = ShepherdGoal.PROFESSION.equals(profession) || BreederGoal.PROFESSION.equals(profession);
        return herder ? AreaService.nearest(village, BuildingType.PASTURE, workplace.center()) : java.util.Optional.empty();
    }

    /** The assigned work zone; M21d: without one, the nearest pasture marked with a marker. */
    private HerdArea area() {
        Building workplace = workplace();
        if (workplace == null) {
            return null;
        }
        VillageZone zone = workArea();
        if (zone != null) {
            return new HerdArea(zone, workplace.center().getY(), workplace.id().toString());
        }
        return AreaService.nearest(resident.brain().village(), BuildingType.PASTURE, workplace.center())
                .map(pasture -> new HerdArea(AreaService.asZone(pasture), pasture.center().getY(), workplace.id().toString()))
                .orElse(null);
    }

    private boolean usable(T animal) {
        return accepts(animal) && !ignored.containsKey(animal.getUUID());
    }

    // ---- choosing ------------------------------------------------------------------------------

    /** Escaped animals, loose products, own task (fetching supplies first), then per kind: too many animals, breeding. */
    private boolean chooseWork(ServerLevel level) {
        HerdArea area = area();
        if (area == null) {
            return false;
        }
        long now = level.getGameTime();
        ignored.values().removeIf(until -> until < now);
        T escaped = area.escaped(level, animalClass, this::usable);
        if (escaped != null) {
            return approach(escaped, null, Action.RETURN);
        }
        ItemEntity loose = area.loose(level, item -> isLooseProduct(item.getItem()) && !ignored.containsKey(item.getUUID()));
        if (loose != null) {
            return approach(loose, null, null);
        }
        Map<?, Herd<T>> herds = area.herds(level, animalClass, this::usable);
        List<T> adults = new ArrayList<>();
        herds.values().forEach(herd -> adults.addAll(herd.adults));
        Supply supply = neededSupply(adults);
        if (supply != null && mayFetch(level, supply.key())) {
            return fetch(supply);
        }
        T special = specialTarget(adults);
        if (special != null) {
            return approach(special, null, Action.SPECIAL);
        }
        int limit = resident.brain().village() == null ? 8 : resident.brain().village().settings().herdLimit();
        for (Herd<T> herd : herds.values()) {
            if (herd.adults.isEmpty()) {
                continue;
            }
            T sample = herd.adults.getFirst();
            Predicate<ItemStack> food = sample::isFood;
            boolean carried = resident.carriedCount(food) > 0;
            String key = "feed:" + sample.getType().getDescriptionId();
            Task task = HerdRules.next(herd.adults.size(), herd.young, herd.ready.size(), limit, carried || mayFetch(level, key));
            if (task == Task.CULL) {
                return approach(cullCandidate(herd.adults), null, Action.CULL);
            }
            if (task == Task.BREED) {
                return carried ? approach(herd.ready.get(0), herd.ready.get(1), Action.FEED)
                        : fetch(new Supply(key, food, FEED_TO_KEEP));
            }
        }
        return false;
    }

    private boolean fetch(Supply supply) {
        fetching = supply;
        setPhase(Phase.FETCH);
        return true;
    }

    /** Walk to an animal ({@code what} = what to do with it) or to a loose item ({@code what} = null). */
    private boolean approach(Entity entity, T second, Action what) {
        target = entity;
        partner = second;
        action = what;
        setPhase(Phase.TO_TARGET);
        return true;
    }

    // ---- doing ---------------------------------------------------------------------------------

    @Override
    public void tick() {
        if (!(resident.level() instanceof ServerLevel level) || phase == null) {
            return;
        }
        phaseTicks++;
        switch (phase) {
            case TO_TARGET -> walkToTarget(level);
            case COLLECT -> collect(level);
            case LEAD -> lead(level);
            case FETCH -> {
                if (fetchStep(level, fetching.key(), fetching.wanted(), fetching.amount()) != FetchStatus.RUNNING) {
                    next(level);
                }
            }
            case DELIVER -> {
                if (deliverStep(level)) {
                    next(level);
                }
            }
        }
    }

    private void walkToTarget(ServerLevel level) {
        if (target == null || !target.isAlive()) {
            next(level);
            return;
        }
        if (resident.distanceTo(target) <= REACH) {
            resident.getNavigation().stop();
            resident.getLookControl().setLookAt(target);
            if (action == null) { // a loose item: just pick it up
                dropPos = target.position();
                setPhase(Phase.COLLECT);
                phaseTicks = COLLECT_DELAY_TICKS;
            } else {
                act(level, animalClass.cast(target));
            }
            return;
        }
        if (phaseTicks > GIVE_UP_TICKS) {
            ignored.put(target.getUUID(), level.getGameTime() + IGNORE_TICKS);
            next(level);
            return;
        }
        if (phaseTicks % REPATH_TICKS == 1) {
            resident.getNavigation().moveTo(target, speedModifier);
        }
    }

    private void act(ServerLevel level, T animal) {
        swing();
        switch (action) {
            case SPECIAL -> {
                doSpecial(level, animal);
                if (specialWearsTool()) {
                    wearTool(level);
                }
                if (specialDrops()) {
                    startCollecting(animal);
                } else {
                    next(level);
                }
            }
            case CULL -> {
                animal.hurtServer(level, level.damageSources().mobAttack(resident), Float.MAX_VALUE);
                startCollecting(animal);
            }
            case RETURN -> {
                lure = new HerdLure(level, resident, animal, area());
                setPhase(Phase.LEAD);
            }
            case FEED -> {
                if (!resident.takeCarried(animal::isFood, 1).isEmpty()) {
                    animal.setInLove(null);
                }
                T second = partner;
                partner = null;
                if (second != null && second.isAlive() && resident.carriedCount(second::isFood) > 0) {
                    approach(second, null, Action.FEED);
                } else {
                    next(level);
                }
            }
        }
    }

    /** Brings the escaped animal back ({@link HerdLure}); afterwards the herder holds its tool again. */
    private void lead(ServerLevel level) {
        if (lure == null || area() == null || lure.tick(level, speedModifier) == HerdLure.Status.DONE) {
            if (lure != null && area() == null) {
                lure.finish(level);
            }
            lure = null;
            showTool();
            next(level);
        }
    }

    private void startCollecting(T animal) {
        dropPos = animal.position();
        setPhase(Phase.COLLECT);
    }

    /** Waits a moment for the drops to land, then takes everything lying around the spot. */
    private void collect(ServerLevel level) {
        if (phaseTicks < COLLECT_DELAY_TICKS) {
            return;
        }
        AABB around = new AABB(BlockPos.containing(dropPos)).inflate(COLLECT_RADIUS);
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, around, ItemEntity::isAlive)) {
            resident.carry(item.getItem().copy());
            item.discard();
        }
        next(level);
    }

    private void next(ServerLevel level) {
        target = null;
        if (isFull()) {
            setPhase(Phase.DELIVER);
        } else if (!chooseWork(level)) {
            phase = hasSomethingToDeliver() ? Phase.DELIVER : null;
            phaseTicks = 0;
            nextLookGameTime = level.getGameTime() + IDLE_COOLDOWN_TICKS;
        }
    }

    private void setPhase(Phase next) {
        phase = next;
        phaseTicks = 0;
        resetPath();
    }
}
