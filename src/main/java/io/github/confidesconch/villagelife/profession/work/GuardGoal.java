package io.github.confidesconch.villagelife.profession.work;

import java.util.List;

import io.github.confidesconch.villagelife.VillageLife;
import io.github.confidesconch.villagelife.building.Building;
import io.github.confidesconch.villagelife.resident.ResidentEntity;
import io.github.confidesconch.villagelife.resident.ai.ResidentHooks;
import io.github.confidesconch.villagelife.village.Village;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Work time of the defenders (M20a): guard and captain (sword), archer (bow). First they equip
 * themselves from the village storage – a better weapon, armor for empty slots, arrows for the archer –
 * then they patrol inside the village: gates often, buildings, the center and the inside of the border. Fighting itself is
 * {@code defense.DefendGoal}, which also runs outside working hours.
 */
public class GuardGoal extends WorkGoal {
    public static final TagKey<Item> ARCHER_WEAPONS =
            TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(VillageLife.MOD_ID, "archer_weapons"));

    private static final int ARROWS_WANTED = 32;
    private static final int ARROWS_LOW = 8;
    private static final int LINGER_TICKS = 100;
    private static final double ARRIVED = 3.0;
    /** Give up a patrol point that cannot be reached within 30 s. */
    private static final int PATROL_TIMEOUT = 600;
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private final boolean archer;
    private BlockPos patrolPoint;
    private int linger;
    private int walking;

    private GuardGoal(ResidentEntity resident, double speedModifier, String profession, boolean archer) {
        super(resident, speedModifier, profession, archer ? ARCHER_WEAPONS : ItemTags.SWORDS,
                archer ? Items.BOW : Items.WOODEN_SWORD);
        this.archer = archer;
    }

    /** Guard or captain: sword, close combat. */
    public static GuardGoal melee(ResidentEntity resident, double speedModifier, String profession) {
        return new GuardGoal(resident, speedModifier, profession, false);
    }

    public static GuardGoal archer(ResidentEntity resident, double speedModifier, String profession) {
        return new GuardGoal(resident, speedModifier, profession, true);
    }

    @Override
    public boolean canUse() {
        return isWorkTime() && !resident.isKnockedOut() && !ResidentHooks.isEscorting(resident); // M20c
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    protected int keepMaterial(ItemStack stack) {
        return archer && stack.getItem() instanceof ArrowItem ? 64 : 0;
    }

    @Override
    public void tick() {
        if (!(resident.level() instanceof ServerLevel level)) {
            return;
        }
        if (mayFetchTool(level) && fetchToolStep(level) == FetchStatus.RUNNING) {
            return;
        }
        if (hasFreeArmorSlot() && fetchStep(level, "armor", this::fitsFreeSlot, 1) == FetchStatus.RUNNING) {
            return;
        }
        wearArmor();
        if (archer && resident.carriedCount(s -> s.getItem() instanceof ArrowItem) < ARROWS_LOW
                && fetchStep(level, "arrows", s -> s.getItem() instanceof ArrowItem, ARROWS_WANTED) == FetchStatus.RUNNING) {
            return;
        }
        resident.brain().setWorkIssue(archer && resident.carriedCount(s -> s.getItem() instanceof ArrowItem) == 0
                ? "archer.no_arrows" : null);
        patrol();
    }

    // ---- armor ---------------------------------------------------------------------------------

    private boolean hasFreeArmorSlot() {
        for (EquipmentSlot slot : ARMOR) {
            if (resident.getItemBySlot(slot).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** Armor for a slot that is still empty. */
    private boolean fitsFreeSlot(ItemStack stack) {
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        return equippable != null && equippable.slot().isArmor() && resident.getItemBySlot(equippable.slot()).isEmpty();
    }

    /** Puts fetched armor on (from the defender's own inventory). */
    private void wearArmor() {
        for (int i = 0; i < resident.inventory().getContainerSize(); i++) {
            ItemStack stack = resident.inventory().getItem(i);
            if (!stack.isEmpty() && fitsFreeSlot(stack)) {
                EquipmentSlot slot = stack.get(DataComponents.EQUIPPABLE).slot();
                resident.setItemSlot(slot, stack.split(1));
                resident.inventory().setChanged();
            }
        }
    }

    // ---- patrol --------------------------------------------------------------------------------

    private void patrol() {
        if (patrolPoint == null) {
            patrolPoint = nextPatrolPoint();
            linger = 0;
            walking = 0;
            resetPath();
            if (patrolPoint == null) {
                return;
            }
        }
        if (!patrolPoint.closerToCenterThan(resident.position(), ARRIVED)) {
            showTool();
            moveTowards(patrolPoint, 2);
            if (++walking > PATROL_TIMEOUT) { // cannot get there: on to the next point instead of standing around
                patrolPoint = null;
            }
            return;
        }
        resident.getNavigation().stop();
        if (++linger >= LINGER_TICKS) {
            patrolPoint = null;
        }
    }

    /**
     * Each guard its own round (M20b2, owner's choice "4a"): often a gate of the village, otherwise a
     * random building, the village center or a point on the inside of the border – always inside.
     */
    private BlockPos nextPatrolPoint() {
        Village village = resident.brain().village();
        if (village == null || !(resident.level() instanceof ServerLevel level)) {
            return null;
        }
        int roll = resident.getRandom().nextInt(100);
        List<BlockPos> gates = VillageGates.of(level, village);
        if (!gates.isEmpty() && roll < 40) {
            // stand watch just inside the gate, never in it or outside
            return VillageGates.watchPoint(gates.get(resident.getRandom().nextInt(gates.size())), village.center());
        }
        List<Building> buildings = villageBuildings().stream().filter(Building::isValid).toList();
        if (!buildings.isEmpty() && roll < 75) {
            return buildings.get(resident.getRandom().nextInt(buildings.size())).center();
        }
        if (roll < 85) {
            return village.center();
        }
        double angle = resident.getRandom().nextDouble() * Math.PI * 2; // along the inside of the border
        int distance = Math.max(4, village.radius() - 10);
        int x = village.center().getX() + (int) Math.round(Math.cos(angle) * distance);
        int z = village.center().getZ() + (int) Math.round(Math.sin(angle) * distance);
        BlockPos column = new BlockPos(x, village.center().getY(), z);
        return level.isLoaded(column)
                ? new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z) : village.center();
    }

    @Override
    public void stop() {
        super.stop();
        patrolPoint = null;
    }
}
