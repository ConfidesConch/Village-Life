package io.github.confidesconch.villagelife.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.github.confidesconch.villagelife.building.BuildingMarkerBlock;
import io.github.confidesconch.villagelife.building.BuildingService;
import io.github.confidesconch.villagelife.building.BuildingType;
import io.github.confidesconch.villagelife.core.registry.ModBlocks;
import io.github.confidesconch.villagelife.village.Village;
import io.github.confidesconch.villagelife.village.VillageManager;
import io.github.confidesconch.villagelife.village.VillageService;
import io.github.confidesconch.villagelife.village.VillageZone;

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
 * {@code /villagelife debug testvillage} (M12a): builds a complete test village around the player on flat
 * ground – village center, houses, a dormitory with 100 beds, a storage and one workplace for every
 * profession incl. a market, tavern, school, music room, infirmary and grove (with fields, pond, pastures, rock as needed), a
 * wooden wall with a gate (M20b2) –
 * and registers every building. Clears the area
 * first: meant for test worlds only.
 */
final class TestVillage {
    private TestVillage() {}

    /** Area that is cleared, relative to the village center. */
    private static final int MIN_X = -50;
    private static final int MAX_X = 42;
    private static final int MIN_Z = -44;
    private static final int MAX_Z = 36;
    private static final int CLEAR_HEIGHT = 12;
    private static final int DORM_BEDS = 100;
    /** The wooden wall (M20b2): inside the village radius (64), outside all buildings. */
    static final int WALL_RADIUS = 58;

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

        List<BlockPos> houses = new ArrayList<>();
        List<BlockPos> others = new ArrayList<>();
        BlockState[] walls = {Blocks.OAK_PLANKS.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState(),
                Blocks.BRICKS.defaultBlockState(), Blocks.STONE_BRICKS.defaultBlockState(),
                Blocks.SPRUCE_PLANKS.defaultBlockState(), Blocks.SANDSTONE.defaultBlockState()};
        for (int i = 0; i < walls.length; i++) {
            TestSite site = new TestSite(level, new BlockPos(center.getX() - 48 + i * 10, floorY, center.getZ() - 24));
            site.house(7, 5, 7, walls[i], walls[i]);
            site.door(2);
            site.bed(1, 4, DyeColor.RED);
            site.bed(5, 4, DyeColor.BLUE);
            houses.add(site.marker(3, 2, BuildingType.HOUSE));
        }
        houses.add(dormitory(new TestSite(level, new BlockPos(center.getX() - 13, floorY, center.getZ() + 8))));

        TestSite storage = new TestSite(level, new BlockPos(center.getX() + 14, floorY, center.getZ() - 24));
        storage.house(9, 5, 7, Blocks.STONE_BRICKS.defaultBlockState(), Blocks.STONE_BRICKS.defaultBlockState());
        storage.door(2);
        storage.chest(5, 4, new ItemStack(Items.BREAD, 64), new ItemStack(Items.BREAD, 64), new ItemStack(Items.BREAD, 64),
                new ItemStack(Items.BREAD, 64), new ItemStack(Items.OAK_SAPLING, 16), new ItemStack(Items.WHEAT_SEEDS, 32));
        storage.chest(6, 4, new ItemStack(Items.BREAD, 64), new ItemStack(Items.BREAD, 64), worn(Items.IRON_AXE),
                new ItemStack(Items.MUSIC_DISC_CAT), new ItemStack(Items.MUSIC_DISC_13)); // M23: for the bard
        others.add(storage.marker(3, 2, BuildingType.STORAGE));

        TestSite lumberjack = new TestSite(level, new BlockPos(center.getX() - 40, floorY, center.getZ() + 2));
        lumberjack.house(7, 5, 7, Blocks.SPRUCE_PLANKS.defaultBlockState(), Blocks.SPRUCE_PLANKS.defaultBlockState());
        lumberjack.door(2);
        lumberjack.set(4, 1, 4, Blocks.CRAFTING_TABLE.defaultBlockState());
        lumberjack.chest(5, 4, new ItemStack(Items.OAK_SAPLING, 16));
        others.add(lumberjack.marker(2, 2, BuildingType.WORKPLACE));
        TestSite forest = new TestSite(level, new BlockPos(center.getX() - 46, floorY, center.getZ() + 12));
        for (int x = 0; x <= 15; x += 5) {
            for (int z = 0; z <= 15; z += 5) {
                forest.tree(x, z);
            }
        }

        TestSite farm = new TestSite(level, new BlockPos(center.getX() + 24, floorY, center.getZ() + 2));
        farm.house(7, 5, 7, Blocks.OAK_PLANKS.defaultBlockState(), Blocks.OAK_PLANKS.defaultBlockState());
        farm.door(2);
        farm.set(5, 1, 1, Blocks.COMPOSTER.defaultBlockState());
        farm.chest(5, 2, new ItemStack(Items.WHEAT_SEEDS, 32));
        others.add(farm.marker(3, 5, BuildingType.WORKPLACE)); // south side: the whole field is within 12 blocks
        TestSite field = new TestSite(level, new BlockPos(center.getX() + 23, floorY, center.getZ() + 10));
        for (int x = 0; x < 9; x++) {
            for (int z = 0; z < 9; z++) {
                field.set(x, 0, z, x == 4 && z == 4 ? Blocks.WATER.defaultBlockState() : Blocks.FARMLAND.defaultBlockState());
            }
        }

        TestSite fisher = new TestSite(level, new BlockPos(center.getX() + 34, floorY, center.getZ() + 2));
        fisher.house(7, 5, 7, Blocks.BIRCH_PLANKS.defaultBlockState(), Blocks.BIRCH_PLANKS.defaultBlockState());
        fisher.door(2);
        fisher.set(5, 1, 5, Blocks.BARREL.defaultBlockState()); // workstation and storage at once
        others.add(fisher.marker(3, 5, BuildingType.WORKPLACE));
        TestSite pond = new TestSite(level, new BlockPos(center.getX() + 33, floorY, center.getZ() + 12));
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 8; z++) {
                pond.set(x, -1, z, Blocks.WATER.defaultBlockState());
                pond.set(x, 0, z, Blocks.WATER.defaultBlockState());
            }
        }

        TestSite shepherd = new TestSite(level, new BlockPos(center.getX() + 18, floorY, center.getZ() + 27));
        shepherd.house(7, 5, 7, Blocks.DARK_OAK_PLANKS.defaultBlockState(), Blocks.DARK_OAK_PLANKS.defaultBlockState());
        shepherd.door(3);
        shepherd.set(5, 1, 5, Blocks.LOOM.defaultBlockState());
        shepherd.chest(1, 5, new ItemStack(Items.WHEAT, 16));
        BlockPos shepherdMarker = shepherd.marker(3, 3, BuildingType.WORKPLACE);
        others.add(shepherdMarker);
        BlockPos pastureMin = center.offset(27, 0, 23);
        BlockPos pastureMax = center.offset(39, 0, 34);
        pasture(level, pastureMin, pastureMax, floorY, List.of(EntityTypes.SHEEP, EntityTypes.SHEEP, EntityTypes.SHEEP, EntityTypes.SHEEP));

        TestSite breeder = new TestSite(level, new BlockPos(center.getX() - 19, floorY, center.getZ() + 28));
        breeder.house(7, 5, 7, Blocks.SPRUCE_PLANKS.defaultBlockState(), Blocks.SPRUCE_PLANKS.defaultBlockState());
        breeder.door(3);
        breeder.set(5, 1, 5, Blocks.HAY_BLOCK.defaultBlockState());
        breeder.chest(1, 5, new ItemStack(Items.WHEAT, 16), new ItemStack(Items.CARROT, 16),
                new ItemStack(Items.WHEAT_SEEDS, 16), new ItemStack(Items.BUCKET, 4));
        BlockPos breederMarker = breeder.marker(3, 3, BuildingType.WORKPLACE);
        others.add(breederMarker);
        BlockPos stableMin = center.offset(-10, 0, 28);
        BlockPos stableMax = center.offset(6, 0, 35);
        pasture(level, stableMin, stableMax, floorY, List.of(EntityTypes.COW, EntityTypes.COW, EntityTypes.PIG,
                EntityTypes.PIG, EntityTypes.CHICKEN, EntityTypes.CHICKEN, EntityTypes.CHICKEN));

        TestSite miner = new TestSite(level, new BlockPos(center.getX() + 25, floorY, center.getZ() - 14));
        miner.house(7, 5, 7, Blocks.COBBLESTONE.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState());
        miner.door(3);
        miner.set(5, 1, 5, Blocks.STONECUTTER.defaultBlockState());
        miner.chest(1, 5, new ItemStack(Items.IRON_PICKAXE)); // a better pickaxe to fetch
        BlockPos minerMarker = miner.marker(3, 3, BuildingType.WORKPLACE);
        others.add(minerMarker);
        BlockPos quarryMin = center.offset(33, 0, -16);
        BlockPos quarryMax = center.offset(42, 0, -5);
        rock(level, quarryMin, quarryMax, floorY);

        TestSite kitchen = new TestSite(level, new BlockPos(center.getX() - 24, floorY, center.getZ() - 12));
        kitchen.house(7, 5, 7, Blocks.BRICKS.defaultBlockState(), Blocks.BRICKS.defaultBlockState());
        kitchen.door(3);
        kitchen.chimney(5, 5, 5);
        kitchen.set(5, 1, 5, Blocks.SMOKER.defaultBlockState());
        kitchen.chest(1, 5, new ItemStack(Items.BEEF, 16), new ItemStack(Items.COD, 16), new ItemStack(Items.POTATO, 16),
                new ItemStack(Items.COAL, 4));
        others.add(kitchen.marker(3, 3, BuildingType.WORKPLACE));

        TestSite smithy = new TestSite(level, new BlockPos(center.getX() - 33, floorY, center.getZ() - 12));
        smithy.house(7, 5, 7, Blocks.DEEPSLATE_BRICKS.defaultBlockState(), Blocks.DEEPSLATE_BRICKS.defaultBlockState());
        smithy.door(3);
        smithy.chimney(1, 5, 5);
        smithy.set(5, 1, 5, Blocks.SMITHING_TABLE.defaultBlockState());
        smithy.set(1, 1, 5, Blocks.BLAST_FURNACE.defaultBlockState());
        smithy.chest(5, 2, new ItemStack(Items.RAW_IRON, 16), new ItemStack(Items.COAL, 8), new ItemStack(Items.STICK, 8));
        others.add(smithy.marker(3, 3, BuildingType.WORKPLACE));

        TestSite bakery = new TestSite(level, new BlockPos(center.getX() - 42, floorY, center.getZ() - 12));
        bakery.house(7, 5, 7, Blocks.TERRACOTTA.defaultBlockState(), Blocks.TERRACOTTA.defaultBlockState());
        bakery.door(3);
        bakery.chimney(5, 5, 5);
        bakery.set(5, 1, 5, Blocks.FURNACE.defaultBlockState());
        bakery.chest(1, 5, new ItemStack(Items.WHEAT, 32), new ItemStack(Items.COCOA_BEANS, 4), new ItemStack(Items.PUMPKIN, 2),
                new ItemStack(Items.SUGAR, 2), new ItemStack(Items.EGG, 2));
        others.add(bakery.marker(3, 3, BuildingType.WORKPLACE));

        TestSite carpentry = new TestSite(level, new BlockPos(center.getX() - 50, floorY, center.getZ() - 4));
        carpentry.house(7, 5, 7, Blocks.STRIPPED_OAK_LOG.defaultBlockState(), Blocks.OAK_PLANKS.defaultBlockState());
        carpentry.door(3);
        carpentry.set(5, 1, 5, Blocks.FLETCHING_TABLE.defaultBlockState());
        carpentry.chest(1, 5, new ItemStack(Items.OAK_LOG, 16), new ItemStack(Items.WOOL.pick(DyeColor.WHITE), 6));
        others.add(carpentry.marker(3, 3, BuildingType.WORKPLACE));

        TestSite office = new TestSite(level, new BlockPos(center.getX() + 25, floorY, center.getZ() - 27));
        office.house(7, 5, 7, Blocks.POLISHED_ANDESITE.defaultBlockState(), Blocks.POLISHED_ANDESITE.defaultBlockState());
        office.door(3);
        office.set(5, 1, 5, Blocks.LECTERN.defaultBlockState());
        office.chest(1, 5);
        others.add(office.marker(3, 3, BuildingType.WORKPLACE));

        TestSite market = new TestSite(level, new BlockPos(center.getX() + 14, floorY, center.getZ() - 12));
        market.house(7, 5, 7, Blocks.ACACIA_PLANKS.defaultBlockState(), Blocks.ACACIA_PLANKS.defaultBlockState());
        market.door(3);
        market.set(5, 1, 5, Blocks.CARTOGRAPHY_TABLE.defaultBlockState());
        market.chest(1, 5);
        others.add(market.marker(3, 3, BuildingType.WORKPLACE));

        TestSite tavern = new TestSite(level, new BlockPos(center.getX() + 3, floorY, center.getZ() - 13));
        tavern.house(7, 5, 7, Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState(), Blocks.SPRUCE_PLANKS.defaultBlockState());
        tavern.door(3);
        tavern.set(5, 1, 5, Blocks.BREWING_STAND.defaultBlockState());
        tavern.chest(1, 5, new ItemStack(Items.BREAD, 16));
        others.add(tavern.marker(3, 3, BuildingType.WORKPLACE)); // M17b
        TestSite school = new TestSite(level, new BlockPos(center.getX() - 13, floorY, center.getZ() - 13));
        school.house(7, 5, 7, Blocks.STRIPPED_BIRCH_LOG.defaultBlockState(), Blocks.BIRCH_PLANKS.defaultBlockState());
        school.door(3);
        school.set(3, 1, 5, Blocks.BOOKSHELF.defaultBlockState());
        others.add(school.marker(1, 3, BuildingType.WORKPLACE)); // M18c
        TestSite musicRoom = new TestSite(level, new BlockPos(center.getX() + 15, floorY, center.getZ() + 2));
        musicRoom.house(7, 5, 7, Blocks.CHERRY_PLANKS.defaultBlockState(), Blocks.CHERRY_PLANKS.defaultBlockState());
        musicRoom.door(3);
        musicRoom.set(5, 1, 5, Blocks.JUKEBOX.defaultBlockState());
        musicRoom.chest(1, 5, new ItemStack(Items.MUSIC_DISC_BLOCKS));
        others.add(musicRoom.marker(3, 3, BuildingType.WORKPLACE)); // M23: bard
        TestSite infirmary = new TestSite(level, new BlockPos(center.getX() - 20, floorY, center.getZ() + 2));
        infirmary.house(7, 5, 7, Blocks.DYED_TERRACOTTA.pick(DyeColor.WHITE).defaultBlockState(), Blocks.QUARTZ_BLOCK.defaultBlockState());
        infirmary.door(3);
        infirmary.set(5, 1, 5, Blocks.WATER_CAULDRON.defaultBlockState());
        others.add(infirmary.marker(3, 3, BuildingType.WORKPLACE)); // M24: healer
        TestSite grove = new TestSite(level, new BlockPos(center.getX() + 15, floorY, center.getZ() + 11));
        grove.house(7, 5, 7, Blocks.MOSSY_COBBLESTONE.defaultBlockState(), Blocks.MOSS_BLOCK.defaultBlockState());
        grove.door(3);
        grove.set(5, 0, 5, Blocks.ROOTED_DIRT.defaultBlockState()); // azaleas need soil
        grove.set(5, 1, 5, Blocks.FLOWERING_AZALEA.defaultBlockState());
        others.add(grove.marker(3, 3, BuildingType.WORKPLACE)); // M25: druid
        // M20a/M20b2: defense – guardhouse with 2 beds (= 2 guards) and the captain's post at the north gate,
        // shooting range with 2 beds (= 2 archers)
        TestSite guardhouse = new TestSite(level, new BlockPos(center.getX() - 14, floorY, center.getZ() - 42));
        guardhouse.house(9, 5, 9, Blocks.STONE_BRICKS.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState());
        guardhouse.door(4);
        guardhouse.bed(1, 2, DyeColor.GRAY);
        guardhouse.bed(7, 2, DyeColor.GRAY);
        guardhouse.set(4, 1, 7, Blocks.GRINDSTONE.defaultBlockState());
        guardhouse.chest(1, 7, new ItemStack(Items.IRON_SWORD, 2), new ItemStack(Items.IRON_CHESTPLATE),
                new ItemStack(Items.IRON_CHESTPLATE), new ItemStack(Items.IRON_HELMET), new ItemStack(Items.IRON_HELMET));
        others.add(guardhouse.marker(4, 4, BuildingType.WORKPLACE));
        TestSite post = new TestSite(level, new BlockPos(center.getX() + 5, floorY, center.getZ() - 42));
        post.house(7, 5, 7, Blocks.DEEPSLATE_BRICKS.defaultBlockState(), Blocks.DEEPSLATE_TILES.defaultBlockState());
        post.door(3);
        post.set(3, 1, 5, Blocks.BELL.defaultBlockState());
        others.add(post.marker(3, 3, BuildingType.WORKPLACE));
        TestSite range = new TestSite(level, new BlockPos(center.getX() - 30, floorY, center.getZ() + 2));
        range.house(9, 5, 9, Blocks.SPRUCE_PLANKS.defaultBlockState(), Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState());
        range.door(4);
        range.set(7, 1, 7, Blocks.TARGET.defaultBlockState());
        range.bed(1, 2, DyeColor.GREEN); // defenders: one place per bed (owner's wish)
        range.bed(7, 2, DyeColor.GREEN);
        range.chest(1, 7, new ItemStack(Items.ARROW, 64), new ItemStack(Items.ARROW, 64));
        others.add(range.marker(4, 4, BuildingType.WORKPLACE));
        TestWall.build(level, center, WALL_RADIUS); // a wooden wall with a gate in the north
        TestDecor.build(level, center, floorY, WALL_RADIUS); // market square, lamp posts, stalls, paths

        List<BlockPos> all = new ArrayList<>(houses);
        all.addAll(others);
        for (BlockPos marker : all) {
            BuildingType type = level.getBlockState(marker).getValue(BuildingMarkerBlock.TYPE);
            BuildingService.scanAndRegister(level, marker, type, player);
        }
        assignPasture(level, center, shepherdMarker, "Weide", 2, pastureMin, pastureMax);
        assignPasture(level, center, breederMarker, "Stall", 6, stableMin, stableMax);
        assignPasture(level, center, minerMarker, "Grube", 8, quarryMin, quarryMax);
        return VillageManager.get(level).findAt(center).map(v -> v.buildings().size()).orElse(0);
    }

    /** A fenced pasture (gate on the west side) with the given animals (M13c/M13d). */
    private static void pasture(ServerLevel level, BlockPos min, BlockPos max, int floorY, List<EntityType<?>> animals) {
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
    private static void rock(ServerLevel level, BlockPos min, BlockPos max, int floorY) {
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

    /** Draws the pasture as a zone on the village map and makes it the work area of the building at {@code marker}. */
    private static void assignPasture(ServerLevel level, BlockPos center, BlockPos marker, String name, int color,
                                      BlockPos min, BlockPos max) {
        VillageManager manager = VillageManager.get(level);
        manager.findAt(center).ifPresent(village -> village.buildingAt(marker).ifPresent(building ->
                VillageZone.add(village.zones(), village.area(), name, color, min.getX(), min.getZ(), max.getX(), max.getZ(), 32)
                        .ifPresent(zones -> {
                            Village withZone = village.withZones(zones);
                            int zoneId = zones.getLast().id();
                            manager.update(withZone.withWorkArea(building.id(), Optional.of(zoneId)));
                        })));
    }

    /**
     * A hall with {@value #DORM_BEDS} beds: rows of two bed columns with an aisle between them, so
     * every bed can be reached; three doors on the north side.
     */
    static BlockPos dormitory(TestSite site) {
        int width = 27;
        int depth = 18;
        site.house(width, 5, depth, Blocks.STONE_BRICKS.defaultBlockState(), Blocks.STONE_BRICKS.defaultBlockState());
        site.door(4);
        site.door(13);
        site.door(22);
        int beds = 0;
        DyeColor[] colors = DyeColor.values();
        for (int x = 1; x < width - 1 && beds < DORM_BEDS; x++) {
            if ((x - 1) % 3 == 0) {
                continue; // aisle
            }
            for (int z = 2; z + 1 < depth - 2 && beds < DORM_BEDS; z += 2) {
                site.bed(x, z, colors[beds % colors.length]);
                beds++;
            }
        }
        return site.marker(1, 1, BuildingType.HOUSE);
    }

    /** Clears the area above the ground and lays grass, so the village stands on flat land. */
    private static void clear(ServerLevel level, BlockPos center, int floorY) {
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = MIN_X; x <= MAX_X; x++) {
            for (int z = MIN_Z; z <= MAX_Z; z++) {
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
