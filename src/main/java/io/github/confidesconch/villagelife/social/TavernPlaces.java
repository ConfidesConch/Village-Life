package io.github.confidesconch.villagelife.social;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import io.github.confidesconch.villagelife.building.Building;
import io.github.confidesconch.villagelife.building.BuildingService;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The places in a tavern (M21e, owner's choice "1": a bar evening): seats on bottom stairs and slabs
 * (chairs, benches) and standing places on the floor, and which guest has taken which. Every guest gets
 * a place of its own – seats first, then standing places with nobody right next to them. When all are
 * taken, the tavern is full and the others spend the evening elsewhere. Not saved (evenings are short).
 */
final class TavernPlaces {
    private TavernPlaces() {}

    /** {@code pos}: the stair/slab of a seat or the floor cell of a standing place; {@code yaw}: where a seated guest faces. */
    record Place(BlockPos pos, boolean seat, float yaw) {
        /** The air cell the guest walks to. */
        BlockPos target() {
            return seat ? pos.above() : pos;
        }
    }

    private record Layout(List<Place> places, long scannedAt) {}

    private record Taken(UUID guest, long until) {}

    /** The tavern's room is scanned again after this time (furniture may change). */
    private static final long RESCAN_TICKS = 1200;
    /** A guest who stops renewing its place loses it after this time. */
    private static final long HOLD_TICKS = 100;

    private static final Map<UUID, Layout> LAYOUTS = new HashMap<>();
    private static final Map<BlockPos, Taken> TAKEN = new HashMap<>();

    /** Forgets everything when the server stops. */
    static void clear() {
        LAYOUTS.clear();
        TAKEN.clear();
    }

    /**
     * Takes a free place for {@code guest}: a free seat if there is one, otherwise (unless {@code seatOnly})
     * a standing place with nobody right next to it; null if the tavern is full.
     */
    static Place claim(ServerLevel level, Building tavern, UUID guest, boolean seatOnly, RandomSource random) {
        long now = level.getGameTime();
        List<Place> places = layout(level, tavern, now).places();
        List<Place> seats = new ArrayList<>();
        List<Place> standing = new ArrayList<>();
        for (Place place : places) {
            if (isFree(place.pos(), guest, now)) {
                (place.seat() ? seats : standing).add(place);
            }
        }
        Place chosen = !seats.isEmpty() ? seats.get(random.nextInt(seats.size()))
                : seatOnly ? null : roomyStandingPlace(standing, guest, now, random);
        if (chosen != null) {
            TAKEN.put(chosen.pos(), new Taken(guest, now + HOLD_TICKS));
        }
        return chosen;
    }

    /** The guest is still there: keeps its place. */
    static void hold(Place place, UUID guest, long now) {
        TAKEN.put(place.pos(), new Taken(guest, now + HOLD_TICKS));
    }

    static void release(Place place, UUID guest) {
        Taken taken = TAKEN.get(place.pos());
        if (taken != null && taken.guest().equals(guest)) {
            TAKEN.remove(place.pos());
        }
    }

    private static boolean isFree(BlockPos pos, UUID guest, long now) {
        Taken taken = TAKEN.get(pos);
        return taken == null || taken.until() < now || taken.guest().equals(guest);
    }

    /** A free standing place without another guest standing right next to it (so they do not crowd). */
    private static Place roomyStandingPlace(List<Place> free, UUID guest, long now, RandomSource random) {
        List<Place> roomy = new ArrayList<>();
        for (Place place : free) {
            boolean crowded = false;
            for (BlockPos near : BlockPos.betweenClosed(place.pos().offset(-1, 0, -1), place.pos().offset(1, 0, 1))) {
                if (!near.equals(place.pos()) && !isFree(near.immutable(), guest, now)) {
                    crowded = true;
                    break;
                }
            }
            if (!crowded) {
                roomy.add(place);
            }
        }
        return roomy.isEmpty() ? null : roomy.get(random.nextInt(roomy.size()));
    }

    private static Layout layout(ServerLevel level, Building tavern, long now) {
        Layout layout = LAYOUTS.get(tavern.id());
        if (layout == null || now - layout.scannedAt() > RESCAN_TICKS) {
            layout = new Layout(scan(level, tavern), now);
            LAYOUTS.put(tavern.id(), layout);
            TAKEN.values().removeIf(taken -> taken.until() < now);
        }
        return layout;
    }

    /** Seats (an air cell above a bottom stair or slab) and standing places (air cell with headroom above a firm floor). */
    private static List<Place> scan(ServerLevel level, Building tavern) {
        List<BlockPos> air = BuildingService.interior(level, tavern);
        Set<BlockPos> airSet = new HashSet<>(air);
        List<Place> places = new ArrayList<>();
        for (BlockPos cell : air) {
            BlockPos below = cell.below();
            BlockState floor = level.getBlockState(below);
            if (isSeat(floor)) {
                places.add(new Place(below, true, seatYaw(level, below, floor)));
            } else if (airSet.contains(cell.above()) && isFloor(level, below, floor)) {
                places.add(new Place(cell, false, 0));
            }
        }
        return places;
    }

    /** Chairs and benches: stairs and slabs in their lower half. */
    private static boolean isSeat(BlockState state) {
        return state.getBlock() instanceof StairBlock && state.getValue(StairBlock.HALF) == Half.BOTTOM
                || state.getBlock() instanceof SlabBlock && state.getValue(SlabBlock.TYPE) == SlabType.BOTTOM;
    }

    /** A firm floor to stand on (also with a carpet), but not a table top, a fence or a bed. */
    private static boolean isFloor(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.is(BlockTags.WOOL_CARPETS)) {
            BlockPos under = pos.below();
            return level.getBlockState(under).isFaceSturdy(level, under, Direction.UP); // a carpet on a fence (table) is not
        }
        return state.isFaceSturdy(level, pos, Direction.UP) && !state.is(BlockTags.FENCES) && !state.is(BlockTags.WALLS)
                && !state.is(BlockTags.BEDS);
    }

    /** Facing a table next to the seat, otherwise the way a stair faces (its back behind the guest). */
    private static float seatYaw(ServerLevel level, BlockPos seat, BlockState state) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (isTable(level, seat.relative(side))) {
                return side.toYRot();
            }
        }
        if (state.getBlock() instanceof StairBlock) {
            return state.getValue(StairBlock.FACING).getOpposite().toYRot();
        }
        return 0;
    }

    /** A table: a fence or wall post, an upper slab, or any block with a carpet or pressure plate on top. */
    private static boolean isTable(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        BlockState top = level.getBlockState(pos.above());
        return state.is(BlockTags.FENCES) || state.is(BlockTags.WALLS)
                || state.getBlock() instanceof SlabBlock && state.getValue(SlabBlock.TYPE) == SlabType.TOP
                || !state.isAir() && (top.is(BlockTags.WOOL_CARPETS) || top.is(BlockTags.PRESSURE_PLATES));
    }
}
