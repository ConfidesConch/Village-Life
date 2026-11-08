package io.github.confidesconch.villagelife.command;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What makes the test village look like a village (M20b2, owner's wish): a paved market square around
 * the village center with lamp posts and two market stalls, and dirt paths to the gate and along the
 * main street. Only decoration – nothing here counts as a building. Coordinates relative to the center,
 * {@code floorY} is the ground.
 */
final class TestDecor {
    private TestDecor() {}

    /** Half the size of the market square (11 × 11). */
    private static final int SQUARE = 5;
    /** The gate in the wall is at x 0–1 in the north (TestWall). */
    private static final int GATE_X = 0;

    static void build(ServerLevel level, BlockPos center, int floorY, int wallRadius) {
        square(level, center, floorY);
        // main street west – east, and the road north to the gate
        path(level, center, floorY, -43, -1, -SQUARE - 1, 0);
        path(level, center, floorY, SQUARE + 1, -1, 23, 0);
        path(level, center, floorY, GATE_X, -wallRadius + 1, GATE_X + 1, -SQUARE - 1);
        path(level, center, floorY, GATE_X, SQUARE + 1, GATE_X + 1, 7);
    }

    private static void square(ServerLevel level, BlockPos center, int floorY) {
        BlockState paving = Blocks.POLISHED_ANDESITE.defaultBlockState();
        BlockState border = Blocks.STONE_BRICKS.defaultBlockState();
        for (int x = -SQUARE; x <= SQUARE; x++) {
            for (int z = -SQUARE; z <= SQUARE; z++) {
                boolean edge = Math.abs(x) == SQUARE || Math.abs(z) == SQUARE;
                set(level, center.offset(x, 0, z).atY(floorY), edge ? border : paving);
            }
        }
        for (int x : new int[]{-SQUARE, SQUARE}) {
            for (int z : new int[]{-SQUARE, SQUARE}) {
                lampPost(level, center.offset(x, 0, z).atY(floorY + 1));
            }
        }
        stall(level, center.offset(-4, 0, -3).atY(floorY + 1), DyeColor.RED);
        stall(level, center.offset(2, 0, 2).atY(floorY + 1), DyeColor.BLUE);
    }

    /** A fence post with a lantern on top. */
    private static void lampPost(ServerLevel level, BlockPos foot) {
        set(level, foot, Blocks.OAK_FENCE.defaultBlockState());
        set(level, foot.above(), Blocks.OAK_FENCE.defaultBlockState());
        set(level, foot.above(2), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false));
    }

    /** A small stall: four posts, a striped wool awning, a counter of slabs with some goods. */
    private static void stall(ServerLevel level, BlockPos corner, DyeColor color) {
        for (int x = 0; x <= 2; x += 2) {
            for (int z = 0; z <= 1; z++) {
                set(level, corner.offset(x, 0, z), Blocks.OAK_FENCE.defaultBlockState());
                set(level, corner.offset(x, 1, z), Blocks.OAK_FENCE.defaultBlockState());
            }
        }
        for (int x = 0; x <= 2; x++) {
            for (int z = 0; z <= 1; z++) {
                set(level, corner.offset(x, 2, z), Blocks.WOOL.pick(x == 1 ? DyeColor.WHITE : color).defaultBlockState());
            }
        }
        set(level, corner.offset(1, 0, 0), Blocks.SPRUCE_SLAB.defaultBlockState());
        set(level, corner.offset(1, 0, 1), Blocks.PUMPKIN.defaultBlockState());
    }

    /** Dirt path on grass in the rectangle (x1, z1)–(x2, z2). */
    private static void path(ServerLevel level, BlockPos center, int floorY, int x1, int z1, int x2, int z2) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                BlockPos ground = center.offset(x, 0, z).atY(floorY);
                if (level.getBlockState(ground).is(Blocks.GRASS_BLOCK) && level.getBlockState(ground.above()).isAir()) {
                    set(level, ground, Blocks.DIRT_PATH.defaultBlockState());
                }
            }
        }
    }

    private static void set(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, Block.UPDATE_CLIENTS);
    }
}
