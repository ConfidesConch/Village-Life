package io.github.confidesconch.villagelife.building.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.confidesconch.villagelife.building.scan.RoomScanner.Cell;
import io.github.confidesconch.villagelife.building.scan.RoomScanner.Failure;

class RoomScannerTest {

    /** Simple test world: everything is air unless set solid; sky is visible above {@code roofY}. */
    private static final class FakeWorld implements RoomScanner.View {
        final Set<Cell> solid = new HashSet<>();
        int roofY = Integer.MAX_VALUE;
        int loadedLimit = Integer.MAX_VALUE;

        /** Hollow box with walls from (x1,y1,z1) to (x2,y2,z2) inclusive. */
        FakeWorld box(int x1, int y1, int z1, int x2, int y2, int z2) {
            for (int x = x1; x <= x2; x++)
                for (int y = y1; y <= y2; y++)
                    for (int z = z1; z <= z2; z++)
                        if (x == x1 || x == x2 || y == y1 || y == y2 || z == z1 || z == z2)
                            solid.add(new Cell(x, y, z));
            return this;
        }

        @Override public boolean isLoaded(int x, int y, int z) { return Math.abs(x) <= loadedLimit; }
        @Override public boolean isPassable(int x, int y, int z) { return !solid.contains(new Cell(x, y, z)); }
        @Override public boolean canSeeSky(int x, int y, int z) {
            // sky is visible if no solid block is above in this column (up to roofY)
            if (y >= roofY) return true;
            for (int above = y + 1; above < y + 64; above++) {
                if (solid.contains(new Cell(x, above, z))) return false;
            }
            return true;
        }
    }

    @Test
    void closedBoxIsRoomWithCorrectVolumeAndBounds() {
        FakeWorld world = new FakeWorld().box(0, 0, 0, 4, 4, 4); // inside 3x3x3
        RoomScanner.Result result = RoomScanner.scan(world, new Cell(2, 1, 2), 100);
        assertTrue(result.isSuccess());
        assertEquals(27, result.interior().size());
        assertEquals(new Cell(1, 1, 1), result.min());
        assertEquals(new Cell(3, 3, 3), result.max());
        // boundary = walls touching the room: 6 faces of 3x3
        assertEquals(54, result.boundary().size());
    }

    @Test
    void missingRoofIsDetected() {
        FakeWorld world = new FakeWorld().box(0, 0, 0, 4, 4, 4);
        world.solid.remove(new Cell(2, 4, 2)); // hole in the roof
        assertEquals(Failure.NO_ROOF, RoomScanner.scan(world, new Cell(2, 1, 2), 100).failure());
    }

    @Test
    void holeInWallLeaksOutAndExceedsVolume() {
        FakeWorld world = new FakeWorld().box(0, 0, 0, 4, 4, 4);
        world.solid.remove(new Cell(0, 1, 2)); // hole in the wall at floor level
        // build a ceiling over the outside so the leak is not caught by the sky check first
        for (int x = -20; x <= 20; x++)
            for (int z = -20; z <= 20; z++)
                world.solid.add(new Cell(x, 10, z));
        world.solid.add(new Cell(-1, 0, 2)); // ground outside is irrelevant: scan just keeps going
        assertEquals(Failure.NOT_ENCLOSED, RoomScanner.scan(world, new Cell(2, 1, 2), 200).failure());
    }

    @Test
    void roomLargerThanLimitIsRejected() {
        FakeWorld world = new FakeWorld().box(0, 0, 0, 11, 5, 11); // inside 10x4x10 = 400
        assertEquals(Failure.NOT_ENCLOSED, RoomScanner.scan(world, new Cell(5, 1, 5), 399).failure());
        assertTrue(RoomScanner.scan(world, new Cell(5, 1, 5), 400).isSuccess());
    }

    @Test
    void unloadedTerrainStopsTheScan() {
        FakeWorld world = new FakeWorld().box(0, 0, 0, 10, 4, 4);
        world.loadedLimit = 5;
        assertEquals(Failure.UNLOADED, RoomScanner.scan(world, new Cell(2, 1, 2), 1000).failure());
    }
}
