package io.github.confidesconch.villagelife.profession.work;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

import io.github.confidesconch.villagelife.building.Building;
import io.github.confidesconch.villagelife.building.BuildingType;
import io.github.confidesconch.villagelife.inventory.StorageAccess;
import io.github.confidesconch.villagelife.profession.Craft;
import io.github.confidesconch.villagelife.profession.JobAllocator.Workplace;
import io.github.confidesconch.villagelife.profession.JobService;
import io.github.confidesconch.villagelife.profession.Profession;
import io.github.confidesconch.villagelife.profession.Professions;
import io.github.confidesconch.villagelife.resident.ResidentEntity;
import io.github.confidesconch.villagelife.village.Village;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Work of the storekeeper (M14c, owner's choice "3a"): keeps the goods moving between the workplaces
 * and the storage. Empties the workplace whose containers hold the most products into storage, and
 * brings each workplace a small supply (up to {@link CourierPlanner#SUPPLY_UP_TO}) of what its
 * profession needs – the materials of its profession file and the inputs of its recipes. What a
 * workplace needs (and tools) stays there; a crafting workplace's own outputs count as products.
 * Workplaces without a container are not served.
 */
public class StorekeeperGoal extends WorkGoal {
    public static final String PROFESSION = "villagelife:storekeeper";

    private static final int PLAN_INTERVAL_TICKS = 40;
    private static final double CONTAINER_DISTANCE = 2.5;

    private enum Phase { COLLECT, FETCH, BRING, DELIVER }

    /** A served workplace: its building, what its profession needs and what it makes itself. */
    private record Site(Building building, List<String> needs, List<String> outputs) {
        boolean isNeed(ItemStack stack) {
            return needs.stream().anyMatch(spec -> ItemSpec.matcher(spec).test(stack)) && !isOutput(stack);
        }

        boolean isOutput(ItemStack stack) {
            return outputs.stream().anyMatch(spec -> ItemSpec.matcher(spec).test(stack));
        }

        /** Products: everything that is not needed there and is not a tool. */
        boolean isProduct(ItemStack stack) {
            return !stack.isEmpty() && !stack.isDamageableItem() && !isNeed(stack);
        }
    }

    private Phase phase;
    private int phaseTicks;
    private Site site;
    private String supply;
    private int supplyAmount;
    private long nextPlanGameTime;

    public StorekeeperGoal(ResidentEntity resident, double speedModifier) {
        super(resident, speedModifier, PROFESSION, ItemTags.SHOVELS, Items.BOOK); // a book for the lists
    }

    @Override
    public boolean canUse() {
        if (!isWorkTime() || !(resident.level() instanceof ServerLevel level) || level.getGameTime() < nextPlanGameTime) {
            return false;
        }
        nextPlanGameTime = level.getGameTime() + PLAN_INTERVAL_TICKS;
        return plan(level);
    }

    @Override
    public boolean canContinueToUse() {
        return isWorkTime() && phase != null;
    }

    @Override
    public void stop() {
        phase = null;
        site = null;
        super.stop();
    }

    @Override
    protected boolean deliversToWorkplace() {
        return false; // everything goes to the storage buildings
    }

    // ---- planning ------------------------------------------------------------------------------

    /** Deliver what it carries, else empty the fullest workplace, else bring a missing material. */
    private boolean plan(ServerLevel level) {
        if (hasSomethingToDeliver()) {
            return setPhase(Phase.DELIVER);
        }
        List<Site> sites = sites(level);
        List<Integer> products = new ArrayList<>();
        for (Site candidate : sites) {
            products.add(count(level, candidate.building(), candidate::isProduct));
        }
        int fullest = CourierPlanner.fullest(products);
        if (fullest >= 0) {
            site = sites.get(fullest);
            return setPhase(Phase.COLLECT);
        }
        for (Site candidate : sites) {
            for (String need : candidate.needs()) {
                Predicate<ItemStack> wanted = ItemSpec.matcher(need);
                if (candidate.outputs().contains(need)) {
                    continue; // made there itself
                }
                int amount = CourierPlanner.supplyAmount(count(level, candidate.building(), wanted));
                if (amount > 0 && mayFetch(level, "supply:" + need) && inStorage(level, wanted)) {
                    site = candidate;
                    supply = need;
                    supplyAmount = amount;
                    return setPhase(Phase.FETCH);
                }
            }
        }
        return false;
    }

    /** The village's workplaces with containers, except the own office. */
    private List<Site> sites(ServerLevel level) {
        List<Site> sites = new ArrayList<>();
        Village village = resident.brain().village();
        if (village == null) {
            return sites;
        }
        UUID own = workplace().id();
        for (Workplace workplace : JobService.workplaces(level, village)) {
            if (workplace.buildingId().equals(own)) {
                continue;
            }
            village.buildings().stream()
                    .filter(b -> b.id().equals(workplace.buildingId()) && !b.containers().isEmpty())
                    .findFirst()
                    .ifPresent(building -> Professions.get(workplace.profession())
                            .ifPresent(profession -> sites.add(site(building, profession))));
        }
        return sites;
    }

    private static Site site(Building building, Profession profession) {
        List<String> needs = new ArrayList<>();
        profession.materials().forEach(id -> needs.add(id.toString()));
        List<String> outputs = new ArrayList<>();
        for (Craft craft : profession.crafts()) {
            craft.inputs().forEach(input -> needs.add(input.item()));
            outputs.add(craft.output().item());
        }
        return new Site(building, List.copyOf(needs), List.copyOf(outputs));
    }

    private static int count(ServerLevel level, Building building, Predicate<ItemStack> wanted) {
        int count = 0;
        for (BlockPos pos : building.containers()) {
            Container container = StorageAccess.containerAt(level, pos);
            for (int slot = 0; container != null && slot < container.getContainerSize(); slot++) {
                ItemStack stack = container.getItem(slot);
                count += wanted.test(stack) ? stack.getCount() : 0;
            }
        }
        return count;
    }

    private boolean inStorage(ServerLevel level, Predicate<ItemStack> wanted) {
        Village village = resident.brain().village();
        return village != null && village.buildings().stream()
                .filter(b -> b.type() == BuildingType.STORAGE && b.isValid())
                .anyMatch(b -> count(level, b, wanted) > 0);
    }

    private boolean setPhase(Phase next) {
        phase = next;
        phaseTicks = 0;
        resetPath();
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
            case COLLECT -> {
                if (atSite(level)) {
                    takeProducts(level);
                    setPhase(Phase.DELIVER);
                }
            }
            case FETCH -> {
                if (fetchStep(level, "supply:" + supply, ItemSpec.matcher(supply), supplyAmount) != FetchStatus.RUNNING) {
                    setPhase(resident.carriedCount(ItemSpec.matcher(supply)) > 0 ? Phase.BRING : Phase.DELIVER);
                }
            }
            case BRING -> {
                if (atSite(level)) {
                    bringSupply(level);
                    setPhase(Phase.DELIVER); // whatever did not fit goes back to storage
                }
            }
            case DELIVER -> {
                if (deliverStep(level)) {
                    phase = plan(level) ? phase : null;
                }
            }
        }
    }

    /** Walks to the site's first container; true once there. Gives up (back to storage) if it cannot get there. */
    private boolean atSite(ServerLevel level) {
        if (site == null || site.building().containers().isEmpty()) {
            phase = Phase.DELIVER;
            return false;
        }
        BlockPos target = site.building().containers().getFirst();
        if (target.closerToCenterThan(resident.position(), CONTAINER_DISTANCE)) {
            resident.getNavigation().stop();
            resident.getLookControl().setLookAt(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
            swing();
            return true;
        }
        if (phaseTicks > GIVE_UP_TICKS) {
            setPhase(Phase.DELIVER);
            return false;
        }
        moveTowards(target, 1);
        return false;
    }

    private void takeProducts(ServerLevel level) {
        for (BlockPos pos : site.building().containers()) {
            Container container = StorageAccess.containerAt(level, pos);
            int room = CARRY_LIMIT - resident.carriedCount();
            if (container != null && room > 0) {
                StorageAccess.take(container, site::isProduct, room).forEach(resident::carry);
            }
        }
    }

    private void bringSupply(ServerLevel level) {
        Predicate<ItemStack> wanted = ItemSpec.matcher(supply);
        for (BlockPos pos : site.building().containers()) {
            Container container = StorageAccess.containerAt(level, pos);
            if (container == null) {
                continue;
            }
            ItemStack carried = resident.takeCarried(wanted, 64);
            if (!carried.isEmpty()) {
                resident.carry(StorageAccess.insert(container, carried));
            }
        }
    }
}
