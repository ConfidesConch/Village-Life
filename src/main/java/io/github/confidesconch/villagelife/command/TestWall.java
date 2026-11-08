package io.github.confidesconch.villagelife.command;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The wooden wall around the test village (M20b2, owner's wish): a ring of spruce logs, two blocks
 * high, so that monsters do not get in so easily, with a gate (double door) in the north that the guards
 * patrol often. Built on the ground as it is; meant for test worlds only.
 */
final class TestWall {
    private TestWall() {}

    private static final int HEIGHT = 2;
    private static final int CLEAR_ABOVE = 4;

    /** Builds the ring at {@code radius} around {@code center}; the gate is at the north (−Z). */
    static void build(ServerLevel level, BlockPos center, int radius) {
        BlockState log = Blocks.SPRUCE_LOG.defaultBlockState();
        for (int dx = -radius - 1; dx <= radius + 1; dx++) {
            for (int dz = -radius - 1; dz <= radius + 1; dz++) {
                double distance = Math.sqrt(dx * dx + dz * dz);
                if (distance < radius - 0.5 || distance >= radius + 0.5) {
                    continue;
                }
                int x = center.getX() + dx;
                int z = center.getZ() + dz;
                int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                for (int y = 0; y < HEIGHT + CLEAR_ABOVE; y++) {
                    level.setBlock(new BlockPos(x, ground + y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
                if (dz < 0 && (dx == 0 || dx == 1)) {
                    gate(level, new BlockPos(x, ground, z), dx == 0 ? DoorHingeSide.LEFT : DoorHingeSide.RIGHT);
                } else {
                    for (int y = 0; y < HEIGHT; y++) {
                        level.setBlock(new BlockPos(x, ground + y, z), log, Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
    }

    /** M21: a second gate in the south (stress test: the outer quarter is reached from both sides). */
    static void southGate(ServerLevel level, BlockPos center, int radius) {
        for (int dx = 0; dx <= 1; dx++) {
            for (int dz = radius - 1; dz <= radius + 1; dz++) {
                double distance = Math.sqrt(dx * dx + dz * dz);
                if (distance >= radius - 0.5 && distance < radius + 0.5) {
                    int x = center.getX() + dx;
                    int z = center.getZ() + dz;
                    int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - HEIGHT;
                    gate(level, new BlockPos(x, ground, z), dx == 0 ? DoorHingeSide.RIGHT : DoorHingeSide.LEFT, Direction.NORTH);
                }
            }
        }
    }

    private static void gate(ServerLevel level, BlockPos at, DoorHingeSide hinge) {
        gate(level, at, hinge, Direction.SOUTH);
    }

    /** One half of the double door gate, opening towards the village. */
    private static void gate(ServerLevel level, BlockPos at, DoorHingeSide hinge, Direction facing) {
        BlockState door = Blocks.SPRUCE_DOOR.defaultBlockState().setValue(DoorBlock.FACING, facing)
                .setValue(DoorBlock.HINGE, hinge);
        level.setBlock(at, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), Block.UPDATE_CLIENTS);
        level.setBlock(at.above(), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_CLIENTS);
    }
}
