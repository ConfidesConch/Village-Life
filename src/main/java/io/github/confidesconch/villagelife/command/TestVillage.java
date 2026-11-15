package io.github.confidesconch.villagelife.command;

import java.util.ArrayList;
import java.util.List;

import io.github.confidesconch.villagelife.building.BuildingMarkerBlock;
import io.github.confidesconch.villagelife.building.BuildingService;
import io.github.confidesconch.villagelife.building.BuildingType;
import io.github.confidesconch.villagelife.building.MarkerSymbol;
import io.github.confidesconch.villagelife.core.registry.ModBlocks;
import io.github.confidesconch.villagelife.village.VillageManager;
import io.github.confidesconch.villagelife.village.VillageService;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * {@code /villagelife debug testvillage} (M12a; new layout 15.11.2026, owner's wish): builds a complete test
 * village around the player on flat ground, laid out like a real village – the village center on a paved
 * square with the storage right next to it, the residential quarter with two-storey houses on the west
 * side, all workplaces on the east side ({@link TestWorkshops}), streets, lanes, door paths, lamp posts
 * and signposts ({@link TestDecor}), a wooden wall with a gate in the north (M20b2) – and registers every
 * building. Clears the area first: meant for test worlds only.
 */
final class TestVillage {
    private TestVillage() {}

    /** Cleared: a circle of this radius around the center (inside the wall). */
    private static final int CLEAR_RADIUS = 57;
    private static final int CLEAR_HEIGHT = 14;
    /** The wooden wall (M20b2): inside the village radius (64), outside all buildings. */
    static final int WALL_RADIUS = 58;
    /** Beds in the residential quarter: 8 two-storey houses with 4 beds each (StressVillage counts on it). */
    static final int HOUSE_BEDS = 32;

    static final List<EntityType<?>> SHEEP = List.of(EntityTypes.SHEEP, EntityTypes.SHEEP, EntityTypes.SHEEP, EntityTypes.SHEEP);
    static final List<EntityType<?>> STABLE_ANIMALS = List.of(EntityTypes.COW, EntityTypes.COW, EntityTypes.PIG,
            EntityTypes.PIG, EntityTypes.CHICKEN, EntityTypes.CHICKEN, EntityTypes.CHICKEN);

    /** Builds everything; returns the number of registered buildings, or -1 if a village is already too close. */
    static int build(ServerLevel level, ServerPlayer player) {
        BlockPos center = player.blockPosition();
        if (!VillageService.canFoundVillageAt(level, center, player)) {
            return -1;
        }
        int floorY = center.getY() - 1;
        clear(level, center, floorY);

        level.setBlock(center, ModBlocks.VILLAGE_CENTER.get().defaultBlockState(), Block.UPDATE_ALL);
        VillageService.onCenterPlaced(level, center, player);
        TestDecor.streets(level, center, floorY, WALL_RADIUS);

        List<BlockPos> markers = new ArrayList<>();
        houses(level, center, floorY, markers);
        storage(level, center, floorY, markers);
        TestWorkshops.build(level, center, floorY, markers);
        // M28b: work areas only by sign – sheep pasture, open stable, fenced quarry
        markers.add(areaMarker(level, center, floorY, TestWorkshops.SHEEP_AREA, BuildingType.PASTURE, MarkerSymbol.PASTURE_SHEEP));
        markers.add(areaMarker(level, center, floorY, TestWorkshops.STABLE_AREA, BuildingType.PASTURE, MarkerSymbol.PASTURE));
        int[] quarry = TestWorkshops.QUARRY_AREA;
        pasture(level, center.offset(quarry[0], 0, quarry[1]), center.offset(quarry[2], 0, quarry[3]), floorY, List.of());
        markers.add(areaMarker(level, center, floorY, quarry, BuildingType.QUARRY, MarkerSymbol.QUARRY));
        TestWall.build(level, center, WALL_RADIUS); // a wooden wall with a gate in the north
        TestDecor.finish(level, center, floorY);     // lamp posts and signposts

        for (BlockPos marker : markers) {
            BuildingType type = level.getBlockState(marker).getValue(BuildingMarkerBlock.TYPE);
            BuildingService.scanAndRegister(level, marker, type, player);
        }
        return VillageManager.get(level).findAt(center).map(v -> v.buildings().size()).orElse(0);
    }

    /** The residential quarter (west): two rows of four two-storey houses along the main street. */
    private static void houses(ServerLevel level, BlockPos center, int floorY, List<BlockPos> markers) {
        BlockState[] walls = {Blocks.OAK_PLANKS.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState(),
                Blocks.BRICKS.defaultBlockState(), Blocks.STONE_BRICKS.defaultBlockState(),
                Blocks.SPRUCE_PLANKS.defaultBlockState(), Blocks.SANDSTONE.defaultBlockState(),
                Blocks.BIRCH_PLANKS.defaultBlockState(), Blocks.MUD_BRICKS.defaultBlockState()};
        DyeColor[] beds = {DyeColor.RED, DyeColor.BLUE, DyeColor.YELLOW, DyeColor.GREEN,
                DyeColor.ORANGE, DyeColor.PURPLE, DyeColor.CYAN, DyeColor.PINK};
        int[] columns = {-14, -24, -34, -44};
        for (int i = 0; i < 8; i++) {
            boolean north = i < 4;
            BlockPos northWest = new BlockPos(center.getX() + columns[i % 4], floorY, center.getZ() + (north ? -12 : 6));
            TestSite site = north ? TestSite.facingSouth(level, northWest, 7) : new TestSite(level, northWest);
            site.twoStoreyHouse(walls[i], walls[i], beds[i]);
            site.door(3);
            markers.add(site.marker(3, 2, BuildingType.HOUSE));
            TestDecor.doorPath(site, 3);
        }
    }

    /** The storage right at the square (south side), door towards the square. */
    private static void storage(ServerLevel level, BlockPos center, int floorY, List<BlockPos> markers) {
        TestSite storage = new TestSite(level, new BlockPos(center.getX() - 4, floorY, center.getZ() + TestDecor.SQUARE + 4));
        storage.house(9, 5, 7, Blocks.STONE_BRICKS.defaultBlockState(), Blocks.STONE_BRICKS.defaultBlockState());
        storage.door(4);
        storage.chest(2, 4, new ItemStack(Items.BREAD, 64), new ItemStack(Items.BREAD, 64), new ItemStack(Items.BREAD, 64),
                new ItemStack(Items.BREAD, 64), new ItemStack(Items.OAK_SAPLING, 16), new ItemStack(Items.WHEAT_SEEDS, 32));
        storage.chest(6, 4, new ItemStack(Items.BREAD, 64), new ItemStack(Items.BREAD, 64), worn(Items.IRON_AXE),
                new ItemStack(Items.MUSIC_DISC_CAT), new ItemStack(Items.MUSIC_DISC_13), // M23: for the bard
                new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.IRON_AXE)); // M26: for the enchanter
        markers.add(storage.marker(4, 2, BuildingType.STORAGE));
        TestDecor.doorPath(storage, 4);
        TestDecor.nameSign(storage, 4, "villagelife.testvillage.sign.storage");
    }

    /** A fenced pasture (gate on the west side) with the given animals (M13c/M13d). */
    static void pasture(ServerLevel level, BlockPos min, BlockPos max, int floorY, List<EntityType<?>> animals) {
        List<BlockPos> fences = new ArrayList<>();
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                if (x == min.getX() || x == max.getX() || z == min.getZ() || z == max.getZ()) {
                    fences.add(new BlockPos(x, floorY + 1, z));
                }
            }
        }
        BlockPos gate = new BlockPos(min.getX(), floorY + 1, (min.getZ() + max.getZ()) / 2);
        for (BlockPos pos : fences) {
            level.setBlock(pos, pos.equals(gate)
                    ? Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.FACING, Direction.EAST)
                    : Blocks.OAK_FENCE.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        for (BlockPos pos : fences) { // connect the fence posts now that all of them stand
            level.setBlock(pos, Block.updateFromNeighbourShapes(level.getBlockState(pos), level, pos), Block.UPDATE_CLIENTS);
        }
        BlockPos middle = new BlockPos((min.getX() + max.getX()) / 2, floorY + 1, (min.getZ() + max.getZ()) / 2);
        for (int i = 0; i < animals.size(); i++) {
            animals.get(i).spawn(level, middle.offset(i - animals.size() / 2, 0, i % 2), EntitySpawnReason.COMMAND);
        }
    }

    /** A tool with only a fifth of its durability left, for the smith to repair (M14d). */
    private static ItemStack worn(Item tool) {
        ItemStack stack = new ItemStack(tool);
        stack.setDamageValue(stack.getMaxDamage() * 4 / 5);
        return stack;
    }

    /**
     * Rock with some coal and iron for the miner (M13e), level with the ground: the ground of the area
     * is turned into stone down to just above the bedrock (in a flat world only a few blocks).
     */
    static void rock(ServerLevel level, BlockPos min, BlockPos max, int floorY) {
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                for (int y = floorY; y > level.getMinY(); y--) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState old = level.getBlockState(pos);
                    if (old.isAir() || old.is(Blocks.BEDROCK)) {
                        continue;
                    }
                    int hash = Math.floorMod(x * 31 + y * 17 + z * 13, 23);
                    BlockState state = hash == 0 ? Blocks.COAL_ORE.defaultBlockState()
                            : hash == 1 ? Blocks.IRON_ORE.defaultBlockState() : Blocks.STONE.defaultBlockState();
                    level.setBlock(pos, state, Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    /**
     * A marker on the north fence of a fenced area (outside, facing north), like a player would hang the
     * sign (M28b: work areas only by sign); {@code symbol} picks the pasture's animal.
     */
    private static BlockPos areaMarker(ServerLevel level, BlockPos center, int floorY, int[] area, BuildingType type,
                                       MarkerSymbol symbol) {
        BlockPos pos = new BlockPos(center.getX() + area[0] + 1, floorY + 1, center.getZ() + area[1] - 1);
        level.setBlock(pos, ModBlocks.BUILDING_MARKER.get().defaultBlockState().setValue(BuildingMarkerBlock.TYPE, type)
                .setValue(BuildingMarkerBlock.FACING, Direction.NORTH).setValue(BuildingMarkerBlock.SYMBOL, symbol),
                Block.UPDATE_ALL);
        return pos;
    }

    /** Clears a circle above the ground and lays grass, so the village stands on flat land. */
    private static void clear(ServerLevel level, BlockPos center, int floorY) {
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = -CLEAR_RADIUS; x <= CLEAR_RADIUS; x++) {
            for (int z = -CLEAR_RADIUS; z <= CLEAR_RADIUS; z++) {
                if (x * x + z * z > CLEAR_RADIUS * CLEAR_RADIUS) {
                    continue;
                }
                pos.set(center.getX() + x, floorY, center.getZ() + z);
                level.setBlock(pos, grass, Block.UPDATE_CLIENTS);
                for (int y = 1; y <= CLEAR_HEIGHT; y++) {
                    pos.set(center.getX() + x, floorY + y, center.getZ() + z);
                    if (!level.getBlockState(pos).isAir()) {
                        level.setBlock(pos, air, Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
    }
}
