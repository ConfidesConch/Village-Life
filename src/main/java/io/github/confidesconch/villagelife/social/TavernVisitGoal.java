package io.github.confidesconch.villagelife.social;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import io.github.confidesconch.villagelife.building.Building;
import io.github.confidesconch.villagelife.core.registry.ModEntities;
import io.github.confidesconch.villagelife.inventory.StorageAccess;
import io.github.confidesconch.villagelife.profession.JobAllocator.Workplace;
import io.github.confidesconch.villagelife.profession.JobService;
import io.github.confidesconch.villagelife.resident.ResidentEntity;
import io.github.confidesconch.villagelife.resident.ai.EatGoal;
import io.github.confidesconch.villagelife.resident.needs.NeedsRules;
import io.github.confidesconch.villagelife.resident.schedule.Activity;
import io.github.confidesconch.villagelife.resident.schedule.ScheduleClock;
import io.github.confidesconch.villagelife.village.Village;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * An evening in the tavern (M17b, owner's choice "2a 3a"; M21e "1": like a bar): after work, while the
 * village has a tavern with an innkeeper, residents spend their free time there. Every guest takes a place
 * of its own ({@link TavernPlaces}) – a chair or bench (stairs, slabs) where it sits down, otherwise a free
 * spot on the floor; when all are taken the tavern is full and the others stay out. Guests sip a drink now
 * and then (only for show, nothing is used up). Once per evening each guest eats from the tavern's food (it
 * counts as a meal) and is "sociable" until the next morning (+5 mood). Without a tavern nothing changes.
 */
public class TavernVisitGoal extends Goal {
    public static final String INNKEEPER = "villagelife:innkeeper";

    /** Evenings start at 17:00. */
    private static final int EVENING_FROM_MINUTE = 17 * 60;
    private static final int CHECK_INTERVAL_TICKS = 200;
    /** Standing guests look for a free seat this often. */
    private static final int SEAT_CHECK_TICKS = 400;
    /** Giving up a place that cannot be reached. */
    private static final int GIVE_UP_TICKS = 600;
    private static final double CLOSE_ENOUGH = 1.2;
    /** About every 15 s a sip, lasting 3 s. */
    private static final int SIP_CHANCE = 300;
    private static final int SIP_TICKS = 60;
    private static final double LOOK_AROUND = 4.0;

    private final ResidentEntity resident;
    private final double speedModifier;
    private BlockPos stand;
    private Building tavern;
    private TavernPlaces.Place place;
    private SeatEntity seat;
    private boolean arrived;
    private int ticks;
    private int sipTicks;
    private long nextCheckGameTime;
    private long servedUntil;

    public TavernVisitGoal(ResidentEntity resident, double speedModifier) {
        this.resident = resident;
        this.speedModifier = speedModifier;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!isEvening() || !(resident.level() instanceof ServerLevel level) || level.getGameTime() < nextCheckGameTime) {
            return false;
        }
        nextCheckGameTime = level.getGameTime() + CHECK_INTERVAL_TICKS;
        Village village = resident.brain().village();
        tavern = village == null ? null : tavern(level, village).orElse(null);
        stand = village == null ? null : brewingStand(level, village).orElse(null);
        if (tavern == null || stand == null) {
            return false;
        }
        place = TavernPlaces.claim(level, tavern, resident.getUUID(), false, resident.getRandom());
        return place != null; // null: the tavern is full tonight
    }

    @Override
    public boolean canContinueToUse() {
        return isEvening() && place != null && stand != null && resident.level().isLoaded(stand)
                && resident.level().getBlockState(stand).is(Blocks.BREWING_STAND)
                && (seat == null || (!seat.isRemoved() && resident.getVehicle() == seat));
    }

    private boolean isEvening() {
        return resident.brain().activity() == Activity.LEISURE
                && ScheduleClock.minuteOfDay(resident.level().getOverworldClockTime()) >= EVENING_FROM_MINUTE;
    }

    @Override
    public void start() {
        ticks = 0;
        sipTicks = 0;
        arrived = false;
    }

    @Override
    public void stop() {
        standUp();
        if (place != null) {
            TavernPlaces.release(place, resident.getUUID());
        }
        if (sipTicks > 0) {
            resident.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }
        place = null;
        stand = null;
        resident.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (!(resident.level() instanceof ServerLevel level) || place == null) {
            return;
        }
        ticks++;
        long now = level.getGameTime();
        TavernPlaces.hold(place, resident.getUUID(), now);
        if (!arrived) {
            walk(level);
            return;
        }
        if (seat != null) {
            seat.keepUntil(now + 40);
            resident.setYRot(place.yaw());
            resident.setYBodyRot(place.yaw());
        } else if (ticks % SEAT_CHECK_TICKS == 0) {
            tryToSit(level);
        }
        lookAround();
        sip(level);
        if (now >= servedUntil) {
            serve(level);
        }
    }

    /** Walks to the place; sits down on arrival if it is a seat. */
    private void walk(ServerLevel level) {
        BlockPos target = place.target();
        if (target.closerToCenterThan(resident.position(), CLOSE_ENOUGH)
                || place.seat() && target.closerToCenterThan(resident.position(), CLOSE_ENOUGH + 0.8)
                && resident.getNavigation().isDone()) {
            resident.getNavigation().stop();
            arrived = true;
            if (place.seat()) {
                sitDown(level);
            }
            return;
        }
        if (ticks > GIVE_UP_TICKS) { // cannot get there: leave the place to others, try again later
            TavernPlaces.release(place, resident.getUUID());
            place = null;
            return;
        }
        if (ticks % 20 == 1) {
            resident.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, speedModifier);
        }
    }

    private void sitDown(ServerLevel level) {
        BlockPos pos = place.pos();
        SeatEntity created = new SeatEntity(ModEntities.SEAT.get(), level);
        created.setPos(pos.getX() + 0.5, pos.getY() + 0.5 - SeatEntity.RIDER_DROP, pos.getZ() + 0.5); // seat surface minus the thigh
        created.setYRot(place.yaw());
        created.keepUntil(level.getGameTime() + 40);
        if (level.addFreshEntity(created) && resident.startRiding(created, true, false)) {
            seat = created;
        } else {
            created.discard(); // could not sit: stays standing at the seat
        }
    }

    private void standUp() {
        if (seat != null) {
            if (resident.getVehicle() == seat) {
                resident.stopRiding();
            }
            seat.discard();
            seat = null;
        }
    }

    /** A standing guest takes a seat that became free. */
    private void tryToSit(ServerLevel level) {
        TavernPlaces.Place free = TavernPlaces.claim(level, tavern, resident.getUUID(), true, resident.getRandom());
        if (free != null && !free.pos().equals(place.pos())) {
            TavernPlaces.release(place, resident.getUUID());
            place = free;
            arrived = false;
            ticks = 0;
        }
    }

    /** Looks at the nearest other guest, otherwise towards the counter. */
    private void lookAround() {
        if (ticks % 40 != 0) {
            return;
        }
        List<ResidentEntity> near = resident.level().getEntitiesOfClass(ResidentEntity.class,
                resident.getBoundingBox().inflate(LOOK_AROUND), other -> other != resident);
        if (!near.isEmpty()) {
            ResidentEntity other = near.get(resident.getRandom().nextInt(near.size()));
            resident.getLookControl().setLookAt(other, 30.0F, 30.0F);
        } else {
            resident.getLookControl().setLookAt(stand.getX() + 0.5, stand.getY() + 0.5, stand.getZ() + 0.5);
        }
    }

    /** Now and then a sip from a drink – only for show (M21e, owner's choice "1"). */
    private void sip(ServerLevel level) {
        if (sipTicks > 0) {
            if (--sipTicks == 0) {
                resident.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            } else if (sipTicks == SIP_TICKS / 2) {
                resident.playSound(SoundEvents.GENERIC_DRINK.value(), 0.4F, 1.0F);
            }
            return;
        }
        if (resident.getMainHandItem().isEmpty() && resident.getRandom().nextInt(SIP_CHANCE) == 0) {
            resident.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.HONEY_BOTTLE)); // looks like a mug of mead
            sipTicks = SIP_TICKS;
        }
    }

    /** Once per evening: eat something from the tavern if hungry at all, and become sociable. */
    private void serve(ServerLevel level) {
        long morning = RelationshipRules.nextMorning(level.getGameTime(), level.getOverworldClockTime());
        servedUntil = morning;
        SocialService.markSociable(resident.getUUID(), morning);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, resident.getX(), resident.getY() + resident.getBbHeight() + 0.4,
                resident.getZ(), 4, 0.3, 0.2, 0.3, 0.0);
        if (resident.needs().food() >= NeedsRules.FULL) {
            return;
        }
        for (BlockPos pos : tavern.containers()) {
            Container container = StorageAccess.containerAt(level, pos);
            if (container == null) {
                continue;
            }
            for (ItemStack eaten : StorageAccess.take(container, EatGoal::isFood, 1)) {
                FoodProperties food = eaten.get(DataComponents.FOOD);
                if (food != null) {
                    resident.needs().eat(food.nutrition());
                    resident.playSound(SoundEvents.GENERIC_EAT.value(), 0.5F, 1.0F);
                    return;
                }
            }
        }
    }

    // ---- finding the tavern --------------------------------------------------------------------

    /** The village's tavern: a workplace of the innkeeper profession whose innkeeper has the job. */
    static Optional<Building> tavern(ServerLevel level, Village village) {
        for (Workplace workplace : JobService.workplaces(level, village)) {
            if (!workplace.profession().equals(INNKEEPER)) {
                continue;
            }
            boolean staffed = village.residents().stream().anyMatch(r -> r.job()
                    .map(job -> job.workplace().equals(workplace.buildingId()) && job.profession().equals(INNKEEPER))
                    .orElse(false));
            if (staffed) {
                return village.buildings().stream().filter(b -> b.id().equals(workplace.buildingId())).findFirst();
            }
        }
        return Optional.empty();
    }

    private static Optional<BlockPos> brewingStand(ServerLevel level, Village village) {
        return tavern(level, village).flatMap(building -> building.workstations().stream()
                .filter(pos -> level.isLoaded(pos) && level.getBlockState(pos).is(Blocks.BREWING_STAND))
                .findFirst());
    }
}
