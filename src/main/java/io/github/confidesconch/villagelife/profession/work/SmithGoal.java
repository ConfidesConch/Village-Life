package io.github.confidesconch.villagelife.profession.work;

import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import io.github.confidesconch.villagelife.building.Building;
import io.github.confidesconch.villagelife.building.BuildingType;
import io.github.confidesconch.villagelife.inventory.StorageAccess;
import io.github.confidesconch.villagelife.profession.JobAllocator.Workplace;
import io.github.confidesconch.villagelife.profession.JobService;
import io.github.confidesconch.villagelife.profession.Profession;
import io.github.confidesconch.villagelife.profession.Professions;
import io.github.confidesconch.villagelife.profession.work.OvenTender.Need;
import io.github.confidesconch.villagelife.profession.work.SmithRules.Kind;
import io.github.confidesconch.villagelife.resident.ResidentEntity;
import io.github.confidesconch.villagelife.village.Village;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlastFurnaceBlockEntity;
import net.neoforged.neoforge.common.Tags;

/**
 * Work of the smith (M13g, owner's choice "1a 2a 3a"): smelts raw iron and copper from storage in the
 * workshop's blast furnace ({@link OvenTender}, fuel from storage) and forges better tools for the
 * village's professions at the smithing table – iron, or copper if there is no iron – keeping
 * {@link SmithRules#RESERVE} of each needed tool in storage, where the workers fetch them (M9b). Tools
 * and spare ingots go to the storage buildings, not to the workshop. Before forging he repairs worn
 * iron and copper tools that the workers brought back to storage ({@link ToolRepair}, M14d).
 */
public class SmithGoal extends WorkGoal {
    public static final String PROFESSION = "villagelife:smith";
    /** Shown in the book as {@code villagelife.problem.work.smith.no_fuel}. */
    public static final String ISSUE_NO_FUEL = "smith.no_fuel";

    private static final String[] MATERIALS = {"iron", "copper"};
    private static final int RAW_TO_FETCH = 16;
    private static final int FUEL_TO_FETCH = 8;
    private static final int INGOTS_TO_KEEP = 16;
    private static final int WOOD_TO_KEEP = 8;
    private static final int FORGE_TICKS = 60;
    private static final int STOCK_REFRESH_TICKS = 200;
    private static final double STATION_DISTANCE = 2.5;

    private enum Phase { TO_FURNACE, TO_TABLE, REPAIR, FETCH, DELIVER }

    /** A tool to forge: kind, item and the ingot it is made of. */
    private record Order(Kind kind, Item tool, Item ingot) {}

    private final OvenTender furnace;
    private final Map<Kind, Integer> stock = new EnumMap<>(Kind.class);
    private final Set<Kind> needed = new LinkedHashSet<>();
    private long stockCountedGameTime = -STOCK_REFRESH_TICKS;
    private Phase phase;
    private int phaseTicks;
    /** Ticks spent hammering at the smithing table. */
    private int forgeTicks;
    private Order order;
    private String fetchKey;
    private Predicate<ItemStack> fetchWanted;
    private int fetchAmount;

    public SmithGoal(ResidentEntity resident, double speedModifier) {
        super(resident, speedModifier, PROFESSION, ItemTags.SHOVELS, Items.IRON_INGOT); // an ingot in the hand
        this.furnace = new OvenTender(resident, BlastFurnaceBlockEntity.class, SmithGoal::isRaw);
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
        order = null;
        super.stop();
    }

    @Override
    protected boolean deliversToWorkplace() {
        return false; // the tools are for the other workers: they look into the storage buildings
    }

    @Override
    protected int keepMaterial(ItemStack stack) {
        if (isRaw(stack)) {
            return RAW_TO_FETCH * 4;
        }
        if (stack.is(Items.IRON_INGOT) || stack.is(Items.COPPER_INGOT)) {
            return INGOTS_TO_KEEP;
        }
        return isWood(stack) || OvenTender.isFuel(stack) ? WOOD_TO_KEEP + FUEL_TO_FETCH : 0;
    }

    // ---- choosing ------------------------------------------------------------------------------

    /** Deliver, then the blast furnace, then forging; false if there is nothing to do. */
    private boolean chooseWork(ServerLevel level) {
        if (isFull()) {
            return setPhase(Phase.DELIVER);
        }
        if (furnace.find(level, workplace()) != null) {
            Need need = furnace.need(level, mayFetch(level, "raw"), mayFetch(level, "fuel"));
            if (need == Need.NO_FUEL) {
                resident.brain().setWorkIssue(ISSUE_NO_FUEL);
            } else if (ISSUE_NO_FUEL.equals(resident.brain().workIssue())) {
                resident.brain().setWorkIssue(null);
            }
            switch (need) {
                case FETCH_RAW -> {
                    return fetch("raw", SmithGoal::isRaw, RAW_TO_FETCH);
                }
                case FETCH_FUEL -> {
                    return fetch("fuel", OvenTender::isFuel, FUEL_TO_FETCH);
                }
                case TEND -> {
                    return setPhase(Phase.TO_FURNACE);
                }
                default -> { }
            }
        }
        if (chooseRepair(level) || chooseOrder(level)) {
            return true;
        }
        return hasSomethingToDeliver() && setPhase(Phase.DELIVER);
    }

    /** M14d: repair a worn tool before forging new ones (cheaper); fetches the tool or the ingots first. */
    private boolean chooseRepair(ServerLevel level) {
        ItemStack worn = ToolRepair.carried(resident);
        if (worn == null) {
            return mayFetch(level, "repair") && fetch("repair", ToolRepair::isRepairable, 1);
        }
        String material = ToolRepair.material(worn);
        Item ingot = ToolRepair.ingot(material);
        if (resident.carriedCount(stack -> stack.is(ingot)) > 0) {
            return setPhase(Phase.REPAIR);
        }
        return mayFetch(level, "ingot:" + material) && fetch("ingot:" + material, stack -> stack.is(ingot), 4);
    }

    /** The next tool the storage lacks that can be made (fetching ingots or wood first if needed). */
    private boolean chooseOrder(ServerLevel level) {
        refreshStock(level);
        for (Kind kind : needed) {
            if (stock.getOrDefault(kind, 0) >= SmithRules.RESERVE) {
                continue;
            }
            for (String material : MATERIALS) {
                Item tool = kind.itemPath(material).map(this::item).orElse(Items.AIR);
                Item ingot = material.equals("iron") ? Items.IRON_INGOT : Items.COPPER_INGOT;
                if (tool == Items.AIR) {
                    continue;
                }
                if (resident.carriedCount(stack -> stack.is(ingot)) < kind.ingots()) {
                    if (mayFetch(level, "ingot:" + material)) {
                        return fetch("ingot:" + material, stack -> stack.is(ingot), kind.ingots() * SmithRules.RESERVE);
                    }
                    continue;
                }
                if (sticksAvailable() < kind.sticks()) {
                    if (mayFetch(level, "wood")) {
                        return fetch("wood", SmithGoal::isWood, WOOD_TO_KEEP);
                    }
                    continue;
                }
                order = new Order(kind, tool, ingot);
                return setPhase(Phase.TO_TABLE);
            }
        }
        return false;
    }

    /** Which kinds of tools the village's professions use, and how many of each lie in storage (every 10 s). */
    private void refreshStock(ServerLevel level) {
        if (level.getGameTime() - stockCountedGameTime < STOCK_REFRESH_TICKS) {
            return;
        }
        stockCountedGameTime = level.getGameTime();
        Village village = resident.brain().village();
        needed.clear();
        stock.clear();
        if (village == null) {
            return;
        }
        for (Workplace workplace : JobService.workplaces(level, village)) {
            Professions.get(workplace.profession()).flatMap(Profession::tool)
                    .flatMap(tool -> SmithRules.kindOf(tool.getPath()))
                    .ifPresent(needed::add);
        }
        for (Building building : village.buildings()) {
            if (building.type() != BuildingType.STORAGE || !building.isValid()) {
                continue;
            }
            for (BlockPos pos : building.containers()) {
                Container container = StorageAccess.containerAt(level, pos);
                for (int slot = 0; container != null && slot < container.getContainerSize(); slot++) {
                    countTool(container.getItem(slot));
                }
            }
        }
    }

    private void countTool(ItemStack stack) {
        for (Kind kind : needed) {
            for (String material : MATERIALS) {
                if (kind.itemPath(material).map(this::item).filter(stack::is).isPresent()) {
                    stock.merge(kind, stack.getCount(), Integer::sum);
                }
            }
        }
    }

    private boolean fetch(String key, Predicate<ItemStack> wanted, int amount) {
        fetchKey = key;
        fetchWanted = wanted;
        fetchAmount = amount;
        return setPhase(Phase.FETCH);
    }

    private boolean setPhase(Phase next) {
        phase = next;
        phaseTicks = 0;
        forgeTicks = 0;
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
            case TO_FURNACE -> {
                if (arrived(furnace.oven())) {
                    swing();
                    furnace.tend(level, SoundEvents.BLASTFURNACE_FIRE_CRACKLE);
                    next(level);
                }
            }
            case REPAIR -> {
                if (arrived(table(level)) && hammer(level)) {
                    ItemStack worn = ToolRepair.carried(resident);
                    if (worn != null) {
                        ToolRepair.repair(resident, worn);
                        level.playSound(null, resident.blockPosition(), SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.5F, 1.0F);
                    }
                    next(level);
                }
            }
            case TO_TABLE -> {
                if (arrived(table(level))) {
                    forge(level);
                }
            }
            case FETCH -> {
                if (fetchStep(level, fetchKey, fetchWanted, fetchAmount) != FetchStatus.RUNNING) {
                    next(level);
                }
            }
            case DELIVER -> {
                if (deliverStep(level)) {
                    stockCountedGameTime = -STOCK_REFRESH_TICKS; // count again: new tools lie in storage
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

    /** Walks to a station; true once there (or gives up). */
    private boolean arrived(BlockPos station) {
        if (station == null || phaseTicks > GIVE_UP_TICKS) {
            phase = null;
            return false;
        }
        if (station.closerToCenterThan(resident.position(), STATION_DISTANCE)) {
            resident.getNavigation().stop();
            resident.getLookControl().setLookAt(station.getX() + 0.5, station.getY() + 0.5, station.getZ() + 0.5);
            return true;
        }
        moveTowards(station, 1);
        return false;
    }

    /** Hammers for a while, then turns ingots and sticks into the tool. */
    private void forge(ServerLevel level) {
        if (order == null) {
            next(level);
            return;
        }
        if (!hammer(level)) {
            return;
        }
        if (resident.carriedCount(stack -> stack.is(order.ingot())) >= order.kind().ingots() && takeSticks(order.kind().sticks())) {
            resident.takeCarried(stack -> stack.is(order.ingot()), order.kind().ingots());
            resident.carry(new ItemStack(order.tool()));
            level.playSound(null, resident.blockPosition(), SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.5F, 1.0F);
            stock.merge(order.kind(), 1, Integer::sum);
        }
        order = null;
        next(level);
    }

    /** One tick of hammering at the table; true once the work is done. */
    private boolean hammer(ServerLevel level) {
        if (++forgeTicks % 10 == 0) {
            swing();
            level.playSound(null, resident.blockPosition(), SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.3F, 1.4F);
        }
        return forgeTicks >= ToolTiers.ticks(FORGE_TICKS, workSpeed());
    }

    // ---- materials -----------------------------------------------------------------------------

    private BlockPos table(ServerLevel level) {
        for (BlockPos pos : workplace().workstations()) {
            if (level.isLoaded(pos) && level.getBlockState(pos).is(Blocks.SMITHING_TABLE)) {
                return pos;
            }
        }
        return null;
    }

    /** Sticks the smith can get from what it carries (sticks, planks, logs). */
    private int sticksAvailable() {
        return resident.carriedCount(stack -> stack.is(Items.STICK))
                + SmithRules.sticksFromPlanks(resident.carriedCount(stack -> stack.is(ItemTags.PLANKS)))
                + SmithRules.sticksFromLogs(resident.carriedCount(stack -> stack.is(ItemTags.LOGS)));
    }

    /** Takes {@code count} sticks, cutting planks or logs into sticks first if needed. */
    private boolean takeSticks(int count) {
        while (resident.carriedCount(stack -> stack.is(Items.STICK)) < count) {
            if (!resident.takeCarried(stack -> stack.is(ItemTags.PLANKS), 1).isEmpty()) {
                resident.carry(new ItemStack(Items.STICK, SmithRules.sticksFromPlanks(1)));
            } else if (!resident.takeCarried(stack -> stack.is(ItemTags.LOGS), 1).isEmpty()) {
                resident.carry(new ItemStack(Items.STICK, SmithRules.sticksFromLogs(1)));
            } else {
                return false;
            }
        }
        if (count > 0) {
            resident.takeCarried(stack -> stack.is(Items.STICK), count);
        }
        return true;
    }

    private Item item(String path) {
        return BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(path));
    }

    /** Raw ores the blast furnace turns into ingots. */
    private static boolean isRaw(ItemStack stack) {
        return stack.is(Tags.Items.RAW_MATERIALS);
    }

    private static boolean isWood(ItemStack stack) {
        return stack.is(Items.STICK) || stack.is(ItemTags.PLANKS) || stack.is(ItemTags.LOGS);
    }
}
