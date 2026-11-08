package io.github.confidesconch.villagelife.building.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

class AreaScannerTest {

    /** Flat ground at height 64 everywhere; fences and raised columns can be added. */
    private static final class FakeView implements AreaScanner.View {
        final Set<Long> fences = new HashSet<>();
        final Map<Long, Integer> heights = new HashMap<>();
        int loadedRadius = 1000;

        static long key(int x, int z) {
            return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
        }

        FakeView fence(int x, int z) {
            fences.add(key(x, z));
            return this;
        }

        /** A fence ring around the rectangle (x0..x1, z0..z1) – the inside is that rectangle. */
        FakeView ring(int x0, int z0, int x1, int z1) {
            for (int x = x0 - 1; x <= x1 + 1; x++) {
                fence(x, z0 - 1).fence(x, z1 + 1);
            }
            for (int z = z0 - 1; z <= z1 + 1; z++) {
                fence(x0 - 1, z).fence(x1 + 1, z);
            }
            return this;
        }

        @Override
        public boolean isLoaded(int x, int z) {
            return Math.abs(x) <= loadedRadius && Math.abs(z) <= loadedRadius;
        }

        @Override
        public int surface(int x, int z) {
            return heights.getOrDefault(key(x, z), 64);
        }

        @Override
        public boolean isBorder(int x, int z) {
            return fences.contains(key(x, z));
        }
    }

    @Test
    void fencedRectangleIsFound() {
        FakeView view = new FakeView().ring(0, 0, 9, 4);
        AreaScanner.Result result = AreaScanner.scan(view, 3, 2, 1000);
        assertTrue(result.isSuccess());
        assertEquals(50, result.columns().size());
        assertEquals(0, result.minX());
        assertEquals(9, result.maxX());
        assertEquals(4, result.maxZ());
        assertEquals(64, result.minY());
    }

    @Test
    void gapInTheFenceMakesItOpen() {
        FakeView view = new FakeView().ring(0, 0, 9, 4);
        view.fences.remove(FakeView.key(10, 2)); // one fence post missing
        AreaScanner.Result result = AreaScanner.scan(view, 3, 2, 1000);
        assertEquals(AreaScanner.Failure.NOT_ENCLOSED, result.failure());
    }

    @Test
    void notchInTheFenceBelongsToTheArea() {
        // a square with one fence post pushed outwards: the notch belongs to the area
        FakeView view = new FakeView();
        for (int i = -1; i <= 6; i++) {
            view.fence(i, -1); // north
            view.fence(-1, i); // west
            view.fence(i, 6); // south
        }
        for (int z = -1; z <= 6; z++) {
            if (z != 1) {
                view.fence(6, z); // east side, but (6,1) moved outwards ...
            }
        }
        view.fence(7, 1).fence(6, 0).fence(6, 2); // ... a notch, closed again
        AreaScanner.Result result = AreaScanner.scan(view, 2, 2, 1000);
        assertTrue(result.isSuccess());
        assertEquals(37, result.columns().size()); // 6x6 plus the notch column (6,1)
    }

    @Test
    void houseWallsAndCliffsAreBordersToo() {
        FakeView view = new FakeView().ring(0, 0, 4, 4);
        for (int z = 0; z <= 4; z++) {
            view.heights.put(FakeView.key(4, z), 70); // a house wall along one side
        }
        AreaScanner.Result result = AreaScanner.scan(view, 1, 1, 1000);
        assertTrue(result.isSuccess());
        assertEquals(20, result.columns().size());
    }

    @Test
    void gentleSlopeStaysInside() {
        FakeView view = new FakeView().ring(0, 0, 4, 0);
        for (int x = 0; x <= 4; x++) {
            view.heights.put(FakeView.key(x, 0), 64 + x); // one block per column, like a hillside field
        }
        AreaScanner.Result result = AreaScanner.scan(view, 0, 0, 1000);
        assertTrue(result.isSuccess());
        assertEquals(5, result.columns().size());
        assertEquals(68, result.maxY());
    }

    @Test
    void tooLargeOrUnloadedOrOnTheFence() {
        FakeView view = new FakeView().ring(0, 0, 9, 9);
        assertEquals(AreaScanner.Failure.NOT_ENCLOSED, AreaScanner.scan(view, 3, 3, 99).failure());
        assertTrue(AreaScanner.scan(view, 3, 3, 100).isSuccess());
        assertEquals(AreaScanner.Failure.ON_BORDER, AreaScanner.scan(view, -1, 3, 1000).failure());
        view.loadedRadius = 5;
        assertEquals(AreaScanner.Failure.UNLOADED, AreaScanner.scan(view, 3, 3, 1000).failure());
    }
}
