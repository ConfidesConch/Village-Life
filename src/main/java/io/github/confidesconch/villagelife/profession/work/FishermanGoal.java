package io.github.confidesconch.villagelife.profession.work;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.github.confidesconch.villagelife.profession.Professions;
import io.github.confidesconch.villagelife.profession.work.FishingSpots.Spot;
import io.github.confidesconch.villagelife.resident.ResidentEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/**
 * Work of the fisherman (M13a): finds open water (at least {@link FishingSpots#MIN_OPEN_WATER} water
 * blocks around) near its hut, stands on the bank and fishes with the normal fishing loot of Minecraft
 * (fish and some junk, no treasure), then brings the catch to the hut (its barrel) or a storage.
 * Like every worker it uses a real fishing rod from storage if there is one (a bit faster, wears out).
 * Without water nearby it reports the problem in the village book.
 */
public class FishermanGoal extends WorkGoal {
    public static final String PROFESSION = "villagelife:fisherman";
    /** Shown in the book as {@code villagelife.problem.work.fisherman.no_water}. */
    public static final String ISSUE_NO_WATER = "fisherman.no_water";

    private static final int SEARCH_COOLDOWN_TICKS = 600;
    /** Water may be this much higher or lower than the hut's marker. */
    private static final int MAX_HEIGHT_DIFFERENCE = 6;
    private static final double STAND_DISTANCE = 1.5;
    private static final double STAND_DISTANCE_PATH_END = 3.0;
    /** Bring the catch away once carrying this many items. */
    private static final int DELIVER_AT = 16;
    private static final int SPLASH_INTERVAL_TICKS = 40;

    private enum Phase { TOOL, TO_SPOT, FISH, DELIVER }

    private final List<Spot> spots = new ArrayList<>();
    private Phase phase;
    private Spot spot;
    private int phaseTicks;
    private int catchIn;
    private long nextSearchGameTime;

    public FishermanGoal(ResidentEntity resident, double speedModifier) {
        super(resident, speedModifier, PROFESSION, ItemTags.FISHING_ENCHANTABLE, Items.FISHING_ROD);
    }

    @Override
    public boolean canUse() {
        if (!isWorkTime()) {
            return false;
        }
        if (isFull() || deliverables() >= DELIVER_AT) {
            setPhase(Phase.DELIVER);
            return true;
        }
        if (resident.level() instanceof ServerLevel level && mayFetchTool(level)) {
            setPhase(Phase.TOOL); // a real fishing rod from storage is faster than the basic one (M9b)
            return true;
        }
        if (findSpot()) {
            return true;
        }
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
        phase = null;
        spot = null;
        super.stop();
    }

    @Override
    public void tick() {
        if (!(resident.level() instanceof ServerLevel level) || phase == null) {
            return;
        }
        phaseTicks++;
        switch (phase) {
            case TO_SPOT -> walkToSpot(level);
            case FISH -> fish(level);
            case TOOL -> {
                if (fetchToolStep(level) != FetchStatus.RUNNING) {
                    phase = findSpot() ? Phase.TO_SPOT : null;
                }
            }
            case DELIVER -> {
                if (deliverStep(level)) {
                    phase = findSpot() ? Phase.TO_SPOT : null;
                }
            }
        }
    }

    private int deliverables() {
        return resident.carriedCount() - keptCount();
    }

    private void setPhase(Phase next) {
        phase = next;
        phaseTicks = 0;
        resetPath();
    }

    // ---- finding water ---------------------------------------------------------------------

    /** Picks a fishing spot; rescans the water around the hut if none is known (throttled). */
    private boolean findSpot() {
        if (!(resident.level() instanceof ServerLevel level)) {
            return false;
        }
        if (spots.isEmpty() && level.getGameTime() >= nextSearchGameTime) {
            scanWater(level);
            nextSearchGameTime = level.getGameTime() + SEARCH_COOLDOWN_TICKS;
            resident.brain().setWorkIssue(spots.isEmpty() ? ISSUE_NO_WATER : null);
        }
        Optional<Spot> next = FishingSpots.choose(spots, resident.getX(), resident.getZ(), resident.getRandom().nextDouble());
        if (next.isEmpty()) {
            return false;
        }
        spot = next.get();
        setPhase(Phase.TO_SPOT);
        return true;
    }

    /** Finds water surfaces with a free bank next to them (one height map lookup per column, never loads chunks). */
    private void scanWater(ServerLevel level) {
        spots.clear();
        BlockPos center = workplace().center();
        int radius = Professions.get(PROFESSION).map(p -> p.workRadius()).orElse(16);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
            for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
                if (!level.hasChunkAt(x, z)) {
                    continue;
                }
                int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                if (Math.abs(top - center.getY()) > MAX_HEIGHT_DIFFERENCE
                        || !isWaterSource(level.getFluidState(pos.set(x, top, z)))
                        || BuildingAreas.isInside(villageBuildings(), x, top, z)) {
                    continue;
                }
                BlockPos water = new BlockPos(x, top, z);
                Spot found = bankNextTo(level, water);
                if (found != null && FishingSpots.isOpenWater(waterAround(level, water))) {
                    spots.add(found);
                }
            }
        }
    }

    /** A dry, walkable position right next to the water (same level or one block higher), or null. */
    private Spot bankNextTo(ServerLevel level, BlockPos water) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            for (int up = 0; up <= 1; up++) {
                BlockPos ground = water.relative(side).above(up);
                BlockPos stand = ground.above();
                if (!level.isLoaded(ground)) {
                    continue;
                }
                BlockState groundState = level.getBlockState(ground);
                if (groundState.isFaceSturdy(level, ground, Direction.UP) && isFree(level, stand) && isFree(level, stand.above())
                        && !BuildingAreas.isInside(villageBuildings(), stand.getX(), stand.getY(), stand.getZ())) {
                    return new Spot(water.getX(), water.getY(), water.getZ(), stand.getX(), stand.getY(), stand.getZ());
                }
            }
        }
        return null;
    }

    private static boolean isFree(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().isEmpty();
    }

    private static int waterAround(ServerLevel level, BlockPos water) {
        int count = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                pos.set(water.getX() + dx, water.getY(), water.getZ() + dz);
                if (level.isLoaded(pos) && isWaterSource(level.getFluidState(pos))) {
                    count++;
                }
            }
        }
        return count;
    }

    private static boolean isWaterSource(FluidState fluid) {
        return fluid.is(FluidTags.WATER) && fluid.isSource();
    }

    // ---- fishing ---------------------------------------------------------------------------

    private void walkToSpot(ServerLevel level) {
        BlockPos stand = new BlockPos(spot.standX(), spot.standY(), spot.standZ());
        boolean pathEnded = resident.getNavigation().isDone() && phaseTicks > REPATH_TICKS;
        if (stand.closerToCenterThan(resident.position(), STAND_DISTANCE)
                || (pathEnded && stand.closerToCenterThan(resident.position(), STAND_DISTANCE_PATH_END))) {
            resident.getNavigation().stop();
            setPhase(Phase.FISH);
            catchIn = FishingSpots.catchTicks(resident.getRandom().nextDouble(), workSpeed());
            swing();
            level.playSound(null, resident.blockPosition(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.NEUTRAL, 0.5F, 0.4F);
            return;
        }
        if (phaseTicks > GIVE_UP_TICKS) {
            spots.remove(spot); // unreachable: try another spot
            phase = findSpot() ? Phase.TO_SPOT : null;
            return;
        }
        moveTowards(stand, 0);
    }

    private void fish(ServerLevel level) {
        BlockPos water = new BlockPos(spot.waterX(), spot.waterY(), spot.waterZ());
        resident.getLookControl().setLookAt(water.getX() + 0.5, water.getY() + 0.8, water.getZ() + 0.5);
        if (!isWaterSource(level.getFluidState(water))) {
            spots.remove(spot); // the water is gone
            phase = findSpot() ? Phase.TO_SPOT : null;
            return;
        }
        if (phaseTicks % SPLASH_INTERVAL_TICKS == 0) {
            level.sendParticles(ParticleTypes.FISHING, water.getX() + 0.5, water.getY() + 0.9, water.getZ() + 0.5,
                    2, 0.2, 0.0, 0.2, 0.0);
        }
        if (phaseTicks < catchIn) {
            return;
        }
        swing();
        level.sendParticles(ParticleTypes.SPLASH, water.getX() + 0.5, water.getY() + 1.0, water.getZ() + 0.5,
                6, 0.2, 0.0, 0.2, 0.0);
        level.playSound(null, resident.blockPosition(), SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.NEUTRAL, 0.25F, 1.0F);
        catchSomething(level, water);
        if (isFull() || deliverables() >= DELIVER_AT) {
            setPhase(Phase.DELIVER);
        } else {
            phaseTicks = 0;
            catchIn = FishingSpots.catchTicks(resident.getRandom().nextDouble(), workSpeed());
        }
    }

    /**
     * Rolls Minecraft's fishing loot. The resident is the fishing entity, not a real bobber in open
     * water, so the treasure part never applies: fish and some junk only.
     */
    private void catchSomething(ServerLevel level, BlockPos water) {
        ItemStack rod = resident.getMainHandItem().isEmpty() ? new ItemStack(Items.FISHING_ROD) : resident.getMainHandItem();
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(water))
                .withParameter(LootContextParams.TOOL, rod)
                .withParameter(LootContextParams.THIS_ENTITY, resident)
                .create(LootContextParamSets.FISHING);
        LootTable table = level.getServer().reloadableRegistries().getLootTable(BuiltInLootTables.FISHING);
        table.getRandomItems(params).forEach(resident::carry);
        wearTool(level);
    }
}
