package io.github.confidesconch.villagelife.building;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.github.confidesconch.villagelife.building.BuildingRequirement.Problem;
import io.github.confidesconch.villagelife.building.scan.AreaScanner;
import io.github.confidesconch.villagelife.building.scan.LevelAreaView;
import io.github.confidesconch.villagelife.core.config.SyncedConfig;
import io.github.confidesconch.villagelife.core.registry.ModBlocks;
import io.github.confidesconch.villagelife.village.Village;
import io.github.confidesconch.villagelife.village.VillageManager;
import io.github.confidesconch.villagelife.village.VillageZone;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Fields and pastures marked with a building marker (M21d, owner's wish "like TekTopia"): the fenced open
 * area around the marker is found by {@link AreaScanner} and stored as a building of type
 * {@link BuildingType#FIELD} or {@link BuildingType#PASTURE}. Its "volume" is the area in columns; the
 * largest area is {@code maxBuildingVolume} columns. Workers look the exact columns up again when they
 * plan their work, so the stored record only keeps the bounds.
 */
public final class AreaService {
    private AreaService() {}

    /** Problem key while part of the area is not loaded – the last result is kept then. */
    static final String UNLOADED = "villagelife.building.problem.unloaded";
    private static final String OPEN = "villagelife.building.problem.area_open";
    private static final String TOO_SMALL = "villagelife.building.problem.area_too_small";
    private static final String ALREADY_REGISTERED = "villagelife.building.problem.already_registered";

    /** Scans the area around the marker and returns the record to store (valid, or with the reason). */
    static Building build(ServerLevel level, BlockPos markerPos, BuildingType type, Optional<Building> previous) {
        int maxArea = SyncedConfig.MAX_BUILDING_VOLUME.getAsInt();
        AreaScanner.Result scan = scan(level, markerPos, maxArea);
        UUID id = previous.map(Building::id).orElseGet(UUID::randomUUID);
        long now = level.getGameTime();
        if (!scan.isSuccess()) {
            // keep the last known bounds so later changes nearby still trigger a rescan
            BlockPos min = previous.map(Building::min).orElse(markerPos);
            BlockPos max = previous.map(Building::max).orElse(markerPos);
            String reason = scan.failure() == AreaScanner.Failure.UNLOADED ? UNLOADED : OPEN;
            return new Building(id, type, markerPos, min, max, 0, List.of(), List.of(), List.of(), List.of(), now,
                    Optional.of(new Problem(reason, maxArea)));
        }
        // from the ground to one block above the highest ground: fences and gates lie within touches()
        BlockPos min = new BlockPos(scan.minX(), scan.minY(), scan.minZ());
        BlockPos max = new BlockPos(scan.maxX(), scan.maxY() + 1, scan.maxZ());
        int area = scan.columns().size();
        int required = type.requirement().minVolume();
        Optional<Problem> problem = registeredElsewhere(level, markerPos, min, max)
                ? Optional.of(new Problem(ALREADY_REGISTERED, 0))
                : area < required ? Optional.of(new Problem(TOO_SMALL, required)) : Optional.empty();
        return new Building(id, type, markerPos, min, max, area, List.of(), List.of(), List.of(), List.of(), now, problem);
    }

    /** The columns of a registered area as they are now; empty if it is open, unloaded or not an area. */
    public static List<AreaScanner.Column> columns(ServerLevel level, Building area) {
        if (!area.type().isArea() || !level.isLoaded(area.markerPos())) {
            return List.of();
        }
        AreaScanner.Result scan = scan(level, area.markerPos(), SyncedConfig.MAX_BUILDING_VOLUME.getAsInt());
        return scan.isSuccess() ? scan.columns() : List.of();
    }

    /** The valid area of {@code type} closest to {@code from}, if the village has one. */
    public static Optional<Building> nearest(Village village, BuildingType type, BlockPos from) {
        return village == null ? Optional.empty() : village.buildings().stream()
                .filter(b -> b.type() == type && b.isValid())
                .min(Comparator.comparingDouble(b -> b.center().distSqr(from)));
    }

    /** The area's bounds as a work zone (for herders, who think in rectangles). */
    public static VillageZone asZone(Building area) {
        return new VillageZone(-1, "", 0, area.min().getX(), area.min().getZ(), area.max().getX(), area.max().getZ());
    }

    /**
     * The marker may stand inside the area or hang on its fence, facing in or out. Tries the marker's own
     * column, then behind it and in front of it; the first enclosed area wins, otherwise the marker's own
     * result is reported.
     */
    private static AreaScanner.Result scan(ServerLevel level, BlockPos markerPos, int maxArea) {
        LevelAreaView view = new LevelAreaView(level);
        AreaScanner.Result first = AreaScanner.scan(view, markerPos.getX(), markerPos.getZ(), maxArea);
        if (first.isSuccess() || first.failure() == AreaScanner.Failure.UNLOADED) {
            return first;
        }
        BlockState marker = level.getBlockState(markerPos);
        if (!marker.is(ModBlocks.BUILDING_MARKER.get())) {
            return first;
        }
        Direction facing = marker.getValue(BuildingMarkerBlock.FACING);
        for (Direction side : List.of(facing.getOpposite(), facing)) {
            for (int step = 1; step <= 2; step++) {
                BlockPos start = markerPos.relative(side, step);
                AreaScanner.Result result = AreaScanner.scan(view, start.getX(), start.getZ(), maxArea);
                if (result.isSuccess() || result.failure() == AreaScanner.Failure.UNLOADED) {
                    return result;
                }
            }
        }
        return first;
    }

    /** True if another marker already registered exactly this area. */
    private static boolean registeredElsewhere(ServerLevel level, BlockPos markerPos, BlockPos min, BlockPos max) {
        return VillageManager.get(level).all().stream().flatMap(v -> v.buildings().stream())
                .anyMatch(b -> b.type().isArea() && !b.markerPos().equals(markerPos) && b.min().equals(min) && b.max().equals(max));
    }
}
