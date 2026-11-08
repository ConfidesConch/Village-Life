package io.github.confidesconch.villagelife.profession.work;

import java.util.List;

import io.github.confidesconch.villagelife.resident.ResidentEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.FenceGateBlock;

/**
 * Brings one escaped animal back into the work area (M13d, chosen by the owner: "lure, with carrying as
 * a fallback"). The herder holds the animal's feed in its hand and the animal walks on its own to the
 * pasture's fence gate, where the herder waits. The gate stays closed – opening it let more animals
 * out – the herder lifts the animal over it instead. Without a gate the animal walks into the middle
 * of the area. If nothing worked after a minute, the herder "carries" the animal in: it vanishes in a
 * puff of dust and reappears in the middle of the area.
 */
final class HerdLure {
    /** The lure gives up and the animal is carried in after this long. */
    static final int CARRY_AFTER_TICKS = 1200;
    private static final int ANIMAL_REPATH_TICKS = 10;
    private static final int HERDER_REPATH_TICKS = 20;
    /** The animal is lifted over once both are this close to the spot in front of the gate. */
    private static final double LIFT_DISTANCE = 2.0;
    private static final double HERDER_LIFT_DISTANCE = 3.5;
    /** Feed shown in the herder's hand: the first one the animal likes. */
    private static final List<Item> LURES = List.of(Items.WHEAT, Items.CARROT, Items.WHEAT_SEEDS, Items.POTATO, Items.BEETROOT);

    enum Status { RUNNING, DONE }

    private final ResidentEntity herder;
    private final Animal animal;
    private final HerdArea area;
    private final BlockPos gate;
    private final Direction outward;
    private int ticks;

    HerdLure(ServerLevel level, ResidentEntity herder, Animal animal, HerdArea area) {
        this.herder = herder;
        this.animal = animal;
        this.area = area;
        this.gate = findGate(level, area);
        this.outward = gate == null ? null : outwardSide(gate, area);
        showLure();
    }

    Status tick(ServerLevel level, double speed) {
        ticks++;
        if (!animal.isAlive() || area.contains(animal)) {
            return finish(level);
        }
        if (ticks > CARRY_AFTER_TICKS) {
            moveInto(level, area.middle(level));
            return finish(level);
        }
        herder.getLookControl().setLookAt(animal);
        if (gate == null) {
            BlockPos middle = area.middle(level);
            if (ticks % ANIMAL_REPATH_TICKS == 1) {
                animal.getNavigation().moveTo(middle.getX() + 0.5, middle.getY(), middle.getZ() + 0.5, 1.0);
            }
            if (ticks % HERDER_REPATH_TICKS == 1) {
                herder.getNavigation().moveTo(animal, speed); // no gate: walk along with the animal
            }
            return Status.RUNNING;
        }
        BlockPos front = gate.relative(outward); // in front of the gate, outside
        if (ticks % ANIMAL_REPATH_TICKS == 1) {
            animal.getNavigation().moveTo(front.getX() + 0.5, front.getY(), front.getZ() + 0.5, 1.0);
        }
        BlockPos stand = front.relative(outward.getClockWise()); // next to it, so the animal has room
        if (ticks % HERDER_REPATH_TICKS == 1 && !stand.closerToCenterThan(herder.position(), 1.5)) {
            herder.getNavigation().moveTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, speed);
        }
        if (front.closerToCenterThan(animal.position(), LIFT_DISTANCE)
                && front.closerToCenterThan(herder.position(), HERDER_LIFT_DISTANCE)) {
            herder.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
            moveInto(level, gate.relative(outward.getOpposite(), 2)); // lifted over the closed gate
            return finish(level);
        }
        return Status.RUNNING;
    }

    /** Ends the lure: the herder's hand is emptied (the goal shows its tool again). */
    Status finish(ServerLevel level) {
        herder.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        animal.getNavigation().stop();
        return Status.DONE;
    }

    private void showLure() {
        for (Item lure : LURES) {
            ItemStack stack = new ItemStack(lure);
            if (animal.isFood(stack)) {
                herder.setItemSlot(EquipmentSlot.MAINHAND, stack);
                return;
            }
        }
    }

    /** Puff of dust, and the animal stands at {@code pos} (lifted over the gate, or carried in as fallback). */
    private void moveInto(ServerLevel level, BlockPos pos) {
        level.sendParticles(ParticleTypes.POOF, animal.getX(), animal.getY() + 0.5, animal.getZ(), 8, 0.3, 0.3, 0.3, 0.02);
        animal.getNavigation().stop();
        animal.teleportTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        level.sendParticles(ParticleTypes.POOF, animal.getX(), animal.getY() + 0.5, animal.getZ(), 8, 0.3, 0.3, 0.3, 0.02);
        level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.4F, 0.8F);
    }

    /** A fence gate on the border of the area or one block outside it, or null (open pasture). */
    private static BlockPos findGate(ServerLevel level, HerdArea area) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int ring = 0; ring <= 1; ring++) {
            int minX = area.zone().minX() - ring;
            int maxX = area.zone().maxX() + ring;
            int minZ = area.zone().minZ() - ring;
            int maxZ = area.zone().maxZ() + ring;
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (x != minX && x != maxX && z != minZ && z != maxZ) {
                        continue; // only the border
                    }
                    for (int y = area.markerY() - 4; y <= area.markerY() + 4; y++) {
                        if (level.isLoaded(pos.set(x, y, z)) && level.getBlockState(pos).getBlock() instanceof FenceGateBlock) {
                            return pos.immutable();
                        }
                    }
                }
            }
        }
        return null;
    }

    private static Direction outwardSide(BlockPos gate, HerdArea area) {
        if (gate.getX() <= area.zone().minX()) {
            return Direction.WEST;
        }
        if (gate.getX() >= area.zone().maxX()) {
            return Direction.EAST;
        }
        return gate.getZ() <= area.zone().minZ() ? Direction.NORTH : Direction.SOUTH;
    }
}
