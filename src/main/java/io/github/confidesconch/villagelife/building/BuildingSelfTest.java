package io.github.confidesconch.villagelife.building;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import io.github.confidesconch.villagelife.core.config.SyncedConfig;
import io.github.confidesconch.villagelife.core.registry.ModBlocks;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * In-game regression test for the room scan ({@code /villagelife debug buildtests}).
 * Builds a row of test houses with known edge cases next to the player, evaluates each one and
 * reports PASS/FAIL. The structures stay in the world so they can be inspected.
 * Overwrites blocks in the area – meant for test worlds only.
 */
public final class BuildingSelfTest {
    private BuildingSelfTest() {}

    private record Case(String name, int width, int height, int depth, Consumer<Builder> extra, Expectation expected) {}

    private enum Expectation { VALID, NO_ROOF, INVALID_ANY, NOT_ENCLOSED }

    /** Places blocks for one test house; origin = lower north-west corner of the outer walls. */
    private static final class Builder {
        final ServerLevel level;
        final BlockPos origin;
        final int w, h, d;

        Builder(ServerLevel level, BlockPos origin, int w, int h, int d) {
            this.level = level;
            this.origin = origin;
            this.w = w;
            this.h = h;
            this.d = d;
        }

        BlockPos at(int x, int y, int z) {
            return origin.offset(x, y, z);
        }

        void set(int x, int y, int z, BlockState state) {
            // UPDATE_CLIENTS only: no neighbor updates, so half-built doors/beds do not pop off
            level.setBlock(at(x, y, z), state, Block.UPDATE_CLIENTS);
        }

        void clear() {
            for (int x = -1; x <= w; x++)
                for (int y = 0; y <= h + 3; y++)
                    for (int z = -1; z <= d; z++)
                        set(x, y, z, Blocks.AIR.defaultBlockState());
        }

        void box(BlockState walls, BlockState roof) {
            for (int x = 0; x < w; x++)
                for (int y = 0; y < h; y++)
                    for (int z = 0; z < d; z++) {
                        boolean shell = x == 0 || x == w - 1 || z == 0 || z == d - 1 || y == 0;
                        if (y == h - 1) {
                            set(x, y, z, roof);
                        } else if (shell) {
                            set(x, y, z, walls);
                        }
                    }
        }

        void door() {
            BlockState door = Blocks.OAK_DOOR.defaultBlockState();
            set(2, 1, 0, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
            set(2, 2, 0, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        }

        void bed() {
            // bed faces north by default: head is one block north (z - 1) of the foot
            BlockState bed = Blocks.BED.pick(DyeColor.RED).defaultBlockState();
            set(w - 2, 1, d - 2, bed.setValue(AbstractBedBlock.PART, BedPart.FOOT));
            set(w - 2, 1, d - 3, bed.setValue(AbstractBedBlock.PART, BedPart.HEAD));
        }

        BlockPos marker() {
            set(2, 1, 2, ModBlocks.BUILDING_MARKER.get().defaultBlockState());
            return at(2, 1, 2);
        }
    }

    public static List<Component> run(ServerLevel level, BlockPos start) {
        int maxVolume = SyncedConfig.MAX_BUILDING_VOLUME.getAsInt();
        // big enough that the inside (side² x 3 high) exceeds the configured maximum
        int bigSide = Math.min(110, (int) Math.ceil(Math.sqrt(maxVolume / 3.0)) + 3);
        BlockState stone = Blocks.STONE.defaultBlockState();

        List<Case> cases = List.of(
                new Case("basic_house", 7, 5, 7, b -> {}, Expectation.VALID),
                new Case("glass_roof", 7, 5, 7, b -> {
                    for (int x = 0; x < 7; x++) for (int z = 0; z < 7; z++) b.set(x, 4, z, Blocks.GLASS.defaultBlockState());
                }, Expectation.VALID),
                new Case("pane_window_and_fence_wall", 7, 5, 7, b -> {
                    b.set(0, 2, 3, Blocks.GLASS_PANE.defaultBlockState());
                    b.set(6, 2, 3, Blocks.OAK_FENCE.defaultBlockState());
                }, Expectation.VALID),
                new Case("cellar_under_dirt", 7, 5, 7, b -> {
                    for (int x = 0; x < 7; x++) for (int z = 0; z < 7; z++) {
                        b.set(x, 5, z, Blocks.DIRT.defaultBlockState());
                        b.set(x, 6, z, Blocks.GRASS_BLOCK.defaultBlockState());
                    }
                }, Expectation.VALID),
                new Case("two_floors_with_stair_hole", 7, 9, 7, b -> {
                    for (int x = 1; x < 6; x++) for (int z = 1; z < 6; z++) b.set(x, 4, z, stone);
                    b.set(4, 4, 4, Blocks.AIR.defaultBlockState());
                }, Expectation.VALID),
                new Case("missing_roof_block", 7, 5, 7, b -> b.set(3, 4, 3, Blocks.AIR.defaultBlockState()),
                        Expectation.NO_ROOF),
                new Case("hole_in_wall", 7, 5, 7, b -> b.set(0, 1, 4, Blocks.AIR.defaultBlockState()),
                        Expectation.INVALID_ANY),
                new Case("too_big_hall", bigSide, 5, bigSide, b -> {}, Expectation.NOT_ENCLOSED)
        );

        List<Component> report = new ArrayList<>();
        int passed = 0;
        int offsetX = 0;
        for (Case c : cases) {
            Builder b = new Builder(level, start.offset(offsetX, 0, 0), c.width(), c.height(), c.depth());
            b.clear();
            b.box(stone, stone);
            b.door();
            b.bed();
            c.extra().accept(b);
            BlockPos marker = b.marker();
            BuildingService.Evaluation eval = BuildingService.evaluate(level, marker, BuildingType.HOUSE);
            boolean ok = switch (c.expected()) {
                case VALID -> eval.isValid();
                case INVALID_ANY -> !eval.isValid();
                case NO_ROOF -> hasReason(eval, "villagelife.building.problem.no_roof");
                case NOT_ENCLOSED -> hasReason(eval, "villagelife.building.problem.not_enclosed");
            };
            Component actual = eval.isValid()
                    ? Component.translatable("villagelife.debug.buildtest.valid", eval.volume())
                    : BuildingService.describe(eval.problem().get());
            report.add(Component.translatable(ok ? "villagelife.debug.buildtest.pass" : "villagelife.debug.buildtest.fail",
                            c.name(), marker.toShortString(), actual)
                    .withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED));
            if (ok) {
                passed++;
            }
            offsetX += c.width() + 3;
        }
        report.add(Component.translatable("villagelife.debug.buildtest.summary", passed, cases.size())
                .withStyle(passed == cases.size() ? ChatFormatting.GREEN : ChatFormatting.RED));
        return report;
    }

    private static boolean hasReason(BuildingService.Evaluation eval, String key) {
        return eval.problem().map(p -> p.reasonKey().equals(key)).orElse(false);
    }
}
