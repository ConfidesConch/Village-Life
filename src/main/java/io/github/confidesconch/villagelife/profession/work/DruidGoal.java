package io.github.confidesconch.villagelife.profession.work;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.github.confidesconch.villagelife.resident.ResidentEntity;
import io.github.confidesconch.villagelife.village.Village;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.BonemealSource;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Work of the druid (M25, owner's choice "1a 2a 3b"): looks for growing plants inside the village border
 * – crops on fields, melon and pumpkin stems, saplings – walks to the nearest one and helps it grow one
 * step, for free and at a gentle pace (about one plant every few seconds). Without plants he stands at
 * his flowering azalea. He does not refill the miner's pits (owner's choice).
 */
public class DruidGoal extends WorkGoal {
    public static final String PROFESSION = "villagelife:druid";

    /** Column offsets per village radius, shared by all druids. */
    private static final Map<Integer, int[]> DISCS = new ConcurrentHashMap<>();

    private final List<BlockPos> targets = new ArrayList<>();
    /** Plants he could not get to, and until when (game time) he leaves them alone (bug review 13.11.2026). */
    private final Map<BlockPos, Long> skipUntil = new HashMap<>();
    private int[] offsets = new int[0];
    private int radius = -1;
    private int cursor;
    private BlockPos target;
    private int targetTicks;
    /** Ticks spent tending the current plant (walking there does not count; bug review 13.11.2026). */
    private int tendTicks;

    public DruidGoal(ResidentEntity resident, double speedModifier) {
        super(resident, speedModifier, PROFESSION, ItemTags.SHOVELS, Items.STICK); // a staff in the hand
    }

    @Override
    public boolean canUse() {
        return isWorkTime() && resident.level() instanceof ServerLevel && resident.brain().village() != null;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void stop() {
        target = null;
        super.stop();
    }

    @Override
    public void tick() {
        if (!(resident.level() instanceof ServerLevel level)) {
            return;
        }
        Village village = resident.brain().village();
        scan(level, village);
        if (target == null) {
            target = nearestTarget();
            targetTicks = 0;
            tendTicks = 0;
            resetPath();
            if (target == null) {
                standAtAzalea(level);
                return;
            }
        }
        targetTicks++;
        if (!isGrowing(level, target)) {
            target = null; // ripe meanwhile or harvested
            return;
        }
        if (targetTicks > DruidRules.GIVE_UP_TICKS) {
            if (skipUntil.size() > 256) {
                skipUntil.clear();
            }
            skipUntil.put(target, level.getGameTime() + DruidRules.SKIP_TICKS); // out of reach: not again for a while
            target = null;
            return;
        }
        if (!target.closerToCenterThan(resident.position(), DruidRules.REACH)) {
            moveTowards(target, 1);
            return;
        }
        tend(level);
    }

    // ---- finding plants ----------------------------------------------------------------------------

    /** Looks at a few columns of the village per tick for plants that can grow. */
    private void scan(ServerLevel level, Village village) {
        if (village.radius() != radius) {
            radius = village.radius();
            offsets = DISCS.computeIfAbsent(radius, DruidRules::discOffsets);
            cursor = 0;
        }
        BlockPos center = village.center();
        int columns = offsets.length / 2;
        for (int i = 0; i < DruidRules.COLUMNS_PER_TICK && targets.size() < DruidRules.MAX_TARGETS && columns > 0; i++) {
            int x = center.getX() + offsets[cursor * 2];
            int z = center.getZ() + offsets[cursor * 2 + 1];
            cursor = (cursor + 1) % columns;
            if (!level.hasChunkAt(x, z)) {
                continue;
            }
            BlockPos top = new BlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1, z);
            if (isGrowing(level, top) && !targets.contains(top) && !top.equals(target)
                    && skipUntil.getOrDefault(top, 0L) <= level.getGameTime()) {
                targets.add(top);
            }
        }
    }

    private BlockPos nearestTarget() {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : targets) {
            double distance = pos.distToCenterSqr(resident.position());
            if (distance < bestDistance) {
                best = pos;
                bestDistance = distance;
            }
        }
        targets.remove(best);
        return best;
    }

    /** True for a crop, stem or sapling that is not fully grown yet (and a sapling with room for its tree). */
    private static boolean isGrowing(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();
        if (block instanceof CropBlock crop) {
            return !crop.isMaxAge(state);
        }
        if (block instanceof StemBlock) {
            return state.getValue(StemBlock.AGE) < StemBlock.MAX_AGE;
        }
        if (block instanceof SaplingBlock sapling) {
            return sapling.isValidBonemealTarget(level, pos, state, BonemealSource.MOB);
        }
        return false;
    }

    // ---- tending -----------------------------------------------------------------------------------

    private void tend(ServerLevel level) {
        resident.getNavigation().stop();
        resident.getLookControl().setLookAt(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
        if (tendTicks++ % 20 == 0) {
            swing();
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, target.getX() + 0.5, target.getY() + 0.6, target.getZ() + 0.5,
                    3, 0.3, 0.2, 0.3, 0.0);
        }
        if (tendTicks < DruidRules.TEND_TICKS / workSpeed()) {
            return;
        }
        grow(level, target);
        level.playSound(null, target, SoundEvents.BONE_MEAL_USE, SoundSource.NEUTRAL, 0.6F, 1.0F);
        target = null;
    }

    /** One growth step. */
    private static void grow(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();
        if (block instanceof CropBlock crop) {
            int next = DruidRules.nextAge(crop.getAge(state), crop.getMaxAge());
            if (next >= 0) {
                level.setBlock(pos, crop.getStateForAge(next), Block.UPDATE_CLIENTS);
            }
        } else if (block instanceof StemBlock) {
            int next = DruidRules.nextAge(state.getValue(StemBlock.AGE), StemBlock.MAX_AGE);
            if (next >= 0) {
                level.setBlock(pos, state.setValue(StemBlock.AGE, next), Block.UPDATE_CLIENTS);
            }
        } else if (block instanceof SaplingBlock sapling) {
            sapling.advanceTree(level, pos, state, level.getRandom()); // stage 1, then the tree
        }
    }

    private void standAtAzalea(ServerLevel level) {
        for (BlockPos pos : workplace().workstations()) {
            if (level.isLoaded(pos) && level.getBlockState(pos).is(Blocks.FLOWERING_AZALEA)) {
                if (!pos.closerToCenterThan(resident.position(), 2.5)) {
                    moveTowards(pos, 1);
                } else {
                    resident.getNavigation().stop();
                }
                return;
            }
        }
    }
}
