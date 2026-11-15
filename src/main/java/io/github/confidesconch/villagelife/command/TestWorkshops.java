package io.github.confidesconch.villagelife.command;

import java.util.List;

import io.github.confidesconch.villagelife.building.BuildingType;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The workplaces of the test village (new layout 15.11.2026, owner's wish): all professions on the east
 * side in four rows along the main street and the back lanes (doors towards the street, 3 blocks between
 * the buildings), the lumberjack and the shepherd at the edge of the residential west side next to their
 * forest and pasture, and the defenders at the north gate. Every workplace gets a path to the street and
 * a name sign. Coordinates relative to the village center.
 */
final class TestWorkshops {
    private TestWorkshops() {}

    /** Plot columns on the east side (west edge of each 7-wide building). */
    private static final int[] EAST = {7, 17, 27, 37};
    /** North edges of the four rows: A (main street, north), B (main street, south), C/D (back lanes). */
    private static final int ROW_A = -12;
    private static final int ROW_B = 6;
    private static final int ROW_C = -26;
    private static final int ROW_D = 20;

    /** Work areas (x1, z1, x2, z2 relative to the center): sheep pasture, stable, quarry. */
    static final int[] SHEEP_AREA = {-44, -32, -20, -22};
    static final int[] STABLE_AREA = {17, 29, 31, 36};
    static final int[] QUARRY_AREA = {24, -42, 34, -30};

    /** Markers of the buildings whose work area is a pasture or a quarry (assigned after registering). */
    record Areas(BlockPos shepherd, BlockPos breeder, BlockPos miner) {}

    private record Ctx(ServerLevel level, BlockPos center, int floorY, List<BlockPos> markers) {
        /** A 7 × 7 (or larger) plot; doors face south for the rows north of the street. */
        TestSite plot(int x, int zNorth, int depth, boolean doorSouth) {
            BlockPos northWest = new BlockPos(center.getX() + x, floorY, center.getZ() + zNorth);
            return doorSouth ? TestSite.facingSouth(level, northWest, depth) : new TestSite(level, northWest);
        }

        TestSite workshop(int x, int zNorth, boolean doorSouth, BlockState walls, BlockState roof) {
            TestSite site = plot(x, zNorth, 7, doorSouth);
            site.house(7, 5, 7, walls, roof);
            site.door(3);
            return site;
        }

        /** Marker over the door, path to the street, name sign; returns the marker. */
        BlockPos finish(TestSite site, int doorX, String profession) {
            BlockPos marker = site.marker(3, 3, BuildingType.WORKPLACE);
            markers.add(marker);
            TestDecor.doorPath(site, doorX);
            TestDecor.nameSign(site, doorX, "villagelife.workplace." + profession);
            return marker;
        }

        BlockPos at(int x, int z) {
            return center.offset(x, 0, z);
        }
    }

    static Areas build(ServerLevel level, BlockPos center, int floorY, List<BlockPos> markers) {
        Ctx c = new Ctx(level, center, floorY, markers);
        rowA(c);
        rowB(c);
        rowC(c);
        BlockPos breeder = rowD(c);
        BlockPos shepherd = westEdge(c);
        defense(c);
        TestSite mine = c.workshop(EAST[2], ROW_C, true, Blocks.COBBLESTONE.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState());
        mine.set(5, 1, 5, Blocks.STONECUTTER.defaultBlockState());
        mine.chest(1, 5, new ItemStack(Items.IRON_PICKAXE)); // a better pickaxe to fetch
        BlockPos miner = c.finish(mine, 3, "miner");
        TestVillage.rock(level, c.at(QUARRY_AREA[0], QUARRY_AREA[1]), c.at(QUARRY_AREA[2], QUARRY_AREA[3]), floorY);
        return new Areas(shepherd, breeder, miner);
    }

    /** Row A, north of the main street: market, storekeeper, smith, baker. */
    private static void rowA(Ctx c) {
        TestSite market = c.workshop(EAST[0], ROW_A, true, Blocks.ACACIA_PLANKS.defaultBlockState(), Blocks.ACACIA_PLANKS.defaultBlockState());
        market.set(5, 1, 5, Blocks.CARTOGRAPHY_TABLE.defaultBlockState());
        market.chest(1, 5);
        c.finish(market, 3, "merchant");
        TestSite office = c.workshop(EAST[1], ROW_A, true, Blocks.POLISHED_ANDESITE.defaultBlockState(), Blocks.POLISHED_ANDESITE.defaultBlockState());
        office.set(5, 1, 5, Blocks.LECTERN.defaultBlockState());
        office.chest(1, 5);
        c.finish(office, 3, "storekeeper");
        TestSite smithy = c.workshop(EAST[2], ROW_A, true, Blocks.DEEPSLATE_BRICKS.defaultBlockState(), Blocks.DEEPSLATE_BRICKS.defaultBlockState());
        smithy.chimney(1, 5, 5);
        smithy.set(5, 1, 5, Blocks.SMITHING_TABLE.defaultBlockState());
        smithy.set(1, 1, 5, Blocks.BLAST_FURNACE.defaultBlockState());
        smithy.chest(5, 2, new ItemStack(Items.RAW_IRON, 16), new ItemStack(Items.COAL, 8), new ItemStack(Items.STICK, 8),
                new ItemStack(Items.DIAMOND, 3)); // M28a: one diamond pickaxe
        c.finish(smithy, 3, "smith");
        TestSite bakery = c.workshop(EAST[3], ROW_A, true, Blocks.TERRACOTTA.defaultBlockState(), Blocks.TERRACOTTA.defaultBlockState());
        bakery.chimney(5, 5, 5);
        bakery.set(5, 1, 5, Blocks.FURNACE.defaultBlockState());
        bakery.chest(1, 5, new ItemStack(Items.WHEAT, 32), new ItemStack(Items.COCOA_BEANS, 4), new ItemStack(Items.PUMPKIN, 2),
                new ItemStack(Items.SUGAR, 2), new ItemStack(Items.EGG, 2));
        c.finish(bakery, 3, "baker");
    }

    /** Row B, south of the main street: tavern, kitchen, carpenter, music room. */
    private static void rowB(Ctx c) {
        TestSite tavern = c.workshop(EAST[0], ROW_B, false, Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState(), Blocks.SPRUCE_PLANKS.defaultBlockState());
        tavern.set(5, 1, 5, Blocks.BREWING_STAND.defaultBlockState());
        tavern.chest(1, 5, new ItemStack(Items.BREAD, 16));
        c.finish(tavern, 3, "innkeeper");
        TestSite kitchen = c.workshop(EAST[1], ROW_B, false, Blocks.BRICKS.defaultBlockState(), Blocks.BRICKS.defaultBlockState());
        kitchen.chimney(5, 5, 5);
        kitchen.set(5, 1, 5, Blocks.SMOKER.defaultBlockState());
        kitchen.chest(1, 5, new ItemStack(Items.BEEF, 16), new ItemStack(Items.COD, 16), new ItemStack(Items.POTATO, 16),
                new ItemStack(Items.COAL, 4));
        c.finish(kitchen, 3, "cook");
        TestSite carpentry = c.workshop(EAST[2], ROW_B, false, Blocks.STRIPPED_OAK_LOG.defaultBlockState(), Blocks.OAK_PLANKS.defaultBlockState());
        carpentry.set(5, 1, 5, Blocks.FLETCHING_TABLE.defaultBlockState());
        carpentry.chest(1, 5, new ItemStack(Items.OAK_LOG, 16), new ItemStack(Items.WOOL.pick(DyeColor.WHITE), 6));
        c.finish(carpentry, 3, "carpenter");
        TestSite music = c.workshop(EAST[3], ROW_B, false, Blocks.CHERRY_PLANKS.defaultBlockState(), Blocks.CHERRY_PLANKS.defaultBlockState());
        music.set(5, 1, 5, Blocks.JUKEBOX.defaultBlockState());
        music.chest(1, 5, new ItemStack(Items.MUSIC_DISC_BLOCKS));
        c.finish(music, 3, "bard"); // M23
    }

    /** Row C, north back lane: enchanter, healer, (miner, built in {@link #build}), school. */
    private static void rowC(Ctx c) {
        TestSite enchanting = c.workshop(EAST[0], ROW_C, true, Blocks.DEEPSLATE_BRICKS.defaultBlockState(), Blocks.DARK_OAK_PLANKS.defaultBlockState());
        enchanting.set(3, 1, 3, Blocks.ENCHANTING_TABLE.defaultBlockState());
        for (int[] shelf : new int[][]{{1, 1}, {5, 1}, {1, 3}, {5, 3}, {1, 5}, {3, 5}, {5, 5}}) { // 7 shelves: strength II
            enchanting.set(shelf[0], 1, shelf[1], Blocks.BOOKSHELF.defaultBlockState());
        }
        c.finish(enchanting, 3, "enchanter"); // M26
        TestSite infirmary = c.workshop(EAST[1], ROW_C, true, Blocks.DYED_TERRACOTTA.pick(DyeColor.WHITE).defaultBlockState(),
                Blocks.QUARTZ_BLOCK.defaultBlockState());
        infirmary.set(5, 1, 5, Blocks.WATER_CAULDRON.defaultBlockState());
        c.finish(infirmary, 3, "healer"); // M24
        TestSite school = c.workshop(EAST[3], ROW_C, true, Blocks.STRIPPED_BIRCH_LOG.defaultBlockState(), Blocks.BIRCH_PLANKS.defaultBlockState());
        school.set(3, 1, 5, Blocks.BOOKSHELF.defaultBlockState());
        c.finish(school, 3, "teacher"); // M18c
    }

    /** Row D, south back lane: farmer with field, breeder with stable, druid, fisherman with pond; butcher behind the storage. */
    private static BlockPos rowD(Ctx c) {
        TestSite farm = c.workshop(EAST[0], ROW_D, false, Blocks.OAK_PLANKS.defaultBlockState(), Blocks.OAK_PLANKS.defaultBlockState());
        farm.set(5, 1, 5, Blocks.COMPOSTER.defaultBlockState());
        farm.chest(1, 5, new ItemStack(Items.WHEAT_SEEDS, 32));
        c.finish(farm, 3, "farmer");
        TestSite field = new TestSite(c.level(), c.at(EAST[0] - 1, 29).atY(c.floorY()));
        for (int x = 0; x < 9; x++) {
            for (int z = 0; z < 9; z++) {
                field.set(x, 0, z, x == 4 && z == 4 ? Blocks.WATER.defaultBlockState() : Blocks.FARMLAND.defaultBlockState());
            }
        }
        TestSite breeder = c.workshop(EAST[1], ROW_D, false, Blocks.SPRUCE_PLANKS.defaultBlockState(), Blocks.SPRUCE_PLANKS.defaultBlockState());
        breeder.set(5, 1, 5, Blocks.HAY_BLOCK.defaultBlockState());
        breeder.chest(1, 5, new ItemStack(Items.WHEAT, 16), new ItemStack(Items.CARROT, 16),
                new ItemStack(Items.WHEAT_SEEDS, 16), new ItemStack(Items.BUCKET, 4));
        BlockPos breederMarker = c.finish(breeder, 3, "breeder");
        TestVillage.pasture(c.level(), c.at(STABLE_AREA[0], STABLE_AREA[1]), c.at(STABLE_AREA[2], STABLE_AREA[3]), c.floorY(),
                TestVillage.STABLE_ANIMALS);
        TestSite grove = c.workshop(EAST[2], ROW_D, false, Blocks.MOSSY_COBBLESTONE.defaultBlockState(), Blocks.MOSS_BLOCK.defaultBlockState());
        grove.set(5, 0, 5, Blocks.ROOTED_DIRT.defaultBlockState()); // azaleas need soil
        grove.set(5, 1, 5, Blocks.FLOWERING_AZALEA.defaultBlockState());
        c.finish(grove, 3, "druid"); // M25
        TestSite butcher = c.workshop(-3, ROW_D, false, Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState(), Blocks.DARK_OAK_PLANKS.defaultBlockState());
        butcher.set(5, 1, 5, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false)); // unlit: nobody gets burnt
        butcher.chest(1, 5);
        c.finish(butcher, 3, "butcher"); // M27, behind the storage
        TestSite fisher = c.workshop(EAST[3], ROW_D, false, Blocks.BIRCH_PLANKS.defaultBlockState(), Blocks.BIRCH_PLANKS.defaultBlockState());
        fisher.set(5, 1, 5, Blocks.BARREL.defaultBlockState()); // workstation and storage at once
        c.finish(fisher, 3, "fisherman");
        TestSite pond = new TestSite(c.level(), c.at(EAST[3] - 1, 29).atY(c.floorY()));
        for (int x = 0; x < 7; x++) {
            for (int z = 0; z < 7; z++) {
                pond.set(x, -1, z, Blocks.WATER.defaultBlockState());
                pond.set(x, 0, z, Blocks.WATER.defaultBlockState());
            }
        }
        return breederMarker;
    }

    /** Lumberjack and shepherd at the outer edge of the west side, next to their forest and pasture. */
    private static BlockPos westEdge(Ctx c) {
        TestSite lumberjack = c.workshop(-14, ROW_D, false, Blocks.SPRUCE_PLANKS.defaultBlockState(), Blocks.SPRUCE_PLANKS.defaultBlockState());
        lumberjack.set(4, 1, 4, Blocks.CRAFTING_TABLE.defaultBlockState());
        lumberjack.chest(5, 4, new ItemStack(Items.OAK_SAPLING, 16));
        c.finish(lumberjack, 3, "lumberjack");
        TestSite forest = new TestSite(c.level(), c.at(-42, 22).atY(c.floorY()));
        for (int x = 0; x <= 15; x += 5) {
            for (int z = 0; z <= 15; z += 5) {
                if (z < 15 || x >= 10) { // the far corner would touch the wall
                    forest.tree(x, z);
                }
            }
        }
        TestSite shepherd = c.workshop(-14, ROW_C, true, Blocks.DARK_OAK_PLANKS.defaultBlockState(), Blocks.DARK_OAK_PLANKS.defaultBlockState());
        shepherd.set(5, 1, 5, Blocks.LOOM.defaultBlockState());
        shepherd.chest(1, 5, new ItemStack(Items.WHEAT, 16));
        BlockPos marker = c.finish(shepherd, 3, "shepherd");
        TestVillage.pasture(c.level(), c.at(SHEEP_AREA[0], SHEEP_AREA[1]), c.at(SHEEP_AREA[2], SHEEP_AREA[3]), c.floorY(),
                TestVillage.SHEEP);
        return marker;
    }

    /** At the north gate: guardhouse (2 guards), the captain's post, the shooting range (2 archers). */
    private static void defense(Ctx c) {
        TestSite guardhouse = c.plot(-14, -44, 9, true);
        guardhouse.house(9, 5, 9, Blocks.STONE_BRICKS.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState());
        guardhouse.door(4);
        guardhouse.bed(1, 2, DyeColor.GRAY);
        guardhouse.bed(7, 2, DyeColor.GRAY);
        guardhouse.set(4, 1, 7, Blocks.GRINDSTONE.defaultBlockState());
        guardhouse.chest(1, 7, new ItemStack(Items.IRON_SWORD, 2), new ItemStack(Items.IRON_CHESTPLATE),
                new ItemStack(Items.IRON_CHESTPLATE), new ItemStack(Items.IRON_HELMET), new ItemStack(Items.IRON_HELMET));
        c.finish(guardhouse, 4, "guard");
        TestSite post = c.workshop(4, -44, true, Blocks.DEEPSLATE_BRICKS.defaultBlockState(), Blocks.DEEPSLATE_TILES.defaultBlockState());
        post.set(3, 1, 5, Blocks.BELL.defaultBlockState());
        c.finish(post, 3, "captain");
        TestSite range = c.plot(-28, -45, 9, true);
        range.house(9, 5, 9, Blocks.SPRUCE_PLANKS.defaultBlockState(), Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState());
        range.door(4);
        range.set(7, 1, 7, Blocks.TARGET.defaultBlockState());
        range.bed(1, 2, DyeColor.GREEN); // defenders: one place per bed (owner's wish)
        range.bed(7, 2, DyeColor.GREEN);
        range.chest(1, 7, new ItemStack(Items.ARROW, 64), new ItemStack(Items.ARROW, 64));
        c.finish(range, 4, "archer");
    }
}
