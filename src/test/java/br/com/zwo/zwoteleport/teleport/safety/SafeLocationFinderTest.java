package br.com.zwo.zwoteleport.teleport.safety;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SafeLocationFinderTest {

    /** Grade 16x16 com chão de pedra em y=63 e ar acima, salvo blocos marcados. */
    private static final class Grid implements BlockGrid {
        private final Map<String, BlockKind> blocks = new HashMap<>();

        Grid set(int x, int y, int z, BlockKind kind) {
            blocks.put(x + "," + y + "," + z, kind);
            return this;
        }

        @Override
        public boolean contains(int x, int z) {
            return x >= 0 && x < 16 && z >= 0 && z < 16;
        }

        @Override
        public int minY() {
            return -64;
        }

        @Override
        public int maxY() {
            return 320;
        }

        @Override
        public BlockKind kind(int x, int y, int z) {
            BlockKind kind = blocks.get(x + "," + y + "," + z);
            if (kind != null) {
                return kind;
            }
            return y <= 63 ? BlockKind.SOLID : BlockKind.PASSABLE;
        }
    }

    @Test
    public void groundWithTwoFreeBlocksIsSafe() {
        assertTrue(SafeLocationFinder.isSafe(new Grid(), 5, 64, 5, false));
    }

    @Test
    public void floatingInAirIsNotSafe() {
        assertFalse(SafeLocationFinder.isSafe(new Grid(), 5, 70, 5, false));
    }

    @Test
    public void insideBlockIsNotSafe() {
        assertFalse(SafeLocationFinder.isSafe(new Grid(), 5, 63, 5, false));
    }

    @Test
    public void lavaBelowOrAtFeetIsNotSafe() {
        assertFalse(SafeLocationFinder.isSafe(new Grid().set(5, 63, 5, BlockKind.HAZARD), 5, 64, 5, true));
        assertFalse(SafeLocationFinder.isSafe(new Grid().set(5, 64, 5, BlockKind.HAZARD), 5, 64, 5, true));
        assertFalse(SafeLocationFinder.isSafe(new Grid().set(5, 65, 5, BlockKind.HAZARD), 5, 64, 5, true));
    }

    @Test
    public void waterDependsOnPolicy() {
        Grid grid = new Grid().set(5, 64, 5, BlockKind.WATER).set(5, 65, 5, BlockKind.WATER);
        assertTrue(SafeLocationFinder.isSafe(grid, 5, 64, 5, true));
        assertFalse(SafeLocationFinder.isSafe(grid, 5, 64, 5, false));
    }

    @Test
    public void adjustFindsTheGroundBelowAFloatingTarget() {
        Optional<SafeLocationFinder.Position> found = SafeLocationFinder.findNearest(new Grid(), 5, 66, 5, 2, 6, true);
        assertEquals(new SafeLocationFinder.Position(5, 64, 5), found.orElseThrow());
    }

    @Test
    public void adjustStepsOutOfLavaToTheClosestSafeColumn() {
        Grid grid = new Grid().set(5, 64, 5, BlockKind.HAZARD);
        SafeLocationFinder.Position found = SafeLocationFinder.findNearest(grid, 5, 64, 5, 2, 6, true).orElseThrow();
        int distance = Math.abs(found.x() - 5) + Math.abs(found.z() - 5);
        assertEquals(1, distance);
        assertEquals(64, found.y());
    }

    @Test
    public void adjustRespectsTheGridBounds() {
        Grid grid = new Grid();
        for (int dy = -6; dy <= 6; dy++) {
            grid.set(0, 64 + dy, 0, BlockKind.HAZARD);
        }
        SafeLocationFinder.Position found = SafeLocationFinder.findNearest(grid, 0, 64, 0, 1, 6, true).orElseThrow();
        assertTrue(found.x() >= 0 && found.z() >= 0);
    }

    @Test
    public void adjustGivesUpWhenNothingIsSafe() {
        Grid grid = new Grid();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 55; y <= 75; y++) {
                    grid.set(x, y, z, BlockKind.HAZARD);
                }
            }
        }
        assertFalse(SafeLocationFinder.findNearest(grid, 8, 64, 8, 2, 6, true).isPresent());
    }

    @Test
    public void offsetsStartAtTheOriginAndPreferUp() {
        var offsets = SafeLocationFinder.offsets(1, 1);
        assertEquals(0, offsets.get(0)[0] | offsets.get(0)[1] | offsets.get(0)[2]);
        assertEquals(1, offsets.get(1)[1]);
        assertEquals(-1, offsets.get(2)[1]);
    }
}
