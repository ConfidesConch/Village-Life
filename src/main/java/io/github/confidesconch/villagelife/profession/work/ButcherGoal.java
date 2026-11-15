package io.github.confidesconch.villagelife.profession.work;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import io.github.confidesconch.villagelife.building.Building;
import io.github.confidesconch.villagelife.profession.JobAllocator.Workplace;
import io.github.confidesconch.villagelife.profession.JobService;
import io.github.confidesconch.villagelife.profession.work.HerdArea.Herd;
import io.github.confidesconch.villagelife.resident.ResidentEntity;
import io.github.confidesconch.villagelife.village.Village;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

/**
 * Work of the butcher (M27, owner's choice "campfire, only the butcher slaughters, leather armor"): walks
 * to the work areas of the village's shepherds and breeders and slaughters adults above the village's herd
 * limit – per kind of animal, sheared sheep first – picks up meat, leather and wool and brings them to
 * storage, where the cook finds the meat. Leather armor for the guards is made by the generic craft goal
 * from the crafts in {@code profession/butcher.json}.
 */
public class ButcherGoal extends WorkGoal {
    public static final String PROFESSION = "villagelife:butcher";

    private static final double REACH = 2.2;
    private static final int COLLECT_DELAY_TICKS = 10;
    private static final double COLLECT_RADIUS = 3.0;
    private static final int IDLE_COOLDOWN_TICKS = 200;
    private static final int IGNORE_TICKS = 1200;

    private enum Phase { TO_TARGET, COLLECT, DELIVER }

    private final Map<UUID, Long> ignored = new HashMap<>();
    private Phase phase;
    private Animal target;
    private BlockPos dropPos;
    private int phaseTicks;
    private long nextLookGameTime;

    public ButcherGoal(ResidentEntity resident, double speedModifier) {
        super(resident, speedModifier, PROFESSION, ItemTags.SWORDS, Items.IRON_AXE); // a cleaver in the hand
    }

    @Override
    public boolean canUse() {
        if (!isWorkTime() || !(resident.level() instanceof ServerLevel level) || resident.brain().village() == null) {
            return false;
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
        if (hasSomethingToDeliver()) {
            setPhase(Phase.DELIVER);
            return true;
        }
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        return isWorkTime() && phase != null && resident.brain().village() != null;
    }

    @Override
    public void stop() {
        phase = null;
        target = null;
        super.stop();
    }

    @Override
    protected boolean deliversToWorkplace() {
        return false; // meat and leather belong in the storage, where the cook and the craft goal find them
    }

    // ---- choosing ----------------------------------------------------------------------------------

    /** The first herd (of any herder's area) with more adults than the limit: its next animal to slaughter. */
    private boolean chooseWork(ServerLevel level) {
        Village village = resident.brain().village();
        long now = level.getGameTime();
        ignored.values().removeIf(until -> until < now);
        int limit = village.settings().herdLimit();
        Set<UUID> seen = new HashSet<>(); // a workplace with several places is listed several times
        Set<String> owners = new HashSet<>(); // M28b: shepherd and breeder share the open pasture
        for (Workplace workplace : JobService.workplaces(level, village)) {
            Predicate<Animal> kind = kindFor(workplace.profession());
            if (kind == null || !seen.add(workplace.buildingId())) {
                continue;
            }
            Building building = village.buildings().stream()
                    .filter(b -> b.id().equals(workplace.buildingId())).findFirst().orElse(null);
            List<HerdArea> areas = building == null ? List.of()
                    : HerdPastures.areasOf(level, village, building, workplace.profession());
            for (HerdArea area : areas) {
                if (!owners.add(area.owner() + "/" + workplace.profession())) {
                    continue;
                }
                Map<?, Herd<Animal>> herds = area.herds(level, Animal.class, a -> kind.test(a) && !ignored.containsKey(a.getUUID()));
                for (Herd<Animal> herd : herds.values()) {
                    if (HerdRules.next(herd.adults.size(), herd.young, herd.ready.size(), limit, false) == HerdRules.Task.CULL) {
                        target = candidate(herd.adults);
                        setPhase(Phase.TO_TARGET);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Whose animals the butcher looks after in a workplace's area; null for other workplaces. */
    private static Predicate<Animal> kindFor(String profession) {
        if (ShepherdGoal.PROFESSION.equals(profession)) {
            return animal -> animal instanceof Sheep;
        }
        if (BreederGoal.PROFESSION.equals(profession)) {
            return animal -> BreederGoal.isLivestock(animal) || animal instanceof Sheep; // M28b: the breeder keeps sheep, too
        }
        return null;
    }

    /** Sheared sheep first (their wool is already taken), then the oldest-looking: the first one found. */
    private static Animal candidate(List<Animal> adults) {
        return adults.stream().min(Comparator.comparingInt(a -> a instanceof Sheep sheep && sheep.isSheared() ? 0 : 1))
                .orElse(adults.getFirst());
    }

    // ---- doing -------------------------------------------------------------------------------------

    @Override
    public void tick() {
        if (!(resident.level() instanceof ServerLevel level) || phase == null) {
            return;
        }
        phaseTicks++;
        switch (phase) {
            case TO_TARGET -> walkToTarget(level);
            case COLLECT -> collect(level);
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
            swing();
            dropPos = target.blockPosition();
            target.hurtServer(level, level.damageSources().mobAttack(resident), Float.MAX_VALUE);
            target = null;
            setPhase(Phase.COLLECT);
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

    /** Waits a moment for the drops to land, then takes everything lying around the spot. */
    private void collect(ServerLevel level) {
        if (phaseTicks < COLLECT_DELAY_TICKS) {
            return;
        }
        AABB around = new AABB(dropPos).inflate(COLLECT_RADIUS);
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
