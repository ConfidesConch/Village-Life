package io.github.confidesconch.villagelife.defense;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import io.github.confidesconch.villagelife.inventory.StorageAccess;
import io.github.confidesconch.villagelife.village.Village;
import io.github.confidesconch.villagelife.village.VillageManager;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;

/**
 * What a bandit does when it is not fighting (M20b, owner: "everything physically, no tricks"): march
 * from outside to the village center, walk to a storage chest, open it and take a small handful
 * (1–8 items), then run back out of the village with it and disappear only out there. In the morning
 * all bandits run. A bandit killed on the way drops what it carries ({@link RaidService#onDrops}).
 * Fighting is done by the illager's own attack goals: this goal only runs without a target.
 */
public class BanditRaidGoal extends Goal {
    private enum Phase { ADVANCE, PLUNDER, FLEE }

    private static final int REPATH_TICKS = 40;
    private static final double CENTER_REACHED_SQR = 8 * 8;
    private static final double CHEST_REACH = 2.5;
    private static final int OPEN_TICKS = 30;
    private static final int MAX_STOLEN = 8;
    private static final int MAX_CHESTS = 3;
    /** Give up on a phase that does not get anywhere (stuck bandits). */
    private static final int ADVANCE_TIMEOUT = 2400;
    private static final int CHEST_TIMEOUT = 1200;
    private static final int FLEE_TIMEOUT = 3000;

    private final Mob bandit;
    private final UUID villageId;
    private final BlockPos center;
    private final BlockPos exit;
    private final int escapeDistance;
    private final long retreatAt;
    private Phase phase = Phase.ADVANCE;
    private int phaseTicks;
    private BlockPos chest;
    private int chestsTried;
    private int openTicks;

    BanditRaidGoal(Mob bandit, UUID villageId, BlockPos center, BlockPos exit, int escapeDistance, long retreatAt) {
        this.bandit = bandit;
        this.villageId = villageId;
        this.center = center;
        this.exit = exit;
        this.escapeDistance = escapeDistance;
        this.retreatAt = retreatAt;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return bandit.getTarget() == null && bandit.isAlive();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (!(bandit.level() instanceof ServerLevel level)) {
            return;
        }
        phaseTicks++;
        if (phase != Phase.FLEE && level.getGameTime() >= retreatAt) {
            setPhase(Phase.FLEE); // morning: run with whatever was taken
        }
        switch (phase) {
            case ADVANCE -> advance();
            case PLUNDER -> plunder(level);
            case FLEE -> flee();
        }
    }

    private void setPhase(Phase next) {
        phase = next;
        phaseTicks = 0;
        bandit.getNavigation().stop();
    }

    private void advance() {
        walkTo(center);
        if (bandit.blockPosition().distSqr(center) < CENTER_REACHED_SQR || phaseTicks > ADVANCE_TIMEOUT) {
            setPhase(Phase.PLUNDER);
        }
    }

    // ---- stealing ------------------------------------------------------------------------------

    private void plunder(ServerLevel level) {
        if (chest == null) {
            chest = chestsTried < MAX_CHESTS ? nextChest(level) : null;
            openTicks = 0;
            if (chest == null) {
                setPhase(Phase.FLEE);
                return;
            }
        }
        if (!chest.closerToCenterThan(bandit.position(), CHEST_REACH)) {
            walkTo(chest);
            if (phaseTicks > CHEST_TIMEOUT) { // cannot get there: try another chest
                chest = null;
                phaseTicks = 0;
            }
            return;
        }
        bandit.getNavigation().stop();
        bandit.getLookControl().setLookAt(chest.getX() + 0.5, chest.getY() + 0.5, chest.getZ() + 0.5);
        if (openTicks++ == 0) {
            bandit.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
            level.blockEvent(chest, level.getBlockState(chest).getBlock(), 1, 1); // lid opens
            level.playSound(null, chest, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.6F, 0.9F);
        } else if (openTicks >= OPEN_TICKS) {
            ItemStack stolen = steal(level, chest);
            level.blockEvent(chest, level.getBlockState(chest).getBlock(), 1, 0); // lid closes
            level.playSound(null, chest, SoundEvents.CHEST_CLOSE, SoundSource.BLOCKS, 0.6F, 0.9F);
            if (stolen.isEmpty()) { // empty chest: try the next one
                chest = null;
                return;
            }
            RaidService.carry(bandit.getUUID(), stolen);
            bandit.setItemSlot(EquipmentSlot.OFFHAND, stolen.copy()); // you can see what it carries
            bandit.setDropChance(EquipmentSlot.OFFHAND, 0.0F); // the loot drops via RaidService, not twice
            setPhase(Phase.FLEE);
        }
    }

    /** The nearest storage chest of the village not tried yet. */
    private BlockPos nextChest(ServerLevel level) {
        Village village = VillageManager.get(level).byId(villageId).orElse(null);
        if (village == null) {
            return null;
        }
        List<BlockPos> containers = new ArrayList<>(StorageAccess.containers(village, null, bandit.blockPosition()));
        chestsTried++;
        int index = chestsTried - 1;
        return index < containers.size() ? containers.get(index) : null;
    }

    /** Takes a small handful (1–8) from one random non-empty slot. */
    private ItemStack steal(ServerLevel level, BlockPos pos) {
        Container container = StorageAccess.containerAt(level, pos);
        if (container == null) {
            return ItemStack.EMPTY;
        }
        List<Integer> filled = new ArrayList<>();
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            if (!container.getItem(slot).isEmpty()) {
                filled.add(slot);
            }
        }
        if (filled.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = container.getItem(filled.get(bandit.getRandom().nextInt(filled.size())));
        int amount = 1 + bandit.getRandom().nextInt(Math.min(MAX_STOLEN, stack.getCount()));
        ItemStack stolen = stack.split(amount);
        container.setChanged();
        return stolen;
    }

    // ---- running away --------------------------------------------------------------------------

    private void flee() {
        walkTo(exit);
        double dx = bandit.getX() - center.getX();
        double dz = bandit.getZ() - center.getZ();
        if (dx * dx + dz * dz > (double) escapeDistance * escapeDistance || phaseTicks > FLEE_TIMEOUT) {
            RaidService.escaped(bandit);
            bandit.discard(); // out of sight of the village
        }
    }

    private void walkTo(BlockPos pos) {
        if (phaseTicks % REPATH_TICKS == 1 || bandit.getNavigation().isDone()) {
            bandit.getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 1.0);
        }
    }
}
