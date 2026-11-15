package io.github.confidesconch.villagelife.command;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What makes the test village look like a village (M20b2; new layout 15.11.2026, owner's wish): a paved
 * square with two market stalls around the village center, a main street west – east, the road north to
 * the gate, a back lane on each side, side streets between the plots, a path from every door to the
 * nearest street, lamp posts along the streets and signposts at the square. Only decoration – nothing
 * here counts as a building. Coordinates relative to the center, {@code floorY} is the ground.
 */
final class TestDecor {
    private TestDecor() {}

    /** Half the size of the market square (11 × 11). */
    static final int SQUARE = 5;
    /** Main street: z −1 … 1; back lanes: z ±16 … ±18; west and east end. */
    static final int STREET_END = 46;
    static final int LANE = 16;

    /** Streets, lanes, side streets and the square; lamps and signposts come last ({@link #finish}). */
    static void streets(ServerLevel level, BlockPos center, int floorY, int wallRadius) {
        square(level, center, floorY);
        path(level, center, floorY, -STREET_END, -1, -SQUARE - 1, 1);           // main street west
        path(level, center, floorY, SQUARE + 1, -1, STREET_END, 1);             // main street east
        path(level, center, floorY, -1, -wallRadius + 1, 1, -SQUARE - 1);       // north to the gate
        path(level, center, floorY, -STREET_END + 2, -LANE - 2, STREET_END - 2, -LANE);  // north lane
        path(level, center, floorY, -STREET_END + 2, LANE, STREET_END - 2, LANE + 2);    // south lane
        for (int x : new int[]{-17, 14}) { // side streets between the plots, main street to the lanes
            path(level, center, floorY, x, -LANE + 1, x + 2, -2);
            path(level, center, floorY, x, 2, x + 2, LANE - 1);
        }
    }

    /** A path from the door of {@code site} out to the next street or lane (at most 20 blocks). */
    static void doorPath(TestSite site, int doorX) {
        for (int k = 1; k <= 20; k++) {
            BlockPos ground = site.front(doorX, k);
            if (!site.level().getBlockState(ground).is(Blocks.GRASS_BLOCK) || !site.level().getBlockState(ground.above()).isAir()) {
                return;
            }
            set(site.level(), ground, Blocks.DIRT_PATH.defaultBlockState());
        }
    }

    /** A sign next to the door with the building's name (e.g. {@code villagelife.workplace.smith}). */
    static void nameSign(TestSite site, int doorX, String key) {
        BlockPos pos = site.front(doorX + 1, 1).above();
        if (site.level().getBlockState(pos).isAir()) {
            sign(site.level(), pos, site.flipped() ? 0 : 8, Component.translatable(key), Component.empty());
        }
    }

    /** Lamp posts along the streets and signposts at the square; after all buildings and door paths. */
    static void finish(ServerLevel level, BlockPos center, int floorY) {
        for (int x : new int[]{8, 18, 28, 38}) {
            for (int sign : new int[]{-1, 1}) {
                lampIfFree(level, center.offset(sign * x, 0, -2).atY(floorY + 1));
                lampIfFree(level, center.offset(sign * x, 0, 2).atY(floorY + 1));
                lampIfFree(level, center.offset(sign * x, 0, -LANE + 1).atY(floorY + 1));
                lampIfFree(level, center.offset(sign * x, 0, LANE - 1).atY(floorY + 1));
            }
        }
        for (int z = -10; z >= -50; z -= 10) {
            lampIfFree(level, center.offset(2, 0, z).atY(floorY + 1));
        }
        signpost(level, center.offset(-SQUARE - 2, 0, 2).atY(floorY + 1), "homes");
        signpost(level, center.offset(SQUARE + 2, 0, 2).atY(floorY + 1), "work");
        signpost(level, center.offset(2, 0, -SQUARE - 2).atY(floorY + 1), "gate");
        signpost(level, center.offset(2, 0, SQUARE + 2).atY(floorY + 1), "storage");
    }

    // ---- pieces ------------------------------------------------------------------------------------

    private static void square(ServerLevel level, BlockPos center, int floorY) {
        BlockState paving = Blocks.POLISHED_ANDESITE.defaultBlockState();
        BlockState border = Blocks.STONE_BRICKS.defaultBlockState();
        for (int x = -SQUARE; x <= SQUARE; x++) {
            for (int z = -SQUARE; z <= SQUARE; z++) {
                boolean edge = Math.abs(x) == SQUARE || Math.abs(z) == SQUARE;
                boolean exit = Math.abs(x) <= 1 || Math.abs(z) <= 1; // the streets run into the square
                set(level, center.offset(x, 0, z).atY(floorY), edge && !exit ? border : paving);
            }
        }
        for (int x : new int[]{-SQUARE, SQUARE}) {
            for (int z : new int[]{-SQUARE, SQUARE}) {
                lampPost(level, center.offset(x, 0, z).atY(floorY + 1));
            }
        }
        stall(level, center.offset(-4, 0, -4).atY(floorY + 1), DyeColor.RED);
        stall(level, center.offset(2, 0, 3).atY(floorY + 1), DyeColor.BLUE);
    }

    private static void lampIfFree(ServerLevel level, BlockPos foot) {
        if (level.getBlockState(foot.below()).is(Blocks.GRASS_BLOCK) && level.getBlockState(foot).isAir()
                && level.getBlockState(foot.above()).isAir() && level.getBlockState(foot.above(2)).isAir()) {
            lampPost(level, foot);
        }
    }

    /** A fence post with a lantern on top. */
    private static void lampPost(ServerLevel level, BlockPos foot) {
        set(level, foot, Blocks.OAK_FENCE.defaultBlockState());
        set(level, foot.above(), Blocks.OAK_FENCE.defaultBlockState());
        set(level, foot.above(2), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false));
    }

    /** A signpost: a fence with a sign on top, the same two lines on both sides. */
    private static void signpost(ServerLevel level, BlockPos foot, String what) {
        if (!level.getBlockState(foot).isAir()) {
            return;
        }
        set(level, foot, Blocks.OAK_FENCE.defaultBlockState());
        sign(level, foot.above(), 8, Component.translatable("villagelife.testvillage.sign." + what),
                Component.translatable("villagelife.testvillage.sign." + what + ".to"));
    }

    private static void sign(ServerLevel level, BlockPos pos, int rotation, Component first, Component second) {
        set(level, pos, Blocks.OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, rotation));
        if (level.getBlockEntity(pos) instanceof SignBlockEntity sign) {
            List<Component> lines = new ArrayList<>(List.of(Component.empty(), first, second, Component.empty()));
            SignText text = new SignText(lines, lines, DyeColor.BLACK, false);
            sign.setText(text, SignTextSlot.FRONT);
            sign.setText(text, SignTextSlot.BACK);
            sign.setWaxed(true); // a test village: nobody should edit them by accident
            level.sendBlockUpdated(pos, sign.getBlockState(), sign.getBlockState(), Block.UPDATE_CLIENTS);
        }
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
