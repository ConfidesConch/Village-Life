package io.github.confidesconch.villagelife.building;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;

/**
 * A registered building inside a village. Valid when {@code problem} is empty; invalid buildings
 * stay registered (with the reason) so the automatic rescan can report when they become valid again.
 * Positions are absolute world positions. See docs/DATA_MODEL.md.
 */
public record Building(
        UUID id,
        BuildingType type,
        BlockPos markerPos,
        BlockPos min,
        BlockPos max,
        int interiorVolume,
        List<BlockPos> beds,
        List<BlockPos> doors,
        List<BlockPos> containers,
        List<BlockPos> workstations,
        long scannedGameTime,
        Optional<BuildingRequirement.Problem> problem) {

    private static final Codec<List<BlockPos>> POSITIONS = BlockPos.CODEC.listOf();

    public static final Codec<Building> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(Building::id),
            BuildingType.CODEC.fieldOf("type").forGetter(Building::type),
            BlockPos.CODEC.fieldOf("marker").forGetter(Building::markerPos),
            BlockPos.CODEC.fieldOf("min").forGetter(Building::min),
            BlockPos.CODEC.fieldOf("max").forGetter(Building::max),
            Codec.INT.fieldOf("interior_volume").forGetter(Building::interiorVolume),
            POSITIONS.optionalFieldOf("beds", List.of()).forGetter(Building::beds),
            POSITIONS.optionalFieldOf("doors", List.of()).forGetter(Building::doors),
            POSITIONS.optionalFieldOf("containers", List.of()).forGetter(Building::containers),
            POSITIONS.optionalFieldOf("workstations", List.of()).forGetter(Building::workstations),
            Codec.LONG.fieldOf("scanned_game_time").forGetter(Building::scannedGameTime),
            // optional: buildings saved by M3a were always valid
            BuildingRequirement.Problem.CODEC.optionalFieldOf("problem").forGetter(Building::problem)
    ).apply(instance, Building::new));

    public Building {
        markerPos = markerPos.immutable();
        beds = List.copyOf(beds);
        doors = List.copyOf(doors);
        containers = List.copyOf(containers);
        workstations = List.copyOf(workstations);
    }

    /**
     * Middle of the room at floor height (first air layer) – the anchor for work radius, quarry and
     * pen height (M18d: the marker may now hang outside on the wall, higher up).
     */
    public BlockPos center() {
        return new BlockPos((min.getX() + max.getX()) / 2, min.getY(), (min.getZ() + max.getZ()) / 2);
    }

    public boolean isValid() {
        return problem.isEmpty();
    }

    /** True if {@code pos} lies within the building's bounds grown by one block (walls, roof, floor). */
    public boolean touches(BlockPos pos) {
        return pos.getX() >= min.getX() - 1 && pos.getX() <= max.getX() + 1
                && pos.getY() >= min.getY() - 1 && pos.getY() <= max.getY() + 1
                && pos.getZ() >= min.getZ() - 1 && pos.getZ() <= max.getZ() + 1;
    }
}
