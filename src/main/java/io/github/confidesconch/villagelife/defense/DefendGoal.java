package io.github.confidesconch.villagelife.defense;

import java.util.Comparator;
import java.util.EnumSet;

import io.github.confidesconch.villagelife.profession.work.ToolTiers;
import io.github.confidesconch.villagelife.profession.work.VillageGates;
import io.github.confidesconch.villagelife.resident.Job;
import io.github.confidesconch.villagelife.resident.ResidentEntity;
import io.github.confidesconch.villagelife.resident.ai.ResidentHooks;
import io.github.confidesconch.villagelife.village.Village;
import io.github.confidesconch.villagelife.village.VillageArea;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.AABB;

/**
 * Defenders fight monsters in and around the village (M20a, owner's choice "2b": close and ranged combat).
 * Guards and the captain fight with the best sword they carry; archers shoot arrows from their own
 * supply and fight close up when they have none. Runs at any time of day and goes after enemies anywhere
 * in the village, also out of sight (M20b); a sleeping defender wakes up for close ones or when the alarm
 * rings. Knocked-out defenders do not fight.
 */
public class DefendGoal extends Goal {
    /** A sleeping defender only wakes up for enemies this close – unless the alarm is on. */
    private static final double SLEEPING_RANGE = 12.0;
    /** Enemies this far outside the village border count too. */
    private static final int BORDER_MARGIN = 0; // M20b2 (owner's choice "4a"): defenders stay inside the village
    private static final int VERTICAL_REACH = 24;
    /** Close to the enemy but no path (fence, slab …): walk straight at it. */
    private static final double DIRECT_WALK_SQR = 8.0 * 8.0;
    private static final double MELEE_REACH_SQR = 2.5 * 2.5;
    private static final double SHOOT_MAX = 15.0;
    private static final int MELEE_COOLDOWN = 15;
    private static final int SHOOT_COOLDOWN = 30;
    private static final int SEARCH_INTERVAL = 10;
    /** Escorts on the road look for enemies this close to them (M20c). */
    private static final double ESCORT_RANGE = 16.0;
    /** No way to an enemy for this long: give it up … */
    private static final int GIVE_UP_TICKS = 100;
    /** … and leave it alone for this long (e.g. a zombie in a cave under the village). */
    private static final int IGNORE_TICKS = 600;

    private final ResidentEntity resident;
    private final double speedModifier;
    private LivingEntity target;
    private int cooldown;
    private int searchIn;
    private int repath;
    private int noPathTicks;
    private final java.util.Map<java.util.UUID, Long> ignored = new java.util.HashMap<>();

    public DefendGoal(ResidentEntity resident, double speedModifier) {
        this.resident = resident;
        this.speedModifier = speedModifier;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!ResidentHooks.isDefender(resident) || resident.isKnockedOut() || --searchIn > 0) {
            return false;
        }
        searchIn = SEARCH_INTERVAL;
        // owner: defenders go after every enemy in the village, also out of sight (sleeping: only close ones)
        boolean sleepy = resident.isSleeping() && !alarm();
        target = findTarget(sleepy);
        return target != null;
    }

    @Override
    public boolean canContinueToUse() {
        boolean escorting = ResidentHooks.isEscorting(resident);
        return target != null && target.isAlive() && ResidentHooks.isDefender(resident) && !resident.isKnockedOut()
                && (escorting ? resident.distanceTo(target) < ESCORT_RANGE * 2 : inVillage(target)) && !isIgnored(target);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        if (resident.isSleeping()) {
            resident.stopSleeping();
        }
        cooldown = 0;
        repath = 0;
        noPathTicks = 0;
        if (resident.level() instanceof ServerLevel level) { // you can see who goes into battle
            level.sendParticles(ParticleTypes.ANGRY_VILLAGER, resident.getX(), resident.getY() + resident.getBbHeight() + 0.4,
                    resident.getZ(), 3, 0.3, 0.2, 0.3, 0.0);
        }
    }

    @Override
    public void stop() {
        target = null;
        resident.getNavigation().stop();
        resident.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
    }

    @Override
    public void tick() {
        if (!(resident.level() instanceof ServerLevel level) || target == null) {
            return;
        }
        resident.getLookControl().setLookAt(target, 30.0F, 30.0F);
        cooldown--;
        if (isArcher() && hasArrows() && resident.distanceToSqr(target) > MELEE_REACH_SQR) { // too close: fight close up
            shootTick(level);
        } else {
            meleeTick(level);
        }
    }

    // ---- close combat ------------------------------------------------------------------------

    private void meleeTick(ServerLevel level) {
        ItemStack sword = bestSword();
        resident.setItemSlot(EquipmentSlot.MAINHAND, sword.isEmpty() ? new ItemStack(Items.WOODEN_SWORD) : sword.copy());
        if (resident.distanceToSqr(target) > MELEE_REACH_SQR) {
            moveToTarget();
            return;
        }
        resident.getNavigation().stop();
        if (cooldown > 0) {
            return;
        }
        cooldown = MELEE_COOLDOWN;
        resident.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
        String path = sword.isEmpty() ? null : BuiltInRegistries.ITEM.getKey(sword.getItem()).getPath();
        target.hurtServer(level, resident.damageSources().mobAttack(resident), DefenseRules.meleeDamage(path, captainNear()));
        if (!sword.isEmpty()) {
            sword.hurtAndBreak(1, level, resident, broken -> { });
            resident.inventory().setChanged();
        }
    }

    /** The strongest sword the defender carries (worn ones are kept for the smith), or empty. */
    private ItemStack bestSword() {
        ItemStack best = ItemStack.EMPTY;
        double bestSpeed = 0;
        for (int slot = 0; slot < resident.inventory().getContainerSize(); slot++) {
            ItemStack stack = resident.inventory().getItem(slot);
            if (stack.is(ItemTags.SWORDS) && !ToolTiers.isWorn(stack.getDamageValue(), stack.getMaxDamage())) {
                double speed = ToolTiers.speed(BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath());
                if (speed > bestSpeed) {
                    best = stack;
                    bestSpeed = speed;
                }
            }
        }
        return best;
    }

    // ---- ranged combat -----------------------------------------------------------------------

    private void shootTick(ServerLevel level) {
        resident.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        double distance = resident.distanceTo(target);
        if (distance > SHOOT_MAX || !resident.getSensing().hasLineOfSight(target)) {
            moveToTarget();
            return;
        }
        resident.getNavigation().stop();
        if (cooldown > 0) {
            return;
        }
        cooldown = SHOOT_COOLDOWN;
        ItemStack arrowStack = resident.takeCarried(stack -> stack.getItem() instanceof ArrowItem, 1);
        if (arrowStack.isEmpty()) {
            return;
        }
        ItemStack bow = new ItemStack(Items.BOW);
        AbstractArrow arrow = ProjectileUtil.getMobArrow(resident, arrowStack, DefenseRules.arrowPower(captainNear()), bow);
        arrow.pickup = AbstractArrow.Pickup.DISALLOWED; // no arrow litter in the village
        double dx = target.getX() - resident.getX();
        double dy = target.getY(1.0 / 3.0) - arrow.getY();
        double dz = target.getZ() - resident.getZ();
        double flat = Math.sqrt(dx * dx + dz * dz);
        Projectile.spawnProjectileUsingShoot(arrow, level, arrowStack, dx, dy + flat * 0.2, dz, 1.6F, 4.0F);
        resident.playSound(SoundEvents.ARROW_SHOOT, 1.0F, 1.0F / (resident.getRandom().nextFloat() * 0.4F + 0.8F));
    }

    private boolean hasArrows() {
        return resident.carriedCount(stack -> stack.getItem() instanceof ArrowItem) > 0;
    }

    // ---- helpers -------------------------------------------------------------------------------

    private void moveToTarget() {
        if (--repath <= 0) {
            repath = 10;
            boolean found = resident.getNavigation().moveTo(target, speedModifier);
            if (!found && resident.distanceToSqr(target) < DIRECT_WALK_SQR) {
                resident.getMoveControl().setWantedPosition(target.getX(), target.getY(), target.getZ(), speedModifier);
            }
            Village village = resident.brain().village();
            if (found && village != null && !ResidentHooks.isEscorting(resident) && resident.level() instanceof ServerLevel level
                    && VillageGates.leadsThroughGate(level, village, resident.getNavigation().getPath())) {
                // M20b2 (owner): defenders stay inside the walls – an enemy only reachable through the gate is left
                // alone (archers may still shoot it from inside)
                if (!isArcher() || !resident.getSensing().hasLineOfSight(target)) {
                    resident.getNavigation().stop();
                    ignored.put(target.getUUID(), resident.level().getGameTime() + IGNORE_TICKS);
                    target = null;
                    return;
                }
                resident.getNavigation().stop();
            }
            noPathTicks = found ? 0 : noPathTicks + 10;
            if (noPathTicks >= GIVE_UP_TICKS) { // cannot get there: stop staring at it, do something useful
                ignored.put(target.getUUID(), resident.level().getGameTime() + IGNORE_TICKS);
                target = null;
            }
        }
    }

    private boolean isIgnored(LivingEntity enemy) {
        Long until = ignored.get(enemy.getUUID());
        if (until == null) {
            return false;
        }
        if (until <= resident.level().getGameTime()) {
            ignored.remove(enemy.getUUID());
            return false;
        }
        return true;
    }

    /**
     * Only enemies a defender could sensibly reach: under the open sky, inside a building of the village,
     * or in sight. Monsters in caves under the village are left alone.
     */
    private boolean isReachable(Monster enemy) {
        BlockPos pos = enemy.blockPosition();
        if (resident.level().canSeeSky(pos) || resident.getSensing().hasLineOfSight(enemy)) {
            return true;
        }
        Village village = resident.brain().village();
        return village != null && village.buildings().stream().anyMatch(b -> b.isValid()
                && pos.getX() >= b.min().getX() && pos.getX() <= b.max().getX()
                && pos.getY() >= b.min().getY() && pos.getY() <= b.max().getY()
                && pos.getZ() >= b.min().getZ() && pos.getZ() <= b.max().getZ());
    }

    private boolean alarm() {
        return resident.brain().village() != null && ResidentHooks.isAlarm(resident.brain().village().id());
    }

    private boolean isArcher() {
        Job job = resident.brain().job();
        return job != null && job.profession().equals(DefenseRules.ARCHER);
    }

    /** True if a captain (not knocked out) is near this defender (the captain himself gets no bonus). */
    private boolean captainNear() {
        return !resident.level().getEntitiesOfClass(ResidentEntity.class,
                resident.getBoundingBox().inflate(DefenseRules.CAPTAIN_RANGE),
                other -> other != resident && !other.isKnockedOut() && other.brain().job() != null
                        && other.brain().job().profession().equals(DefenseRules.CAPTAIN)).isEmpty();
    }

    /**
     * The nearest enemy (monster or bandit) anywhere inside the village; neutral ones (endermen, piglins)
     * and creepers (they would blow up the guard; they cannot damage the village anyway) are left alone. A sleeping defender only looks close by.
     */
    private LivingEntity findTarget(boolean onlyClose) {
        if (ResidentHooks.isEscorting(resident)) { // M20c: on the road, guarding the convoy – enemies close by
            return resident.level().getEntitiesOfClass(Monster.class, resident.getBoundingBox().inflate(ESCORT_RANGE),
                            monster -> monster.isAlive() && !(monster instanceof NeutralMob) && !(monster instanceof Creeper))
                    .stream().min(Comparator.comparingDouble(resident::distanceToSqr)).orElse(null);
        }
        VillageArea area = resident.brain().area();
        if (area == null) {
            return null;
        }
        AABB box;
        if (onlyClose) {
            box = resident.getBoundingBox().inflate(SLEEPING_RANGE);
        } else {
            int r = area.radius() + BORDER_MARGIN;
            box = new AABB(area.centerX() - r, resident.getY() - VERTICAL_REACH, area.centerZ() - r,
                    area.centerX() + r + 1, resident.getY() + VERTICAL_REACH, area.centerZ() + r + 1);
        }
        return resident.level().getEntitiesOfClass(Monster.class, box,
                        monster -> monster.isAlive() && !(monster instanceof NeutralMob) && !(monster instanceof Creeper)
                                && inVillage(monster)
                                && !isIgnored(monster) && isReachable(monster))
                .stream().min(Comparator.comparingDouble(resident::distanceToSqr)).orElse(null);
    }

    private boolean inVillage(LivingEntity entity) {
        VillageArea area = resident.brain().area();
        if (area == null) {
            return false;
        }
        return new VillageArea(area.centerX(), area.centerZ(), area.radius() + BORDER_MARGIN)
                .contains(entity.getBlockX(), entity.getBlockZ());
    }
}
