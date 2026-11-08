package io.github.confidesconch.villagelife.command;

import io.github.confidesconch.villagelife.building.BuildingMarkerBlock;
import io.github.confidesconch.villagelife.building.BuildingType;
import io.github.confidesconch.villagelife.core.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/** Places blocks relative to one origin (lower north-west corner, floor level) for the test village (M12a). */
record TestSite(ServerLevel level, BlockPos origin) {
    BlockPos at(int x, int y, int z) {
        return origin.offset(x, y, z);
    }

    void set(int x, int y, int z, BlockState state) {
        // no neighbour updates while building: half-built doors and beds must not pop off
        level.setBlock(at(x, y, z), state, Block.UPDATE_CLIENTS);
    }

    /** Roof styles of the test village: stairs and the matching slab for the ridge. */
    private static final BlockState[][] ROOFS = {
            {Blocks.DARK_OAK_STAIRS.defaultBlockState(), Blocks.DARK_OAK_SLAB.defaultBlockState()},
            {Blocks.SPRUCE_STAIRS.defaultBlockState(), Blocks.SPRUCE_SLAB.defaultBlockState()},
            {Blocks.BRICK_STAIRS.defaultBlockState(), Blocks.BRICK_SLAB.defaultBlockState()},
            {Blocks.STONE_BRICK_STAIRS.defaultBlockState(), Blocks.STONE_BRICK_SLAB.defaultBlockState()}};

    /**
     * A house like in a real village (M20b2, owner's wish): floor and walls from {@code walls} with log
     * posts at the corners and glass windows on the sides and the back, a flat ceiling from {@code roof}
     * (with glowstone lights, so the room stays closed for the building scan) and above it a pitched roof
     * of stairs with gables – flat for very wide halls.
     */
    void house(int w, int h, int d, BlockState walls, BlockState roof) {
        BlockState post = Blocks.OAK_LOG.defaultBlockState();
        BlockState glass = Blocks.GLASS.defaultBlockState();
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                for (int y = 0; y < h; y++) {
                    boolean side = x == 0 || x == w - 1;
                    boolean shell = side || z == 0 || z == d - 1 || y == 0;
                    if (y == h - 1) {
                        boolean light = x % 4 == 2 && z % 4 == 2;
                        set(x, y, z, light ? Blocks.GLOWSTONE.defaultBlockState() : roof);
                    } else if (y > 0 && side && (z == 0 || z == d - 1)) {
                        set(x, y, z, post);
                    } else if (y == 2 && isWindow(x, z, w, d)) {
                        set(x, y, z, glass);
                    } else if (shell) {
                        set(x, y, z, walls);
                    }
                }
            }
        }
        if (w <= 11) {
            BlockState[] style = ROOFS[Math.floorMod(origin.getX() * 31 + origin.getZ(), ROOFS.length)];
            pitchedRoof(w, h, d, style[0], style[1], walls);
        }
    }

    /** Windows every other block on the side walls and the back wall, not next to the corners. */
    private static boolean isWindow(int x, int z, int w, int d) {
        boolean sideWall = (x == 0 || x == w - 1) && z >= 2 && z <= d - 3 && z % 2 == 0;
        boolean backWall = z == d - 1 && x >= 2 && x <= w - 3 && x % 2 == 0;
        return sideWall || backWall;
    }

    /** A gable roof over the ceiling, ridge running front to back, one block of overhang all round. */
    private void pitchedRoof(int w, int h, int d, BlockState stairs, BlockState slab, BlockState gable) {
        for (int k = 0; ; k++) {
            int left = k - 1;
            int right = w - k;
            int y = h + k;
            if (left > right) {
                return;
            }
            for (int z = -1; z <= d; z++) {
                if (left == right) {
                    set(left, y, z, slab);
                } else {
                    set(left, y, z, stairs.setValue(StairBlock.FACING, Direction.EAST));
                    set(right, y, z, stairs.setValue(StairBlock.FACING, Direction.WEST));
                }
            }
            for (int x = Math.max(0, left + 1); x < Math.min(w, right); x++) {
                set(x, y, 0, gable);
                set(x, y, d - 1, gable);
            }
        }
    }

    /** A chimney through the roof with a smoking campfire on top (kitchen, smithy, bakery). */
    void chimney(int x, int z, int h) {
        for (int y = h; y < h + 6; y++) {
            set(x, y, z, Blocks.COBBLESTONE.defaultBlockState());
        }
        set(x, h + 6, z, Blocks.CAMPFIRE.defaultBlockState());
    }

    /**
     * Some furnishing once everything else stands: flower pots in free corners and a lantern under the
     * ceiling. No workstation or container blocks, so the building keeps its profession.
     */
    void furnish() {
        int w = 1;
        while (w < 32 && level.getBlockState(at(w, 3, 1)).isAir()) {
            w++;
        }
        int d = 1;
        while (d < 32 && level.getBlockState(at(1, 3, d)).isAir()) {
            d++;
        }
        BlockState[] plants = {Blocks.POTTED_POPPY.defaultBlockState(), Blocks.POTTED_FERN.defaultBlockState(),
                Blocks.POTTED_DANDELION.defaultBlockState(), Blocks.POTTED_AZURE_BLUET.defaultBlockState()};
        int[][] corners = {{1, d - 1}, {w - 1, d - 1}, {1, 1}, {w - 1, 1}};
        for (int i = 0; i < corners.length; i++) {
            int x = corners[i][0];
            int z = corners[i][1];
            if (level.getBlockState(at(x, 1, z)).isAir() && !level.getBlockState(at(x, 1, 0)).is(Blocks.OAK_DOOR)) {
                set(x, 1, z, plants[i]);
            }
        }
        if (level.getBlockState(at(w / 2, 3, d / 2)).isAir()) {
            set(w / 2, 3, d / 2, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        }
    }

    void door(int x) {
        BlockState door = Blocks.OAK_DOOR.defaultBlockState();
        set(x, 1, 0, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        set(x, 2, 0, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    /** A bed whose head is at z and foot at z + 1 (the default facing). */
    void bed(int x, int z, DyeColor color) {
        BlockState bed = Blocks.BED.pick(color).defaultBlockState();
        set(x, 1, z, bed.setValue(AbstractBedBlock.PART, BedPart.HEAD));
        set(x, 1, z + 1, bed.setValue(AbstractBedBlock.PART, BedPart.FOOT));
    }

    /**
     * The building marker: like a player would hang it (M18d) – outside on the wall above the door,
     * facing out; without a door it stands inside at (x, z).
     */
    BlockPos marker(int x, int z, BuildingType type) {
        BlockState marker = ModBlocks.BUILDING_MARKER.get().defaultBlockState().setValue(BuildingMarkerBlock.TYPE, type);
        int doorX = -1;
        for (int dx = 0; dx < 32 && doorX < 0; dx++) {
            if (level.getBlockState(at(dx, 1, 0)).getBlock() instanceof DoorBlock) {
                doorX = dx;
            }
        }
        furnish();
        if (doorX >= 0) {
            set(doorX, 3, -1, marker.setValue(BuildingMarkerBlock.FACING, Direction.NORTH));
            return at(doorX, 3, -1);
        }
        set(x, 1, z, marker);
        return at(x, 1, z);
    }

    void chest(int x, int z, ItemStack... contents) {
        set(x, 1, z, Blocks.CHEST.defaultBlockState());
        if (level.getBlockEntity(at(x, 1, z)) instanceof Container container) {
            for (int i = 0; i < contents.length && i < container.getContainerSize(); i++) {
                container.setItem(i, contents[i]);
            }
        }
    }

    void tree(int x, int z) {
        for (int y = 1; y <= 5; y++) {
            set(x, y, z, Blocks.OAK_LOG.defaultBlockState());
        }
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int y = 4; y <= 6; y++) {
                    boolean corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
                    boolean top = y == 6 && (Math.abs(dx) > 1 || Math.abs(dz) > 1);
                    if ((dx != 0 || dz != 0 || y == 6) && !corner && !top) {
                        set(x + dx, y, z + dz, leaves);
                    }
                }
            }
        }
    }
}
