package io.github.confidesconch.villagelife.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.github.confidesconch.villagelife.building.BuildingMarkerBlock;
import io.github.confidesconch.villagelife.building.BuildingService;
import io.github.confidesconch.villagelife.building.BuildingType;
import io.github.confidesconch.villagelife.growth.GrowthService;
import io.github.confidesconch.villagelife.profession.JobService;
import io.github.confidesconch.villagelife.resident.ResidentService;
import io.github.confidesconch.villagelife.village.Village;
import io.github.confidesconch.villagelife.village.VillageManager;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Stress test (M21, owner's choice "1a"): the test village, raised to a city (radius 96), with an outer
 * quarter beyond its wall – houses with 12 beds each, a food store and as many workplaces (farms with fields,
 * bakeries, kitchens, smithies, carpentries, taverns, guardhouses) as are needed to give the residents
 * jobs – then filled with {@code count} residents. Real village life, to be measured with
 * {@code /villagelife debug perf}. Test worlds only.
 */
final class StressVillage {
    private StressVillage() {}

    /** Result: residents now living in the village and job places, or null if nothing could be built. */
    record Result(int residents, int places) {}

    private static final int PITCH = 13;
    private static final int SIZE = 11;
    private static final int INNER = 62;
    /** Sites end 2 blocks inside the city border (radius 96). */
    private static final int OUTER = 94;
    private static final int CLEAR_HEIGHT = 12;
    private static final int TEST_VILLAGE_BEDS = 112;
    private static final int BUNK_BEDS = 12;

    private enum Kind { FARM, BAKERY, KITCHEN, SMITHY, CARPENTRY, TAVERN, GUARDHOUSE }

    static Result build(ServerLevel level, ServerPlayer player, int count) {
        if (TestVillage.build(level, player) < 0) {
            return null;
        }
        BlockPos center = player.blockPosition();
        int floorY = center.getY() - 1;
        VillageManager manager = VillageManager.get(level);
        Optional<Village> found = manager.findAt(center);
        if (found.isEmpty()) {
            return null;
        }
        Village village = found.get();
        while (GrowthService.forceRise(level, village)) { // a city: radius 96, 150 residents and more
            village = manager.byId(village.id()).orElseThrow();
        }
        TestWall.southGate(level, center, TestVillage.WALL_RADIUS);
        List<BlockPos> slots = slots(level, center, floorY);
        List<BlockPos> markers = new ArrayList<>();
        for (int beds = TEST_VILLAGE_BEDS; beds < count && !slots.isEmpty(); beds += BUNK_BEDS) {
            markers.add(bunkhouse(new TestSite(level, slots.removeFirst())));
        }
        if (!slots.isEmpty()) {
            markers.add(store(new TestSite(level, slots.removeFirst())));
        }
        int needed = count - JobService.places(level, village).size();
        for (int i = 0; needed > 0 && !slots.isEmpty(); i++) {
            Kind kind = Kind.values()[i % Kind.values().length];
            markers.add(workplace(new TestSite(level, slots.removeFirst()), kind));
            needed -= kind == Kind.FARM ? 2 : 4;
        }
        for (BlockPos marker : markers) {
            BuildingType type = level.getBlockState(marker).getValue(BuildingMarkerBlock.TYPE);
            BuildingService.scanAndRegister(level, marker, type, player);
        }
        Village current = manager.byId(village.id()).orElseThrow();
        BlockPos spawn = center.offset(0, 0, 3);
        for (int i = current.residents().size(); i < count; i++) {
            ResidentService.spawn(level, spawn, manager.byId(village.id()).orElseThrow(), true);
        }
        Village filled = manager.byId(village.id()).orElseThrow();
        return new Result(filled.residents().size(), JobService.places(level, filled).size());
    }

    /** Free building sites in the ring between the wall and the city border; cleared and laid with grass. */
    private static List<BlockPos> slots(ServerLevel level, BlockPos center, int floorY) {
        List<BlockPos> slots = new ArrayList<>();
        for (int gx = -OUTER; gx <= OUTER; gx += PITCH) {
            for (int gz = -OUTER; gz <= OUTER; gz += PITCH) {
                if (inRing(gx, gz) && inRing(gx + SIZE, gz) && inRing(gx, gz + SIZE) && inRing(gx + SIZE, gz + SIZE)
                        && inRing(gx + SIZE / 2, gz - 1)) {
                    BlockPos origin = new BlockPos(center.getX() + gx, floorY, center.getZ() + gz);
                    clear(level, origin);
                    slots.add(origin);
                }
            }
        }
        return slots;
    }

    private static boolean inRing(int dx, int dz) {
        double distance = Math.sqrt((double) dx * dx + (double) dz * dz);
        return distance >= INNER && distance <= OUTER;
    }

    private static void clear(ServerLevel level, BlockPos origin) {
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = -1; x <= SIZE + 1; x++) {
            for (int z = -2; z <= SIZE + 1; z++) {
                level.setBlock(pos.set(origin.getX() + x, origin.getY(), origin.getZ() + z), Blocks.GRASS_BLOCK.defaultBlockState(),
                        Block.UPDATE_CLIENTS);
                for (int y = 1; y <= CLEAR_HEIGHT; y++) {
                    pos.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                    if (!level.getBlockState(pos).isAir()) {
                        level.setBlock(pos, air, Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
    }

    /** A house with 12 beds in two rows; every bed's foot end touches a walkway. */
    private static BlockPos bunkhouse(TestSite site) {
        site.house(SIZE, 5, SIZE, Blocks.OAK_PLANKS.defaultBlockState(), Blocks.SPRUCE_PLANKS.defaultBlockState());
        site.door(5);
        DyeColor[] colors = DyeColor.values();
        int n = 0;
        for (int z : new int[] {2, 6}) {
            for (int x : new int[] {1, 2, 3, 7, 8, 9}) {
                site.bed(x, z, colors[n++ % colors.length]);
            }
        }
        return site.marker(5, 5, BuildingType.HOUSE);
    }

    /** Food and materials for everybody. */
    private static BlockPos store(TestSite site) {
        site.house(SIZE, 5, SIZE, Blocks.STONE_BRICKS.defaultBlockState(), Blocks.STONE_BRICKS.defaultBlockState());
        site.door(5);
        Item[] goods = {Items.BREAD, Items.BREAD, Items.BREAD, Items.BREAD, Items.WHEAT, Items.RAW_IRON, Items.COAL,
                Items.OAK_LOG, Items.BEEF, Items.POTATO, Items.WHEAT_SEEDS, Items.ARROW};
        for (int i = 0; i < goods.length; i++) {
            ItemStack[] full = new ItemStack[27];
            for (int s = 0; s < full.length; s++) {
                full[s] = new ItemStack(goods[i], goods[i].getDefaultMaxStackSize());
            }
            site.chest(1 + (i % 4) * 2, 4 + (i / 4) * 2, full);
        }
        return site.marker(5, 5, BuildingType.STORAGE);
    }

    private static BlockPos workplace(TestSite site, Kind kind) {
        BlockState wall = switch (kind) {
            case FARM -> Blocks.OAK_PLANKS.defaultBlockState();
            case BAKERY -> Blocks.TERRACOTTA.defaultBlockState();
            case KITCHEN -> Blocks.BRICKS.defaultBlockState();
            case SMITHY -> Blocks.DEEPSLATE_BRICKS.defaultBlockState();
            case CARPENTRY -> Blocks.SPRUCE_PLANKS.defaultBlockState();
            case TAVERN -> Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState();
            case GUARDHOUSE -> Blocks.COBBLESTONE.defaultBlockState();
        };
        if (kind == Kind.FARM) { // a farmhouse (2 places) and its field
            site.house(SIZE, 5, 6, wall, wall);
            site.door(5);
            site.set(5, 1, 4, Blocks.COMPOSTER.defaultBlockState());
            site.chest(1, 4, new ItemStack(Items.WHEAT_SEEDS, 32));
            for (int x = 0; x < SIZE; x++) {
                for (int z = 7; z < SIZE + 1; z++) {
                    site.set(x, 0, z, x == 5 && z == 9 ? Blocks.WATER.defaultBlockState() : Blocks.FARMLAND.defaultBlockState());
                }
            }
            return site.marker(5, 3, BuildingType.WORKPLACE);
        }
        site.house(SIZE, 5, SIZE, wall, wall); // 9 × 9 inside: 4 places
        site.door(5);
        switch (kind) {
            case BAKERY -> site.set(5, 1, 8, Blocks.FURNACE.defaultBlockState());
            case KITCHEN -> site.set(5, 1, 8, Blocks.SMOKER.defaultBlockState());
            case SMITHY -> {
                site.set(5, 1, 8, Blocks.SMITHING_TABLE.defaultBlockState());
                site.set(7, 1, 8, Blocks.BLAST_FURNACE.defaultBlockState());
            }
            case CARPENTRY -> site.set(5, 1, 8, Blocks.FLETCHING_TABLE.defaultBlockState());
            case TAVERN -> site.set(5, 1, 8, Blocks.BREWING_STAND.defaultBlockState());
            case GUARDHOUSE -> { // defenders: one place per bed
                site.set(5, 1, 8, Blocks.GRINDSTONE.defaultBlockState());
                site.bed(1, 2, DyeColor.GRAY);
                site.bed(1, 6, DyeColor.GRAY);
                site.bed(9, 2, DyeColor.GRAY);
                site.bed(9, 6, DyeColor.GRAY);
            }
            default -> { }
        }
        return site.marker(5, 5, BuildingType.WORKPLACE);
    }
}
