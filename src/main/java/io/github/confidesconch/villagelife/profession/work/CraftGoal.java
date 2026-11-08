package io.github.confidesconch.villagelife.profession.work;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import io.github.confidesconch.villagelife.building.Building;
import io.github.confidesconch.villagelife.building.BuildingType;
import io.github.confidesconch.villagelife.inventory.StorageAccess;
import io.github.confidesconch.villagelife.profession.Craft;
import io.github.confidesconch.villagelife.profession.Profession;
import io.github.confidesconch.villagelife.profession.Professions;
import io.github.confidesconch.villagelife.profession.work.CraftPlanner.Action;
import io.github.confidesconch.villagelife.profession.work.CraftPlanner.Choice;
import io.github.confidesconch.villagelife.profession.work.CraftPlanner.State;
import io.github.confidesconch.villagelife.resident.Job;
import io.github.confidesconch.villagelife.resident.ResidentEntity;
import io.github.confidesconch.villagelife.village.Village;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Work of every crafting profession (M14a: baker, carpenter …): the recipes come from the profession
 * file ({@link Craft}), so a new crafting profession needs no code. While the village holds fewer than
 * a recipe's target stock of its output, the worker fetches the inputs from storage, crafts at its
 * workstation and delivers the products ({@link CraftPlanner}). If nothing that is wanted can be made,
 * the book shows "material missing".
 */
public class CraftGoal extends WorkGoal {
    /** Shown in the book as {@code villagelife.problem.work.no_material}. */
    public static final String ISSUE_NO_MATERIAL = "no_material";

    /** Crafts per trip: inputs for up to this many are fetched at once. */
    private static final int BATCH = 8;
    private static final int INPUTS_TO_KEEP = 64;
    private static final int STOCK_REFRESH_TICKS = 200;
    private static final double STATION_DISTANCE = 2.5;

    private enum Phase { FETCH, TO_STATION, DELIVER }

    private final Map<Integer, Integer> stock = new HashMap<>();
    private long stockCountedGameTime = -STOCK_REFRESH_TICKS;
    private Phase phase;
    private int phaseTicks;
    private int craftTicks;
    private Craft current;
    private Craft.Stack fetching;
    private int fetchAmount;

    public CraftGoal(ResidentEntity resident, double speedModifier) {
        super(resident, speedModifier, "", ItemTags.SHOVELS, Items.AIR); // hands empty until crafting
    }

    @Override
    protected boolean worksAs(String jobProfession) {
        return Professions.get(jobProfession).map(p -> !p.crafts().isEmpty()).orElse(false);
    }

    @Override
    public boolean canUse() {
        return isWorkTime() && resident.level() instanceof ServerLevel level && chooseWork(level);
    }

    @Override
    public boolean canContinueToUse() {
        return isWorkTime() && phase != null;
    }

    @Override
    public void stop() {
        phase = null;
        current = null;
        super.stop();
    }

    @Override
    protected int keepMaterial(ItemStack stack) {
        for (Craft craft : crafts()) {
            for (Craft.Stack input : craft.inputs()) {
                if (matcher(input.item()).test(stack)) {
                    return INPUTS_TO_KEEP;
                }
            }
        }
        return 0;
    }

    // ---- choosing ------------------------------------------------------------------------------

    private boolean chooseWork(ServerLevel level) {
        if (isFull()) {
            return setPhase(Phase.DELIVER);
        }
        List<Craft> crafts = crafts();
        refreshStock(level, crafts);
        List<State> states = new ArrayList<>();
        for (int i = 0; i < crafts.size(); i++) {
            Craft craft = crafts.get(i);
            states.add(new State(stock.getOrDefault(i, 0), craft.stock(), hasInputs(craft), canFetch(level, craft)));
        }
        resident.brain().setWorkIssue(CraftPlanner.missingMaterial(states) ? ISSUE_NO_MATERIAL
                : ISSUE_NO_MATERIAL.equals(resident.brain().workIssue()) ? null : resident.brain().workIssue());
        Choice choice = CraftPlanner.choose(states);
        if (choice.action() == Action.CRAFT) {
            current = crafts.get(choice.index());
            return setPhase(Phase.TO_STATION);
        }
        if (choice.action() == Action.FETCH) {
            Craft craft = crafts.get(choice.index());
            int wanted = Math.min(BATCH, CraftPlanner.craftsWanted(states.get(choice.index()).stock(), craft.stock(),
                    craft.output().count()));
            for (Craft.Stack input : craft.inputs()) {
                if (resident.carriedCount(matcher(input.item())) < input.count()) {
                    fetching = input;
                    fetchAmount = input.count() * Math.max(1, wanted);
                    return setPhase(Phase.FETCH);
                }
            }
        }
        return hasSomethingToDeliver() && setPhase(Phase.DELIVER);
    }

    private boolean hasInputs(Craft craft) {
        return craft.inputs().stream().allMatch(input -> resident.carriedCount(matcher(input.item())) >= input.count());
    }

    private boolean canFetch(ServerLevel level, Craft craft) {
        return craft.inputs().stream().allMatch(input -> resident.carriedCount(matcher(input.item())) >= input.count()
                || mayFetch(level, "in:" + input.item()));
    }

    /** How much of each recipe's output the village has (storage and the own workplace), every 10 s. */
    private void refreshStock(ServerLevel level, List<Craft> crafts) {
        if (level.getGameTime() - stockCountedGameTime < STOCK_REFRESH_TICKS) {
            return;
        }
        stockCountedGameTime = level.getGameTime();
        stock.clear();
        Village village = resident.brain().village();
        if (village == null) {
            return;
        }
        for (Building building : village.buildings()) {
            boolean counts = building.type() == BuildingType.STORAGE || building.id().equals(workplace().id());
            if (!counts || !building.isValid()) {
                continue;
            }
            for (BlockPos pos : building.containers()) {
                Container container = StorageAccess.containerAt(level, pos);
                for (int slot = 0; container != null && slot < container.getContainerSize(); slot++) {
                    ItemStack stack = container.getItem(slot);
                    for (int i = 0; i < crafts.size(); i++) {
                        if (stack.is(output(crafts.get(i)))) {
                            stock.merge(i, stack.getCount(), Integer::sum);
                        }
                    }
                }
            }
        }
    }

    private boolean setPhase(Phase next) {
        phase = next;
        phaseTicks = 0;
        craftTicks = 0;
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
            case TO_STATION -> craftAtStation(level);
            case FETCH -> {
                if (fetchStep(level, "in:" + fetching.item(), matcher(fetching.item()), fetchAmount) != FetchStatus.RUNNING) {
                    next(level);
                }
            }
            case DELIVER -> {
                if (deliverStep(level)) {
                    stockCountedGameTime = -STOCK_REFRESH_TICKS; // count again after delivering
                    next(level);
                }
            }
        }
    }

    private void next(ServerLevel level) {
        if (!chooseWork(level)) {
            phase = null;
        }
    }

    /** Walks to the workstation, then crafts one after another while inputs last and the output is wanted. */
    private void craftAtStation(ServerLevel level) {
        BlockPos station = station(level);
        if (station == null || current == null || phaseTicks > GIVE_UP_TICKS + craftTicks) {
            phase = null;
            return;
        }
        if (!station.closerToCenterThan(resident.position(), STATION_DISTANCE)) {
            moveTowards(station, 1);
            return;
        }
        resident.getNavigation().stop();
        resident.getLookControl().setLookAt(station.getX() + 0.5, station.getY() + 0.5, station.getZ() + 0.5);
        if (craftTicks == 0) {
            resident.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(output(current)));
        }
        if (++craftTicks % 10 == 0) {
            swing();
        }
        if (craftTicks < ToolTiers.ticks(current.ticks(), workSpeed())) {
            return;
        }
        if (hasInputs(current)) {
            current.inputs().forEach(input -> resident.takeCarried(matcher(input.item()), input.count()));
            resident.carry(new ItemStack(output(current), current.output().count()));
            int index = crafts().indexOf(current);
            stock.merge(index, current.output().count(), Integer::sum);
        }
        resident.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        current = null;
        next(level);
    }

    // ---- data ----------------------------------------------------------------------------------

    private List<Craft> crafts() {
        Job job = resident.brain().job();
        return job == null ? List.of() : Professions.get(job.profession()).map(Profession::crafts).orElse(List.of());
    }

    /** The workstation of the workplace that belongs to the profession. */
    private BlockPos station(ServerLevel level) {
        Job job = resident.brain().job();
        List<Identifier> blocks = job == null ? List.of()
                : Professions.get(job.profession()).map(Profession::workstations).orElse(List.of());
        for (BlockPos pos : workplace().workstations()) {
            if (level.isLoaded(pos) && blocks.contains(BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()))) {
                return pos;
            }
        }
        return null;
    }

    private static Item output(Craft craft) {
        return ItemSpec.item(craft.output().item());
    }

    private static Predicate<ItemStack> matcher(String spec) {
        return ItemSpec.matcher(spec);
    }
}
