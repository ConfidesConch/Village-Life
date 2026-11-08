package io.github.confidesconch.villagelife.profession.work;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import io.github.confidesconch.villagelife.building.Building;
import io.github.confidesconch.villagelife.inventory.StorageAccess;
import io.github.confidesconch.villagelife.profession.Profession;
import io.github.confidesconch.villagelife.resident.Job;
import io.github.confidesconch.villagelife.resident.ResidentEntity;
import io.github.confidesconch.villagelife.resident.schedule.Activity;
import io.github.confidesconch.villagelife.village.Village;
import io.github.confidesconch.villagelife.village.VillageZone;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;

/**
 * Shared parts of all work goals (M8/M9a): runs only during {@link Activity#WORK} for residents with
 * the given profession and a valid workplace, works with the best fitting tool from its inventory
 * (or the wooden basic tool, which never wears out; M9b), walks with regular re-pathing, delivers the
 * inventory to containers (workplace first, then storage buildings) and fetches material and better
 * tools from them. See {@link StorageAccess} and {@link ToolTiers}.
 */
abstract class WorkGoal extends Goal {
    protected static final int REPATH_TICKS = 40;
    /** Give up a target (or a container) if it cannot be reached within this time. */
    protected static final int GIVE_UP_TICKS = 400;
    /** Deliver once carrying this many items. */
    protected static final int CARRY_LIMIT = 64;
    private static final double CONTAINER_DISTANCE = 2.5;
    /** After finding nothing to fetch, do not look again for this long. */
    private static final int FETCH_COOLDOWN_TICKS = 600;

    /** Result of one tick of fetching. */
    protected enum FetchStatus { RUNNING, DONE, NOTHING }

    protected final ResidentEntity resident;
    protected final double speedModifier;
    private final String profession;
    private final TagKey<Item> toolTag;
    private final Item basicTool;
    private int repath;

    private BlockPos containerTarget;
    private int containerTicks;
    private final Set<BlockPos> unreachable = new HashSet<>();
    private final Map<String, Long> nextFetchGameTime = new HashMap<>();

    protected WorkGoal(ResidentEntity resident, double speedModifier, String profession,
                       TagKey<Item> toolTag, Item basicTool) {
        this.resident = resident;
        this.speedModifier = speedModifier;
        this.profession = profession;
        this.toolTag = toolTag;
        this.basicTool = basicTool;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public void start() {
        repath = 0;
        showTool();
    }

    @Override
    public void stop() {
        containerTarget = null;
        unreachable.clear();
        resident.getNavigation().stop();
        resident.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    protected boolean isWorkTime() {
        Job job = resident.brain().job();
        return resident.brain().activity() == Activity.WORK && job != null
                && worksAs(job.profession()) && workplace() != null
                && resident.workSpeed() > 0; // never 0 since 08.11.2026 (starving: half speed)
    }

    /** True if this goal does the work of {@code jobProfession} (crafting professions share one goal, M14a). */
    protected boolean worksAs(String jobProfession) {
        return profession.equals(jobProfession);
    }

    protected Building workplace() {
        return resident.brain().workplace();
    }

    /** Shown in the book as {@code villagelife.problem.work.no_work_area} (M13c). */
    protected static final String ISSUE_NO_WORK_AREA = Profession.ISSUE_NO_WORK_AREA;

    /** The zone the owner assigned to the workplace as work area (M13c), or null. */
    protected VillageZone workArea() {
        Village village = resident.brain().village();
        Building workplace = workplace();
        return village == null || workplace == null ? null : village.workAreaOf(workplace.id()).orElse(null);
    }

    /** Registered rooms of the village (their insides are off limits for field work); M21d: no fields/pastures. */
    protected List<Building> villageBuildings() {
        return resident.brain().village() == null ? List.of()
                : resident.brain().village().buildings().stream().filter(b -> !b.type().isArea()).toList();
    }

    protected void resetPath() {
        repath = 0;
    }

    protected void moveTowards(BlockPos pos, int reach) {
        if (--repath <= 0) {
            repath = REPATH_TICKS;
            resident.getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, reach, speedModifier);
        }
    }

    protected void swing() {
        resident.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
    }

    // ---- what the worker keeps -------------------------------------------------------------

    /** How many of this item the worker keeps instead of delivering: its best tool plus {@link #keepMaterial}. */
    private int keepAmount(ItemStack stack, Item bestTool) {
        return (bestTool != null && stack.is(bestTool) ? 1 : 0) + keepMaterial(stack);
    }

    /** The item type of the fastest fitting tool among the items, or null. */
    private Item bestToolIn(List<ItemStack> items) {
        Item best = null;
        double bestSpeed = 0;
        for (ItemStack stack : items) {
            if (stack.is(toolTag) && speedOf(stack) > bestSpeed) {
                best = stack.getItem();
                bestSpeed = speedOf(stack);
            }
        }
        return best;
    }

    /** How many of this item the worker keeps for its work (e.g. seeds for sowing). */
    protected int keepMaterial(ItemStack stack) {
        return 0;
    }

    // ---- tools (M9b) ----------------------------------------------------------------------

    /** Inventory slot of the fastest fitting tool, or -1 (then the wooden basic tool is used). */
    private int bestToolSlot() {
        int best = -1;
        double bestSpeed = 0;
        for (int slot = 0; slot < resident.inventory().getContainerSize(); slot++) {
            ItemStack stack = resident.inventory().getItem(slot);
            if (!stack.isEmpty() && stack.is(toolTag) && speedOf(stack) > bestSpeed) {
                best = slot;
                bestSpeed = speedOf(stack);
            }
        }
        return best;
    }

    private static double speedOf(ItemStack stack) { // worn tools count as useless: they go back to storage (M14d)
        return ToolTiers.isWorn(stack.getDamageValue(), stack.getMaxDamage()) ? 0
                : ToolTiers.speed(BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath());
    }

    /** Speed factor of the tool the worker uses right now (1.0 = wooden basic tool). */
    protected double toolSpeed() {
        int slot = bestToolSlot();
        return slot < 0 ? ToolTiers.BASIC : Math.max(ToolTiers.BASIC, speedOf(resident.inventory().getItem(slot)));
    }

    /** Overall speed: tool (M9b) times needs (M10: hungry/unhappy slower, happy faster). */
    protected double workSpeed() {
        return Math.max(0.1, toolSpeed() * resident.workSpeed());
    }

    /** Shows the tool in use in the main hand (a copy; the real tool stays in the inventory). */
    protected void showTool() {
        int slot = bestToolSlot();
        resident.setItemSlot(EquipmentSlot.MAINHAND,
                slot < 0 ? new ItemStack(basicTool) : resident.inventory().getItem(slot).copy());
    }

    /** One use of the tool: better tools wear out (enchantments count); the basic tool never does. */
    protected void wearTool(ServerLevel level) {
        int slot = bestToolSlot();
        if (slot >= 0) {
            resident.inventory().getItem(slot).hurtAndBreak(1, level, resident, broken -> { });
            resident.inventory().setChanged();
            showTool();
        }
    }

    /** One tick of fetching a better tool from the workplace or storage containers. */
    protected FetchStatus fetchToolStep(ServerLevel level) {
        double current = toolSpeed();
        FetchStatus status = fetchStep(level, "tool", stack -> stack.is(toolTag) && speedOf(stack) > current, 1);
        if (status == FetchStatus.DONE) {
            showTool();
        }
        return status;
    }

    /** True if it is worth looking for a better tool right now (not after a recent failed search). */
    protected boolean mayFetchTool(ServerLevel level) {
        Village village = resident.brain().village();
        boolean allowed = village == null || village.settings().toolsFromStorage(); // owner setting (M11c)
        return allowed && mayFetch(level, "tool");
    }

    /** Carried items split into what the worker keeps and what it delivers. */
    private record Split(List<ItemStack> keep, List<ItemStack> deliver) {}

    private Split split(List<ItemStack> items) {
        Item bestTool = bestToolIn(items);
        Map<Item, Integer> kept = new HashMap<>();
        List<ItemStack> keep = new ArrayList<>();
        List<ItemStack> deliver = new ArrayList<>();
        for (ItemStack stack : items) {
            ItemStack rest = stack.copy();
            int already = kept.getOrDefault(rest.getItem(), 0);
            int amount = Math.min(rest.getCount(), Math.max(0, keepAmount(rest, bestTool) - already));
            kept.put(rest.getItem(), already + amount);
            if (amount > 0) {
                keep.add(rest.split(amount));
            }
            if (!rest.isEmpty()) {
                deliver.add(rest);
            }
        }
        return new Split(keep, deliver);
    }

    /** Number of carried items the worker would keep anyway (its best tool and {@link #keepMaterial}). */
    protected int keptCount() {
        return split(resident.carriedItems()).keep().stream().mapToInt(ItemStack::getCount).sum();
    }

    /** True if the worker carries something that belongs in a container. */
    protected boolean hasSomethingToDeliver() {
        return resident.carriedCount() > keptCount();
    }

    /** True if the hands are full: time to deliver. */
    protected boolean isFull() {
        return resident.carriedCount() - keptCount() >= CARRY_LIMIT;
    }

    // ---- delivering ------------------------------------------------------------------------

    /**
     * One tick of bringing the inventory to containers (workplace first, then storage); true when done.
     * If no container has room, the items are dropped (work goes on); if they could only not be
     * reached, the worker keeps them and tries again later.
     */
    protected boolean deliverStep(ServerLevel level) {
        if (!hasSomethingToDeliver()) {
            containerTarget = null;
            return true;
        }
        if (containerTarget == null) {
            containerTarget = findContainer(level, this::acceptsSomething, deliversToWorkplace());
            containerTicks = 0;
            if (containerTarget == null) {
                if (unreachable.isEmpty()) {
                    dropDeliverables(level); // every container is full: drop, so work can go on
                } else {
                    unreachable.clear(); // could not get there (M13e): keep the items and try again later
                }
                return true;
            }
        }
        if (!arrivedAt(containerTarget)) {
            return false;
        }
        Container container = StorageAccess.containerAt(level, containerTarget);
        containerTarget = null;
        if (container != null) {
            swing();
            storeInto(container);
        }
        return !hasSomethingToDeliver();
    }

    private boolean acceptsSomething(Container container) {
        for (ItemStack stack : split(resident.carriedItems()).deliver()) {
            if (StorageAccess.canAccept(container, stack)) {
                return true;
            }
        }
        return false;
    }

    private void storeInto(Container container) {
        Split split = split(resident.takeAllCarried());
        split.keep().forEach(resident::carry);
        for (ItemStack stack : split.deliver()) {
            resident.carry(StorageAccess.insert(container, stack)); // what did not fit goes to the next container
        }
    }

    private void dropDeliverables(ServerLevel level) {
        Split split = split(resident.takeAllCarried());
        split.keep().forEach(resident::carry);
        split.deliver().forEach(stack -> resident.spawnAtLocation(level, stack));
    }

    // ---- fetching --------------------------------------------------------------------------

    /**
     * One tick of getting up to {@code amount} items matching {@code wanted} from a container.
     * Returns NOTHING (and pauses further attempts for a while) if no container has any.
     */
    protected FetchStatus fetchStep(ServerLevel level, String what, Predicate<ItemStack> wanted, int amount) {
        if (containerTarget == null) {
            if (!mayFetch(level, what)) {
                return FetchStatus.NOTHING;
            }
            containerTarget = findContainer(level, container -> StorageAccess.contains(container, wanted), true);
            containerTicks = 0;
            if (containerTarget == null) {
                nextFetchGameTime.put(what, level.getGameTime() + FETCH_COOLDOWN_TICKS);
                return FetchStatus.NOTHING;
            }
        }
        if (!arrivedAt(containerTarget)) {
            return FetchStatus.RUNNING;
        }
        Container container = StorageAccess.containerAt(level, containerTarget);
        containerTarget = null;
        if (container != null) {
            swing();
            int missing = amount - resident.carriedCount(wanted);
            StorageAccess.take(container, wanted, Math.max(0, missing)).forEach(resident::carry);
        }
        return FetchStatus.DONE;
    }

    /** False while fetching {@code what} is paused after a failed search. */
    protected boolean mayFetch(ServerLevel level, String what) {
        return level.getGameTime() >= nextFetchGameTime.getOrDefault(what, 0L);
    }

    // ---- shared container walking ----------------------------------------------------------

    /** False if the products are for other workers (smith's tools, M13g): then only to storage buildings. */
    protected boolean deliversToWorkplace() {
        return true;
    }

    private BlockPos findContainer(ServerLevel level, Predicate<Container> suitable, boolean includeWorkplace) {
        Building from = includeWorkplace ? workplace() : null;
        for (BlockPos pos : StorageAccess.containers(resident.brain().village(), from, resident.blockPosition())) {
            if (unreachable.contains(pos)) {
                continue;
            }
            Container container = StorageAccess.containerAt(level, pos);
            if (container != null && suitable.test(container)) {
                return pos;
            }
        }
        return null;
    }

    /** Walks to the container; true once close enough. Unreachable containers are skipped. */
    private boolean arrivedAt(BlockPos pos) {
        if (pos.closerToCenterThan(resident.position(), CONTAINER_DISTANCE)) {
            resident.getNavigation().stop();
            return true;
        }
        if (++containerTicks > GIVE_UP_TICKS) {
            unreachable.add(pos);
            containerTarget = null;
            return false;
        }
        moveTowards(pos, 1);
        return false;
    }
}
