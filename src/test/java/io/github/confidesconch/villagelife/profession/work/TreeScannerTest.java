package io.github.confidesconch.villagelife.profession.work;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.github.confidesconch.villagelife.profession.work.TreeScanner.Pos;
import io.github.confidesconch.villagelife.profession.work.TreeScanner.Tree;

import org.junit.jupiter.api.Test;

class TreeScannerTest {

    /** A tiny fake world: ground (dirt) at y = 63, everything else as placed. */
    private static final class FakeWorld implements TreeScanner.View {
        final Map<Pos, Character> blocks = new HashMap<>(); // L = log, N = natural leaves, P = placed leaves, S = stone
        Set<Pos> forbidden = Set.of();

        void set(int x, int y, int z, char c) {
            blocks.put(new Pos(x, y, z), c);
        }

        char at(int x, int y, int z) {
            if (y <= 63) {
                return blocks.getOrDefault(new Pos(x, y, z), 'D');
            }
            return blocks.getOrDefault(new Pos(x, y, z), ' ');
        }

        @Override
        public int surfaceY(int x, int z) {
            for (int y = 100; y > 0; y--) {
                char c = at(x, y, z);
                if (c != ' ' && c != 'N' && c != 'P') {
                    return y + 1;
                }
            }
            return 0;
        }

        @Override
        public boolean isLog(int x, int y, int z) {
            return at(x, y, z) == 'L';
        }

        @Override
        public boolean isSoil(int x, int y, int z) {
            return at(x, y, z) == 'D';
        }

        @Override
        public boolean isNaturalLeaves(int x, int y, int z) {
            return at(x, y, z) == 'N';
        }

        @Override
        public boolean isAllowed(int x, int y, int z) {
            return !forbidden.contains(new Pos(x, y, z));
        }

        void tree(int x, int z, int height, char leaves) {
            for (int y = 64; y < 64 + height; y++) {
                set(x, y, z, 'L');
            }
            set(x, 64 + height, z, leaves);
            set(x + 1, 63 + height, z, leaves);
        }
    }

    @Test
    void findsNearestNaturalTreeAndChopsTopFirst() {
        FakeWorld world = new FakeWorld();
        world.tree(10, 0, 5, 'N');
        world.tree(3, 0, 4, 'N');
        Tree tree = TreeScanner.findNearest(world, 0, 0, 16, Set.of()).orElseThrow();
        assertEquals(new Pos(3, 64, 0), tree.base());
        assertEquals(4, tree.logs().size());
        assertEquals(67, tree.logs().getFirst().y());
        assertEquals(64, tree.logs().getLast().y());
    }

    @Test
    void ignoresLogsWithoutNaturalLeaves() {
        FakeWorld world = new FakeWorld();
        world.tree(2, 2, 4, 'P'); // player-placed leaves: decoration
        world.set(5, 64, 5, 'L'); // a single log pillar
        world.set(5, 65, 5, 'L');
        assertTrue(TreeScanner.findNearest(world, 0, 0, 16, Set.of()).isEmpty());
    }

    @Test
    void ignoresLogsNotStandingOnSoil() {
        FakeWorld world = new FakeWorld();
        world.set(2, 63, 2, 'S');
        world.tree(2, 2, 4, 'N');
        assertTrue(TreeScanner.findNearest(world, 0, 0, 16, Set.of()).isEmpty());
    }

    @Test
    void respectsForbiddenAreasAndIgnoredBases() {
        FakeWorld world = new FakeWorld();
        world.tree(2, 0, 4, 'N');
        world.tree(8, 0, 4, 'N');
        world.forbidden = Set.of(new Pos(2, 66, 0)); // one log inside a building
        Optional<Tree> tree = TreeScanner.findNearest(world, 0, 0, 16, Set.of());
        assertEquals(new Pos(8, 64, 0), tree.orElseThrow().base());
        assertTrue(TreeScanner.findNearest(world, 0, 0, 16, Set.of(new Pos(8, 64, 0))).isEmpty());
    }

    @Test
    void ignoresTreesOutsideTheRadius() {
        FakeWorld world = new FakeWorld();
        world.tree(20, 0, 4, 'N');
        assertTrue(TreeScanner.findNearest(world, 0, 0, 16, Set.of()).isEmpty());
    }

    @Test
    void finishesAPartlyChoppedTreeWithoutLeaves() {
        FakeWorld world = new FakeWorld();
        world.set(4, 64, 4, 'L'); // stump left after the leaves decayed
        world.set(4, 65, 4, 'L');
        assertTrue(TreeScanner.findNearest(world, 0, 0, 16, Set.of()).isEmpty());
        Tree stump = TreeScanner.treeAt(world, new Pos(4, 64, 4), false).orElseThrow();
        assertEquals(2, stump.logs().size());
        assertTrue(TreeScanner.treeAt(world, new Pos(5, 64, 5), false).isEmpty()); // no log there
    }

    @Test
    void collectsLeavesOfChoppedTreeButNotOfNeighbours() {
        FakeWorld world = new FakeWorld();
        // chopped tree at x=0: logs are gone, crown remains
        world.set(0, 68, 0, 'N');
        world.set(1, 67, 0, 'N');
        world.set(2, 67, 0, 'N');
        world.set(3, 67, 0, 'N'); // touches the neighbour's log at x=4
        world.set(4, 67, 0, 'L');
        world.set(0, 69, 0, 'P'); // player-placed leaves stay
        List<Pos> chopped = List.of(new Pos(0, 64, 0), new Pos(0, 65, 0), new Pos(0, 66, 0), new Pos(0, 67, 0));
        List<Pos> leaves = TreeScanner.leavesOf(world, chopped);
        assertTrue(leaves.contains(new Pos(0, 68, 0)));
        assertTrue(leaves.contains(new Pos(2, 67, 0)));
        assertTrue(!leaves.contains(new Pos(3, 67, 0)));
        assertTrue(!leaves.contains(new Pos(0, 69, 0)));
        assertEquals(3, leaves.size());
    }

    @Test
    void followsBranches() {
        FakeWorld world = new FakeWorld();
        world.tree(0, 0, 5, 'N');
        world.set(1, 67, 1, 'L'); // diagonal branch (acacia-like)
        world.set(2, 68, 2, 'L');
        Tree tree = TreeScanner.findNearest(world, 0, 0, 8, Set.of()).orElseThrow();
        assertEquals(7, tree.logs().size());
    }
}
